package co.edu.unicauca.saberpro.banco.interfaces.rest;

import java.util.List;

/**
 * No se sabe quién hace la petición: faltan las cabeceras de usuario o vienen
 * mal.
 *
 * <p>Es distinto de {@code AccesoNoAutorizado}: allí sí sabemos quién es y no
 * puede hacer la operación (403); aquí no lo sabemos (401).
 *
 * <p>Vive en la capa de interfaces, no en el dominio, porque las cabeceras son
 * un detalle de transporte: el día que exista autenticación real con JWT, esto
 * desaparece sin que el dominio se entere.
 */
public class UsuarioNoIdentificado extends RuntimeException {

    private final List<String> errores;

    public UsuarioNoIdentificado(List<String> errores) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
    }

    /** Un mensaje por cada cabecera que falta o viene mal. */
    public List<String> getErrores() {
        return errores;
    }
}
