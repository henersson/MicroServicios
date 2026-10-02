package co.edu.unicauca.saberpro.banco.interfaces.rest;

import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.ArchivarPreguntaUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.ConsultarHistorialUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.ConsultarPreguntasUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.CrearPreguntaUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.EditarPreguntaUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.EnviarARevisionUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.PublicarPreguntaUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.CambioEstadoDto;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PaginaDto;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.dominio.modelo.EstadoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.FiltroPreguntas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * API REST del BC Banco de Preguntas.
 *
 * <p>Es un <em>adaptador de entrada</em>: no tiene lógica de negocio. Su trabajo
 * es resolver quién hace la petición, comprobar que su <strong>rol</strong>
 * pueda hacerla, llamar al caso de uso y traducir el resultado a un código HTTP.
 *
 * <p>Ojo a la división de responsabilidades: aquí se comprueba el
 * <strong>rol</strong> (¿un ESTUDIANTE puede publicar? no), pero no la
 * <strong>propiedad</strong> (¿es este el autor de esta pregunta?). Lo segundo
 * es la invariante 7 y la comprueba el agregado, porque depende del estado del
 * dominio y no del transporte.
 *
 * <p>Todos los errores salen en formato Problem Details (RFC 7807); de eso se
 * encarga {@link ManejadorGlobalErrores}.
 */
@RestController
@RequestMapping("/api/v1/preguntas")
@Tag(name = "Preguntas")
public class PreguntaController {

    private final CrearPreguntaUseCase crearPregunta;
    private final EditarPreguntaUseCase editarPregunta;
    private final ConsultarPreguntasUseCase consultarPreguntas;
    private final EnviarARevisionUseCase enviarARevision;
    private final PublicarPreguntaUseCase publicarPregunta;
    private final ArchivarPreguntaUseCase archivarPregunta;
    private final ConsultarHistorialUseCase consultarHistorial;

    public PreguntaController(CrearPreguntaUseCase crearPregunta,
                              EditarPreguntaUseCase editarPregunta,
                              ConsultarPreguntasUseCase consultarPreguntas,
                              EnviarARevisionUseCase enviarARevision,
                              PublicarPreguntaUseCase publicarPregunta,
                              ArchivarPreguntaUseCase archivarPregunta,
                              ConsultarHistorialUseCase consultarHistorial) {
        this.crearPregunta = crearPregunta;
        this.editarPregunta = editarPregunta;
        this.consultarPreguntas = consultarPreguntas;
        this.enviarARevision = enviarARevision;
        this.publicarPregunta = publicarPregunta;
        this.archivarPregunta = archivarPregunta;
        this.consultarHistorial = consultarHistorial;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Creación y edición
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Crear una pregunta",
            description = """
                    Crea una pregunta nueva en estado **BORRADOR**.

                    El contenido se valida contra las invariantes 1 a 4 del banco:
                    exactamente 4 opciones con una sola correcta, sin fórmulas del tipo
                    "todas las anteriores", opciones con longitud mínima y sin repetir, y
                    contexto y pregunta directa obligatorios.

                    Si algo falla, la respuesta es 400 con **todos** los errores en `errores[]`,
                    para poder corregirlos de una sola pasada.

                    Rol requerido: `AUTOR`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pregunta creada en BORRADOR"),
            @ApiResponse(responseCode = "400",
                    description = "El contenido incumple las reglas del banco",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede crear preguntas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @PostMapping
    public ResponseEntity<PreguntaDto> crear(
            @Parameter(description = "UUID del usuario que hace la petición", required = true,
                    example = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @Parameter(description = "Rol del usuario", required = true, example = "AUTOR")
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol,
            @Valid @RequestBody PeticionPregunta peticion) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("crear preguntas", RolUsuario.AUTOR);

        PreguntaDto creada = crearPregunta.ejecutar(
                usuario.usuarioId(), peticion.aDatosDeAplicacion());

        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/preguntas/{id}")
                        .buildAndExpand(creada.id()).toUri())
                .body(creada);
    }

    @Operation(summary = "Editar una pregunta",
            description = """
                    Reemplaza el contenido de una pregunta.

                    Solo el **autor** puede editarla, y solo mientras esté en **BORRADOR**,
                    **EN_CONSTRUCCION** o **RECHAZADA** (invariante 7). Editarla en BORRADOR o
                    en RECHAZADA la pasa a **EN_CONSTRUCCION**, que es el único estado desde el
                    que se envía a revisión.

                    Es también el camino para corregir una pregunta que la revisión por pares
                    rechazó: queda en RECHAZADA con las observaciones en
                    `observacionesUltimaRevision`, y al editarla se reabre.

                    Rol requerido: `AUTOR` (y ser el autor de esta pregunta).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pregunta actualizada"),
            @ApiResponse(responseCode = "400", description = "El contenido incumple las reglas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "No es el autor o el rol no aplica",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409",
                    description = "La pregunta no está en un estado editable",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @PutMapping("/{id}")
    public PreguntaDto editar(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol,
            @Valid @RequestBody PeticionPregunta peticion) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("editar preguntas", RolUsuario.AUTOR);

        return editarPregunta.ejecutar(id, usuario.usuarioId(),
                peticion.aDatosDeAplicacion());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Consultar una pregunta por su identificador",
            description = "Devuelve la pregunta completa. No requiere ningún rol concreto, "
                    + "pero sí las cabeceras de usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pregunta encontrada"),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @GetMapping("/{id}")
    public PreguntaDto consultar(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol) {

        // Se resuelve el contexto aunque cualquier rol pueda consultar: así una
        // cabecera mal formada falla aquí, de forma coherente con el resto de la
        // API, en vez de pasar desapercibida solo en este endpoint.
        ContextoUsuario.desdeCabeceras(usuarioId, rol);

        return consultarPreguntas.porId(id);
    }

    @Operation(summary = "Listar preguntas por filtros",
            description = """
                    Lista preguntas paginadas. Todos los filtros son opcionales y se combinan
                    con AND; omitir uno significa "no filtrar por ese criterio".

                    El tamaño de página se limita a 100 para proteger al servicio.""")
    @ApiResponse(responseCode = "200", description = "Página de resultados (puede venir vacía)")
    @GetMapping
    public PaginaDto<PreguntaDto> listar(
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol,
            @Parameter(description = "Estado del ciclo de vida",
                    schema = @Schema(allowableValues = {"BORRADOR", "EN_CONSTRUCCION",
                            "PENDIENTE_REVISION", "EN_REVISION", "APROBADA", "RECHAZADA",
                            "PUBLICADA", "ARCHIVADA"}))
            @RequestParam(required = false) String estado,
            @Parameter(description = "Código de la competencia", example = "ING-SOFT")
            @RequestParam(required = false) String competencia,
            @Parameter(description = "Tema exacto", example = "Arquitectura de Software")
            @RequestParam(required = false) String tema,
            @Parameter(description = "Nivel de dificultad",
                    schema = @Schema(allowableValues = {"BAJO", "MEDIO", "ALTO"}))
            @RequestParam(required = false) String nivel,
            @Parameter(description = "Número de página, empezando en 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Tamaño de página (máximo 100)")
            @RequestParam(defaultValue = "20") int size) {

        ContextoUsuario.desdeCabeceras(usuarioId, rol);

        FiltroPreguntas filtro = new FiltroPreguntas(
                estado == null || estado.isBlank() ? null : EstadoPregunta.desdeTexto(estado),
                competencia,
                tema,
                nivel == null || nivel.isBlank() ? null : NivelDificultad.desdeTexto(nivel));

        return consultarPreguntas.porFiltros(filtro, page, size);
    }

    @Operation(summary = "Consultar el historial de estados",
            description = """
                    Devuelve la trazabilidad completa de la pregunta: quién la movió de estado,
                    cuándo y por qué, en orden cronológico.

                    Restringido al `ADMINISTRADOR` porque revela qué revisor tomó cada decisión,
                    y la revisión por pares es anónima frente al autor.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de la pregunta"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @GetMapping("/{id}/historial")
    public List<CambioEstadoDto> historial(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("consultar el historial de una pregunta", RolUsuario.ADMINISTRADOR);

        return consultarHistorial.ejecutar(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transiciones del ciclo de vida
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Enviar una pregunta a revisión",
            description = """
                    Envía la pregunta al ciclo de revisión por pares: pasa de
                    **EN_CONSTRUCCION** a **PENDIENTE_REVISION** y se publica el evento
                    `PreguntaEnviadaARevision` en RabbitMQ, que consumirá el `revision-service`.

                    Una pregunta en BORRADOR o en RECHAZADA no se puede enviar: primero hay
                    que editarla, lo que la pasa a EN_CONSTRUCCION (invariante 6).

                    El contenido se revalida antes de salir: es la última puerta antes de que
                    la pregunta deje este contexto.

                    Rol requerido: `AUTOR` (y ser el autor de esta pregunta).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pregunta en PENDIENTE_REVISION"),
            @ApiResponse(responseCode = "400", description = "El contenido incumple las reglas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "No es el autor o el rol no aplica",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409",
                    description = "La pregunta no está en EN_CONSTRUCCION",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @PostMapping("/{id}/enviar-a-revision")
    public PreguntaDto enviarARevision(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("enviar preguntas a revisión", RolUsuario.AUTOR);

        return enviarARevision.ejecutar(id, usuario.usuarioId());
    }

    @Operation(summary = "Publicar una pregunta",
            description = """
                    Publica una pregunta **APROBADA**, que queda disponible para los simulacros.

                    Antes de publicar se revalida el contenido completo: una pregunta PUBLICADA
                    nunca puede quedar sin sus 4 opciones (invariante 5), porque llegaría así a
                    un estudiante en una prueba real.

                    Emite el evento `PreguntaPublicada` con la pregunta completa.

                    Rol requerido: `ADMINISTRADOR`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pregunta PUBLICADA"),
            @ApiResponse(responseCode = "400", description = "El contenido dejó de ser válido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "La pregunta no está en APROBADA",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @PostMapping("/{id}/publicar")
    public PreguntaDto publicar(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("publicar preguntas", RolUsuario.ADMINISTRADOR);

        return publicarPregunta.ejecutar(id, usuario.usuarioId());
    }

    @Operation(summary = "Archivar una pregunta",
            description = """
                    Saca la pregunta de circulación. Es la **única salida** del ciclo de vida:
                    en el banco nada se elimina físicamente (invariante 8), para que una
                    pregunta ya usada en un simulacro siga siendo consultable. Por eso no existe
                    ninguna operación `DELETE` en esta API.

                    Se puede archivar desde BORRADOR, EN_CONSTRUCCION, RECHAZADA, APROBADA o
                    PUBLICADA. Emite el evento `PreguntaArchivada`.

                    Rol requerido: `ADMINISTRADOR`.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pregunta ARCHIVADA"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La pregunta no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409",
                    description = "No se puede archivar desde el estado actual",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    @PostMapping("/{id}/archivar")
    public PreguntaDto archivar(
            @PathVariable UUID id,
            @RequestHeader(name = ContextoUsuario.CABECERA_ID, required = false) String usuarioId,
            @RequestHeader(name = ContextoUsuario.CABECERA_ROL, required = false) String rol,
            @RequestBody(required = false) PeticionArchivar peticion) {

        ContextoUsuario usuario = ContextoUsuario.desdeCabeceras(usuarioId, rol);
        usuario.exigirRol("archivar preguntas", RolUsuario.ADMINISTRADOR);

        return archivarPregunta.ejecutar(id, usuario.usuarioId(),
                peticion == null ? null : peticion.motivo());
    }

    /** Cuerpo opcional de la operación de archivado. */
    @Schema(name = "PeticionArchivar",
            description = "Motivo del archivado. Es opcional, pero queda en el historial.")
    public record PeticionArchivar(
            @Schema(description = "Por qué se archiva la pregunta.",
                    example = "El tema salió del temario de Saber Pro 2027.")
            String motivo) {
    }

    /**
     * Solo para la documentación: describe la forma de los errores en Swagger.
     * El objeto real lo construye {@link ManejadorGlobalErrores} con el
     * {@code ProblemDetail} de Spring.
     */
    @Schema(name = "RespuestaError",
            description = "Error en formato Problem Details (RFC 7807).")
    public record RespuestaError(
            @Schema(example = "https://saberpro.unicauca.edu.co/errores/regla-de-negocio")
            String type,
            @Schema(example = "Contenido de la pregunta inválido") String title,
            @Schema(example = "400") int status,
            @Schema(example = "No se puede crear la pregunta porque su contenido no cumple "
                    + "las reglas del banco.") String detail,
            @Schema(example = "/api/v1/preguntas") String instance,
            @Schema(description = "Una entrada por cada regla incumplida.",
                    example = "[\"La pregunta debe tener exactamente 4 opciones y tiene 3. "
                            + "(Invariante 1)\"]")
            List<String> errores) {
    }
}
