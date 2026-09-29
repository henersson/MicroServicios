package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.DatosDePrueba;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaArchivada;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaEnviadaARevision;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaPublicada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.AccesoNoAutorizado;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.TransicionInvalida;
import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests del Aggregate Root {@code Pregunta}: la máquina de estados (invariante 6),
 * la invariante 5 al publicar, la 7 al editar y la 8 por ausencia de borrado.
 */
@DisplayName("Pregunta — Aggregate Root")
class PreguntaTest {

    private final ValidadorEstructural validador = new ValidadorEstructural();

    private Pregunta nueva() {
        return Pregunta.crear(UUID.randomUUID(), DatosDePrueba.AUTOR,
                DatosDePrueba.contenidoValido(), validador, DatosDePrueba.AHORA);
    }

    /** Lleva una pregunta recién creada hasta el estado pedido. */
    private Pregunta en(EstadoPregunta destino) {
        Pregunta pregunta = nueva();
        if (destino == EstadoPregunta.BORRADOR) {
            return pregunta;
        }
        pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);
        if (destino == EstadoPregunta.PENDIENTE_REVISION) {
            return pregunta;
        }
        pregunta.registrarRevisorAsignado(DatosDePrueba.REVISOR, DatosDePrueba.AHORA);
        if (destino == EstadoPregunta.EN_REVISION) {
            return pregunta;
        }
        pregunta.registrarAprobacionTecnica(DatosDePrueba.REVISOR, DatosDePrueba.AHORA);
        if (destino == EstadoPregunta.APROBADA) {
            return pregunta;
        }
        pregunta.publicar(DatosDePrueba.ADMINISTRADOR, validador, DatosDePrueba.AHORA);
        if (destino == EstadoPregunta.PUBLICADA) {
            return pregunta;
        }
        pregunta.archivar(DatosDePrueba.ADMINISTRADOR, null, DatosDePrueba.AHORA);
        return pregunta;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creación")
    class Creacion {

        @Test
        @DisplayName("nace en BORRADOR con una entrada de historial y sin eventos")
        void naceEnBorrador() {
            Pregunta pregunta = nueva();

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.BORRADOR);
            assertThat(pregunta.getHistorialEstados()).hasSize(1);
            assertThat(pregunta.getHistorialEstados().getFirst().anterior()).isNull();
            assertThat(pregunta.getHistorialEstados().getFirst().nuevo())
                    .isEqualTo(EstadoPregunta.BORRADOR);
            assertThat(pregunta.eventosPendientes())
                    .as("mientras está en BORRADOR solo le importa a su autor")
                    .isEmpty();
        }

        @Test
        @DisplayName("no se puede crear con contenido que incumple las invariantes 1 a 4")
        void rechazaContenidoInvalido() {
            var contenidoCon4Opciones = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesValidas().subList(0, 4));

            assertThatThrownBy(() -> Pregunta.crear(UUID.randomUUID(), DatosDePrueba.AUTOR,
                    contenidoCon4Opciones, validador, DatosDePrueba.AHORA))
                    .isInstanceOf(ReglaDeNegocioViolada.class)
                    .satisfies(e -> assertThat(((ReglaDeNegocioViolada) e).getErrores())
                            .isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Invariante 7 — solo el autor edita, y solo en BORRADOR")
    class Invariante7 {

        @Test
        @DisplayName("el autor puede editar mientras esté en BORRADOR")
        void autorEditaEnBorrador() {
            Pregunta pregunta = nueva();
            var nuevoContenido = DatosDePrueba.contenidoCon(
                    "Un contexto corregido tras las observaciones del revisor.",
                    "¿Qué propiedad se compromete al compartir la base de datos?");

            pregunta.editar(DatosDePrueba.AUTOR, nuevoContenido, validador);

            assertThat(pregunta.getContenido().contexto())
                    .isEqualTo("Un contexto corregido tras las observaciones del revisor.");
        }

        @Test
        @DisplayName("alguien que no es el autor no puede editar")
        void otroUsuarioNoEdita() {
            Pregunta pregunta = nueva();

            assertThatThrownBy(() -> pregunta.editar(DatosDePrueba.OTRO_USUARIO,
                    DatosDePrueba.contenidoValido(), validador))
                    .isInstanceOf(AccesoNoAutorizado.class)
                    .hasMessageContaining("Solo el autor")
                    .hasMessageContaining("Invariante 7");
        }

        @Test
        @DisplayName("no se puede editar fuera de BORRADOR")
        void noEditaFueraDeBorrador() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.PENDIENTE_REVISION,
                    EstadoPregunta.EN_REVISION, EstadoPregunta.APROBADA,
                    EstadoPregunta.PUBLICADA, EstadoPregunta.ARCHIVADA)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> pregunta.editar(DatosDePrueba.AUTOR,
                        DatosDePrueba.contenidoValido(), validador))
                        .as("editar en estado %s", estado)
                        .isInstanceOf(TransicionInvalida.class)
                        .hasMessageContaining("Invariante 7");
            }
        }

        @Test
        @DisplayName("una edición inválida no deja la pregunta a medias")
        void edicionInvalidaNoMuta() {
            Pregunta pregunta = nueva();
            String contextoOriginal = pregunta.getContenido().contexto();
            var contenidoRoto = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesValidas().subList(0, 3));

            assertThatThrownBy(() -> pregunta.editar(DatosDePrueba.AUTOR, contenidoRoto, validador))
                    .isInstanceOf(ReglaDeNegocioViolada.class);

            assertThat(pregunta.getContenido().contexto()).isEqualTo(contextoOriginal);
            assertThat(pregunta.getContenido().opciones()).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Invariante 6 — transiciones válidas")
    class TransicionesValidas {

        @Test
        @DisplayName("BORRADOR → PENDIENTE_REVISION al enviar a revisión")
        void borradorAPendiente() {
            Pregunta pregunta = nueva();

            pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.PENDIENTE_REVISION);
            assertThat(pregunta.eventosPendientes())
                    .hasSize(1)
                    .first().isInstanceOf(PreguntaEnviadaARevision.class);
        }

        @Test
        @DisplayName("PENDIENTE_REVISION → EN_REVISION con el evento RevisorAsignado")
        void pendienteAEnRevision() {
            Pregunta pregunta = en(EstadoPregunta.PENDIENTE_REVISION);

            boolean cambio = pregunta.registrarRevisorAsignado(
                    DatosDePrueba.REVISOR, DatosDePrueba.AHORA);

            assertThat(cambio).isTrue();
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.EN_REVISION);
        }

        @Test
        @DisplayName("EN_REVISION → APROBADA con la aprobación técnica")
        void enRevisionAAprobada() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);

            assertThat(pregunta.registrarAprobacionTecnica(
                    DatosDePrueba.REVISOR, DatosDePrueba.AHORA)).isTrue();
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.APROBADA);
        }

        @Test
        @DisplayName("EN_REVISION → BORRADOR con el rechazo, guardando las observaciones")
        void enRevisionARechazo() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);

            boolean cambio = pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("El contexto supera las 120 palabras recomendadas.",
                            "El distractor 3 es demasiado evidente."),
                    DatosDePrueba.AHORA);

            assertThat(cambio).isTrue();
            assertThat(pregunta.getEstado())
                    .as("el rechazo devuelve la pregunta al autor, no la descarta (ADR 2)")
                    .isEqualTo(EstadoPregunta.BORRADOR);
            assertThat(pregunta.getObservacionesUltimaRevision())
                    .get().asString()
                    .contains("120 palabras")
                    .contains("distractor 3");
        }

        @Test
        @DisplayName("APROBADA → PUBLICADA y emite PreguntaPublicada con el contenido completo")
        void aprobadaAPublicada() {
            Pregunta pregunta = en(EstadoPregunta.APROBADA);

            pregunta.publicar(DatosDePrueba.ADMINISTRADOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.PUBLICADA);
            assertThat(pregunta.eventosPendientes())
                    .filteredOn(PreguntaPublicada.class::isInstance)
                    .hasSize(1)
                    .first()
                    .satisfies(evento -> assertThat(((PreguntaPublicada) evento)
                            .contenido().opciones()).hasSize(5));
        }

        @Test
        @DisplayName("se puede archivar desde BORRADOR, APROBADA y PUBLICADA")
        void archivarDesdeLosTresEstados() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.BORRADOR,
                    EstadoPregunta.APROBADA, EstadoPregunta.PUBLICADA)) {

                Pregunta pregunta = en(estado);

                pregunta.archivar(DatosDePrueba.ADMINISTRADOR,
                        "El tema salió del temario.", DatosDePrueba.AHORA);

                assertThat(pregunta.getEstado())
                        .as("archivar desde %s", estado)
                        .isEqualTo(EstadoPregunta.ARCHIVADA);
                assertThat(pregunta.eventosPendientes())
                        .filteredOn(PreguntaArchivada.class::isInstance)
                        .hasSize(1);
            }
        }

        @Test
        @DisplayName("el rechazo y la corrección permiten reenviar a revisión")
        void cicloCompletoDeCorreccion() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);
            pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("Recorta el contexto."), DatosDePrueba.AHORA);
            pregunta.extraerEventosPendientes();

            pregunta.editar(DatosDePrueba.AUTOR,
                    DatosDePrueba.contenidoCon("Contexto ya recortado a lo esencial.",
                            "¿Qué propiedad se compromete al compartir la base de datos?"),
                    validador);
            pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.PENDIENTE_REVISION);
            assertThat(pregunta.getObservacionesUltimaRevision())
                    .as("al reenviar, las observaciones viejas ya no aplican")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Invariante 6 — transiciones inválidas")
    class TransicionesInvalidas {

        @Test
        @DisplayName("no se puede enviar a revisión algo que no está en BORRADOR")
        void noEnviaDesdeOtrosEstados() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.PENDIENTE_REVISION,
                    EstadoPregunta.EN_REVISION, EstadoPregunta.APROBADA,
                    EstadoPregunta.PUBLICADA, EstadoPregunta.ARCHIVADA)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> pregunta.enviarARevision(
                        DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA))
                        .as("enviar a revisión desde %s", estado)
                        .isInstanceOf(TransicionInvalida.class);
            }
        }

        @Test
        @DisplayName("no se puede publicar algo que no está en APROBADA")
        void noPublicaSinAprobar() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.BORRADOR,
                    EstadoPregunta.PENDIENTE_REVISION, EstadoPregunta.EN_REVISION,
                    EstadoPregunta.PUBLICADA, EstadoPregunta.ARCHIVADA)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> pregunta.publicar(
                        DatosDePrueba.ADMINISTRADOR, validador, DatosDePrueba.AHORA))
                        .as("publicar desde %s", estado)
                        .isInstanceOf(TransicionInvalida.class);
            }
        }

        @Test
        @DisplayName("ARCHIVADA es un estado final: no se sale de ahí")
        void archivadaEsFinal() {
            Pregunta pregunta = en(EstadoPregunta.ARCHIVADA);

            assertThatThrownBy(() -> pregunta.archivar(
                    DatosDePrueba.ADMINISTRADOR, null, DatosDePrueba.AHORA))
                    .isInstanceOf(TransicionInvalida.class)
                    .hasMessageContaining("estado final");

            assertThatThrownBy(() -> pregunta.enviarARevision(
                    DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA))
                    .isInstanceOf(TransicionInvalida.class);
        }

        @Test
        @DisplayName("una pregunta en BORRADOR no puede aprobarse sin pasar por la revisión")
        void noApruebaDesdeBorrador() {
            Pregunta pregunta = nueva();

            assertThatThrownBy(() -> pregunta.registrarAprobacionTecnica(
                    DatosDePrueba.REVISOR, DatosDePrueba.AHORA))
                    .isInstanceOf(TransicionInvalida.class);
        }
    }

    @Nested
    @DisplayName("Idempotencia frente a eventos repetidos")
    class Idempotencia {

        @Test
        @DisplayName("RevisorAsignado repetido no cambia nada ni añade historial")
        void revisorAsignadoRepetido() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);
            int historialAntes = pregunta.getHistorialEstados().size();

            boolean cambio = pregunta.registrarRevisorAsignado(
                    DatosDePrueba.REVISOR, DatosDePrueba.AHORA);

            assertThat(cambio).isFalse();
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.EN_REVISION);
            assertThat(pregunta.getHistorialEstados()).hasSize(historialAntes);
        }

        @Test
        @DisplayName("aprobación repetida no cambia nada")
        void aprobacionRepetida() {
            Pregunta pregunta = en(EstadoPregunta.APROBADA);
            int historialAntes = pregunta.getHistorialEstados().size();

            assertThat(pregunta.registrarAprobacionTecnica(
                    DatosDePrueba.REVISOR, DatosDePrueba.AHORA)).isFalse();
            assertThat(pregunta.getHistorialEstados()).hasSize(historialAntes);
        }

        @Test
        @DisplayName("rechazo repetido no vuelve a mover la pregunta")
        void rechazoRepetido() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);
            pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("Recorta el contexto."), DatosDePrueba.AHORA);
            int historialAntes = pregunta.getHistorialEstados().size();

            boolean segundo = pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("Recorta el contexto."), DatosDePrueba.AHORA);

            assertThat(segundo).isFalse();
            assertThat(pregunta.getHistorialEstados()).hasSize(historialAntes);
        }
    }

    @Nested
    @DisplayName("Invariante 5 — una PUBLICADA nunca queda incompleta")
    class Invariante5 {

        @Test
        @DisplayName("no se publica si el contenido dejó de cumplir las reglas")
        void noPublicaContenidoRoto() {
            // Se simula una pregunta que llegó a APROBADA con el contenido ya
            // corrompido (por ejemplo, por una migración de datos mal hecha).
            Pregunta corrupta = new Pregunta(
                    UUID.randomUUID(), DatosDePrueba.AUTOR,
                    DatosDePrueba.contenidoCon(DatosDePrueba.opcionesValidas().subList(0, 3)),
                    EstadoPregunta.APROBADA, null, List.of());

            assertThatThrownBy(() -> corrupta.publicar(
                    DatosDePrueba.ADMINISTRADOR, validador, DatosDePrueba.AHORA))
                    .isInstanceOf(ReglaDeNegocioViolada.class)
                    .hasMessageContaining("Invariante 5");

            assertThat(corrupta.getEstado())
                    .as("si no se puede publicar, se queda donde estaba")
                    .isEqualTo(EstadoPregunta.APROBADA);
        }

        @Test
        @DisplayName("una PUBLICADA conserva sus 5 opciones con una sola correcta")
        void publicadaConservaSusCincoOpciones() {
            Pregunta pregunta = en(EstadoPregunta.PUBLICADA);

            assertThat(pregunta.getContenido().opciones()).hasSize(5);
            assertThat(pregunta.getContenido().opciones())
                    .filteredOn(Opcion::esCorrecta).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Trazabilidad y eventos")
    class TrazabilidadYEventos {

        @Test
        @DisplayName("el historial registra cada transición con su usuario y fecha")
        void historialCompleto() {
            Pregunta pregunta = en(EstadoPregunta.PUBLICADA);

            assertThat(pregunta.getHistorialEstados())
                    .extracting(CambioEstado::nuevo)
                    .containsExactly(
                            EstadoPregunta.BORRADOR,
                            EstadoPregunta.PENDIENTE_REVISION,
                            EstadoPregunta.EN_REVISION,
                            EstadoPregunta.APROBADA,
                            EstadoPregunta.PUBLICADA);

            assertThat(pregunta.getHistorialEstados())
                    .allSatisfy(cambio -> {
                        assertThat(cambio.fecha()).isEqualTo(DatosDePrueba.AHORA);
                        assertThat(cambio.motivo()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("extraer los eventos los entrega una sola vez")
        void extraerEventosLosVacia() {
            Pregunta pregunta = nueva();
            pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.extraerEventosPendientes()).hasSize(1);
            assertThat(pregunta.extraerEventosPendientes())
                    .as("un evento no se puede publicar dos veces")
                    .isEmpty();
        }

        @Test
        @DisplayName("el evento de envío lleva el código de competencia para el otro contexto")
        void eventoLlevaCompetencia() {
            Pregunta pregunta = nueva();
            pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.extraerEventosPendientes())
                    .first()
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                            .type(PreguntaEnviadaARevision.class))
                    .satisfies(evento -> {
                        assertThat(evento.competenciaCodigo()).isEqualTo("ING-SOFT");
                        assertThat(evento.autorId()).isEqualTo(DatosDePrueba.AUTOR);
                        assertThat(evento.ocurridoEn()).isEqualTo(DatosDePrueba.AHORA);
                        assertThat(evento.routingKey())
                                .isEqualTo("banco.pregunta.enviada-a-revision");
                    });
        }
    }

    @Test
    @DisplayName("Invariante 8 — el agregado no expone ninguna operación de borrado")
    void invariante8SinBorrado() {
        // La invariante 8 se garantiza por ausencia: si no hay método de borrado,
        // no hay forma de borrar. Este test lo deja explícito para que nadie
        // agregue uno sin darse cuenta de que rompe la regla.
        assertThat(Pregunta.class.getDeclaredMethods())
                .extracting(java.lang.reflect.Method::getName)
                .as("la única salida del ciclo de vida es archivar (ADR 3)")
                .doesNotContain("eliminar", "borrar", "delete", "remove");

        Pregunta pregunta = en(EstadoPregunta.PUBLICADA);
        assertThatCode(() -> pregunta.archivar(DatosDePrueba.ADMINISTRADOR, null, Instant.now()))
                .doesNotThrowAnyException();
    }
}
