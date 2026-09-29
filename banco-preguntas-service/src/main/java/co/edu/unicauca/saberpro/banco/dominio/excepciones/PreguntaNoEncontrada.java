package co.edu.unicauca.saberpro.banco.dominio.excepciones;

import java.io.Serial;
import java.util.UUID;

/**
 * Se lanza cuando se pide una pregunta que no existe en el banco.
 *
 * <p>La capa de interfaces la traduce a <strong>404 Not Found</strong>, y el
 * servidor gRPC al código <strong>NOT_FOUND</strong>.
 */
public class PreguntaNoEncontrada extends ExcepcionDominio {

    @Serial
    private static final long serialVersionUID = 1L;

    public PreguntaNoEncontrada(UUID preguntaId) {
        super("No existe una pregunta con el identificador %s.".formatted(preguntaId));
    }
}
