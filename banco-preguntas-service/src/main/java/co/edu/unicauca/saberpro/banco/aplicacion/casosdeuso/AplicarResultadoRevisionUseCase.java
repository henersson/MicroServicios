package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.puertos.RegistroEventosProcesados;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: aplicar la decisión final de la revisión por pares, que llega del
 * revision-service como {@code PreguntaAprobadaTecnicamente} o
 * {@code PreguntaRechazadaPorPares}.
 *
 * <ul>
 *   <li>Aprobada  → la pregunta pasa a APROBADA y queda lista para publicarse.</li>
 *   <li>Rechazada → pasa a RECHAZADA con las observaciones del revisor visibles
 *       para el autor, que la reabre al editarla (ADR 2): una pregunta rechazada
 *       se corrige, no se descarta.</li>
 * </ul>
 *
 * <p>Los dos caminos son idempotentes con la tabla {@code eventos_procesados} y,
 * además, con la comprobación de estado del propio agregado.
 */
@Service
public class AplicarResultadoRevisionUseCase {

    private static final Logger log = LoggerFactory.getLogger(AplicarResultadoRevisionUseCase.class);

    private final PreguntaRepository repositorio;
    private final RegistroEventosProcesados registro;
    private final Clock reloj;

    public AplicarResultadoRevisionUseCase(PreguntaRepository repositorio,
                                           RegistroEventosProcesados registro, Clock reloj) {
        this.repositorio = repositorio;
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * Aplica {@code PreguntaAprobadaTecnicamente}: EN_REVISION → APROBADA.
     *
     * @param promedio promedio del formato de evaluación; solo se registra en el
     *                 log, porque la nota es del otro Bounded Context y el banco
     *                 no debe basar ninguna regla en ella
     * @return true si el evento produjo un cambio; false si ya estaba aplicado
     */
    @Transactional
    public boolean aprobar(UUID eventId, UUID preguntaId, UUID revisorId, Double promedio) {
        final String tipoEvento = "PreguntaAprobadaTecnicamente";
        if (registro.yaFueProcesado(eventId)) {
            log.info("Evento {} [{}] ya procesado; se descarta por idempotencia.",
                    eventId, tipoEvento);
            return false;
        }

        Pregunta pregunta = cargar(preguntaId);
        boolean cambio = pregunta.registrarAprobacionTecnica(revisorId, reloj.instant());
        if (cambio) {
            repositorio.guardar(pregunta);
            log.info("Evento {} [{}]: la pregunta {} pasó a APROBADA (revisor {}, promedio {}).",
                    eventId, tipoEvento, preguntaId, revisorId, promedio);
        } else {
            log.info("Evento {} [{}]: la pregunta {} ya estaba en APROBADA; no se cambia nada.",
                    eventId, tipoEvento, preguntaId);
        }

        registro.marcarComoProcesado(eventId, tipoEvento);
        return cambio;
    }

    /**
     * Aplica {@code PreguntaRechazadaPorPares}: EN_REVISION → RECHAZADA, guardando
     * las observaciones para que el autor sepa qué corregir.
     *
     * @return true si el evento produjo un cambio; false si ya estaba aplicado
     */
    @Transactional
    public boolean rechazar(UUID eventId, UUID preguntaId, UUID revisorId,
                            List<String> observaciones) {
        final String tipoEvento = "PreguntaRechazadaPorPares";
        if (registro.yaFueProcesado(eventId)) {
            log.info("Evento {} [{}] ya procesado; se descarta por idempotencia.",
                    eventId, tipoEvento);
            return false;
        }

        Pregunta pregunta = cargar(preguntaId);
        boolean cambio = pregunta.registrarRechazoPorPares(revisorId, observaciones,
                reloj.instant());
        if (cambio) {
            repositorio.guardar(pregunta);
            log.info("Evento {} [{}]: la pregunta {} pasó a RECHAZADA con {} observación(es).",
                    eventId, tipoEvento, preguntaId,
                    observaciones == null ? 0 : observaciones.size());
        } else {
            log.info("Evento {} [{}]: la pregunta {} no estaba en EN_REVISION ({}); "
                            + "el rechazo ya se había aplicado.",
                    eventId, tipoEvento, preguntaId, pregunta.getEstado());
        }

        registro.marcarComoProcesado(eventId, tipoEvento);
        return cambio;
    }

    private Pregunta cargar(UUID preguntaId) {
        return repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));
    }
}
