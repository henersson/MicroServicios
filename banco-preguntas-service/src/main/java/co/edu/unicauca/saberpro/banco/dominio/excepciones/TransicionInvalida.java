package co.edu.unicauca.saberpro.banco.dominio.excepciones;

import co.edu.unicauca.saberpro.banco.dominio.modelo.EstadoPregunta;

import java.io.Serial;

/**
 * Se lanza cuando se intenta un cambio de estado que la máquina de estados del
 * agregado {@code Pregunta} no permite (invariante 6).
 *
 * <p>La capa de interfaces la traduce a <strong>409 Conflict</strong>: la
 * petición está bien formada, pero choca con el estado actual del recurso.
 */
public class TransicionInvalida extends ExcepcionDominio {

    @Serial
    private static final long serialVersionUID = 1L;

    public TransicionInvalida(EstadoPregunta desde, EstadoPregunta hacia) {
        super("No se puede pasar de %s a %s. Transiciones permitidas desde %s: %s."
                .formatted(desde, hacia, desde,
                        desde.transicionesPermitidas().isEmpty()
                                ? "ninguna, es un estado final"
                                : desde.transicionesPermitidas()));
    }

    public TransicionInvalida(String mensaje) {
        super(mensaje);
    }
}
