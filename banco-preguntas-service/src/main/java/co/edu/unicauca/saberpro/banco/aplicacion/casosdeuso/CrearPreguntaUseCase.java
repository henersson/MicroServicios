package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.DatosContenidoPregunta;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.dominio.modelo.ContenidoPregunta;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import co.edu.unicauca.saberpro.banco.dominio.servicios.ValidadorEstructural;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Caso de uso: un AUTOR crea una pregunta nueva, que nace en BORRADOR.
 *
 * <p>El caso de uso orquesta; las reglas las pone el agregado. Aquí solo se
 * traduce la entrada a Value Objects, se pide al agregado que se cree a sí mismo
 * (momento en el que se aplican las invariantes 1 a 4) y se guarda.
 *
 * <p>Crear no publica ningún evento de integración: mientras la pregunta está en
 * BORRADOR solo le importa a su autor.
 */
@Service
public class CrearPreguntaUseCase {

    private final PreguntaRepository repositorio;
    private final ValidadorEstructural validador;
    private final Clock reloj;

    public CrearPreguntaUseCase(PreguntaRepository repositorio, ValidadorEstructural validador,
                                Clock reloj) {
        this.repositorio = repositorio;
        this.validador = validador;
        this.reloj = reloj;
    }

    /**
     * @param autorId quien la crea, tomado de la cabecera {@code X-Usuario-Id}
     * @throws co.edu.unicauca.saberpro.banco.dominio.excepciones.ReglaDeNegocioViolada
     *         si el contenido incumple las invariantes 1 a 4
     */
    @Transactional
    public PreguntaDto ejecutar(UUID autorId, DatosContenidoPregunta datos) {
        ContenidoPregunta contenido = EnsambladorContenidoPregunta.ensamblar(datos);

        Pregunta pregunta = Pregunta.crear(
                UUID.randomUUID(), autorId, contenido, validador, reloj.instant());

        return PreguntaDto.desde(repositorio.guardar(pregunta));
    }
}
