package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosContenidoPregunta;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Caso de uso: el AUTOR corrige su pregunta.
 *
 * <p>Solo funciona sobre preguntas en BORRADOR y solo para su autor
 * (invariante 7); de las dos comprobaciones se encarga el agregado.
 *
 * <p>Es el camino por el que se corrige una pregunta rechazada: el rechazo la
 * devolvió a BORRADOR con las observaciones del revisor (ADR 2).
 */
@Service
public class EditarPreguntaUseCase {

    private final PreguntaRepository repositorio;
    private final ValidadorEstructural validador;

    public EditarPreguntaUseCase(PreguntaRepository repositorio, ValidadorEstructural validador) {
        this.repositorio = repositorio;
        this.validador = validador;
    }

    /**
     * @throws PreguntaNoEncontrada si la pregunta no existe
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.AccesoNoAutorizado
     *         si quien edita no es el autor
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.TransicionInvalida
     *         si la pregunta no está en BORRADOR
     */
    @Transactional
    public PreguntaDto ejecutar(UUID preguntaId, UUID usuarioId, DatosContenidoPregunta datos) {
        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        pregunta.editar(usuarioId, EnsambladorContenidoPregunta.ensamblar(datos), validador);

        return PreguntaDto.desde(repositorio.guardar(pregunta));
    }
}
