package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosContenidoPregunta;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosOpcion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Bibliografia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Competencia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Justificacion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Subtema;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Tema;

import java.util.List;

/**
 * Traduce el DTO de entrada a los Value Objects del dominio.
 *
 * <p>Es la frontera donde los datos sueltos que llegaron por HTTP se convierten
 * en conceptos del dominio. A partir de aquí ya no circulan cadenas anónimas:
 * circulan una {@code Competencia}, un {@code Tema}, un {@code NivelDificultad}.
 *
 * <p>La usan el caso de uso de creación y el de edición, que reciben exactamente
 * la misma estructura de entrada.
 */
final class EnsambladorContenidoPregunta {

    private EnsambladorContenidoPregunta() {
    }

    /**
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada
     *         si algún valor obligatorio falta o un enum trae un valor desconocido
     */
    static ContenidoPregunta ensamblar(DatosContenidoPregunta datos) {
        List<Opcion> opciones = datos.opciones() == null
                ? List.of()
                : datos.opciones().stream()
                        .filter(opcion -> opcion != null)
                        .map(EnsambladorContenidoPregunta::aOpcion)
                        .toList();

        return new ContenidoPregunta(
                datos.contexto(),
                datos.preguntaDirecta(),
                opciones,
                new Justificacion(datos.justificacion()),
                new Bibliografia(datos.bibliografia()),
                new Competencia(datos.competenciaCodigo(), datos.competenciaNombre()),
                new Tema(datos.tema()),
                new Subtema(datos.subtema()),
                NivelDificultad.desdeTexto(datos.nivelDificultad()));
    }

    private static Opcion aOpcion(DatosOpcion datos) {
        return new Opcion(datos.texto(), datos.esCorrecta());
    }
}
