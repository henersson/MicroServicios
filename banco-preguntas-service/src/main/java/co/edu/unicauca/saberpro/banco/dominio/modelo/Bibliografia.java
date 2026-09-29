package co.edu.unicauca.saberpro.banco.dominio.modelo;

import java.util.List;

/**
 * Value Object del BC Banco de Preguntas: las referencias que respaldan la
 * pregunta.
 *
 * <p>Puede estar vacía (el Taller 1 no la declara obligatoria), pero nunca es
 * nula: quien la recibe no tiene que comprobar nada antes de recorrerla. La
 * lista se copia y se limpia al construir, así que el Value Object es realmente
 * inmutable aunque quien lo creó siga modificando su propia lista.
 */
public record Bibliografia(List<String> referencias) {

    public Bibliografia {
        referencias = referencias == null
                ? List.of()
                : referencias.stream()
                        .filter(referencia -> referencia != null && !referencia.isBlank())
                        .map(String::trim)
                        .toList();
    }

    public static Bibliografia vacia() {
        return new Bibliografia(List.of());
    }

    public boolean estaVacia() {
        return referencias.isEmpty();
    }
}
