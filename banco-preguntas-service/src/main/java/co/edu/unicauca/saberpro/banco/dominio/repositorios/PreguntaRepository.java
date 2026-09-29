package co.edu.unicauca.saberpro.banco.dominio.repositorios;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository del Aggregate Root {@code Pregunta}, declarado en el dominio.
 *
 * <p>Es una interfaz del dominio y no de Spring Data a propósito: el dominio
 * declara <em>qué</em> necesita guardar y buscar, y la capa de infraestructura
 * decide <em>cómo</em> (aquí, JPA sobre PostgreSQL). Esa es la inversión de
 * dependencias que sostiene la Clean Architecture: la flecha apunta hacia
 * adentro.
 *
 * <p>Nótese que <strong>no hay ninguna operación de borrado</strong>: la
 * invariante 8 dice que en el banco nada se elimina físicamente, y la forma más
 * segura de garantizarlo es que el método no exista (ADR 3).
 */
public interface PreguntaRepository {

    /**
     * Guarda la pregunta, sea nueva o existente.
     *
     * @return la pregunta guardada
     */
    Pregunta guardar(Pregunta pregunta);

    /** Busca una pregunta por su identificador. */
    Optional<Pregunta> buscarPorId(UUID id);

    /**
     * Busca preguntas por filtros, paginado.
     *
     * @param pagina número de página empezando en 0
     * @param tamano cuántas preguntas por página
     */
    PaginaPreguntas buscarPorFiltros(FiltroPreguntas filtro, int pagina, int tamano);
}
