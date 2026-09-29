package co.edu.unicauca.saberpro.banco.infraestructura.grpc;

import co.edu.unicauca.saberpro.banco.aplicacion.casosdeuso.ConsultarPreguntasUseCase;
import co.edu.unicauca.saberpro.banco.aplicacion.dto.PreguntaDto;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.ExcepcionDominio;
import co.edu.unicauca.saberpro.banco.dominio.excepciones.PreguntaNoEncontrada;
import co.edu.unicauca.saberpro.banco.dominio.modelo.NivelDificultad;
import co.edu.unicauca.saberpro.banco.dominio.repositorios.FiltroPreguntas;
import co.edu.unicauca.saberpro.banco.grpc.v1.BancoPreguntasGrpc;
import co.edu.unicauca.saberpro.banco.grpc.v1.ListaPreguntasMensaje;
import co.edu.unicauca.saberpro.banco.grpc.v1.ListarPreguntasPublicadasRequest;
import co.edu.unicauca.saberpro.banco.grpc.v1.ObtenerPreguntaRequest;
import co.edu.unicauca.saberpro.banco.grpc.v1.OpcionMensaje;
import co.edu.unicauca.saberpro.banco.grpc.v1.PreguntaMensaje;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Servidor gRPC del BC Banco de Preguntas. Implementa el servicio
 * {@code BancoPreguntas} de
 * {@code contracts/proto/banco_preguntas/v1/banco_preguntas.proto}.
 *
 * <p>Es la <strong>comunicación síncrona</strong> entre microservicios del
 * sistema: el revision-service llama a {@code ObtenerPregunta} al consumir
 * {@code PreguntaEnviadaARevision}, para traerse el contenido que va a evaluar.
 *
 * <p>Escucha en el puerto 9091 con Server Reflection activado, así que se puede
 * explorar con {@code grpcurl} o Postman sin cargar el {@code .proto} a mano.
 *
 * <p>Esta clase es un <em>adaptador de entrada</em>: no tiene lógica de negocio.
 * Traduce el mensaje protobuf a una llamada al mismo caso de uso que usa la API
 * REST, y la respuesta de vuelta. Que los dos protocolos compartan caso de uso
 * es lo que garantiza que no puedan acabar respondiendo cosas distintas.
 */
@Service
public class ServidorBancoPreguntas extends BancoPreguntasGrpc.BancoPreguntasImplBase {

    private static final Logger log = LoggerFactory.getLogger(ServidorBancoPreguntas.class);

    /** Valor por defecto cuando el cliente manda {@code limite = 0}. */
    private static final int LIMITE_POR_DEFECTO = 50;

    private final ConsultarPreguntasUseCase consultarPreguntas;

    public ServidorBancoPreguntas(ConsultarPreguntasUseCase consultarPreguntas) {
        this.consultarPreguntas = consultarPreguntas;
    }

    /**
     * Devuelve una pregunta completa por su identificador.
     *
     * <p>Códigos de error: {@code INVALID_ARGUMENT} si el id falta o no es un
     * UUID; {@code NOT_FOUND} si no existe.
     */
    @Override
    public void obtenerPregunta(ObtenerPreguntaRequest peticion,
                                StreamObserver<PreguntaMensaje> respuesta) {
        try {
            UUID id = parsearUuid(peticion.getPreguntaId());
            PreguntaDto pregunta = consultarPreguntas.porId(id);

            log.debug("gRPC ObtenerPregunta: id={} estado={}", id, pregunta.estado());

            respuesta.onNext(aMensaje(pregunta));
            respuesta.onCompleted();

        } catch (PreguntaNoEncontrada e) {
            log.info("gRPC ObtenerPregunta: no existe la pregunta {}", peticion.getPreguntaId());
            respuesta.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());

        } catch (ArgumentoInvalido e) {
            respuesta.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());

        } catch (ExcepcionDominio e) {
            respuesta.onError(Status.FAILED_PRECONDITION
                    .withDescription(e.getMessage())
                    .asRuntimeException());

        } catch (RuntimeException e) {
            log.error("gRPC ObtenerPregunta: error inesperado con la pregunta {}",
                    peticion.getPreguntaId(), e);
            respuesta.onError(Status.INTERNAL
                    .withDescription("Error interno al obtener la pregunta.")
                    .asRuntimeException());
        }
    }

    /**
     * Devuelve preguntas PUBLICADAS, opcionalmente filtradas. Pensado para el
     * futuro simulacros-service.
     *
     * <p>Un filtro vacío significa "no filtrar por ese criterio" y
     * {@code limite = 0} aplica el valor por defecto de 50. Una lista vacía
     * <strong>no</strong> es un error.
     */
    @Override
    public void listarPreguntasPublicadas(ListarPreguntasPublicadasRequest peticion,
                                          StreamObserver<ListaPreguntasMensaje> respuesta) {
        try {
            if (peticion.getLimite() < 0) {
                throw new ArgumentoInvalido("El límite no puede ser negativo.");
            }

            NivelDificultad nivel = peticion.getNivelDificultad().isBlank()
                    ? null
                    : parsearNivel(peticion.getNivelDificultad());

            FiltroPreguntas filtro = FiltroPreguntas.soloPublicadas(
                    vacioComoNulo(peticion.getCompetenciaCodigo()),
                    vacioComoNulo(peticion.getTema()),
                    nivel);

            int limite = peticion.getLimite() == 0 ? LIMITE_POR_DEFECTO : peticion.getLimite();
            List<PreguntaDto> preguntas = consultarPreguntas.publicadas(filtro, limite);

            log.debug("gRPC ListarPreguntasPublicadas: competencia={} tema={} nivel={} "
                            + "limite={} -> {} resultados",
                    peticion.getCompetenciaCodigo(), peticion.getTema(),
                    peticion.getNivelDificultad(), limite, preguntas.size());

            respuesta.onNext(ListaPreguntasMensaje.newBuilder()
                    .addAllPreguntas(preguntas.stream().map(this::aMensaje).toList())
                    .build());
            respuesta.onCompleted();

        } catch (ArgumentoInvalido e) {
            respuesta.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());

        } catch (RuntimeException e) {
            log.error("gRPC ListarPreguntasPublicadas: error inesperado", e);
            respuesta.onError(Status.INTERNAL
                    .withDescription("Error interno al listar las preguntas publicadas.")
                    .asRuntimeException());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Traducción al contrato protobuf
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Proyecta el DTO de aplicación al mensaje del contrato.
     *
     * <p>protobuf no admite nulos en campos escalares, así que un valor ausente
     * viaja como cadena vacía. Es el comportamiento que espera cualquier cliente
     * proto3 y por eso no hace falta declarar {@code optional}.
     */
    private PreguntaMensaje aMensaje(PreguntaDto pregunta) {
        PreguntaMensaje.Builder constructor = PreguntaMensaje.newBuilder()
                .setId(pregunta.id().toString())
                .setAutorId(pregunta.autorId().toString())
                .setContexto(pregunta.contexto())
                .setPreguntaDirecta(pregunta.preguntaDirecta())
                .setJustificacion(pregunta.justificacion())
                .setCompetenciaCodigo(pregunta.competenciaCodigo())
                .setCompetenciaNombre(pregunta.competenciaNombre())
                .setTema(pregunta.tema())
                .setSubtema(pregunta.subtema())
                .setNivelDificultad(pregunta.nivelDificultad())
                .setEstado(pregunta.estado());

        pregunta.opciones().forEach(opcion -> constructor.addOpciones(
                OpcionMensaje.newBuilder()
                        .setTexto(opcion.texto())
                        .setEsCorrecta(opcion.esCorrecta())
                        .build()));

        constructor.addAllBibliografia(pregunta.bibliografia());

        return constructor.build();
    }

    private UUID parsearUuid(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new ArgumentoInvalido("El campo pregunta_id es obligatorio.");
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException e) {
            throw new ArgumentoInvalido(
                    "El campo pregunta_id '%s' no es un UUID válido.".formatted(valor));
        }
    }

    private NivelDificultad parsearNivel(String valor) {
        try {
            return NivelDificultad.desdeTexto(valor);
        } catch (ExcepcionDominio e) {
            throw new ArgumentoInvalido(e.getMessage());
        }
    }

    private static String vacioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    /** Petición mal formada: se traduce al código gRPC {@code INVALID_ARGUMENT}. */
    private static class ArgumentoInvalido extends RuntimeException {
        ArgumentoInvalido(String mensaje) {
            super(mensaje);
        }
    }
}
