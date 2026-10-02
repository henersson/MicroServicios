package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.eventos.EventoDominio;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaArchivada;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaEnviadaARevision;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaPublicada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.AccesoNoAutorizado;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.TransicionInvalida;
import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * <strong>Aggregate Root</strong> del BC Banco de Preguntas. Garantiza las
 * invariantes 1 a 8.
 *
 * <p>Es el único punto por el que se modifica una pregunta. No tiene setters: se
 * cambia llamando a operaciones con nombre de negocio ({@link #enviarARevision},
 * {@link #publicar}, {@link #archivar}...), y cada una comprueba antes lo que
 * tenga que comprobar. Así es imposible dejar una pregunta en un estado que el
 * dominio no permita, sin importar desde qué capa se la llame.
 *
 * <p>Esta clase <strong>no conoce Spring, JPA, AMQP ni gRPC</strong>. Un test de
 * ArchUnit lo verifica en cada build. La persistencia se hace con entidades JPA
 * aparte y un mapeador, precisamente para que las necesidades del ORM no
 * deformen el modelo de dominio.
 *
 * <h2>Invariantes</h2>
 * <ol>
 *   <li>Exactamente 3 opciones incorrectas y 1 correcta. → {@code ValidadorEstructural}</li>
 *   <li>Ninguna opción del tipo "todas/ninguna de las anteriores". → {@code ValidadorEstructural}</li>
 *   <li>Opciones con longitud mínima y sin repetirse; la coherencia gramatical la
 *       juzga el revisor. → {@code ValidadorEstructural} y el revision-service</li>
 *   <li>Un único contexto y una única pregunta directa, obligatorios. → {@code ValidadorEstructural}</li>
 *   <li>Una pregunta PUBLICADA nunca queda sin sus 4 opciones completas. → {@link #publicar}</li>
 *   <li>Las transiciones respetan la tabla de estados. → {@link EstadoPregunta} y {@link #cambiarEstado}</li>
 *   <li>Solo el autor edita, y solo en BORRADOR, EN_CONSTRUCCION o RECHAZADA. → {@link #editar}</li>
 *   <li>No se elimina físicamente; solo se archiva. → no existe ninguna operación de borrado</li>
 * </ol>
 */
public class Pregunta {

    private final UUID id;
    private final UUID autorId;

    private ContenidoPregunta contenido;
    private EstadoPregunta estado;

    /** Observaciones del último rechazo, para que el autor sepa qué corregir. */
    private String observacionesUltimaRevision;

    private final List<CambioEstado> historialEstados;

    /**
     * Eventos que este agregado generó y todavía no se han publicado. El caso de
     * uso los recoge tras guardar y los entrega al puerto {@code PublicadorEventos}.
     */
    private final List<EventoDominio> eventosPendientes = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Construcción
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructor completo, para uso exclusivo de la capa de persistencia al
     * reconstruir una pregunta ya existente.
     *
     * <p>No valida las invariantes estructurales: lo que está en la base de datos
     * ya pasó por ellas al crearse o editarse, y revalidarlo aquí haría que un
     * cambio de las reglas dejara inaccesibles las preguntas antiguas.
     */
    public Pregunta(UUID id, UUID autorId, ContenidoPregunta contenido, EstadoPregunta estado,
                    String observacionesUltimaRevision, List<CambioEstado> historialEstados) {
        this.id = Objects.requireNonNull(id, "El identificador de la pregunta es obligatorio.");
        this.autorId = Objects.requireNonNull(autorId, "El autor de la pregunta es obligatorio.");
        this.contenido = Objects.requireNonNull(contenido, "El contenido es obligatorio.");
        this.estado = Objects.requireNonNull(estado, "El estado es obligatorio.");
        this.observacionesUltimaRevision = observacionesUltimaRevision;
        this.historialEstados = new ArrayList<>(
                historialEstados == null ? List.of() : historialEstados);
    }

    /**
     * Crea una pregunta nueva en estado BORRADOR.
     *
     * <p>Recibe el {@code ValidadorEstructural} en lugar de instanciarlo porque
     * la longitud mínima de una opción es configurable: el agregado impone la
     * regla, pero no decide su parámetro.
     *
     * @throws ReglaDeNegocioViolada si el contenido incumple las invariantes 1 a 4
     */
    public static Pregunta crear(UUID id, UUID autorId, ContenidoPregunta contenido,
                                 ValidadorEstructural validador, Instant ahora) {
        exigirContenidoValido(contenido, validador,
                "No se puede crear la pregunta porque su contenido no cumple las reglas del banco.");

        Pregunta pregunta = new Pregunta(id, autorId, contenido, EstadoPregunta.BORRADOR,
                null, List.of(CambioEstado.creacion(autorId, ahora)));
        // La creación no publica ningún evento de integración: mientras está en
        // BORRADOR la pregunta solo le interesa a su autor.
        return pregunta;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operaciones de negocio
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reemplaza el contenido de la pregunta.
     *
     * <p><strong>Invariante 7</strong>: solo el autor, y solo en BORRADOR,
     * EN_CONSTRUCCION o RECHAZADA. Lo segundo es lo que impide que alguien cambie
     * una pregunta mientras un revisor la está evaluando, o después de aprobada.
     *
     * <p>Editar en BORRADOR o en RECHAZADA lleva la pregunta a EN_CONSTRUCCION:
     * es el estado en que el autor la trabaja, y desde el único que puede
     * enviarla a revisión. Las ediciones siguientes la dejan donde está.
     *
     * @throws AccesoNoAutorizado    si quien edita no es el autor
     * @throws TransicionInvalida    si la pregunta no está en un estado editable
     * @throws ReglaDeNegocioViolada si el contenido nuevo incumple las invariantes 1 a 4
     */
    public void editar(UUID usuarioId, ContenidoPregunta contenidoNuevo,
                       ValidadorEstructural validador, Instant ahora) {
        exigirQueSeaElAutor(usuarioId, "editar");

        if (!estado.permiteEdicion()) {
            throw new TransicionInvalida(
                    ("Solo se puede editar una pregunta en BORRADOR, EN_CONSTRUCCION o RECHAZADA, "
                            + "y esta está en %s. (Invariante 7)").formatted(estado));
        }

        exigirContenidoValido(contenidoNuevo, validador,
                "No se puede guardar la edición porque el contenido no cumple las reglas del banco.");

        this.contenido = contenidoNuevo;

        if (estado == EstadoPregunta.BORRADOR) {
            cambiarEstado(EstadoPregunta.EN_CONSTRUCCION, usuarioId, ahora,
                    "El autor empezó a trabajar la pregunta.");
        } else if (estado == EstadoPregunta.RECHAZADA) {
            cambiarEstado(EstadoPregunta.EN_CONSTRUCCION, usuarioId, ahora,
                    "El autor reabrió la pregunta rechazada para corregirla.");
        }
    }

    /**
     * El autor manda la pregunta al ciclo de revisión por pares. Solo se puede
     * desde EN_CONSTRUCCION (invariante 6).
     *
     * <p>Revalida el contenido aunque ya se validó al crear y al editar: es la
     * última puerta antes de que la pregunta salga de este contexto, y las reglas
     * pueden haberse endurecido desde que se guardó.
     *
     * <p>Registra el evento {@code PreguntaEnviadaARevision}.
     */
    public void enviarARevision(UUID usuarioId, ValidadorEstructural validador, Instant ahora) {
        exigirQueSeaElAutor(usuarioId, "enviar a revisión");

        if (estado == EstadoPregunta.BORRADOR || estado == EstadoPregunta.RECHAZADA) {
            throw new TransicionInvalida(
                    ("Una pregunta en %s no se puede enviar a revisión: primero tiene que pasar a "
                            + "EN_CONSTRUCCION, y eso ocurre al editarla. (Invariante 6)")
                            .formatted(estado));
        }

        exigirContenidoValido(contenido, validador,
                "La pregunta no se puede enviar a revisión porque no cumple las reglas del banco.");

        cambiarEstado(EstadoPregunta.PENDIENTE_REVISION, usuarioId, ahora,
                "El autor la envió a revisión por pares.");

        // Al reenviar tras un rechazo, las observaciones anteriores dejan de
        // aplicar: se refieren a una versión del contenido que ya cambió.
        this.observacionesUltimaRevision = null;

        eventosPendientes.add(PreguntaEnviadaARevision.de(
                id, autorId, contenido.competencia().codigo(), ahora));
    }

    /**
     * Aplica el evento {@code RevisorAsignado} que llega del revision-service:
     * ya hay un revisor trabajando en la pregunta.
     *
     * <p>Es idempotente por diseño: si la pregunta ya está en EN_REVISION,
     * no hace nada y devuelve false, porque RabbitMQ entrega al menos una vez y
     * un evento repetido no puede romper nada (sección 6.3).
     *
     * @return true si el estado cambió; false si el evento ya estaba aplicado
     */
    public boolean registrarRevisorAsignado(UUID revisorId, Instant ahora) {
        if (estado == EstadoPregunta.EN_REVISION) {
            return false;
        }
        cambiarEstado(EstadoPregunta.EN_REVISION, revisorId, ahora,
                "Se asignó un revisor y comenzó la evaluación.");
        return true;
    }

    /**
     * Aplica el evento {@code PreguntaAprobadaTecnicamente}: la pregunta superó
     * la revisión por pares y queda lista para que un administrador la publique.
     *
     * @return true si el estado cambió; false si el evento ya estaba aplicado
     */
    public boolean registrarAprobacionTecnica(UUID revisorId, Instant ahora) {
        if (estado == EstadoPregunta.APROBADA) {
            return false;
        }
        cambiarEstado(EstadoPregunta.APROBADA, revisorId, ahora,
                "Aprobada técnicamente en la revisión por pares.");
        this.observacionesUltimaRevision = null;
        return true;
    }

    /**
     * Aplica el evento {@code PreguntaRechazadaPorPares}: la pregunta pasa a
     * RECHAZADA con las observaciones del revisor visibles para el autor, que la
     * reabre al editarla (ADR 2).
     *
     * <p>La idempotencia se comprueba con el estado de origen: un rechazo solo
     * tiene sentido desde EN_REVISION. Si llega repetido, la pregunta ya está en
     * RECHAZADA (o el autor ya la reabrió) y no se hace nada.
     *
     * @return true si el estado cambió; false si el evento ya estaba aplicado
     */
    public boolean registrarRechazoPorPares(UUID revisorId, List<String> observaciones,
                                            Instant ahora) {
        if (estado != EstadoPregunta.EN_REVISION) {
            return false;
        }
        cambiarEstado(EstadoPregunta.RECHAZADA, revisorId, ahora,
                "Rechazada en la revisión por pares; el autor debe corregirla.");
        this.observacionesUltimaRevision = observaciones == null || observaciones.isEmpty()
                ? "El revisor rechazó la pregunta sin detallar observaciones."
                : String.join("\n", observaciones);
        return true;
    }

    /**
     * El administrador publica la pregunta, que queda disponible para los
     * simulacros.
     *
     * <p><strong>Invariante 5</strong>: antes de publicar se vuelve a comprobar
     * que las 4 opciones sigan completas. Es la comprobación más importante del
     * agregado, porque una pregunta publicada a medias llegaría a un estudiante
     * en una prueba real.
     *
     * <p>Registra el evento {@code PreguntaPublicada} con el contenido completo.
     */
    public void publicar(UUID administradorId, ValidadorEstructural validador, Instant ahora) {
        // Invariante 5: una pregunta PUBLICADA nunca queda sin sus 4 opciones.
        ValidadorEstructural.ResultadoValidacion resultado = validador.validar(contenido);
        if (!resultado.esValido()) {
            throw new ReglaDeNegocioViolada(
                    "No se puede publicar la pregunta: su contenido dejó de cumplir las reglas "
                            + "del banco. (Invariante 5)",
                    resultado.errores());
        }

        cambiarEstado(EstadoPregunta.PUBLICADA, administradorId, ahora,
                "El administrador la publicó.");

        eventosPendientes.add(PreguntaPublicada.de(id, autorId, contenido, ahora));
    }

    /**
     * El administrador archiva la pregunta: sale de circulación sin borrarse
     * (invariante 8, ADR 3).
     *
     * <p>Registra el evento {@code PreguntaArchivada}.
     */
    public void archivar(UUID administradorId, String motivo, Instant ahora) {
        cambiarEstado(EstadoPregunta.ARCHIVADA, administradorId, ahora,
                motivo == null || motivo.isBlank()
                        ? "El administrador la archivó."
                        : motivo.trim());

        eventosPendientes.add(PreguntaArchivada.de(id, ahora));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reglas transversales
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Único camino para cambiar de estado. <strong>Invariante 6</strong>:
     * comprueba la transición contra la tabla y deja la traza en el historial.
     */
    private void cambiarEstado(EstadoPregunta destino, UUID usuarioId, Instant ahora,
                               String motivo) {
        if (!estado.puedePasarA(destino)) {
            throw new TransicionInvalida(estado, destino);
        }
        historialEstados.add(new CambioEstado(estado, destino, usuarioId, ahora, motivo));
        this.estado = destino;
    }

    /** Invariante 7: la pregunta es de quien la escribió. */
    private void exigirQueSeaElAutor(UUID usuarioId, String accion) {
        if (!autorId.equals(usuarioId)) {
            throw new AccesoNoAutorizado(
                    "Solo el autor de la pregunta puede %s. (Invariante 7)".formatted(accion));
        }
    }

    private static void exigirContenidoValido(ContenidoPregunta contenido,
                                              ValidadorEstructural validador, String mensaje) {
        ValidadorEstructural.ResultadoValidacion resultado = validador.validar(contenido);
        if (!resultado.esValido()) {
            throw new ReglaDeNegocioViolada(mensaje, resultado.errores());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Eventos de dominio
    // ─────────────────────────────────────────────────────────────────────────

    /** Eventos generados y todavía sin publicar. */
    public List<EventoDominio> eventosPendientes() {
        return Collections.unmodifiableList(eventosPendientes);
    }

    /**
     * Devuelve los eventos pendientes y los vacía. Lo llama el caso de uso
     * después de guardar, para entregarlos al publicador exactamente una vez.
     */
    public List<EventoDominio> extraerEventosPendientes() {
        List<EventoDominio> copia = List.copyOf(eventosPendientes);
        eventosPendientes.clear();
        return copia;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public ContenidoPregunta getContenido() {
        return contenido;
    }

    public EstadoPregunta getEstado() {
        return estado;
    }

    public Optional<String> getObservacionesUltimaRevision() {
        return Optional.ofNullable(observacionesUltimaRevision);
    }

    public List<CambioEstado> getHistorialEstados() {
        return Collections.unmodifiableList(historialEstados);
    }

    /** ¿Es {@code usuarioId} el autor de esta pregunta? */
    public boolean esAutor(UUID usuarioId) {
        return autorId.equals(usuarioId);
    }

    /**
     * Dos preguntas son la misma si tienen el mismo identificador, aunque su
     * contenido difiera: es una Entity, no un Value Object.
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        return otro instanceof Pregunta pregunta && id.equals(pregunta.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Pregunta[id=%s, estado=%s, competencia=%s]"
                .formatted(id, estado, contenido.competencia().codigo());
    }
}
