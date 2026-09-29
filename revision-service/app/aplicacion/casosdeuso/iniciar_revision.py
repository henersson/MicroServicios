"""Caso de uso: iniciar la revisión al recibir ``PreguntaEnviadaARevision``."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID

from app.aplicacion.puertos.puertos import ClienteBancoPreguntas, PublicadorEventos
from app.aplicacion.puertos.unidad_de_trabajo import UnidadDeTrabajo
from app.dominio.modelo.revision import Revision
from app.dominio.servicios.asignador_revisor import AsignadorRevisor

log = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ResultadoIniciarRevision:
    """Qué pasó al procesar el evento, para que el consumidor pueda registrarlo."""

    #: True si el evento produjo un cambio; False si ya estaba aplicado.
    aplicado: bool
    revision_id: UUID | None = None
    revisor_id: UUID | None = None
    motivo: str = ""


class IniciarRevisionUseCase:
    """Convierte un ``PreguntaEnviadaARevision`` en una revisión asignada.

    Es el caso de uso donde confluyen los tres mecanismos de integración del
    taller:

    1. Llega un **evento** de RabbitMQ (asíncrono).
    2. Se llama por **gRPC** al banco para traer el contenido (síncrono).
    3. Se publica otro **evento** de vuelta (``RevisorAsignado``).

    El orden importa. Primero se pide el snapshot, porque sin él no hay nada que
    evaluar. Después ``AsignadorRevisor`` elige revisor y se crea la
    ``Revision``, que al nacer registra ``RevisorAsignado``; el banco usa ese
    evento para pasar la pregunta a ``EN_REVISION``.

    Si no hay ningún revisor posible, el asignador lanza una excepción de
    dominio: es la **invariante 9**, que exige que una ``Revision`` nunca exista
    sin revisor. El consumidor registra el motivo y el mensaje va a la DLQ, así
    que la pregunta se queda en ``PENDIENTE_REVISION`` en el banco.
    """

    def __init__(
        self,
        uow: UnidadDeTrabajo,
        cliente_banco: ClienteBancoPreguntas,
        publicador: PublicadorEventos,
        asignador: AsignadorRevisor,
    ) -> None:
        self._uow = uow
        self._cliente_banco = cliente_banco
        self._publicador = publicador
        self._asignador = asignador

    async def ejecutar(
        self, event_id: UUID, pregunta_id: UUID, autor_id: UUID
    ) -> ResultadoIniciarRevision:
        """Procesa el evento.

        :param event_id: identificador del evento, clave de idempotencia
        :param pregunta_id: pregunta que entra al ciclo de revisión
        :param autor_id: autor, para que el asignador no se la asigne a él mismo
        :raises BancoNoDisponible: el banco no respondió; el consumidor lo manda a la DLQ
        :raises PreguntaNoExisteEnBanco: la pregunta no existe; va a la DLQ
        :raises ReglaDeNegocioViolada: no hay revisores disponibles (invariante 9)
        """
        async with self._uow:
            # ── Idempotencia ────────────────────────────────────────────────
            if await self._uow.eventos_procesados.ya_fue_procesado(event_id):
                log.info(
                    "Evento %s [PreguntaEnviadaARevision] ya procesado; se descarta "
                    "por idempotencia.",
                    event_id,
                )
                return ResultadoIniciarRevision(
                    aplicado=False, motivo="evento ya procesado"
                )

            # Segunda barrera: un evento distinto que pida lo mismo. Pasa si el
            # autor reenvía la pregunta antes de que se decida la revisión
            # anterior, o si alguien republica el evento a mano.
            existente = await self._uow.revisiones.buscar_por_pregunta(pregunta_id)
            if existente is not None and existente.esta_activa:
                log.info(
                    "Evento %s: la pregunta %s ya tiene la revisión activa %s; no se "
                    "crea otra.",
                    event_id,
                    pregunta_id,
                    existente.revision_id,
                )
                await self._uow.eventos_procesados.marcar_como_procesado(
                    event_id, "PreguntaEnviadaARevision"
                )
                await self._uow.commit()
                return ResultadoIniciarRevision(
                    aplicado=False,
                    revision_id=existente.revision_id,
                    revisor_id=existente.revisor_id,
                    motivo="la pregunta ya tiene una revisión activa",
                )

        # ── Llamada gRPC al banco, FUERA de la transacción ──────────────────
        # Una llamada de red no debe mantener abierta una transacción de base de
        # datos: con el timeout de 3 s y sus reintentos, la conexión quedaría
        # retenida varios segundos sin necesidad.
        log.info(
            "Evento %s: pidiendo la pregunta %s al banco por gRPC.", event_id, pregunta_id
        )
        snapshot = await self._cliente_banco.obtener_pregunta(pregunta_id)

        ahora = datetime.now(timezone.utc)

        async with self._uow:
            cargas = await self._uow.revisores.cargas()
            # Si no hay revisor posible lanza ReglaDeNegocioViolada (invariante 9).
            revisor = self._asignador.elegir(cargas, autor_id=autor_id)

            revision = Revision.iniciar(
                pregunta_id=pregunta_id,
                revisor_id=revisor.revisor_id,
                snapshot=snapshot,
                ahora=ahora,
            )
            await self._uow.revisiones.guardar(revision)
            await self._uow.eventos_procesados.marcar_como_procesado(
                event_id, "PreguntaEnviadaARevision"
            )
            await self._uow.commit()

            eventos = revision.extraer_eventos_pendientes()

        # Publicación SOLO después del commit.
        await self._publicador.publicar(eventos)

        log.info(
            "Evento %s: revisión %s creada para la pregunta %s, asignada al revisor %s.",
            event_id,
            revision.revision_id,
            pregunta_id,
            revisor.revisor_id,
        )
        return ResultadoIniciarRevision(
            aplicado=True,
            revision_id=revision.revision_id,
            revisor_id=revisor.revisor_id,
            motivo="revisión creada y revisor asignado",
        )
