package co.edu.unicauca.saberpro.banco.dominio.excepciones;

import java.io.Serial;

/**
 * Se lanza cuando quien hace la operación no tiene derecho a hacerla: no es el
 * autor de la pregunta (invariante 7) o su rol no se lo permite.
 *
 * <p>La capa de interfaces la traduce a <strong>403 Forbidden</strong>.
 *
 * <p>Ojo con la diferencia: 401 sería "no sé quién eres"; aquí sí sabemos quién
 * es (llegó en las cabeceras {@code X-Usuario-Id} y {@code X-Usuario-Rol}),
 * simplemente no puede hacer esto.
 */
public class AccesoNoAutorizado extends ExcepcionDominio {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccesoNoAutorizado(String mensaje) {
        super(mensaje);
    }
}
