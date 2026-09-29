package co.edu.unicauca.saberpro.banco.interfaces.rest;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.AccesoNoAutorizado;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.TransicionInvalida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;

/**
 * Traduce las excepciones a respuestas HTTP en formato
 * <strong>Problem Details (RFC 7807)</strong>.
 *
 * <p>Es el único sitio del servicio donde se decide qué código HTTP corresponde
 * a cada fallo. Concentrarlo aquí es lo que permite que el dominio lance
 * excepciones con nombre de negocio ({@code TransicionInvalida}) sin saber nada
 * de HTTP, y que los controladores no tengan un solo {@code try/catch}.
 *
 * <p>Al cuerpo estándar de RFC 7807 se le añade un campo {@code errores[]} con
 * una entrada por cada regla incumplida. Esa es la diferencia entre decirle al
 * autor "la pregunta es inválida" y decirle exactamente qué tres cosas tiene que
 * arreglar.
 *
 * <table>
 *   <caption>Correspondencia entre excepciones y códigos</caption>
 *   <tr><th>Excepción</th><th>HTTP</th><th>Por qué</th></tr>
 *   <tr><td>{@code ReglaDeNegocioViolada}</td><td>400</td>
 *       <td>El contenido enviado no sirve; hay que cambiarlo</td></tr>
 *   <tr><td>{@code UsuarioNoIdentificado}</td><td>401</td>
 *       <td>No sabemos quién hace la petición</td></tr>
 *   <tr><td>{@code AccesoNoAutorizado}</td><td>403</td>
 *       <td>Sabemos quién eres y no puedes hacer esto</td></tr>
 *   <tr><td>{@code PreguntaNoEncontrada}</td><td>404</td>
 *       <td>El recurso no existe</td></tr>
 *   <tr><td>{@code TransicionInvalida}</td><td>409</td>
 *       <td>La petición está bien, choca con el estado actual</td></tr>
 * </table>
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    private static final String BASE_TIPOS = "https://saberpro.unicauca.edu.co/errores/";

    /** Nombre del campo no estándar con el detalle de cada regla incumplida. */
    private static final String CAMPO_ERRORES = "errores";

    // ─────────────────────────────────────────────────────────────────────────
    // Excepciones del dominio
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ReglaDeNegocioViolada.class)
    public ProblemDetail manejarReglaDeNegocio(ReglaDeNegocioViolada e) {
        log.debug("Regla de negocio incumplida: {}", e.getMessage());
        return problema(HttpStatus.BAD_REQUEST,
                "regla-de-negocio",
                "Contenido de la pregunta inválido",
                e.getMessage(),
                e.getErrores());
    }

    @ExceptionHandler(AccesoNoAutorizado.class)
    public ProblemDetail manejarAccesoNoAutorizado(AccesoNoAutorizado e) {
        log.debug("Acceso denegado: {}", e.getMessage());
        return problema(HttpStatus.FORBIDDEN,
                "acceso-no-autorizado",
                "No tienes permiso para esta operación",
                e.getMessage(),
                e.getErrores());
    }

    @ExceptionHandler(PreguntaNoEncontrada.class)
    public ProblemDetail manejarNoEncontrada(PreguntaNoEncontrada e) {
        return problema(HttpStatus.NOT_FOUND,
                "pregunta-no-encontrada",
                "Pregunta no encontrada",
                e.getMessage(),
                e.getErrores());
    }

    @ExceptionHandler(TransicionInvalida.class)
    public ProblemDetail manejarTransicionInvalida(TransicionInvalida e) {
        log.debug("Transición de estado no permitida: {}", e.getMessage());
        return problema(HttpStatus.CONFLICT,
                "transicion-invalida",
                "Cambio de estado no permitido",
                e.getMessage(),
                e.getErrores());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Errores de la petición HTTP
    // ─────────────────────────────────────────────────────────────────────────

    /** Falla Bean Validation sobre el cuerpo de la petición. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException e) {
        List<String> errores = e.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .toList();

        return problema(HttpStatus.BAD_REQUEST,
                "peticion-invalida",
                "Faltan datos o tienen un formato incorrecto",
                "Revisa los campos indicados en 'errores'.",
                errores);
    }

    /**
     * No se sabe quién hace la petición: faltan las cabeceras de usuario o traen
     * valores inválidos.
     *
     * <p>Es <strong>401 y no 403</strong>: 403 sería "sé quién eres y no puedes";
     * aquí todavía no sabemos quién es.
     */
    @ExceptionHandler(UsuarioNoIdentificado.class)
    public ProblemDetail manejarUsuarioNoIdentificado(UsuarioNoIdentificado e) {
        return problema(HttpStatus.UNAUTHORIZED,
                "usuario-no-identificado",
                "No se pudo identificar al usuario",
                ("Cada petición debe declarar quién la hace con las cabeceras %s (UUID) "
                        + "y %s (rol).").formatted(
                                ContextoUsuario.CABECERA_ID, ContextoUsuario.CABECERA_ROL),
                e.getErrores());
    }

    /** JSON mal formado o ilegible. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST,
                "cuerpo-ilegible",
                "El cuerpo de la petición no se pudo leer",
                "Comprueba que el JSON esté bien formado y que el Content-Type sea "
                        + "application/json.",
                List.of());
    }

    /** Un parámetro de ruta o de consulta no tiene el tipo esperado. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarTipoIncorrecto(MethodArgumentTypeMismatchException e) {
        String esperado = e.getRequiredType() == null
                ? "el tipo esperado"
                : e.getRequiredType().getSimpleName();

        return problema(HttpStatus.BAD_REQUEST,
                "parametro-invalido",
                "Parámetro con formato incorrecto",
                "El parámetro '%s' con valor '%s' no es un %s válido."
                        .formatted(e.getName(), e.getValue(), esperado),
                List.of());
    }

    /**
     * Red de seguridad para lo que no se previó.
     *
     * <p>Devuelve un mensaje genérico a propósito: un stack trace en la respuesta
     * le cuenta a cualquiera cómo está construido el servicio. El detalle queda
     * en el log del servidor, que es donde hace falta.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperado(Exception e) {
        log.error("Error inesperado procesando la petición", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "error-interno",
                "Error interno del servidor",
                "Ocurrió un error inesperado. Si persiste, revisa los logs del servicio.",
                List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────

    private ProblemDetail problema(HttpStatus estado, String tipo, String titulo,
                                   String detalle, List<String> errores) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create(BASE_TIPOS + tipo));
        problema.setTitle(titulo);
        // El campo siempre está presente, aunque venga vacío: así los clientes
        // pueden recorrerlo sin comprobar antes si existe.
        problema.setProperty(CAMPO_ERRORES, errores == null ? List.of() : errores);
        return problema;
    }
}
