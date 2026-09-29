package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: el tema dentro de la competencia
 * (por ejemplo "Arquitectura de Software").
 *
 * <p>Se modela como tipo propio y no como {@code String} para que el compilador
 * impida confundirlo con el subtema al pasarlos como argumentos, que es un error
 * fácil de cometer y difícil de ver en una revisión de código.
 */
public record Tema(String nombre) {

    public Tema {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada("El tema es obligatorio.");
        }
        nombre = nombre.trim();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
