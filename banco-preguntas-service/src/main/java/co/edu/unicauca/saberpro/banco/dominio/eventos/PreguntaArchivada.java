package co.edu.unicauca.saberpro.banco.dominio.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: el administrador archivó la pregunta.
 *
 * <p>Archivar es la única salida del ciclo de vida, porque en el banco nada se
 * borra físicamente (invariante 8, ADR 3). El futuro simulacros-service lo
 * consume para dejar de ofrecer la pregunta en nuevos simulacros, sin perder los
 * simulacros pasados que ya la usaron.
 *
 * <p>Contrato: {@code contracts/events/PreguntaArchivada.v1.schema.json}.
 */
public record PreguntaArchivada(
        UUID eventId,
        UUID preguntaId,
        Instant ocurridoEn) implements EventoDominio {

    public static PreguntaArchivada de(UUID preguntaId, Instant ocurridoEn) {
        return new PreguntaArchivada(UUID.randomUUID(), preguntaId, ocurridoEn);
    }

    @Override
    public String tipo() {
        return "PreguntaArchivada";
    }

    @Override
    public String routingKey() {
        return "banco.pregunta.archivada";
    }
}
