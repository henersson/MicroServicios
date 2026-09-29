package co.edu.unicauca.saberpro.banco.dominio.repositorios;

import co.edu.unicauca.saberpro.banco.dominio.modelo.EstadoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;

/**
 * Criterios de búsqueda de preguntas, expresados en el lenguaje del dominio.
 *
 * <p>Un campo en null significa "no filtrar por esto". Se modela como record
 * propio y no como una lista de parámetros para que agregar un filtro más
 * adelante no obligue a cambiar la firma del repositorio en todas las capas.
 *
 * @param estado            estado del ciclo de vida
 * @param competenciaCodigo código de la competencia, por ejemplo "ING-SOFT"
 * @param tema              tema exacto
 * @param nivelDificultad   BAJO, MEDIO o ALTO
 */
public record FiltroPreguntas(
        EstadoPregunta estado,
        String competenciaCodigo,
        String tema,
        NivelDificultad nivelDificultad) {

    /** Sin ningún filtro: devuelve todo el banco. */
    public static FiltroPreguntas sinFiltros() {
        return new FiltroPreguntas(null, null, null, null);
    }

    /** Filtro que usa el simulacros-service por gRPC: solo preguntas publicadas. */
    public static FiltroPreguntas soloPublicadas(String competenciaCodigo, String tema,
                                                 NivelDificultad nivelDificultad) {
        return new FiltroPreguntas(EstadoPregunta.PUBLICADA, competenciaCodigo, tema,
                nivelDificultad);
    }
}
