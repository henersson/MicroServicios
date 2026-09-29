package co.edu.unicauca.saberpro.banco.interfaces.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Quién hace la petición, leído de las cabeceras {@code X-Usuario-Id} y
 * {@code X-Usuario-Rol}.
 *
 * <p>Sustituye al usuario autenticado mientras no exista el microservicio de
 * Usuarios y Roles. Se resuelve una sola vez al entrar al controlador y de ahí
 * en adelante circulan el identificador y el rol ya validados, no cadenas
 * sueltas que cada método tenga que volver a comprobar.
 */
public record ContextoUsuario(UUID usuarioId, RolUsuario rol) {

    public static final String CABECERA_ID = "X-Usuario-Id";
    public static final String CABECERA_ROL = "X-Usuario-Rol";

    /**
     * Valida las dos cabeceras y junta todos los problemas en un solo error, para
     * que quien llama vea de una vez todo lo que le falta.
     *
     * @throws UsuarioNoIdentificado si falta alguna cabecera o trae un valor
     *                               inválido. Se traduce a 401
     */
    public static ContextoUsuario desdeCabeceras(String usuarioId, String rol) {
        List<String> errores = new ArrayList<>();
        UUID id = null;
        RolUsuario rolUsuario = null;

        if (usuarioId == null || usuarioId.isBlank()) {
            errores.add("Falta la cabecera " + CABECERA_ID + " con el UUID del usuario.");
        } else {
            try {
                id = UUID.fromString(usuarioId.trim());
            } catch (IllegalArgumentException e) {
                errores.add("La cabecera %s no contiene un UUID válido: '%s'."
                        .formatted(CABECERA_ID, usuarioId));
            }
        }

        if (rol == null || rol.isBlank()) {
            errores.add("Falta la cabecera %s. Valores válidos: %s."
                    .formatted(CABECERA_ROL, RolUsuario.valoresValidos()));
        } else {
            rolUsuario = RolUsuario.desdeCabeceraONulo(rol);
            if (rolUsuario == null) {
                errores.add("Rol '%s' desconocido. Valores válidos: %s."
                        .formatted(rol, RolUsuario.valoresValidos()));
            }
        }

        if (!errores.isEmpty()) {
            throw new UsuarioNoIdentificado(errores);
        }
        return new ContextoUsuario(id, rolUsuario);
    }

    /** Atajo de {@code rol().exigirQueSeaUnoDe(...)}. */
    public void exigirRol(String operacion, RolUsuario... permitidos) {
        rol.exigirQueSeaUnoDe(operacion, permitidos);
    }
}
