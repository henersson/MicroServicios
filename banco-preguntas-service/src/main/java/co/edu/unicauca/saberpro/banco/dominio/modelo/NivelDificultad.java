package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: qué tan difícil es la pregunta.
 *
 * <p>Se modela como enum porque el conjunto de valores es cerrado y forma parte
 * del contrato publicado (aparece en el {@code .proto} y en los JSON Schema de
 * los eventos como texto).
 */
public enum NivelDificultad {

    BAJO,
    MEDIO,
    ALTO;

    /**
     * Convierte el texto recibido por REST, gRPC o un evento al enum.
     *
     * @throws ReglaDeNegocioViolada si el texto no corresponde a ningún nivel,
     *         con un mensaje que enumera los válidos.
     */
    public static NivelDificultad desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioViolada("El nivel de dificultad es obligatorio.");
        }
        try {
            return valueOf(texto.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ReglaDeNegocioViolada(
                    "Nivel de dificultad '%s' desconocido. Valores válidos: BAJO, MEDIO, ALTO."
                            .formatted(texto));
        }
    }
}
