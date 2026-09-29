package co.edu.unicauca.saberpro.banco.aplicacion.dto;

import co.edu.unicauca.saberpro.banco.dominio.modelo.CambioEstado;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de salida de una entrada del historial de estados.
 *
 * <p>Lo consume el endpoint de historial, reservado al ADMINISTRADOR: es la
 * trazabilidad de quién movió la pregunta, cuándo y por qué.
 *
 * @param estadoAnterior null en la entrada de creación
 */
public record CambioEstadoDto(
        String estadoAnterior,
        String estadoNuevo,
        UUID usuarioId,
        Instant fecha,
        String motivo) {

    public static CambioEstadoDto desde(CambioEstado cambio) {
        return new CambioEstadoDto(
                cambio.anterior() == null ? null : cambio.anterior().name(),
                cambio.nuevo().name(),
                cambio.usuarioId(),
                cambio.fecha(),
                cambio.motivo());
    }
}
