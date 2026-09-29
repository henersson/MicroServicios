package co.edu.unicauca.saberpro.banco.aplicacion.dto;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;

import java.util.List;
import java.util.UUID;

/**
 * DTO de salida de una pregunta.
 *
 * <p>Existe para no exponer nunca el agregado ni la entidad JPA en la API: si se
 * devolviera {@code Pregunta}, cualquier refactor del dominio rompería a los
 * clientes, y las entidades JPA arrastrarían colecciones perezosas hasta el
 * serializador.
 *
 * <p>Los Value Objects se aplanan a tipos primitivos, igual que en el
 * {@code .proto}, para que el JSON sea el que promete el contrato.
 *
 * @param observacionesUltimaRevision null salvo que la pregunta venga de un
 *                                    rechazo; es lo que el autor debe corregir
 */
public record PreguntaDto(
        UUID id,
        UUID autorId,
        String contexto,
        String preguntaDirecta,
        List<OpcionDto> opciones,
        String justificacion,
        List<String> bibliografia,
        String competenciaCodigo,
        String competenciaNombre,
        String tema,
        String subtema,
        String nivelDificultad,
        String estado,
        String observacionesUltimaRevision) {

    /** DTO de salida de una opción de respuesta. */
    public record OpcionDto(String texto, boolean esCorrecta) {
    }

    /** Proyecta el agregado a su representación de salida. */
    public static PreguntaDto desde(Pregunta pregunta) {
        var contenido = pregunta.getContenido();
        return new PreguntaDto(
                pregunta.getId(),
                pregunta.getAutorId(),
                contenido.contexto(),
                contenido.preguntaDirecta(),
                contenido.opciones().stream()
                        .map(opcion -> new OpcionDto(opcion.texto(), opcion.esCorrecta()))
                        .toList(),
                contenido.justificacion().texto(),
                contenido.bibliografia().referencias(),
                contenido.competencia().codigo(),
                contenido.competencia().nombre(),
                contenido.tema().nombre(),
                contenido.subtema().nombre(),
                contenido.nivelDificultad().name(),
                pregunta.getEstado().name(),
                pregunta.getObservacionesUltimaRevision().orElse(null));
    }

    /** Texto de la única opción correcta, útil para logs y pruebas. */
    public String textoOpcionCorrecta() {
        return opciones.stream()
                .filter(OpcionDto::esCorrecta)
                .map(OpcionDto::texto)
                .findFirst()
                .orElse(null);
    }
}
