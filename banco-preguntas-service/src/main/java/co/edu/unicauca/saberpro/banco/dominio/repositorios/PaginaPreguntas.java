package co.edu.unicauca.saberpro.banco.dominio.repositorios;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;

import java.util.List;

/**
 * Una página de resultados de una búsqueda de preguntas.
 *
 * <p>Existe para que el dominio y la capa de aplicación puedan paginar sin
 * importar {@code org.springframework.data.domain.Page}, que arrastraría Spring
 * hasta el dominio y rompería la regla de dependencias de Clean Architecture.
 *
 * @param contenido      preguntas de esta página
 * @param pagina         número de página, empezando en 0
 * @param tamano         tamaño de página solicitado
 * @param totalElementos total de preguntas que cumplen el filtro
 */
public record PaginaPreguntas(
        List<Pregunta> contenido,
        int pagina,
        int tamano,
        long totalElementos) {

    public PaginaPreguntas {
        contenido = List.copyOf(contenido);
    }

    public int totalPaginas() {
        return tamano <= 0 ? 0 : (int) Math.ceil((double) totalElementos / tamano);
    }
}
