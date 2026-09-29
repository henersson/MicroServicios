package co.edu.unicauca.saberpro.banco.aplicacion.dto;

import java.util.List;

/**
 * DTO de entrada con el contenido de una pregunta. Lo usan tanto el caso de uso
 * de creación como el de edición, porque el autor puede escribir exactamente lo
 * mismo en los dos.
 *
 * <p>Llega con tipos primitivos y se convierte a Value Objects del dominio en el
 * caso de uso: es ahí donde un texto suelto se vuelve una {@code Competencia} o
 * un {@code NivelDificultad}, y donde un valor inválido se convierte en un error
 * de negocio legible.
 */
public record DatosContenidoPregunta(
        String contexto,
        String preguntaDirecta,
        List<DatosOpcion> opciones,
        String justificacion,
        List<String> bibliografia,
        String competenciaCodigo,
        String competenciaNombre,
        String tema,
        String subtema,
        String nivelDificultad) {
}
