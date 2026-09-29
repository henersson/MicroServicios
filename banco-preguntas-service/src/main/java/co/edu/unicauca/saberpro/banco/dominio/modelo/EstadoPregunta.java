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
 * <p>Aquí vive la <strong>invariante 6</strong>: las transiciones permitidas
 * están declaradas en un único sitio, de modo que ni un caso de uso ni un
 * consumidor de eventos puedan inventarse una distinta. El agregado
 * {@code Pregunta} consulta este mapa antes de cualquier cambio de estado.
 *
 * <p>Tabla de transiciones:
 * <pre>
 *   BORRADOR            → PENDIENTE_REVISION   el autor la envía a revisión
 *   PENDIENTE_REVISION  → EN_REVISION          llega el evento RevisorAsignado
 *   EN_REVISION         → APROBADA             llega PreguntaAprobadaTecnicamente
 *   EN_REVISION         → BORRADOR             llega PreguntaRechazadaPorPares
 *   APROBADA            → PUBLICADA            el administrador publica
 *   BORRADOR|APROBADA|PUBLICADA → ARCHIVADA    el administrador archiva
 * </pre>
 *
 * <p>No existe un estado RECHAZADA: el rechazo devuelve la pregunta a BORRADOR
 * para que el autor la corrija (ADR 2). ARCHIVADA es la única salida y no
 * tiene retorno, porque en el banco nada se borra (invariante 8, ADR 3).
 */
public enum EstadoPregunta {

    /** Recién creada o devuelta por un rechazo. Es el único estado editable. */
    BORRADOR,

    /** Validada y a la espera de que el revision-service le asigne un revisor. */
    PENDIENTE_REVISION,

    /** Ya tiene revisor asignado y está siendo evaluada. */
    EN_REVISION,

    /** Superó la revisión por pares. Lista para que un administrador la publique. */
    APROBADA,

    /** Disponible para los simulacros. */
    PUBLICADA,

    /** Fuera de uso. Estado final: no se sale de aquí. */
    ARCHIVADA;

    private static final Map<EstadoPregunta, Set<EstadoPregunta>> TRANSICIONES =
            new EnumMap<>(EstadoPregunta.class);

    static {
        TRANSICIONES.put(BORRADOR, Set.of(PENDIENTE_REVISION, ARCHIVADA));
        TRANSICIONES.put(PENDIENTE_REVISION, Set.of(EN_REVISION));
        TRANSICIONES.put(EN_REVISION, Set.of(APROBADA, BORRADOR));
        TRANSICIONES.put(APROBADA, Set.of(PUBLICADA, ARCHIVADA));
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

    /** Invariante 7: solo se edita en BORRADOR. */
    public boolean permiteEdicion() {
        return this == BORRADOR;
    }

    public static EstadoPregunta desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioViolada("El estado es obligatorio.");
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioViolada(
                    "Estado '%s' desconocido. Valores válidos: BORRADOR, PENDIENTE_REVISION, "
                            .formatted(texto)
                            + "EN_REVISION, APROBADA, PUBLICADA, ARCHIVADA.");
        }
    }
}
