package co.edu.unicauca.saberpro.banco.dominio.eventos;

import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event: el administrador publicó la pregunta, que queda disponible para
 * los simulacros.
 *
 * <p>A diferencia de los demás eventos de este contexto, lleva la pregunta
 * <strong>completa</strong> (incluido qué opción es la correcta). Es una
 * decisión deliberada: el futuro simulacros-service puede armar su catálogo solo
 * escuchando, sin llamar al banco por cada pregunta. El costo es que el contrato
 * del evento crece y hay que mantenerlo alineado con {@code PreguntaMensaje} del
 * {@code .proto}.
 *
 * <p>Contrato: {@code contracts/events/PreguntaPublicada.v1.schema.json}.
 *
 * @param autorId    se incluye para que el consumidor pueda atribuir la pregunta
 * @param contenido  el contenido publicado, capturado en el momento de publicar
 */
public record PreguntaPublicada(
        UUID eventId,
        UUID preguntaId,
        UUID autorId,
        ContenidoPregunta contenido,
        Instant ocurridoEn) implements EventoDominio {

    public static PreguntaPublicada de(UUID preguntaId, UUID autorId,
                                       ContenidoPregunta contenido, Instant ocurridoEn) {
        return new PreguntaPublicada(
                UUID.randomUUID(), preguntaId, autorId, contenido, ocurridoEn);
    }

    @Override
    public String tipo() {
        return "PreguntaPublicada";
    }

    @Override
    public String routingKey() {
        return "banco.pregunta.publicada";
    }
}
