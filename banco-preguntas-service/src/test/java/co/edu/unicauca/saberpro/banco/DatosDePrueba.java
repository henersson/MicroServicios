package co.edu.unicauca.saberpro.banco;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Bibliografia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Competencia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Justificacion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Subtema;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Tema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fábrica de datos válidos para los tests del dominio.
 *
 * <p>Los tests parten siempre de una pregunta que cumple todas las invariantes y
 * rompen exactamente una cosa. Así, cuando un test falla, el motivo es lo que el
 * test rompió y no un descuido al construir el caso.
 */
public final class DatosDePrueba {

    public static final UUID AUTOR = UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
    public static final UUID OTRO_USUARIO = UUID.fromString("d4e5f6a7-b8c9-4d0e-9f1a-2b3c4d5e6f70");
    public static final UUID REVISOR = UUID.fromString("b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e");
    public static final UUID ADMINISTRADOR =
            UUID.fromString("0a0b0c0d-1111-4222-8333-444455556666");

    public static final Instant AHORA = Instant.parse("2026-09-22T10:00:00Z");

    private DatosDePrueba() {
    }

    /** Contenido que cumple las invariantes 1 a 4. */
    public static ContenidoPregunta contenidoValido() {
        return new ContenidoPregunta(
                "Una universidad migra su plataforma monolítica a microservicios y un "
                        + "desarrollador propone que tres servicios compartan la misma base de "
                        + "datos para ahorrar costos.",
                "¿Por qué el arquitecto rechaza compartir la base de datos entre los servicios?",
                opcionesValidas(),
                new Justificacion("La independencia de despliegue depende de que cada servicio "
                        + "sea dueño exclusivo de sus datos."),
                new Bibliografia(List.of("Newman, S. (2021). Building Microservices. O'Reilly.")),
                new Competencia("ING-SOFT", "Diseño de Software y Arquitectura"),
                new Tema("Arquitectura de Software"),
                new Subtema("Microservicios"),
                NivelDificultad.MEDIO);
    }

    /** Las 5 opciones válidas: 4 distractores y 1 correcta. */
    public static List<Opcion> opcionesValidas() {
        return List.of(
                new Opcion("El acoplamiento de datos impide el despliegue independiente.", true),
                new Opcion("PostgreSQL no admite conexiones concurrentes de varios servicios.",
                        false),
                new Opcion("El teorema CAP prohíbe compartir un motor relacional.", false),
                new Opcion("Una base compartida siempre es más costosa de operar.", false),
                new Opcion("Los microservicios solo pueden comunicarse de forma asíncrona.",
                        false));
    }

    /** Copia del contenido válido reemplazando solo las opciones. */
    public static ContenidoPregunta contenidoCon(List<Opcion> opciones) {
        ContenidoPregunta base = contenidoValido();
        return new ContenidoPregunta(base.contexto(), base.preguntaDirecta(), opciones,
                base.justificacion(), base.bibliografia(), base.competencia(), base.tema(),
                base.subtema(), base.nivelDificultad());
    }

    /** Copia del contenido válido reemplazando el contexto y la pregunta directa. */
    public static ContenidoPregunta contenidoCon(String contexto, String preguntaDirecta) {
        ContenidoPregunta base = contenidoValido();
        return new ContenidoPregunta(contexto, preguntaDirecta, base.opciones(),
                base.justificacion(), base.bibliografia(), base.competencia(), base.tema(),
                base.subtema(), base.nivelDificultad());
    }

    /** Las 5 opciones válidas, cambiando el texto de la que ocupa la posición dada. */
    public static List<Opcion> opcionesConTextoEnPosicion(int posicion, String texto) {
        List<Opcion> opciones = new ArrayList<>(opcionesValidas());
        Opcion original = opciones.get(posicion);
        opciones.set(posicion, new Opcion(texto, original.esCorrecta()));
        return List.copyOf(opciones);
    }

    /** Construye una lista de opciones a partir de pares texto/esCorrecta. */
    public static List<Opcion> opciones(Object... textoYCorrecta) {
        List<Opcion> opciones = new ArrayList<>();
        for (int i = 0; i < textoYCorrecta.length; i += 2) {
            opciones.add(new Opcion((String) textoYCorrecta[i], (Boolean) textoYCorrecta[i + 1]));
        }
        return List.copyOf(opciones);
    }

    /** Une varias listas de opciones, útil para armar casos a medida. */
    @SafeVarargs
    public static List<Opcion> unir(List<Opcion>... listas) {
        return Arrays.stream(listas).flatMap(List::stream).toList();
    }
}
