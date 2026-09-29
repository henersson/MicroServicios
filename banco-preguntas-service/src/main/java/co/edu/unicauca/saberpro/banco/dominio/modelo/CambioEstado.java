package co.edu.unicauca.saberpro.banco.dominio.modelo;

import java.time.Instant;
import java.util.UUID;

/**
 * Value Object del BC Banco de Preguntas: una entrada del historial de estados
 * de una pregunta.
 *
 * <p>Existe por trazabilidad, que es un requisito real del caso: ante una
 * reclamación hay que poder responder quién movió una pregunta, cuándo y por
 * qué. El historial solo crece; nunca se corrige ni se borra una entrada.
 *
 * @param anterior  estado del que venía. Es null en la creación, porque antes de
 *                  existir la pregunta no estaba en ningún estado
 * @param nuevo     estado al que pasó
 * @param usuarioId quién lo provocó. En los cambios disparados por eventos es el
 *                  revisor que tomó la decisión, no un usuario del banco
 * @param fecha     cuándo ocurrió, en UTC
 * @param motivo    texto corto que explica el cambio, para que el historial se
 *                  entienda sin tener que cruzarlo con los logs
 */
public record CambioEstado(
        EstadoPregunta anterior,
        EstadoPregunta nuevo,
        UUID usuarioId,
        Instant fecha,
        String motivo) {

    public CambioEstado {
        if (nuevo == null) {
            throw new IllegalArgumentException("El estado nuevo del cambio es obligatorio.");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("La fecha del cambio es obligatoria.");
        }
    }

    /** Entrada inicial del historial: la que registra el nacimiento de la pregunta. */
    public static CambioEstado creacion(UUID autorId, Instant fecha) {
        return new CambioEstado(null, EstadoPregunta.BORRADOR, autorId, fecha,
                "Creación de la pregunta.");
    }
}
