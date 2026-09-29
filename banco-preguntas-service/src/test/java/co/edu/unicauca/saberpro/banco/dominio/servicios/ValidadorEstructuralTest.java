package co.edu.unicauca.saberpro.banco.dominio.servicios;

import co.edu.unicauca.saberpro.banco.DatosDePrueba;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del Domain Service {@code ValidadorEstructural}: una batería por cada
 * una de las invariantes 1 a 4.
 *
 * <p>Cada test parte de un contenido válido y rompe <strong>una sola cosa</strong>.
 * Cuando uno falla, el motivo es lo que ese test rompió.
 */
@DisplayName("ValidadorEstructural — invariantes 1 a 4")
class ValidadorEstructuralTest {

    private final ValidadorEstructural validador = new ValidadorEstructural();

    @Test
    @DisplayName("acepta una pregunta que cumple todas las invariantes")
    void aceptaContenidoValido() {
        var resultado = validador.validar(DatosDePrueba.contenidoValido());

        assertThat(resultado.esValido()).isTrue();
        assertThat(resultado.errores()).isEmpty();
    }

    @Nested
    @DisplayName("Invariante 1 — exactamente 4 distractores y 1 correcta")
    class Invariante1 {

        @Test
        @DisplayName("rechaza una pregunta con solo 3 distractores (4 opciones en total)")
        void rechazaTresDistractores() {
            List<Opcion> cuatro = DatosDePrueba.opcionesValidas().subList(0, 4);

            var resultado = validador.validar(DatosDePrueba.contenidoCon(cuatro));

            assertThat(resultado.esValido()).isFalse();
            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("exactamente 5 opciones")
                            .contains("tiene 4")
                            .contains("Invariante 1"));
        }

        @Test
        @DisplayName("rechaza una pregunta con 6 opciones")
        void rechazaSeisOpciones() {
            List<Opcion> seis = DatosDePrueba.unir(
                    DatosDePrueba.opcionesValidas(),
                    DatosDePrueba.opciones("Una sexta opción que sobra por completo.", false));

            var resultado = validador.validar(DatosDePrueba.contenidoCon(seis));

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error).contains("tiene 6"));
        }

        @Test
        @DisplayName("rechaza una pregunta con 2 opciones correctas")
        void rechazaDosCorrectas() {
            List<Opcion> conDosCorrectas = DatosDePrueba.opciones(
                    "El acoplamiento de datos impide el despliegue independiente.", true,
                    "Cada servicio debe ser dueño exclusivo de su esquema de datos.", true,
                    "El teorema CAP prohíbe compartir un motor relacional.", false,
                    "Una base compartida siempre es más costosa de operar.", false,
                    "Los microservicios solo se comunican de forma asíncrona.", false);

            var resultado = validador.validar(DatosDePrueba.contenidoCon(conDosCorrectas));

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("exactamente 1 opción correcta")
                            .contains("tiene 2")
                            .contains("Invariante 1"));
        }

        @Test
        @DisplayName("rechaza una pregunta sin ninguna opción correcta")
        void rechazaCeroCorrectas() {
            List<Opcion> sinCorrecta = DatosDePrueba.opciones(
                    "PostgreSQL no admite conexiones concurrentes de varios servicios.", false,
                    "El teorema CAP prohíbe compartir un motor relacional.", false,
                    "Una base compartida siempre es más costosa de operar.", false,
                    "Los microservicios solo se comunican de forma asíncrona.", false,
                    "Compartir esquema obliga a usar siempre el protocolo REST.", false);

            var resultado = validador.validar(DatosDePrueba.contenidoCon(sinCorrecta));

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error).contains("tiene 0"));
        }
    }

    @Nested
    @DisplayName("Invariante 2 — nada de 'todas/ninguna de las anteriores'")
    class Invariante2 {

        /**
         * La normalización quita tildes, pasa a minúsculas y colapsa espacios, así
         * que todas estas variantes tienen que caer igual.
         */
        @ParameterizedTest(name = "rechaza la opción: \"{0}\"")
        @ValueSource(strings = {
                "Todas las anteriores",
                "TODAS LAS ANTERIORES",
                "todas las anteriores",
                "Todás  las   Anteriores",
                "Ninguna de las anteriores",
                "NINGUNA DE LAS ANTERIORES",
                "Ninguná de las anterióres",
                "Todas son correctas",
                "Todas las opciones son correctas",
                "Ninguna es correcta",
                "Ninguna de las opciones anteriores",
                "Todos los anteriores",
                "Ninguno de los anteriores"
        })
        void rechazaFormulasProhibidas(String textoProhibido) {
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(4, textoProhibido));

            var resultado = validador.validar(contenido);

            assertThat(resultado.esValido()).isFalse();
            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("fórmula prohibida")
                            .contains("Invariante 2"));
        }

        @Test
        @DisplayName("señala la posición exacta de la opción prohibida")
        void senalaLaPosicion() {
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(2, "Todas las anteriores son válidas"));

            var resultado = validador.validar(contenido);

            // Posición 2 del array es la opción 3 para quien lee el mensaje.
            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error).contains("La opción 3"));
        }

        @Test
        @DisplayName("no confunde un uso legítimo de la palabra 'anteriores'")
        void aceptaUsoLegitimoDeLaPalabra() {
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(3,
                            "Porque las versiones anteriores del sistema ya usaban ese esquema."));

            var resultado = validador.validar(contenido);

            assertThat(resultado.esValido())
                    .as("'versiones anteriores' no es la fórmula prohibida")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Invariante 3 — longitud mínima y sin repetidas")
    class Invariante3 {

        @Test
        @DisplayName("rechaza una opción más corta que el mínimo configurado")
        void rechazaOpcionCorta() {
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(1, "No"));

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("La opción 2")
                            .contains("demasiado corta")
                            .contains("Invariante 3"));
        }

        @Test
        @DisplayName("no cuenta los espacios al medir la longitud")
        void noCuentaEspacios() {
            // "a b c" son 5 caracteres con espacios, pero solo 3 sin ellos.
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(1, "a b c"));

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error).contains("3 caracteres sin espacios"));
        }

        @Test
        @DisplayName("respeta la longitud mínima configurada")
        void respetaLongitudConfigurada() {
            var contenidoConOpcionDe8 = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(1, "Compartir"));

            assertThat(new ValidadorEstructural(5).validar(contenidoConOpcionDe8).esValido())
                    .as("con mínimo 5, una opción de 9 caracteres pasa")
                    .isTrue();
            assertThat(new ValidadorEstructural(20).validar(contenidoConOpcionDe8).esValido())
                    .as("con mínimo 20, la misma opción ya no pasa")
                    .isFalse();
        }

        @Test
        @DisplayName("rechaza dos opciones con el mismo texto")
        void rechazaOpcionRepetida() {
            String repetido = "El teorema CAP prohíbe compartir un motor relacional.";
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(3, repetido));

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("está repetida")
                            .contains("Invariante 3"));
        }

        @Test
        @DisplayName("detecta repetidas aunque difieran en mayúsculas, tildes o espacios")
        void detectaRepetidaNormalizada() {
            var contenido = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesConTextoEnPosicion(3,
                            "EL TEOREMA  CAP PROHIBE COMPARTIR UN MOTOR RELACIONAL."));

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error).contains("está repetida"));
        }
    }

    @Nested
    @DisplayName("Invariante 4 — contexto y pregunta directa obligatorios")
    class Invariante4 {

        @Test
        @DisplayName("rechaza una pregunta sin contexto")
        void rechazaSinContexto() {
            ContenidoPregunta contenido = DatosDePrueba.contenidoCon(
                    "   ", "¿Cuál es la razón principal del rechazo del arquitecto?");

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("El contexto es obligatorio")
                            .contains("Invariante 4"));
        }

        @Test
        @DisplayName("rechaza una pregunta sin pregunta directa")
        void rechazaSinPreguntaDirecta() {
            ContenidoPregunta contenido = DatosDePrueba.contenidoCon(
                    "Una universidad migra su plataforma a microservicios.", "");

            var resultado = validador.validar(contenido);

            assertThat(resultado.errores())
                    .anySatisfy(error -> assertThat(error)
                            .contains("La pregunta directa es obligatoria")
                            .contains("Invariante 4"));
        }
    }

    @Test
    @DisplayName("devuelve todos los errores juntos, no solo el primero")
    void acumulaTodosLosErrores() {
        // Tres problemas a la vez: sin contexto, 4 opciones y una demasiado corta.
        List<Opcion> rotas = DatosDePrueba.opciones(
                "El acoplamiento de datos impide el despliegue independiente.", true,
                "No", false,
                "El teorema CAP prohíbe compartir un motor relacional.", false,
                "Una base compartida siempre es más costosa de operar.", false);

        ContenidoPregunta base = DatosDePrueba.contenidoCon(rotas);
        ContenidoPregunta contenido = new ContenidoPregunta("", base.preguntaDirecta(),
                base.opciones(), base.justificacion(), base.bibliografia(), base.competencia(),
                base.tema(), base.subtema(), base.nivelDificultad());

        var resultado = validador.validar(contenido);

        assertThat(resultado.errores())
                .as("el autor debe poder corregir todo de una sola pasada")
                .hasSizeGreaterThanOrEqualTo(3);
    }
}
