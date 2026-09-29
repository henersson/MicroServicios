package co.edu.unicauca.saberpro.banco.aplicacion.dto;

/**
 * DTO de entrada de una opción de respuesta.
 *
 * <p>Vive en la capa de aplicación para que los casos de uso no dependan de las
 * clases de petición HTTP de la capa de interfaces ni, al revés, del Value
 * Object {@code Opcion} del dominio.
 */
public record DatosOpcion(String texto, boolean esCorrecta) {
}
