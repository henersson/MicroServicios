package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: el subtema dentro del tema
 * (por ejemplo "Microservicios" dentro de "Arquitectura de Software").
 *
 * @see Tema
 */
public record Subtema(String nombre) {

    public Subtema {
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaDeNegocioViolada("El subtema es obligatorio.");
        }
        nombre = nombre.trim();
    }

    @Override
    public String toString() {
        return nombre;
    }
}
