"""Cliente gRPC del banco-preguntas-service.

Es la **comunicación síncrona** entre microservicios del sistema: este contexto
la usa al consumir ``PreguntaEnviadaARevision``, para traerse el contenido de la
pregunta que va a evaluar.

El contrato está en ``contracts/proto/banco_preguntas/v1/banco_preguntas.proto``
y el código de los stubs se genera con ``scripts/generar-grpc.ps1``.
"""

from __future__ import annotations

import asyncio
import logging
from uuid import UUID

import grpc

from app.aplicacion.puertos.puertos import (
    BancoNoDisponible,
    ClienteBancoPreguntas,
    PreguntaNoExisteEnBanco,
)
from app.dominio.modelo.enums import NivelDificultad
from app.dominio.modelo.snapshot_pregunta import OpcionSnapshot, SnapshotPregunta
from app.infraestructura.grpc_cliente.generado import banco_preguntas_pb2 as pb
from app.infraestructura.grpc_cliente.generado import banco_preguntas_pb2_grpc as pb_grpc

log = logging.getLogger(__name__)

#: Los dos códigos que merecen un reintento: el banco no está o tardó demasiado.
#: Ambos suelen ser pasajeros, por ejemplo mientras el banco termina de arrancar.
CODIGOS_TRANSITORIOS = {
    grpc.StatusCode.UNAVAILABLE,
    grpc.StatusCode.DEADLINE_EXCEEDED,
}


class ClienteBancoGrpc(ClienteBancoPreguntas):
    """Adaptador del puerto ``ClienteBancoPreguntas`` sobre gRPC.

    Hace la llamada con **timeout de 3 segundos** y hasta **2 intentos**
    separados por **1 segundo**, solo ante ``UNAVAILABLE`` o
    ``DEADLINE_EXCEEDED``. Un ``NOT_FOUND`` o un ``INVALID_ARGUMENT`` fallan de
    inmediato: la pregunta no va a aparecer por insistir.

    El canal se crea una sola vez y se reutiliza. Un canal de gRPC es caro de
    abrir (resuelve DNS, negocia HTTP/2) y está pensado para vivir tanto como el
    proceso, no para abrirse en cada llamada.
    """

    def __init__(
        self,
        direccion: str,
        timeout_segundos: float = 3.0,
        reintentos: int = 2,
        espera_segundos: float = 1.0,
    ) -> None:
        self._direccion = direccion
        self._timeout = timeout_segundos
        self._reintentos = max(1, reintentos)
        self._espera = espera_segundos
        self._canal: grpc.aio.Channel | None = None
        self._stub: pb_grpc.BancoPreguntasStub | None = None

    # ── Ciclo de vida ───────────────────────────────────────────────────────

    async def conectar(self) -> None:
        """Abre el canal. Lo llama el arranque de la aplicación (lifespan).

        No espera a que el banco esté disponible: gRPC conecta de forma
        perezosa. Así el revision-service arranca aunque el banco todavía no
        esté listo, que es justo lo que hace falta cuando los dos se levantan a
        la vez con docker compose.
        """
        if self._canal is not None:
            return
        self._canal = grpc.aio.insecure_channel(
            self._direccion,
            options=[
                # Mantiene viva la conexión a través de NAT y de Docker.
                ("grpc.keepalive_time_ms", 30_000),
                ("grpc.keepalive_timeout_ms", 10_000),
                ("grpc.keepalive_permit_without_calls", 1),
            ],
        )
        self._stub = pb_grpc.BancoPreguntasStub(self._canal)
        log.info("Canal gRPC hacia el banco preparado: %s", self._direccion)

    async def cerrar(self) -> None:
        if self._canal is not None:
            await self._canal.close()
            self._canal = None
            self._stub = None
            log.info("Canal gRPC hacia el banco cerrado.")

    # ── Operaciones ─────────────────────────────────────────────────────────

    async def obtener_pregunta(self, pregunta_id: UUID) -> SnapshotPregunta:
        """Trae la pregunta completa del banco y la convierte en snapshot.

        :raises PreguntaNoExisteEnBanco: el banco respondió NOT_FOUND (permanente)
        :raises BancoNoDisponible: no se pudo contactar tras agotar reintentos
        """
        if self._stub is None:
            await self.conectar()

        peticion = pb.ObtenerPreguntaRequest(pregunta_id=str(pregunta_id))
        ultimo_error: grpc.aio.AioRpcError | None = None

        for intento in range(1, self._reintentos + 1):
            try:
                respuesta = await self._stub.ObtenerPregunta(
                    peticion, timeout=self._timeout
                )
                log.info(
                    "gRPC ObtenerPregunta: pregunta %s obtenida del banco (intento %d).",
                    pregunta_id,
                    intento,
                )
                return self._a_snapshot(respuesta)

            except grpc.aio.AioRpcError as error:
                codigo = error.code()

                if codigo == grpc.StatusCode.NOT_FOUND:
                    # Error permanente: no se reintenta.
                    raise PreguntaNoExisteEnBanco(
                        f"El banco no tiene ninguna pregunta con el identificador "
                        f"{pregunta_id}."
                    ) from error

                if codigo == grpc.StatusCode.INVALID_ARGUMENT:
                    raise PreguntaNoExisteEnBanco(
                        f"El banco rechazó la petición por el identificador "
                        f"{pregunta_id}: {error.details()}"
                    ) from error

                if codigo not in CODIGOS_TRANSITORIOS:
                    raise BancoNoDisponible(
                        f"El banco respondió {codigo.name}: {error.details()}"
                    ) from error

                ultimo_error = error
                if intento < self._reintentos:
                    log.warning(
                        "gRPC ObtenerPregunta falló (intento %d/%d, código %s: %s). "
                        "Reintento en %.1f s.",
                        intento,
                        self._reintentos,
                        codigo.name,
                        error.details(),
                        self._espera,
                    )
                    await asyncio.sleep(self._espera)

        detalle = ultimo_error.details() if ultimo_error else "sin detalle"
        codigo = ultimo_error.code().name if ultimo_error else "DESCONOCIDO"
        log.error(
            "gRPC ObtenerPregunta: el banco no respondió tras %d intentos (%s: %s).",
            self._reintentos,
            codigo,
            detalle,
        )
        raise BancoNoDisponible(
            f"No se pudo obtener la pregunta {pregunta_id} del banco tras "
            f"{self._reintentos} intentos ({codigo}: {detalle})."
        )

    # ── Traducción del contrato al dominio ──────────────────────────────────

    @staticmethod
    def _a_snapshot(mensaje: pb.PreguntaMensaje) -> SnapshotPregunta:
        """Convierte el mensaje protobuf en el Value Object del dominio.

        Es la frontera: a partir de aquí ya no circula un ``PreguntaMensaje``
        sino un ``SnapshotPregunta``, y el dominio no sabe que gRPC existe.
        """
        return SnapshotPregunta(
            pregunta_id=UUID(mensaje.id),
            autor_id=UUID(mensaje.autor_id),
            contexto=mensaje.contexto,
            pregunta_directa=mensaje.pregunta_directa,
            opciones=tuple(
                OpcionSnapshot(texto=opcion.texto, es_correcta=opcion.es_correcta)
                for opcion in mensaje.opciones
            ),
            justificacion=mensaje.justificacion,
            bibliografia=tuple(mensaje.bibliografia),
            competencia_codigo=mensaje.competencia_codigo,
            competencia_nombre=mensaje.competencia_nombre,
            tema=mensaje.tema,
            subtema=mensaje.subtema,
            nivel_dificultad=NivelDificultad(mensaje.nivel_dificultad or "MEDIO"),
            estado_en_banco=mensaje.estado,
        )
