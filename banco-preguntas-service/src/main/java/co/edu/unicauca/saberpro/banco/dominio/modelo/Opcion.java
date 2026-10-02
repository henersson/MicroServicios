package co.edu.unicauca.saberpro.banco.dominio.modelo;

import co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada;

/**
 * Value Object del BC Banco de Preguntas: una de las cuatro opciones de
 * respuesta de una pregunta de selección múltiple.
 *
 * <p>Es inmutable y se compara por valor, como todo Value Object: dos opciones
 * con el mismo texto y la misma marca de correcta son la misma opción. En eso se
 * apoya la <strong>invariante 3</strong> para detectar opciones repetidas.
 *
 * @param texto      lo que lee el estudiante
 * @param esCorrecta true solo en la única opción correcta (invariante 1)
 */
public record Opcion(String texto, boolean esCorrecta) {

    public Opcion {
        if (texto == null || texto.isBlank()) {
            throw new ReglaDeNegocioViolada("El texto de una opción no puede estar vacío.");
        }
        // Se normalizan los espacios de los extremos al construir: así "  A  " y
        // "A" son la misma opción para la invariante 3, sin depender de cómo la
        // haya escrito el autor.
        texto = texto.trim();
    }

    /**
     * Longitud del texto sin contar espacios, que es lo que mide la
     * invariante 3. Se cuenta así para que "a b c d e" no cuele como una opción
     * de 9 caracteres cuando en realidad aporta 5.
     */
    public int longitudSinEspacios() {
        return texto.replaceAll("\s", "").length();
    }
}
