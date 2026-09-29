package co.edu.unicauca.saberpro.banco.dominio.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: el autor envió la pregunta a revisión y esta superó el
 * {@code ValidadorEstructural}, así que pasó a PENDIENTE_REVISION.
 *
 * <p>Lo consume el revision-service (cola {@code revision.preguntas-enviadas}),
 * que a continuación pide el contenido completo por gRPC. Por eso el evento
 * lleva solo identificadores y no la pregunta entera: quien la necesita ya sabe
 * dónde pedirla, y así el contrato del evento no se rompe cada vez que cambia un
 * campo de la pregunta.
 *
 * <p>Contrato: {@code contracts/events/PreguntaEnviadaARevision.v1.schema.json}.
 *
 * @param competenciaCodigo lo lleva para que el AsignadorRevisor del otro
 *                          contexto pueda preferir un revisor afín sin tener que
 *                          llamar de vuelta al banco
 */
public record PreguntaEnviadaARevision(
        UUID eventId,
        UUID preguntaId,
        UUID autorId,
        String competenciaCodigo,
        Instant ocurridoEn) implements EventoDominio {

    public static PreguntaEnviadaARevision de(UUID preguntaId, UUID autorId,
                                              String competenciaCodigo, Instant ocurridoEn) {
        return new PreguntaEnviadaARevision(
                UUID.randomUUID(), preguntaId, autorId, competenciaCodigo, ocurridoEn);
    }

    @Override
    public String tipo() {
        return "PreguntaEnviadaARevision";
    }

    @Override
    public String routingKey() {
        return "banco.pregunta.enviada-a-revision";
    }
}
