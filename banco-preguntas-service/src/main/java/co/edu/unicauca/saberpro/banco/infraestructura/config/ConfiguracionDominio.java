package co.edu.unicauca.saberpro.banco.infraestructura.config;

import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Publica como beans las piezas del dominio que no llevan anotaciones de Spring.
 *
 * <p>El dominio no conoce Spring: no hay ni un {@code @Service} en
 * {@code dominio/}, y un test de ArchUnit lo comprueba en cada build. Por eso
 * sus objetos se construyen aquí, en la capa de infraestructura, que es la que
 * sí puede conocer el framework.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesBanco.class)
public class ConfiguracionDominio {

    /**
     * Domain Service que aplica las invariantes 1 a 4, con la longitud mínima de
     * opción que diga la configuración.
     */
    @Bean
    public ValidadorEstructural validadorEstructural(PropiedadesBanco propiedades) {
        return new ValidadorEstructural(propiedades.opcionLongitudMinima());
    }

    /**
     * Reloj inyectable en vez de {@code Instant.now()} disperso por el código.
     *
     * <p>Es lo que permite que un test fije la hora y compruebe que el historial
     * de estados registra exactamente la fecha esperada, en lugar de conformarse
     * con "algo parecido a ahora".
     */
    @Bean
    public Clock reloj() {
        // UTC y no la zona del sistema: todas las fechas del contrato son UTC, y
        // depender de la zona del servidor haría que los eventos cambiaran de
        // significado según dónde se despliegue.
        return Clock.systemUTC();
    }
}
