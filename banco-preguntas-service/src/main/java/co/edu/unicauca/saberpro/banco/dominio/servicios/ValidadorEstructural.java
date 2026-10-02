package co.edu.unicauca.saberpro.banco.dominio.servicios;

import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Domain Service del BC Banco de Preguntas: comprueba que el contenido de una
 * pregunta cumpla las <strong>invariantes 1 a 4</strong>.
 *
 * <p>Es un Domain Service y no un método del agregado porque la regla no
 * pertenece a una pregunta concreta sino a lo que el banco considera una
 * pregunta bien construida, y porque necesita configuración externa (la longitud
 * mínima de una opción). El agregado lo recibe y lo invoca: así las invariantes
 * siguen siendo responsabilidad del agregado, pero su formulación vive aquí.
 *
 * <p>Se usa al crear, al editar y al enviar a revisión. Devuelve
 * <strong>todos</strong> los errores de una vez, en español, para que el autor
 * los corrija en una sola pasada en vez de descubrirlos de uno en uno.
 *
 * <h2>La parte gramatical de la invariante 3</h2>
 * La invariante 3 del Taller 1 pide además una "estructura gramatical coherente
 * con la pregunta directa". Eso no se puede comprobar de forma fiable sin
 * análisis de lenguaje natural, así que aquí se aplican las dos reglas
 * mecánicas (longitud mínima y no repetición) y la coherencia gramatical la
 * juzga una persona: es el criterio obligatorio {@code COHERENCIA_GRAMATICAL}
 * del formato de evaluación en el revision-service. Como la invariante 10 no
 * deja decidir con el formato incompleto, ninguna pregunta se aprueba sin que
 * el revisor haya puntuado ese criterio.
 */
public class ValidadorEstructural {

    /** Invariante 1: toda pregunta Saber Pro tiene 4 opciones. */
    public static final int OPCIONES_REQUERIDAS = 4;

    /** Invariante 1: exactamente una de las 4 es correcta. */
    public static final int CORRECTAS_REQUERIDAS = 1;

    public static final int LONGITUD_MINIMA_OPCION_POR_DEFECTO = 5;

    /**
     * Invariante 2: fórmulas prohibidas en una opción.
     *
     * <p>Se prohíben porque convierten la pregunta en un acertijo de estrategia
     * en vez de una medición de la competencia: quien detecta dos opciones
     * correctas deduce "todas las anteriores" sin saber el tema.
     *
     * <p>Los patrones se evalúan sobre el texto ya normalizado (sin tildes, en
     * minúsculas y con los espacios colapsados), así que "TODAS LAS ANTERIORES",
     * "todas  las   anteriores" y "Todás las Anteriores" caen igual.
     */
    private static final List<Pattern> FORMULAS_PROHIBIDAS = List.of(
            // "todas las anteriores", "todas las opciones anteriores", "todas son correctas"...
            Pattern.compile("\\btodas\\s+(las\\s+)?(opciones\\s+|respuestas\\s+)?anteriores\\b"),
            Pattern.compile("\\btodas\\s+(las\\s+)?(opciones\\s+|respuestas\\s+)?son\\s+correctas\\b"),
            Pattern.compile("\\btodas\\s+son\\s+correctas\\b"),
            Pattern.compile("\\btodas\\s+las\\s+anteriores\\s+son\\s+correctas\\b"),
            // "ninguna de las anteriores", "ninguna es correcta"...
            Pattern.compile("\\bninguna\\s+(de\\s+)?(las\\s+)?(opciones\\s+|respuestas\\s+)?anteriores\\b"),
            Pattern.compile("\\bninguna\\s+(de\\s+)?(las\\s+)?(opciones\\s+|respuestas\\s+)?es\\s+correcta\\b"),
            Pattern.compile("\\bninguna\\s+es\\s+correcta\\b"),
            Pattern.compile("\\bninguna\\s+de\\s+las\\s+anteriores\\b"),
            // Variantes en masculino, que también aparecen en la práctica.
            Pattern.compile("\\btodos\\s+los\\s+anteriores\\b"),
            Pattern.compile("\\bninguno\\s+de\\s+los\\s+anteriores\\b"));

    private static final Pattern DIACRITICOS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private final int longitudMinimaOpcion;

    public ValidadorEstructural() {
        this(LONGITUD_MINIMA_OPCION_POR_DEFECTO);
    }

    public ValidadorEstructural(int longitudMinimaOpcion) {
        if (longitudMinimaOpcion < 1) {
            throw new IllegalArgumentException(
                    "La longitud mínima de una opción debe ser al menos 1.");
        }
        this.longitudMinimaOpcion = longitudMinimaOpcion;
    }

    /**
     * Aplica las invariantes 1 a 4 y devuelve el resultado con todos los errores
     * encontrados. No lanza excepciones: quien decide qué hacer con los errores
     * es el agregado.
     */
    public ResultadoValidacion validar(ContenidoPregunta contenido) {
        List<String> errores = new ArrayList<>();

        validarInvariante4(contenido, errores);
        validarInvariante1(contenido.opciones(), errores);
        validarInvariante2(contenido.opciones(), errores);
        validarInvariante3(contenido.opciones(), errores);

        return new ResultadoValidacion(errores);
    }

    /** Invariante 4: un único contexto y una única pregunta directa, obligatorios. */
    private void validarInvariante4(ContenidoPregunta contenido, List<String> errores) {
        if (contenido.contexto().isBlank()) {
            errores.add("El contexto es obligatorio: describe la situación que el "
                    + "estudiante debe analizar. (Invariante 4)");
        }
        if (contenido.preguntaDirecta().isBlank()) {
            errores.add("La pregunta directa es obligatoria: es el interrogante "
                    + "concreto que se le plantea al estudiante. (Invariante 4)");
        }
    }

    /** Invariante 1: exactamente 3 distractores y 1 opción correcta. */
    private void validarInvariante1(List<Opcion> opciones, List<String> errores) {
        if (opciones.size() != OPCIONES_REQUERIDAS) {
            errores.add("La pregunta debe tener exactamente %d opciones y tiene %d. (Invariante 1)"
                    .formatted(OPCIONES_REQUERIDAS, opciones.size()));
        }

        long correctas = opciones.stream().filter(Opcion::esCorrecta).count();
        if (correctas != CORRECTAS_REQUERIDAS) {
            errores.add("La pregunta debe tener exactamente 1 opción correcta y tiene %d. "
                    .formatted(correctas)
                    + "Las otras 3 son distractores. (Invariante 1)");
        }
    }

    /** Invariante 2: nada de "todas/ninguna de las anteriores" ni variantes. */
    private void validarInvariante2(List<Opcion> opciones, List<String> errores) {
        for (int i = 0; i < opciones.size(); i++) {
            String normalizado = normalizar(opciones.get(i).texto());
            boolean prohibida = FORMULAS_PROHIBIDAS.stream()
                    .anyMatch(patron -> patron.matcher(normalizado).find());
            if (prohibida) {
                errores.add(("La opción %d usa una fórmula prohibida del tipo \"todas/ninguna de "
                        + "las anteriores\". Cada opción debe ser una respuesta concreta. "
                        + "(Invariante 2)").formatted(i + 1));
            }
        }
    }

    /** Invariante 3: longitud mínima y sin opciones repetidas. */
    private void validarInvariante3(List<Opcion> opciones, List<String> errores) {
        for (int i = 0; i < opciones.size(); i++) {
            Opcion opcion = opciones.get(i);
            if (opcion.longitudSinEspacios() < longitudMinimaOpcion) {
                errores.add(("La opción %d es demasiado corta: tiene %d caracteres sin espacios y "
                        + "el mínimo es %d. Los distractores muy cortos delatan la respuesta. "
                        + "(Invariante 3)")
                        .formatted(i + 1, opcion.longitudSinEspacios(), longitudMinimaOpcion));
            }
        }

        // La comparación de repetidas se hace sobre el texto normalizado: dos
        // opciones que solo difieren en tildes, mayúsculas o espacios son la
        // misma opción a efectos de quien responde.
        Set<String> vistas = new HashSet<>();
        Set<String> yaReportadas = new HashSet<>();
        for (int i = 0; i < opciones.size(); i++) {
            String normalizado = normalizar(opciones.get(i).texto());
            if (!vistas.add(normalizado) && yaReportadas.add(normalizado)) {
                errores.add(("La opción %d está repetida: su texto coincide con el de otra opción. "
                        + "Las 4 opciones deben ser distintas entre sí. (Invariante 3)")
                        .formatted(i + 1));
            }
        }
    }

    /**
     * Deja el texto en minúsculas, sin tildes y con los espacios colapsados.
     *
     * <p>Es lo que hace que las invariantes 2 y 3 no se puedan burlar escribiendo
     * "Todás  Las   Anteriores".
     */
    static String normalizar(String texto) {
        String sinTildes = DIACRITICOS
                .matcher(Normalizer.normalize(texto, Normalizer.Form.NFD))
                .replaceAll("");
        return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT).trim()).replaceAll(" ");
    }

    /**
     * Resultado de una validación: o está limpio, o trae la lista de todo lo que
     * hay que corregir.
     */
    public record ResultadoValidacion(List<String> errores) {

        public ResultadoValidacion {
            errores = List.copyOf(errores);
        }

        public boolean esValido() {
            return errores.isEmpty();
        }
    }
}
