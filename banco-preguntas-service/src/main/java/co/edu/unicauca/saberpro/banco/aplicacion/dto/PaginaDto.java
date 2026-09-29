package co.edu.unicauca.saberpro.banco.aplicacion.dto;

import co.edu.unicauca.saberpro.banco.dominio.repositorios.PaginaPreguntas;

import java.util.List;

/**
 * DTO de salida de una página de resultados.
 *
 * <p>Tiene forma propia en vez de devolver el {@code Page} de Spring Data porque
 * ese JSON incluye media docena de campos internos (pageable, sort, first,
 * numberOfElements...) que convertirían un detalle de la librería en parte del
 * contrato público de la API.
 */
public record PaginaDto<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    public static PaginaDto<PreguntaDto> desde(PaginaPreguntas pagina) {
        return new PaginaDto<>(
                pagina.contenido().stream().map(PreguntaDto::desde).toList(),
                pagina.pagina(),
                pagina.tamano(),
                pagina.totalElementos(),
                pagina.totalPaginas());
    }
}
