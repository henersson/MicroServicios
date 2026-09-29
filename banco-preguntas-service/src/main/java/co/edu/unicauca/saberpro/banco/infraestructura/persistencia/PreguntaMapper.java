package co.edu.unicauca.saberpro.banco.infraestructura.persistencia;

import co.edu.unicauca.saberpro.banco.dominio.modelo.Bibliografia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.CambioEstado;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Competencia;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.EstadoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Justificacion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Opcion;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Subtema;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Tema;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce entre el Aggregate Root {@code Pregunta} y su entidad JPA.
 *
 * <p>Es el precio de mantener el dominio limpio de anotaciones de persistencia,
 * y se paga con gusto: gracias a este mapeador el agregado puede cambiar de
 * forma sin arrastrar una migración, y la base de datos puede reorganizarse sin
 * tocar las reglas de negocio.
 *
 * <h2>Por qué actualiza en vez de recrear</h2>
 * Al guardar, las colecciones hijas se actualizan <strong>en su sitio</strong>
 * en lugar de vaciarlas y volver a llenarlas. Si se hiciera lo segundo,
 * Hibernate podría emitir los INSERT antes que los DELETE dentro del mismo
 * flush y chocar con la restricción {@code UNIQUE (pregunta_id, orden)}. Además,
 * así cada guardado genera solo los UPDATE que de verdad hacen falta.
 */
@Component
public class PreguntaMapper {

    /** Reconstruye el agregado a partir de lo que hay en la base de datos. */
    public Pregunta aDominio(PreguntaEntity entidad) {
        List<Opcion> opciones = entidad.getOpciones().stream()
                .map(opcion -> new Opcion(opcion.getTexto(), opcion.isEsCorrecta()))
                .toList();

        List<String> referencias = entidad.getBibliografia().stream()
                .map(BibliografiaEntity::getReferencia)
                .toList();

        ContenidoPregunta contenido = new ContenidoPregunta(
                entidad.getContexto(),
                entidad.getPreguntaDirecta(),
                opciones,
                new Justificacion(entidad.getJustificacion()),
                new Bibliografia(referencias),
                new Competencia(entidad.getCompetenciaCodigo(), entidad.getCompetenciaNombre()),
                new Tema(entidad.getTema()),
                new Subtema(entidad.getSubtema()),
                NivelDificultad.valueOf(entidad.getNivelDificultad()));

        List<CambioEstado> historial = entidad.getHistorialEstados().stream()
                .map(cambio -> new CambioEstado(
                        cambio.getEstadoAnterior() == null
                                ? null
                                : EstadoPregunta.valueOf(cambio.getEstadoAnterior()),
                        EstadoPregunta.valueOf(cambio.getEstadoNuevo()),
                        cambio.getUsuarioId(),
                        cambio.getFecha(),
                        cambio.getMotivo()))
                .toList();

        return new Pregunta(
                entidad.getId(),
                entidad.getAutorId(),
                contenido,
                EstadoPregunta.valueOf(entidad.getEstado()),
                entidad.getObservacionesUltimaRevision(),
                historial);
    }

    /** Crea la entidad de una pregunta que todavía no está en la base de datos. */
    public PreguntaEntity aEntidadNueva(Pregunta pregunta) {
        PreguntaEntity entidad = new PreguntaEntity(pregunta.getId(), pregunta.getAutorId());
        volcar(pregunta, entidad);
        return entidad;
    }

    /** Vuelca el estado actual del agregado sobre una entidad ya existente. */
    public void actualizar(Pregunta pregunta, PreguntaEntity entidad) {
        volcar(pregunta, entidad);
    }

    private void volcar(Pregunta pregunta, PreguntaEntity entidad) {
        ContenidoPregunta contenido = pregunta.getContenido();

        entidad.setContexto(contenido.contexto());
        entidad.setPreguntaDirecta(contenido.preguntaDirecta());
        entidad.setJustificacion(contenido.justificacion().texto());
        entidad.setCompetenciaCodigo(contenido.competencia().codigo());
        entidad.setCompetenciaNombre(contenido.competencia().nombre());
        entidad.setTema(contenido.tema().nombre());
        entidad.setSubtema(contenido.subtema().nombre());
        entidad.setNivelDificultad(contenido.nivelDificultad().name());
        entidad.setEstado(pregunta.getEstado().name());
        entidad.setObservacionesUltimaRevision(
                pregunta.getObservacionesUltimaRevision().orElse(null));

        volcarOpciones(contenido.opciones(), entidad);
        volcarBibliografia(contenido.bibliografia().referencias(), entidad);
        volcarHistorial(pregunta.getHistorialEstados(), entidad);
    }

    private void volcarOpciones(List<Opcion> opciones, PreguntaEntity entidad) {
        List<OpcionEntity> destino = entidad.getOpciones();

        for (int i = 0; i < opciones.size(); i++) {
            Opcion opcion = opciones.get(i);
            if (i < destino.size()) {
                OpcionEntity existente = destino.get(i);
                existente.setOrden(i);
                existente.setTexto(opcion.texto());
                existente.setEsCorrecta(opcion.esCorrecta());
            } else {
                destino.add(new OpcionEntity(entidad, i, opcion.texto(), opcion.esCorrecta()));
            }
        }
        // Sobrantes: se quitan desde el final para no desplazar los índices.
        while (destino.size() > opciones.size()) {
            destino.remove(destino.size() - 1);
        }
    }

    private void volcarBibliografia(List<String> referencias, PreguntaEntity entidad) {
        List<BibliografiaEntity> destino = entidad.getBibliografia();

        for (int i = 0; i < referencias.size(); i++) {
            if (i < destino.size()) {
                BibliografiaEntity existente = destino.get(i);
                existente.setOrden(i);
                existente.setReferencia(referencias.get(i));
            } else {
                destino.add(new BibliografiaEntity(entidad, i, referencias.get(i)));
            }
        }
        while (destino.size() > referencias.size()) {
            destino.remove(destino.size() - 1);
        }
    }

    /**
     * El historial solo se amplía: las entradas ya guardadas no se tocan nunca,
     * porque son la traza de lo que pasó.
     */
    private void volcarHistorial(List<CambioEstado> historial, PreguntaEntity entidad) {
        List<CambioEstadoEntity> destino = entidad.getHistorialEstados();

        for (int i = destino.size(); i < historial.size(); i++) {
            CambioEstado cambio = historial.get(i);
            destino.add(new CambioEstadoEntity(
                    entidad,
                    i,
                    cambio.anterior() == null ? null : cambio.anterior().name(),
                    cambio.nuevo().name(),
                    cambio.usuarioId(),
                    cambio.fecha(),
                    cambio.motivo()));
        }
    }
}
