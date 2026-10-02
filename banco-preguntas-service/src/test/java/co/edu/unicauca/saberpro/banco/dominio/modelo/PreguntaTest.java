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

    /** El autor edita la pregunta sin cambiar su contenido. */
    private void trabajar(Pregunta pregunta) {
        pregunta.editar(DatosDePrueba.AUTOR, DatosDePrueba.contenidoValido(), validador,
                DatosDePrueba.AHORA);
    }

    /** Lleva una pregunta recién creada hasta el estado pedido. */
    private Pregunta en(EstadoPregunta destino) {
        Pregunta pregunta = nueva();
        if (destino == EstadoPregunta.BORRADOR) {
            return pregunta;
        }
        trabajar(pregunta);
        if (destino == EstadoPregunta.EN_CONSTRUCCION) {
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
        if (destino == EstadoPregunta.RECHAZADA) {
            pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("Recorta el contexto."), DatosDePrueba.AHORA);
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
            var contenidoCon3Opciones = DatosDePrueba.contenidoCon(
                    DatosDePrueba.opcionesValidas().subList(0, 3));

            assertThatThrownBy(() -> Pregunta.crear(UUID.randomUUID(), DatosDePrueba.AUTOR,
                    contenidoCon3Opciones, validador, DatosDePrueba.AHORA))
                    .isInstanceOf(ReglaDeNegocioViolada.class)
                    .satisfies(e -> assertThat(((ReglaDeNegocioViolada) e).getErrores())
                            .isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Invariante 7 — solo el autor edita, y solo en BORRADOR, EN_CONSTRUCCION o RECHAZADA")
    class Invariante7 {

        @Test
        @DisplayName("editar en BORRADOR la pasa a EN_CONSTRUCCION")
        void editarEnBorradorPasaAEnConstruccion() {
            Pregunta pregunta = nueva();
            var nuevoContenido = DatosDePrueba.contenidoCon(
                    "Un contexto ya trabajado por el autor.",
                    "¿Qué propiedad se compromete al compartir la base de datos?");

            pregunta.editar(DatosDePrueba.AUTOR, nuevoContenido, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.getContenido().contexto())
                    .isEqualTo("Un contexto ya trabajado por el autor.");
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.EN_CONSTRUCCION);
            assertThat(pregunta.getHistorialEstados().getLast().anterior())
                    .isEqualTo(EstadoPregunta.BORRADOR);
        }

        @Test
        @DisplayName("editar en EN_CONSTRUCCION no cambia el estado ni añade historial")
        void editarEnConstruccionNoCambiaEstado() {
            Pregunta pregunta = en(EstadoPregunta.EN_CONSTRUCCION);
            int historialAntes = pregunta.getHistorialEstados().size();

            trabajar(pregunta);

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.EN_CONSTRUCCION);
            assertThat(pregunta.getHistorialEstados()).hasSize(historialAntes);
        }

        @Test
        @DisplayName("editar en RECHAZADA la reabre: pasa a EN_CONSTRUCCION")
        void editarEnRechazadaLaReabre() {
            Pregunta pregunta = en(EstadoPregunta.RECHAZADA);

            trabajar(pregunta);

            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.EN_CONSTRUCCION);
            assertThat(pregunta.getHistorialEstados().getLast().anterior())
                    .isEqualTo(EstadoPregunta.RECHAZADA);
            assertThat(pregunta.getObservacionesUltimaRevision())
                    .as("el autor sigue viendo qué corregir hasta que la reenvía")
                    .isPresent();
        }

        @Test
        @DisplayName("alguien que no es el autor no puede editar")
        void otroUsuarioNoEdita() {
            Pregunta pregunta = nueva();

            assertThatThrownBy(() -> pregunta.editar(DatosDePrueba.OTRO_USUARIO,
                    DatosDePrueba.contenidoValido(), validador, DatosDePrueba.AHORA))
                    .isInstanceOf(AccesoNoAutorizado.class)
                    .hasMessageContaining("Solo el autor")
                    .hasMessageContaining("Invariante 7");
        }

        @Test
        @DisplayName("no se puede editar fuera de BORRADOR, EN_CONSTRUCCION o RECHAZADA")
        void noEditaFueraDeLosEstadosEditables() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.PENDIENTE_REVISION,
                    EstadoPregunta.EN_REVISION, EstadoPregunta.APROBADA,
                    EstadoPregunta.PUBLICADA, EstadoPregunta.ARCHIVADA)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> trabajar(pregunta))
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
                    DatosDePrueba.opcionesValidas().subList(0, 2));

            assertThatThrownBy(() -> pregunta.editar(DatosDePrueba.AUTOR, contenidoRoto, validador,
                    DatosDePrueba.AHORA))
                    .isInstanceOf(ReglaDeNegocioViolada.class);

            assertThat(pregunta.getContenido().contexto()).isEqualTo(contextoOriginal);
            assertThat(pregunta.getContenido().opciones()).hasSize(4);
            assertThat(pregunta.getEstado())
                    .as("una edición rechazada no cuenta como trabajar la pregunta")
                    .isEqualTo(EstadoPregunta.BORRADOR);
        }
    }

    @Nested
    @DisplayName("Invariante 6 — transiciones válidas")
    class TransicionesValidas {

        @Test
        @DisplayName("EN_CONSTRUCCION → PENDIENTE_REVISION al enviar a revisión")
        void enConstruccionAPendiente() {
            Pregunta pregunta = en(EstadoPregunta.EN_CONSTRUCCION);

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
        @DisplayName("EN_REVISION → RECHAZADA con el rechazo, guardando las observaciones")
        void enRevisionARechazada() {
            Pregunta pregunta = en(EstadoPregunta.EN_REVISION);

            boolean cambio = pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("El contexto supera las 120 palabras recomendadas.",
                            "El distractor 3 es demasiado evidente."),
                    DatosDePrueba.AHORA);

            assertThat(cambio).isTrue();
            assertThat(pregunta.getEstado())
                    .as("el rechazo deja la pregunta en RECHAZADA para que el autor la corrija (ADR 2)")
                    .isEqualTo(EstadoPregunta.RECHAZADA);
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
                            .contenido().opciones()).hasSize(4));
        }

        @Test
        @DisplayName("se puede archivar desde BORRADOR, EN_CONSTRUCCION, RECHAZADA, APROBADA y PUBLICADA")
        void archivarDesdeLosCincoEstados() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.BORRADOR,
                    EstadoPregunta.EN_CONSTRUCCION, EstadoPregunta.RECHAZADA,
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
        @DisplayName("el rechazo, la reapertura y la corrección permiten reenviar a revisión")
        void cicloCompletoDeCorreccion() {
            Pregunta pregunta = en(EstadoPregunta.RECHAZADA);
            pregunta.extraerEventosPendientes();

            pregunta.editar(DatosDePrueba.AUTOR,
                    DatosDePrueba.contenidoCon("Contexto ya recortado a lo esencial.",
                            "¿Qué propiedad se compromete al compartir la base de datos?"),
                    validador, DatosDePrueba.AHORA);
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
        @DisplayName("una pregunta recién creada no puede saltar a PENDIENTE_REVISION")
        void noSaltaDeBorradorAPendiente() {
            Pregunta pregunta = nueva();

            assertThatThrownBy(() -> pregunta.enviarARevision(
                    DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA))
                    .isInstanceOf(TransicionInvalida.class)
                    .hasMessageContaining("EN_CONSTRUCCION")
                    .hasMessageContaining("Invariante 6");
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.BORRADOR);
        }

        @Test
        @DisplayName("solo se envía a revisión desde EN_CONSTRUCCION")
        void noEnviaDesdeOtrosEstados() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.BORRADOR,
                    EstadoPregunta.RECHAZADA, EstadoPregunta.PENDIENTE_REVISION,
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
                    EstadoPregunta.EN_CONSTRUCCION, EstadoPregunta.PENDIENTE_REVISION,
                    EstadoPregunta.EN_REVISION, EstadoPregunta.RECHAZADA,
                    EstadoPregunta.PUBLICADA, EstadoPregunta.ARCHIVADA)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> pregunta.publicar(
                        DatosDePrueba.ADMINISTRADOR, validador, DatosDePrueba.AHORA))
                        .as("publicar desde %s", estado)
                        .isInstanceOf(TransicionInvalida.class);
            }
        }

        @Test
        @DisplayName("no se puede archivar mientras está en el ciclo de revisión")
        void noArchivaDuranteLaRevision() {
            for (EstadoPregunta estado : List.of(EstadoPregunta.PENDIENTE_REVISION,
                    EstadoPregunta.EN_REVISION)) {

                Pregunta pregunta = en(estado);

                assertThatThrownBy(() -> pregunta.archivar(
                        DatosDePrueba.ADMINISTRADOR, null, DatosDePrueba.AHORA))
                        .as("archivar desde %s", estado)
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
            Pregunta pregunta = en(EstadoPregunta.RECHAZADA);
            int historialAntes = pregunta.getHistorialEstados().size();

            boolean segundo = pregunta.registrarRechazoPorPares(DatosDePrueba.REVISOR,
                    List.of("Recorta el contexto."), DatosDePrueba.AHORA);

            assertThat(segundo).isFalse();
            assertThat(pregunta.getEstado()).isEqualTo(EstadoPregunta.RECHAZADA);
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
        @DisplayName("una PUBLICADA conserva sus 4 opciones con una sola correcta")
        void publicadaConservaSusCuatroOpciones() {
            Pregunta pregunta = en(EstadoPregunta.PUBLICADA);

            assertThat(pregunta.getContenido().opciones()).hasSize(4);
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
                            EstadoPregunta.EN_CONSTRUCCION,
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
            Pregunta pregunta = en(EstadoPregunta.EN_CONSTRUCCION);
            pregunta.enviarARevision(DatosDePrueba.AUTOR, validador, DatosDePrueba.AHORA);

            assertThat(pregunta.extraerEventosPendientes()).hasSize(1);
            assertThat(pregunta.extraerEventosPendientes())
                    .as("un evento no se puede publicar dos veces")
                    .isEmpty();
        }

        @Test
        @DisplayName("el evento de envío lleva el código de competencia para el otro contexto")
        void eventoLlevaCompetencia() {
            Pregunta pregunta = en(EstadoPregunta.EN_CONSTRUCCION);
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
