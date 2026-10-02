package co.edu.unicauca.saberpro.banco.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de las respuestas a rutas y métodos que la API no tiene.
 *
 * <p>Antes los dos casos caían en la red de seguridad y respondían 500, como si
 * el servicio hubiera fallado. Un {@code DELETE} sobre una pregunta, que es justo
 * lo que la invariante 8 prohíbe, se veía como un error interno.
 */
@DisplayName("ManejadorGlobalErrores — rutas y métodos inexistentes")
class ManejadorGlobalErroresTest {

    private static final String RUTA_PREGUNTA =
            "/api/v1/preguntas/3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c";

    private final ManejadorGlobalErrores manejador = new ManejadorGlobalErrores();

    @Test
    @DisplayName("DELETE sobre una pregunta → 405 explicando la invariante 8")
    void deleteDeUnaPreguntaCitaLaInvariante8() {
        var peticion = new MockHttpServletRequest("DELETE", RUTA_PREGUNTA);
        var excepcion = new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "PUT"));

        ResponseEntity<ProblemDetail> respuesta =
                manejador.manejarMetodoNoPermitido(excepcion, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getHeaders().getAllow())
                .as("la cabecera Allow dice qué métodos sí admite la ruta")
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.PUT);
        ProblemDetail cuerpo = respuesta.getBody();
        assertThat(cuerpo).isNotNull();
        assertThat(cuerpo.getType().toString()).endsWith("pregunta-no-se-elimina");
        assertThat(cuerpo.getDetail()).contains("Invariante 8");
        assertThat(cuerpo.getProperties().get("errores").toString()).contains("/archivar");
    }

    @Test
    @DisplayName("otro método no soportado → 405 genérico, sin hablar de la invariante 8")
    void otroMetodoNoSoportadoEsGenerico() {
        var peticion = new MockHttpServletRequest("PUT", RUTA_PREGUNTA + "/publicar");
        var excepcion = new HttpRequestMethodNotSupportedException("PUT", List.of("POST"));

        ResponseEntity<ProblemDetail> respuesta =
                manejador.manejarMetodoNoPermitido(excepcion, peticion);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getType().toString()).endsWith("metodo-no-permitido");
        assertThat(respuesta.getBody().getDetail())
                .contains("/publicar")
                .contains("PUT")
                .doesNotContain("Invariante 8");
    }

    @Test
    @DisplayName("una ruta que no existe → 404")
    void rutaInexistenteEs404() {
        var peticion = new MockHttpServletRequest("GET", "/api/v1/no-existe");
        var excepcion = new NoResourceFoundException(HttpMethod.GET, "/api/v1/no-existe",
                "api/v1/no-existe");

        ProblemDetail cuerpo = manejador.manejarRutaInexistente(excepcion, peticion);

        assertThat(cuerpo.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(cuerpo.getType().toString()).endsWith("ruta-inexistente");
        assertThat(cuerpo.getDetail()).contains("GET /api/v1/no-existe");
    }
}
