package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: la competencia Saber Pro que evalúa
 * la pregunta.
 *
 * <p>Lleva código y nombre juntos porque en el dominio no tienen sentido por
 * separado: un código sin nombre no se puede mostrar y un nombre sin código no
 * se puede filtrar. En el contrato gRPC y en los eventos viajan aplanados como
 * {@code competenciaCodigo} y {@code competenciaNombre}.
 *
 * @param codigo identificador corto y estable, por ejemplo "ING-SOFT"
 * @param nombre nombre legible, por ejemplo "Diseño de Software y Arquitectura"
 */
public record Competencia(String codigo, String nombre) {

    public Competencia {
        if (codigo == null || codigo.isBlank()) {
            throw new ReglaDeNegocioViolada("El código de la competencia es obligatorio.");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada("El nombre de la competencia es obligatorio.");
        }
        // El código se guarda siempre en mayúsculas para que los filtros por
        // competencia no dependan de cómo lo escribió quien creó la pregunta.
        codigo = codigo.trim().toUpperCase();
        nombre = nombre.trim();
    }
}
