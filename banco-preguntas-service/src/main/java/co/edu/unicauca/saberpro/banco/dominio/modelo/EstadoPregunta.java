package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Value Object del BC Banco de Preguntas: el estado del ciclo de vida de una
 * pregunta, junto con la máquina de estados que lo gobierna.
 *
 * <p>Son los 8 estados del lenguaje ubicuo del Taller 1 (término 11).
 *
 * <p>Aquí vive la <strong>invariante 6</strong>: las transiciones permitidas
 * están declaradas en un único sitio, de modo que ni un caso de uso ni un
 * consumidor de eventos puedan inventarse una distinta. El agregado
 * {@code Pregunta} consulta este mapa antes de cualquier cambio de estado.
 *
 * <p>Tabla de transiciones:
 * <pre>
 *   BORRADOR            → EN_CONSTRUCCION      el autor la edita por primera vez
 *   RECHAZADA           → EN_CONSTRUCCION      el autor la edita para corregirla
 *   EN_CONSTRUCCION     → PENDIENTE_REVISION   el autor la envía a revisión
 *   PENDIENTE_REVISION  → EN_REVISION          llega el evento RevisorAsignado
 *   EN_REVISION         → APROBADA             llega PreguntaAprobadaTecnicamente
 *   EN_REVISION         → RECHAZADA            llega PreguntaRechazadaPorPares
 *   APROBADA            → PUBLICADA            el administrador publica
 *   BORRADOR|EN_CONSTRUCCION|RECHAZADA|APROBADA|PUBLICADA → ARCHIVADA
 *                                              el administrador archiva
 * </pre>
 *
 * <p>Una pregunta recién creada no puede enviarse a revisión directamente: tiene
 * que pasar por EN_CONSTRUCCION, que es el estado en que el autor la trabaja
 * (invariante 6, sin saltos). El rechazo la deja en RECHAZADA con las
 * observaciones del revisor, y el autor la reabre al editarla (ADR 2).
 * ARCHIVADA es la única salida y no tiene retorno, porque en el banco nada se
 * borra (invariante 8, ADR 3).
 */
public enum EstadoPregunta {

    /** Recién creada por el autor. Todavía no la ha trabajado. Editable. */
    BORRADOR,

    /** El autor la está trabajando: la editó al menos una vez. Editable. */
    EN_CONSTRUCCION,

    /** Validada y a la espera de que el revision-service le asigne un revisor. */
    PENDIENTE_REVISION,

    /** Ya tiene revisor asignado y está siendo evaluada. */
    EN_REVISION,

    /** Superó la revisión por pares. Lista para que un administrador la publique. */
    APROBADA,

    /**
     * No superó la revisión por pares. Guarda las observaciones del revisor y el
     * autor la reabre al editarla. Editable.
     */
    RECHAZADA,

    /** Disponible para los simulacros. */
    PUBLICADA,

    /** Fuera de uso. Estado final: no se sale de aquí. */
    ARCHIVADA;

    private static final Map<EstadoPregunta, Set<EstadoPregunta>> TRANSICIONES =
            new EnumMap<>(EstadoPregunta.class);

    static {
        TRANSICIONES.put(BORRADOR, Set.of(EN_CONSTRUCCION, ARCHIVADA));
        TRANSICIONES.put(EN_CONSTRUCCION, Set.of(PENDIENTE_REVISION, ARCHIVADA));
        TRANSICIONES.put(PENDIENTE_REVISION, Set.of(EN_REVISION));
        TRANSICIONES.put(EN_REVISION, Set.of(APROBADA, RECHAZADA));
        TRANSICIONES.put(APROBADA, Set.of(PUBLICADA, ARCHIVADA));
        TRANSICIONES.put(RECHAZADA, Set.of(EN_CONSTRUCCION, ARCHIVADA));
        TRANSICIONES.put(PUBLICADA, Set.of(ARCHIVADA));
        TRANSICIONES.put(ARCHIVADA, Set.of());
    }

    /** Estados a los que se puede pasar desde este. Vacío si es un estado final. */
    public Set<EstadoPregunta> transicionesPermitidas() {
        return Collections.unmodifiableSet(TRANSICIONES.get(this));
    }

    /** Invariante 6: ¿es legal pasar de este estado a {@code destino}? */
    public boolean puedePasarA(EstadoPregunta destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    /**
     * Invariante 7: el autor edita en BORRADOR, EN_CONSTRUCCION y RECHAZADA.
     * Editar en BORRADOR o RECHAZADA es lo que lleva la pregunta a
     * EN_CONSTRUCCION.
     */
    public boolean permiteEdicion() {
        return this == BORRADOR || this == EN_CONSTRUCCION || this == RECHAZADA;
    }

    public static EstadoPregunta desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioViolada("El estado es obligatorio.");
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioViolada(
                    "Estado '%s' desconocido. Valores válidos: BORRADOR, EN_CONSTRUCCION, "
                            .formatted(texto)
                            + "PENDIENTE_REVISION, EN_REVISION, APROBADA, RECHAZADA, PUBLICADA, "
                            + "ARCHIVADA.");
        }
    }
}
