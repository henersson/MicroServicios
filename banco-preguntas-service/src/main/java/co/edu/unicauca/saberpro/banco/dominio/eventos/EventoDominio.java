package co.edu.unicauca.saberpro.banco.dominio.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain Event del BC Banco de Preguntas: algo de negocio que ya ocurrió.
 *
 * <p>Los eventos los registra el propio agregado {@code Pregunta} cuando cambia
 * de estado, y el caso de uso los entrega al puerto {@code PublicadorEventos}
 * después de guardar. La publicación real en RabbitMQ ocurre
 * <strong>después de confirmar la transacción</strong>, para no anunciar nunca
 * algo que terminó deshaciéndose (ADR 4).
 *
 * <p>Es una interfaz sellada a propósito: el conjunto de eventos que produce
 * este Bounded Context es cerrado y está publicado en {@code contracts/events}.
 * Agregar uno obliga a tocar este archivo, y de ahí a acordarse de crear su
 * JSON Schema y su binding en RabbitMQ.
 *
 * <p>El nombre en pasado no es un capricho de estilo: un evento describe un
 * hecho consumado, nunca una orden. Por eso ningún consumidor puede rechazarlo.
 */
public sealed interface EventoDominio
        permits PreguntaEnviadaARevision, PreguntaPublicada, PreguntaArchivada {

    /**
     * Identificador único de esta ocurrencia. Viaja como {@code eventId} del
     * envelope y como {@code message_id} de AMQP, y es la clave con la que los
     * consumidores descartan los repetidos.
     */
    UUID eventId();

    /** Momento del hecho de negocio en UTC, no el de la publicación en el broker. */
    Instant ocurridoEn();

    /** Nombre del evento tal como aparece en el contrato ({@code eventType}). */
    String tipo();

    /** Routing key con la que se publica en el exchange {@code saberpro.eventos}. */
    String routingKey();

    /** Pregunta a la que se refiere el hecho. */
    UUID preguntaId();
}
