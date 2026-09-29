package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import co.edu.unicauca.saberpro.banco.aplicacion.puertos.RegistroEventosProcesados;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Adaptador del puerto {@code RegistroEventosProcesados} sobre PostgreSQL.
 *
 * <p>Escribe en la misma transacción que aplica el cambio de la pregunta: o se
 * guardan las dos cosas o ninguna. Si se hiciera en transacciones separadas
 * habría una ventana en la que el evento constaría como procesado sin haberse
 * aplicado, y el reintento lo descartaría creyendo que ya estaba hecho.
 */
@Component
public class RegistroEventosProcesadosJpa implements RegistroEventosProcesados {

    private final EventoProcesadoJpaRepository repositorio;
    private final Clock reloj;

    public RegistroEventosProcesadosJpa(EventoProcesadoJpaRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public boolean yaFueProcesado(UUID eventId) {
        return repositorio.existsById(eventId);
    }

    @Override
    public void marcarComoProcesado(UUID eventId, String tipoEvento) {
        repositorio.save(new EventoProcesadoEntity(eventId, tipoEvento, reloj.instant()));
    }
}
