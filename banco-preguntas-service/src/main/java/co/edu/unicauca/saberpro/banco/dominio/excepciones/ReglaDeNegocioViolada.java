package co.edu.unicauca.saberpro.banco.dominio.excepciones;

import java.io.Serial;
import java.util.List;

/**
 * Se lanza cuando el contenido de una pregunta incumple las invariantes
 * estructurales 1 a 5 (las que valida {@code ValidadorEstructural} más la 5).
 *
 * <p>La capa de interfaces la traduce a <strong>400 Bad Request</strong> con la
 * lista completa de errores, para que el autor pueda corregirlos todos de una
 * sola pasada en vez de uno por intento.
 */
public class ReglaDeNegocioViolada extends ExcepcionDominio {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReglaDeNegocioViolada(String mensaje) {
        super(mensaje);
    }

    public ReglaDeNegocioViolada(String mensaje, List<String> errores) {
        super(mensaje, errores);
    }
}
