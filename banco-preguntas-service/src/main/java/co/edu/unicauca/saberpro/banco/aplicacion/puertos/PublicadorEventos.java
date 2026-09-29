package co.edu.unicauca.saberpro.banco.aplicacion.puertos;

import co.edu.unicauca.saberpro.banco.dominio.eventos.EventoDominio;

import java.util.List;

/**
 * Puerto de salida: por aquí salen los eventos de dominio hacia el mundo.
 *
 * <p>La capa de aplicación solo conoce esta interfaz; que al otro lado haya
 * RabbitMQ es un detalle de infraestructura. Eso permite probar los casos de uso
 * con un doble en memoria, sin levantar un broker.
 *
 * <p><strong>Garantía que debe cumplir toda implementación</strong>: los eventos
 * se envían al broker únicamente <em>después</em> de que la transacción de base
 * de datos haya confirmado. Nunca se anuncia algo que podría deshacerse
 * (el patrón Outbox queda como trabajo futuro, ADR 4).
 */
public interface PublicadorEventos {

    /**
     * Encola los eventos para publicarlos cuando la transacción actual confirme.
     *
     * @param eventos eventos extraídos del agregado; puede venir vacía
     */
    void publicar(List<EventoDominio> eventos);
}
