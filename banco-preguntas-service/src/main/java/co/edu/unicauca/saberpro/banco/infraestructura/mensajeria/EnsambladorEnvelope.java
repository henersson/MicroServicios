package co.edu.unicauca.saberpro.banco.infraestructura.mensajeria;

import co.edu.unicauca.saberpro.banco.dominio.eventos.EventoDominio;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaArchivada;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaEnviadaARevision;
import co.edu.unicauca.saberpro.banco.dominio.eventos.PreguntaPublicada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte un evento de dominio en el envelope JSON que define el contrato
 * compartido de {@code contracts/events}.
 *
 * <pre>
 * {
 *   "eventId": "uuid",
 *   "eventType": "PreguntaEnviadaARevision",
 *   "eventVersion": 1,
 *   "occurredAt": "2026-09-06T14:20:00Z",
 *   "source": "banco-preguntas-service",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>Se construye un {@code Map} y no se serializa el record directamente por
 * dos razones: el JSON que sale es exactamente el del contrato (no el que decida
 * Jackson a partir de los nombres de los campos del dominio), y un refactor de
 * un record no puede romper en silencio a los consumidores de otro lenguaje.
 *
 * <p>Las fechas se formatean a mano con {@code ISO_INSTANT} para no depender de
 * cómo esté configurado Jackson: el contrato exige UTC ISO-8601
 * ({@code 2026-09-06T14:20:00Z}) y así queda garantizado.
 */
@Component
public class EnsambladorEnvelope {

    /** Nombre de este microservicio en el campo {@code source} del envelope. */
    public static final String ORIGEN = "banco-preguntas-service";

    /** Versión del contrato de eventos. Un cambio incompatible subiría a 2. */
    public static final int VERSION_EVENTO = 1;

    /**
     * @return el envelope listo para serializar a JSON
     */
    public Map<String, Object> ensamblar(EventoDominio evento) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", evento.eventId().toString());
        envelope.put("eventType", evento.tipo());
        envelope.put("eventVersion", VERSION_EVENTO);
        envelope.put("occurredAt", DateTimeFormatter.ISO_INSTANT.format(evento.ocurridoEn()));
        envelope.put("source", ORIGEN);
        envelope.put("data", datos(evento));
        return envelope;
    }

    /**
     * Carga útil propia de cada evento. El switch sobre la interfaz sellada hace
     * que agregar un evento nuevo sea un error de compilación aquí, en vez de un
     * evento publicado con el {@code data} vacío.
     */
    private Map<String, Object> datos(EventoDominio evento) {
        return switch (evento) {
            case PreguntaEnviadaARevision e -> Map.of(
                    "preguntaId", e.preguntaId().toString(),
                    "autorId", e.autorId().toString(),
                    "competenciaCodigo", e.competenciaCodigo());

            case PreguntaArchivada e -> Map.of(
                    "preguntaId", e.preguntaId().toString());

            case PreguntaPublicada e -> Map.of(
                    "pregunta", preguntaCompleta(e));
        };
    }

    /**
     * La pregunta completa que viaja en {@code PreguntaPublicada}: los mismos
     * campos que {@code PreguntaMensaje} del {@code .proto}, pero en camelCase e
     * incluyendo {@code esCorrecta}, tal como exige el contrato.
     */
    private Map<String, Object> preguntaCompleta(PreguntaPublicada evento) {
        ContenidoPregunta contenido = evento.contenido();

        Map<String, Object> pregunta = new LinkedHashMap<>();
        pregunta.put("id", evento.preguntaId().toString());
        pregunta.put("autorId", evento.autorId().toString());
        pregunta.put("contexto", contenido.contexto());
        pregunta.put("preguntaDirecta", contenido.preguntaDirecta());
        pregunta.put("opciones", opciones(contenido.opciones()));
        pregunta.put("justificacion", contenido.justificacion().texto());
        pregunta.put("bibliografia", contenido.bibliografia().referencias());
        pregunta.put("competenciaCodigo", contenido.competencia().codigo());
        pregunta.put("competenciaNombre", contenido.competencia().nombre());
        pregunta.put("tema", contenido.tema().nombre());
        pregunta.put("subtema", contenido.subtema().nombre());
        pregunta.put("nivelDificultad", contenido.nivelDificultad().name());
        // Siempre PUBLICADA: es el estado en el que este evento se emite.
        pregunta.put("estado", "PUBLICADA");
        return pregunta;
    }

    private List<Map<String, Object>> opciones(List<Opcion> opciones) {
        return opciones.stream()
                .map(opcion -> {
                    Map<String, Object> mapa = new LinkedHashMap<String, Object>();
                    mapa.put("texto", opcion.texto());
                    mapa.put("esCorrecta", opcion.esCorrecta());
                    return mapa;
                })
                .toList();
    }
}
