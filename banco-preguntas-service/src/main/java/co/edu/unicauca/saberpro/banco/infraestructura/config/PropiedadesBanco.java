package co.edu.unicauca.saberpro.banco.infraestructura.config;

import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros configurables de las reglas de negocio del banco.
 *
 * <p>Están fuera del dominio a propósito: el agregado impone la regla, pero no
 * decide su umbral. Así el coordinador de la asignatura puede endurecer la
 * longitud mínima de una opción sin tocar ni recompilar el dominio.
 *
 * <p>Se configuran con el prefijo {@code banco} y llegan desde variables de
 * entorno en Docker (ver {@code .env.example}).
 *
 * @param opcionLongitudMinima invariante 3: caracteres mínimos de una opción,
 *                             sin contar espacios
 */
@ConfigurationProperties(prefix = "banco")
public record PropiedadesBanco(Integer opcionLongitudMinima) {

    public PropiedadesBanco {
        if (opcionLongitudMinima == null || opcionLongitudMinima < 1) {
            opcionLongitudMinima = ValidadorEstructural.LONGITUD_MINIMA_OPCION_POR_DEFECTO;
        }
    }
}
