package co.edu.unicauca.saberpro.banco.aplicacion.puertos;

import java.util.UUID;

/**
 * Puerto de salida: la memoria de qué eventos entrantes ya se aplicaron.
 *
 * <p>Es lo que hace idempotentes a los consumidores. RabbitMQ garantiza entrega
 * <em>al menos una vez</em>, así que el mismo evento puede llegar dos veces: por
 * un reintento tras un fallo de red, o porque el consumidor murió justo después
 * de aplicar el cambio y antes de confirmar el ACK. Sin esta memoria, una
 * pregunta podría registrar dos veces el mismo cambio en su historial.
 *
 * <p>La implementación guarda los identificadores en la tabla
 * {@code eventos_procesados}, <strong>dentro de la misma transacción</strong> que
 * aplica el cambio: o se hacen las dos cosas, o ninguna.
 */
public interface RegistroEventosProcesados {

    /** ¿Este evento ya se aplicó antes? */
    boolean yaFueProcesado(UUID eventId);

    /**
     * Deja constancia de que el evento se aplicó.
     *
     * @param tipoEvento nombre del evento, solo para poder auditar la tabla
     */
    void marcarComoProcesado(UUID eventId, String tipoEvento);
}
