package co.edu.unicauca.saberpro.banco.infraestructura.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Hace que todas las respuestas JSON declaren {@code charset=UTF-8}.
 *
 * <p>El RFC 8259 ya obliga a que todo JSON sea UTF-8, así que Spring dejó de
 * declararlo. Se declara igualmente porque hay clientes que, sin ese parámetro,
 * decodifican el cuerpo como ISO-8859-1 y destrozan los acentos del español.
 *
 * <p>Alcanza también a los errores, que viajan como
 * {@code application/problem+json}: se recorren los convertidores ya
 * configurados y se reemplazan sus tipos JSON por la versión con charset, en vez
 * de nombrar una clase concreta de convertidor.
 */
@Configuration
public class ConfiguracionCodificacion implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> convertidores) {
        for (HttpMessageConverter<?> convertidor : convertidores) {
            if (convertidor instanceof AbstractHttpMessageConverter<?> concreto) {
                List<MediaType> conCharset = concreto.getSupportedMediaTypes().stream()
                        .map(ConfiguracionCodificacion::agregarCharsetSiEsJson)
                        .toList();
                concreto.setSupportedMediaTypes(conCharset);
            }
        }
    }

    /** Devuelve el tipo con {@code charset=UTF-8} si es JSON; si no, sin tocar. */
    private static MediaType agregarCharsetSiEsJson(MediaType tipo) {
        boolean esJson = "json".equals(tipo.getSubtype())
                || tipo.getSubtype().endsWith("+json");
        if (!esJson || tipo.getCharset() != null) {
            return tipo;
        }
        return new MediaType(tipo.getType(), tipo.getSubtype(), StandardCharsets.UTF_8);
    }
}
