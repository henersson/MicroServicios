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
 * Caso de uso: el ADMINISTRADOR publica una pregunta ya aprobada.
 *
 * <p>Publica el evento {@code PreguntaPublicada} con la pregunta completa, que
 * queda esperando en la cola del futuro simulacros-service.
 */
@Service
public class PublicarPreguntaUseCase {

    private final PreguntaRepository repositorio;
    private final ValidadorEstructural validador;
    private final PublicadorEventos publicador;
    private final Clock reloj;

    public PublicarPreguntaUseCase(PreguntaRepository repositorio, ValidadorEstructural validador,
                                   PublicadorEventos publicador, Clock reloj) {
        this.repositorio = repositorio;
        this.validador = validador;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /**
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.TransicionInvalida
     *         si la pregunta no está en APROBADA
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada
     *         si el contenido dejó de cumplir las reglas (invariante 5)
     */
    @Transactional
    public PreguntaDto ejecutar(UUID preguntaId, UUID administradorId) {
        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        pregunta.publicar(administradorId, validador, reloj.instant());

        Pregunta guardada = repositorio.guardar(pregunta);
        publicador.publicar(guardada.extraerEventosPendientes());

        return PreguntaDto.desde(guardada);
    }
}
