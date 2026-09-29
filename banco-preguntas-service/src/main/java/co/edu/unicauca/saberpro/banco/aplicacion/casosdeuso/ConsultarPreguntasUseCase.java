package co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso;

import co.edu.unicauca.saberpro.banco.aplicacion.dto.PaginaDto;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.Pregunta;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.FiltroPreguntas;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PaginaPreguntas;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.PreguntaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Caso de uso de consulta: buscar una pregunta por su identificador o listar
 * preguntas por filtros.
 *
 * <p>Lo usan tanto la API REST como el servidor gRPC. Que los dos pasen por el
 * mismo caso de uso es justamente lo que evita que la lógica se duplique y que
 * REST y gRPC terminen respondiendo cosas distintas.
 *
 * @see ConsultarHistorialUseCase para la trazabilidad, que es de administradores
 */
@Service
public class ConsultarPreguntasUseCase {

    /** Tope de seguridad: sin él, un {@code size} enorme podría tumbar el servicio. */
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    public static final int TAMANO_PAGINA_POR_DEFECTO = 20;

    private final PreguntaRepository repositorio;

    public ConsultarPreguntasUseCase(PreguntaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @throws PreguntaNoEncontrada si no existe
     */
    @Transactional(readOnly = true)
    public PreguntaDto porId(UUID preguntaId) {
        return PreguntaDto.desde(repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId)));
    }

    /**
     * Lista preguntas por filtros. Los valores de paginación se normalizan aquí
     * (nunca negativos, nunca por encima del tope) para que ninguna capa de
     * entrada tenga que acordarse de hacerlo.
     */
    @Transactional(readOnly = true)
    public PaginaDto<PreguntaDto> porFiltros(FiltroPreguntas filtro, int pagina, int tamano) {
        int paginaSegura = Math.max(pagina, 0);
        int tamanoSeguro = tamano <= 0
                ? TAMANO_PAGINA_POR_DEFECTO
                : Math.min(tamano, TAMANO_PAGINA_MAXIMO);

        return PaginaDto.desde(repositorio.buscarPorFiltros(filtro, paginaSegura, tamanoSeguro));
    }

    /**
     * Variante para el servidor gRPC: devuelve la lista sin envoltorio de
     * página, porque {@code ListarPreguntasPublicadas} entrega un simple
     * {@code repeated} con un límite.
     */
    @Transactional(readOnly = true)
    public List<PreguntaDto> publicadas(FiltroPreguntas filtro, int limite) {
        int tamano = limite <= 0 ? 50 : Math.min(limite, TAMANO_PAGINA_MAXIMO);
        PaginaPreguntas pagina = repositorio.buscarPorFiltros(filtro, 0, tamano);
        return pagina.contenido().stream().map(PreguntaDto::desde).toList();
    }

    /** Devuelve el agregado, para los casos de uso que necesitan más que el DTO. */
    @Transactional(readOnly = true)
    public Pregunta agregadoPorId(UUID preguntaId) {
        return repositorio.buscarPorId(preguntaId)
                .orElseThrow(() -> new PreguntaNoEncontrada(preguntaId));
    }
}
