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
import java.util.UUID;

/**
 * Caso de uso: aplicar el evento {@code RevisorAsignado} que llega del
 * revision-service. La pregunta pasa de PENDIENTE_REVISION a EN_REVISION.
 *
 * <p>Este evento existe para poder cumplir la invariante 9 del otro contexto:
 * si al enviar la pregunta no hay revisores libres, la solicitud queda pendiente
 * y el banco no debe dar por empezada una revisión que no empezó (ADR 1).
 *
 * <h2>Idempotencia</h2>
 * Hay dos barreras, y las dos hacen falta:
 * <ol>
 *   <li>La tabla {@code eventos_procesados}, que descarta el mismo {@code eventId}
 *       repetido. Se escribe en la misma transacción que el cambio, así que o se
 *       aplican las dos cosas o ninguna.</li>
 *   <li>El propio agregado, que ignora la transición si ya está en EN_REVISION.
 *       Cubre el caso de dos eventos <em>distintos</em> que piden lo mismo.</li>
 * </ol>
 */
@Service
public class AplicarRevisorAsignadoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AplicarRevisorAsignadoUseCase.class);
    private static final String TIPO_EVENTO = "RevisorAsignado";

    private final PreguntaRepository repositorio;
    private final RegistroEventosProcesados registro;
    private final Clock reloj;

    public AplicarRevisorAsignadoUseCase(PreguntaRepository repositorio,
                                         RegistroEventosProcesados registro, Clock reloj) {
        this.repositorio = repositorio;
        this.registro = registro;
        this.reloj = reloj;
    }

    /**
     * @return true si el evento produjo un cambio; false si ya estaba aplicado
     * @throws PreguntaNoEncontrada si la pregunta no existe: es un error
     *         permanente y el consumidor mandará el mensaje a la DLQ
     */
    @Transactional
    public boolean ejecutar(UUID eventId, UUID preguntaId, UUID revisorId) {
        if (registro.yaFueProcesado(eventId)) {
            log.info("Evento {} [{}] ya procesado; se descarta por idempotencia.",
                    eventId, TIPO_EVENTO);
            return false;
        }

        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        boolean cambio = pregunta.registrarRevisorAsignado(revisorId, reloj.instant());
        if (cambio) {
            repositorio.guardar(pregunta);
            log.info("Evento {} [{}]: la pregunta {} pasó a EN_REVISION (revisor {}).",
                    eventId, TIPO_EVENTO, preguntaId, revisorId);
        } else {
            log.info("Evento {} [{}]: la pregunta {} ya estaba en EN_REVISION; no se cambia nada.",
                    eventId, TIPO_EVENTO, preguntaId);
        }

        registro.marcarComoProcesado(eventId, TIPO_EVENTO);
        return cambio;
    }
}
