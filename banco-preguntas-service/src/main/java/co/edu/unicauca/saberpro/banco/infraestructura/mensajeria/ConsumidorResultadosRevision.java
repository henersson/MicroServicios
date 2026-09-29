package co.edu.unicauca.saberpro.banco.infraestructura.mensajeria;

import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.AplicarResultadoRevisionUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.AplicarRevisorAsignadoUseCase;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consumidor de la cola {@code banco.resultados-revision}: aplica al agregado
 * {@code Pregunta} lo que decidió el revision-service.
 *
 * <p>Procesa los tres eventos que produce el otro contexto:
 * <ul>
 *   <li>{@code RevisorAsignado}              → PENDIENTE_REVISION → EN_REVISION</li>
 *   <li>{@code PreguntaAprobadaTecnicamente} → EN_REVISION → APROBADA</li>
 *   <li>{@code PreguntaRechazadaPorPares}    → EN_REVISION → BORRADOR + observaciones</li>
 * </ul>
 *
 * <h2>Cómo trata los fallos</h2>
 * Usa <strong>ACK manual</strong>: el mensaje no se confirma hasta que el cambio
 * está guardado. Si el proceso muere antes, RabbitMQ lo vuelve a entregar.
 *
 * <p>Ante <strong>cualquier</strong> fallo el mensaje va a la DLQ con
 * {@code basicNack(requeue=false)}. No hay reintentos: reencolar lo devolvería al
 * frente de la cola y produciría un bucle a toda velocidad, y volver a intentarlo
 * aquí mismo solo retrasaría el problema. En la DLQ el evento no se pierde y se
 * puede reprocesar cuando la causa esté resuelta.
 *
 * <p>La <strong>idempotencia</strong> no está aquí sino en los casos de uso, que
 * consultan la tabla {@code eventos_procesados} dentro de la misma transacción
 * que aplica el cambio. Aquí sería inútil: el proceso podría morir entre marcar
 * y aplicar.
 */
@Component
public class ConsumidorResultadosRevision {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorResultadosRevision.class);

    private final AplicarRevisorAsignadoUseCase aplicarRevisorAsignado;
    private final AplicarResultadoRevisionUseCase aplicarResultadoRevision;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public ConsumidorResultadosRevision(AplicarRevisorAsignadoUseCase aplicarRevisorAsignado,
                                        AplicarResultadoRevisionUseCase aplicarResultadoRevision) {
        this.aplicarRevisorAsignado = aplicarRevisorAsignado;
        this.aplicarResultadoRevision = aplicarResultadoRevision;
    }

    @RabbitListener(queues = ConfiguracionRabbitMQ.COLA_RESULTADOS_REVISION,
            containerFactory = "fabricaEventosCrudos",
            ackMode = "MANUAL")
    public void recibir(Message mensaje, Channel canal) throws Exception {
        long etiqueta = mensaje.getMessageProperties().getDeliveryTag();
        String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        UUID eventId = null;
        String tipoEvento = "desconocido";

        try {
            JsonNode envelope = jsonMapper.readTree(cuerpo);
            tipoEvento = texto(envelope, "eventType");
            eventId = UUID.fromString(texto(envelope, "eventId"));

            log.info("Evento recibido: eventId={} eventType={}", eventId, tipoEvento);

            aplicar(tipoEvento, eventId, envelope);
            canal.basicAck(etiqueta, false);

        } catch (Exception e) {
            log.error("Evento eventId={} eventType={} no se pudo procesar: {}. "
                            + "Se envía a la DLQ. Cuerpo: {}",
                    eventId != null ? eventId : "ilegible",
                    tipoEvento,
                    e.getMessage(),
                    recortar(cuerpo),
                    e);
            canal.basicNack(etiqueta, false, false);
        }
    }

    /** Despacha el evento a su caso de uso. */
    private void aplicar(String tipoEvento, UUID eventId, JsonNode envelope) {
        JsonNode datos = envelope.path("data");

        switch (tipoEvento) {
            case "RevisorAsignado" -> aplicarRevisorAsignado.ejecutar(
                    eventId,
                    uuid(datos, "preguntaId"),
                    uuid(datos, "revisorId"));

            case "PreguntaAprobadaTecnicamente" -> aplicarResultadoRevision.aprobar(
                    eventId,
                    uuid(datos, "preguntaId"),
                    uuid(datos, "revisorId"),
                    datos.has("promedio") ? datos.get("promedio").asDouble() : null);

            case "PreguntaRechazadaPorPares" -> aplicarResultadoRevision.rechazar(
                    eventId,
                    uuid(datos, "preguntaId"),
                    uuid(datos, "revisorId"),
                    observaciones(datos));

            // La cola solo tiene bindings para los tres de arriba, así que llegar
            // aquí significa que alguien agregó un binding sin actualizar este
            // consumidor. A la DLQ, que es donde se ve.
            default -> throw new EventoNoProcesable(
                    "tipo de evento desconocido '%s' en la cola de resultados de revisión"
                            .formatted(tipoEvento));
        }
    }

    /**
     * Extrae los textos de las observaciones del rechazo.
     *
     * <p>Acepta las dos formas posibles por la regla del lector tolerante: una
     * lista de objetos {@code {observacionId, revisorId, texto, fecha}}, que es
     * lo que dice el contrato, o una lista de cadenas sueltas.
     */
    private List<String> observaciones(JsonNode datos) {
        List<String> textos = new ArrayList<>();
        JsonNode nodo = datos.path("observaciones");
        if (nodo.isArray()) {
            nodo.forEach(observacion -> {
                if (observacion.isTextual()) {
                    textos.add(observacion.asString());
                } else if (observacion.has("texto")) {
                    textos.add(observacion.get("texto").asString());
                }
            });
        }
        return textos;
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull() || valor.asString().isBlank()) {
            throw new EventoNoProcesable("falta el campo obligatorio '%s'".formatted(campo));
        }
        return valor.asString();
    }

    private UUID uuid(JsonNode nodo, String campo) {
        try {
            return UUID.fromString(texto(nodo, campo));
        } catch (IllegalArgumentException e) {
            throw new EventoNoProcesable(
                    "el campo '%s' no es un UUID válido".formatted(campo));
        }
    }

    private static String recortar(String texto) {
        return texto.length() <= 500 ? texto : texto.substring(0, 500) + "...";
    }

    /**
     * Error permanente del mensaje: está mal formado o no lo sabemos procesar.
     * Reintentarlo daría exactamente el mismo resultado, así que va a la DLQ.
     */
    static class EventoNoProcesable extends RuntimeException {
        EventoNoProcesable(String mensaje) {
            super(mensaje);
        }
    }
}
