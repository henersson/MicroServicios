package co.edu.unicauca.saberpro.banco.infraestructura.mensajeria;

import co.edu.unicauca.saberpro.banco.aplicacion.puertos.PublicadorEventos;
import co.edu.unicauca.saberpro.banco.dominio.eventos.EventoDominio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Adaptador del puerto {@code PublicadorEventos} sobre RabbitMQ.
 *
 * <h2>Por qué hay dos pasos y no uno</h2>
 * El caso de uso llama a {@link #publicar}, que <strong>no</strong> envía nada al
 * broker: solo republica el evento dentro de Spring. El envío real ocurre en
 * {@link #enviarTrasConfirmarTransaccion}, anotado con
 * {@code @TransactionalEventListener(AFTER_COMMIT)}, así que solo se ejecuta si
 * la transacción de base de datos terminó bien.
 *
 * <p>Ese rodeo es lo que evita el peor fallo posible en una arquitectura de
 * eventos: anunciar algo que nunca ocurrió. Si la transacción se deshace después
 * de haber publicado, el revision-service se pondría a revisar una pregunta que
 * en el banco sigue en BORRADOR, y nada lo desharía.
 *
 * <p>Queda una ventana abierta en el sentido contrario: si el proceso muere
 * entre el commit y el envío, el evento se pierde. Se asume conscientemente para
 * el alcance del taller; cerrarla es justamente para lo que sirve el patrón
 * Outbox, anotado como trabajo futuro en ADR 4.
 */
@Component
public class PublicadorEventosRabbitMQ implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorEventosRabbitMQ.class);

    private final ApplicationEventPublisher publicadorInterno;
    private final RabbitTemplate rabbitTemplate;
    private final EnsambladorEnvelope ensamblador;
    private final JsonMapper jsonMapper;

    public PublicadorEventosRabbitMQ(ApplicationEventPublisher publicadorInterno,
                                     RabbitTemplate rabbitTemplate,
                                     EnsambladorEnvelope ensamblador) {
        this.publicadorInterno = publicadorInterno;
        this.rabbitTemplate = rabbitTemplate;
        this.ensamblador = ensamblador;
        // Mapper propio y sin configuración heredada: el envelope ya trae las
        // fechas formateadas como texto, así que no hace falta nada especial y
        // así el JSON no depende de cómo esté configurado el mapper global.
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    public void publicar(List<EventoDominio> eventos) {
        eventos.forEach(publicadorInterno::publishEvent);
    }

    /**
     * Envía el evento al exchange {@code saberpro.eventos}, ya con la
     * transacción confirmada.
     *
     * <p>Las propiedades del mensaje son parte del contrato (sección 6.3):
     * {@code content_type: application/json}, {@code delivery_mode: 2}
     * (persistente, sobrevive a un reinicio del broker), {@code message_id}
     * igual al {@code eventId} y la cabecera {@code eventType}.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviarTrasConfirmarTransaccion(EventoDominio evento) {
        try {
            Map<String, Object> envelope = ensamblador.ensamblar(evento);
            byte[] cuerpo = jsonMapper.writeValueAsString(envelope)
                    .getBytes(StandardCharsets.UTF_8);

            Message mensaje = MessageBuilder.withBody(cuerpo)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(evento.eventId().toString())
                    .setHeader("eventType", evento.tipo())
                    .build();

            rabbitTemplate.send(ConfiguracionRabbitMQ.EXCHANGE_EVENTOS,
                    evento.routingKey(), mensaje);

            log.info("Evento publicado: eventId={} eventType={} routingKey={} preguntaId={}",
                    evento.eventId(), evento.tipo(), evento.routingKey(), evento.preguntaId());

        } catch (RuntimeException e) {
            // No se relanza: la transacción ya confirmó y relanzar no la
            // desharía. Se registra con el nivel más alto para que el fallo sea
            // visible y el evento se pueda reenviar a mano.
            log.error("No se pudo publicar el evento eventId={} eventType={} preguntaId={}. "
                            + "El cambio SÍ quedó guardado en la base de datos; el evento habrá "
                            + "que reenviarlo manualmente.",
                    evento.eventId(), evento.tipo(), evento.preguntaId(), e);
        }
    }
}
