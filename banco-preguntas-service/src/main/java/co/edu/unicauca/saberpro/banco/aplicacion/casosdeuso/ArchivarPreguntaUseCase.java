package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.aplicacion.puertos.PublicadorEventos;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Caso de uso: el ADMINISTRADOR archiva una pregunta.
 *
 * <p>Es la única "salida" del ciclo de vida. No existe un caso de uso de borrado
 * porque en el banco nada se elimina físicamente (invariante 8, ADR 3): una
 * pregunta que ya se usó en un simulacro tiene que seguir siendo consultable.
 */
@Service
public class ArchivarPreguntaUseCase {

    private final PreguntaRepository repositorio;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public ArchivarPreguntaUseCase(PreguntaRepository repositorio, PublicadorEventos publicador,
                                   Clock reloj) {
        this.repositorio = repositorio;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /**
     * @param motivo explicación opcional; queda en el historial para la auditoría
     */
    @Transactional
    public PreguntaDto ejecutar(UUID preguntaId, UUID administradorId, String motivo) {
        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        pregunta.archivar(administradorId, motivo, reloj.instant());

        Pregunta guardada = repositorio.guardar(pregunta);
        publicador.publicar(guardada.extraerEventosPendientes());

        return PreguntaDto.desde(guardada);
    }
}
