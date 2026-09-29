package co.edu.unicauca.saberpro.banco.dominio.excepciones;

import java.io.Serial;
import java.util.List;

/**
 * Raíz de las excepciones del dominio del BC Banco de Preguntas.
 *
 * <p>Todas llevan el mensaje en español porque llegan tal cual al usuario a
 * través del manejador global de errores de la capa de interfaces, que las
 * traduce a Problem Details (RFC 7807).
 *
 * <p>El dominio no conoce HTTP: quién decide el código de estado es la capa de
 * interfaces, según la subclase concreta que se haya lanzado.
 */
public abstract class ExcepcionDominio extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Detalles adicionales del fallo, uno por regla incumplida. Puede ir vacía. */
    private final List<String> errores;

    protected ExcepcionDominio(String mensaje) {
        this(mensaje, List.of());
    }

    protected ExcepcionDominio(String mensaje, List<String> errores) {
        super(mensaje);
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
