package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.CambioEstadoDto;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso: el ADMINISTRADOR consulta la trazabilidad de una pregunta.
 *
 * <p>Devuelve el historial completo de cambios de estado en orden cronológico:
 * quién la movió, cuándo y por qué. Está restringido al administrador porque
 * revela qué revisor tomó cada decisión, y la revisión por pares es anónima
 * frente al autor.
 */
@Service
public class ConsultarHistorialUseCase {

    private final PreguntaRepository repositorio;

    public ConsultarHistorialUseCase(PreguntaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<CambioEstadoDto> ejecutar(UUID preguntaId) {
        Pregunta pregunta = repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));

        return pregunta.getHistorialEstados().stream()
                .map(CambioEstadoDto::desde)
                .toList();
    }
}
