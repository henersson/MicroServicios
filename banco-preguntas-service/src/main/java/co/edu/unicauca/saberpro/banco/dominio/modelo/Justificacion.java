package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: la explicación de por qué la opción
 * correcta lo es y, idealmente, por qué cada distractor no.
 *
 * <p>Es obligatoria: una pregunta de Saber Pro sin justificación no se puede
 * revisar por pares, porque el revisor no tendría contra qué contrastar el
 * criterio CALIDAD_JUSTIFICACION de su formato de evaluación.
 */
public record Justificacion(String texto) {

    public Justificacion {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioViolada("La justificación es obligatoria.");
        }
        texto = texto.trim();
    }

    @Override
    public String toString() {
        return texto;
    }
}
