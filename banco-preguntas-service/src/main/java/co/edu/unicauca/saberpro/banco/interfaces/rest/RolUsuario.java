package co.edu.unicauca.saberpro.banco.interfaces.rest;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.AccesoNoAutorizado;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Roles del sistema, simulados con la cabecera {@code X-Usuario-Rol}.
 *
 * <p>El Bounded Context de Usuarios y Roles no se implementa en este taller. En
 * su lugar, cada petición declara quién es y qué rol tiene, y esta capa lo
 * comprueba.
 *
 * <p>Vive en {@code interfaces} y no en {@code dominio} justamente porque es un
 * apaño de transporte: el día que exista autenticación real con JWT, esto
 * desaparece sin que el dominio se entere. Lo que sí es del dominio es la
 * invariante 7 ("solo el autor edita"), y esa la comprueba el agregado.
 */
public enum RolUsuario {

    AUTOR,
    REVISOR,
    ADMINISTRADOR,
    DOCENTE,
    ESTUDIANTE,
    COORDINADOR;

    public static RolUsuario desdeCabeceraONulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Comprueba que el rol sea uno de los permitidos para la operación.
     *
     * @throws AccesoNoAutorizado con un mensaje que dice qué rol haría falta
     */
    public void exigirQueSeaUnoDe(String operacion, RolUsuario... permitidos) {
        boolean autorizado = Arrays.asList(permitidos).contains(this);
        if (!autorizado) {
            throw new AccesoNoAutorizado(
                    "El rol %s no puede %s. Se requiere: %s.".formatted(
                            this, operacion,
                            Arrays.stream(permitidos).map(Enum::name)
                                    .collect(Collectors.joining(" o "))));
        }
    }

    static String valoresValidos() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
