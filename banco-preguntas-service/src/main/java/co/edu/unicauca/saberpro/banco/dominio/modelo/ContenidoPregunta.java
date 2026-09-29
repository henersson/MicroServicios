package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

import java.util.List;

/**
 * Value Object del BC Banco de Preguntas: todo lo que un autor puede escribir o
 * modificar de una pregunta.
 *
 * <p>Agrupar el contenido en un solo Value Object no es cosmético: es lo que
 * permite que {@code ValidadorEstructural} valide las invariantes 1 a 4 sobre un
 * objeto completo antes de que exista el agregado, de modo que una
 * {@code Pregunta} nunca llegue a construirse en un estado inválido. Y hace que
 * crear y editar compartan exactamente la misma validación.
 *
 * <p>Lo que <strong>no</strong> está aquí es lo que el autor no controla: el
 * identificador, el autor, el estado, el historial y las observaciones de la
 * última revisión. Eso lo gobierna el agregado.
 *
 * @param contexto        situación a analizar (invariante 4)
 * @param preguntaDirecta el interrogante planteado (invariante 4)
 * @param opciones        exactamente 5: 4 distractores y 1 correcta (invariante 1)
 * @param justificacion   por qué la correcta lo es
 * @param bibliografia    referencias de respaldo; puede ir vacía
 * @param competencia     competencia Saber Pro evaluada
 * @param tema            tema dentro de la competencia
 * @param subtema         subtema dentro del tema
 * @param nivelDificultad BAJO, MEDIO o ALTO
 */
public record ContenidoPregunta(
        String contexto,
        String preguntaDirecta,
        List<Opcion> opciones,
        Justificacion justificacion,
        Bibliografia bibliografia,
        Competencia competencia,
        Tema tema,
        Subtema subtema,
        NivelDificultad nivelDificultad) {

    public ContenidoPregunta {
        // Aquí solo se normaliza y se comprueba lo que impediría siquiera
        // construir el objeto. Las reglas de negocio de verdad (invariantes 1 a
        // 4) las aplica ValidadorEstructural, que sabe devolver TODOS los
        // errores juntos en vez de reventar en el primero.
        contexto = contexto == null ? "" : contexto.trim();
        preguntaDirecta = preguntaDirecta == null ? "" : preguntaDirecta.trim();
        opciones = opciones == null ? List.of() : List.copyOf(opciones);
        bibliografia = bibliografia == null ? Bibliografia.vacia() : bibliografia;

        if (competencia == null) {
            throw new ReglaDeNegocioViolada("La competencia es obligatoria.");
        }
        if (tema == null) {
            throw new ReglaDeNegocioViolada("El tema es obligatorio.");
        }
        if (subtema == null) {
            throw new ReglaDeNegocioViolada("El subtema es obligatorio.");
        }
        if (nivelDificultad == null) {
            throw new ReglaDeNegocioViolada("El nivel de dificultad es obligatorio.");
        }
        if (justificacion == null) {
            throw new ReglaDeNegocioViolada("La justificación es obligatoria.");
        }
    }

    /** La única opción correcta. Solo tiene sentido si ya pasó la invariante 1. */
    public Opcion opcionCorrecta() {
        return opciones.stream()
                .filter(Opcion::esCorrecta)
                .findFirst()
                .orElseThrow(() -> new ReglaDeNegocioViolada(
                        "La pregunta no tiene ninguna opción marcada como correcta."));
    }
}
