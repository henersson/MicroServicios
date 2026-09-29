package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.aplicacion.puertos.PublicadorEventos;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Caso de uso: el AUTOR envía su pregunta al ciclo de revisión por pares.
 *
 * <p>Es el primer punto del flujo en el que este Bounded Context habla con otro:
 * la pregunta pasa a PENDIENTE_REVISION y se publica
 * {@code PreguntaEnviadaARevision}, que consumirá el revision-service.
 *
 * <p>Fíjese en el orden: primero se guarda y después se entregan los eventos al
 * publicador, que los enviará al broker solo cuando la transacción confirme
 * (ADR 4). Nunca se anuncia algo que podría deshacerse.
 */
@Service
public class EnviarARevisionUseCase {

    private final PreguntaRepository repositorio;
    private final ValidadorEstructural validador;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public EnviarARevisionUseCase(PreguntaRepository repositorio, ValidadorEstructural validador,
                                  PublicadorEventos publicador, Clock reloj) {
        this.repositorio = repositorio;
        this.validador = validador;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    @Transactional
    public PreguntaDto ejecutar(UUID preguntaId, UUID usuarioId) {
        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        pregunta.enviarARevision(usuarioId, validador, reloj.instant());

        Pregunta guardada = repositorio.guardar(pregunta);
        publicador.publicar(guardada.extraerEventosPendientes());

        return PreguntaDto.desde(guardada);
    }
}
