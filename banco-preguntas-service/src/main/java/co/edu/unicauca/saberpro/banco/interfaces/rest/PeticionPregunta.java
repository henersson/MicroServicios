package co.edu.unicauca.saberpro.banco.interfaces.rest;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosContenidoPregunta;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosOpcion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Cuerpo de las peticiones de creación ({@code POST}) y edición ({@code PUT})
 * de una pregunta.
 *
 * <p>Las anotaciones de Bean Validation cubren solo lo <strong>sintáctico</strong>:
 * que un campo obligatorio venga, que el nivel sea uno de los tres válidos. Las
 * reglas de negocio de verdad —las invariantes 1 a 4— las aplica
 * {@code ValidadorEstructural} en el dominio, y por eso ahí no se declara
 * {@code @Size(min=4, max=4)} sobre las opciones: si la validación de formato
 * rechazara la lista antes, el autor recibiría "tamaño inválido" en vez del
 * mensaje explicativo del banco.
 */
@Schema(name = "PeticionPregunta",
        description = "Contenido de una pregunta de selección múltiple.")
public record PeticionPregunta(

        @Schema(description = "Situación o caso que el estudiante debe analizar. Obligatorio "
                + "(invariante 4).",
                example = "Una universidad está migrando su plataforma académica monolítica "
                        + "hacia una arquitectura de microservicios...")
        @NotBlank(message = "El contexto es obligatorio.")
        String contexto,

        @Schema(description = "El interrogante concreto que se le plantea al estudiante. "
                + "Obligatorio (invariante 4).",
                example = "¿Cuál es la razón principal por la que el arquitecto rechaza "
                        + "compartir la base de datos entre los tres servicios?")
        @NotBlank(message = "La pregunta directa es obligatoria.")
        String preguntaDirecta,

        @Schema(description = "Exactamente 4 opciones: 3 distractores y 1 correcta "
                + "(invariante 1).")
        @NotEmpty(message = "Debes enviar las opciones de respuesta.")
        @Valid
        List<PeticionOpcion> opciones,

        @Schema(description = "Explicación de por qué la opción correcta lo es.",
                example = "La independencia de despliegue depende de que cada servicio sea "
                        + "dueño exclusivo de sus datos (database per service)...")
        @NotBlank(message = "La justificación es obligatoria.")
        String justificacion,

        @Schema(description = "Referencias que respaldan la pregunta. Puede ir vacía.",
                example = "[\"Newman, S. (2021). Building Microservices (2nd ed.). O'Reilly.\"]")
        List<String> bibliografia,

        @Schema(description = "Código de la competencia Saber Pro evaluada.",
                example = "ING-SOFT")
        @NotBlank(message = "El código de la competencia es obligatorio.")
        String competenciaCodigo,

        @Schema(description = "Nombre legible de la competencia.",
                example = "Diseño de Software y Arquitectura")
        @NotBlank(message = "El nombre de la competencia es obligatorio.")
        String competenciaNombre,

        @Schema(description = "Tema dentro de la competencia.",
                example = "Arquitectura de Software")
        @NotBlank(message = "El tema es obligatorio.")
        String tema,

        @Schema(description = "Subtema dentro del tema.", example = "Microservicios")
        @NotBlank(message = "El subtema es obligatorio.")
        String subtema,

        @Schema(description = "Nivel de dificultad.",
                allowableValues = {"BAJO", "MEDIO", "ALTO"}, example = "MEDIO")
        @NotBlank(message = "El nivel de dificultad es obligatorio.")
        @Pattern(regexp = "(?i)BAJO|MEDIO|ALTO",
                message = "El nivel de dificultad debe ser BAJO, MEDIO o ALTO.")
        String nivelDificultad) {

    /** Una opción de respuesta dentro de la petición. */
    @Schema(name = "PeticionOpcion", description = "Una opción de respuesta.")
    public record PeticionOpcion(

            @Schema(description = "Texto de la opción tal como lo ve el estudiante.",
                    example = "Porque el acoplamiento a nivel de datos impide que cada servicio "
                            + "evolucione de forma independiente.")
            @NotBlank(message = "El texto de la opción es obligatorio.")
            String texto,

            @Schema(description = "true en la única opción correcta; false en los 3 distractores.",
                    example = "false")
            @NotNull(message = "Hay que indicar si la opción es correcta o no.")
            Boolean esCorrecta) {
    }

    /** Traduce la petición HTTP al DTO que entiende la capa de aplicación. */
    public DatosContenidoPregunta aDatosDeAplicacion() {
        return new DatosContenidoPregunta(
                contexto,
                preguntaDirecta,
                opciones == null
                        ? List.of()
                        : opciones.stream()
                                .map(opcion -> new DatosOpcion(
                                        opcion.texto(),
                                        Boolean.TRUE.equals(opcion.esCorrecta())))
                                .toList(),
                justificacion,
                bibliografia,
                competenciaCodigo,
                competenciaNombre,
                tema,
                subtema,
                nivelDificultad);
    }
}
