"""Domain Events del BC Ciclo de Vida y Revisión.

Los registra el agregado ``Revision`` cuando pasa algo de negocio, y el caso de
uso los entrega al puerto ``PublicadorEventos`` después de guardar. La
publicación real en RabbitMQ ocurre **después de confirmar la transacción**,
para no anunciar nunca algo que terminó deshaciéndose (ADR 4).

El nombre en pasado no es estilo: un evento describe un hecho consumado, nunca
una orden. Por eso ningún consumidor puede rechazarlo.

Los contratos están en ``contracts/events/``; cualquier cambio de forma aquí
tiene que ir acompañado del cambio en su JSON Schema.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import TYPE_CHECKING, Any
from uuid import UUID, uuid4

if TYPE_CHECKING:  # pragma: no cover - solo para los type hints
    from app.dominio.modelo.observacion import Observacion

#: Nombre de este microservicio en el campo ``source`` del envelope.
ORIGEN = "revision-service"


@dataclass(frozen=True, slots=True)
class EventoDominio:
    """Base de los eventos de integración que produce este contexto.

    Lleva el envelope común de el contrato de eventos. La carga útil propia
    de cada evento la aporta :meth:`datos` en cada subclase.
    """

    event_id: UUID
    ocurrido_en: datetime
    revision_id: UUID
    pregunta_id: UUID
    revisor_id: UUID

    #: Nombre del evento (``eventType``). Lo define cada subclase.
    tipo: str = field(default="", init=False)
    #: Routing key con la que se publica en ``saberpro.eventos``.
    routing_key: str = field(default="", init=False)

    def datos(self) -> dict[str, Any]:
        """Carga útil del campo ``data`` del envelope."""
        raise NotImplementedError


@dataclass(frozen=True, slots=True)
class RevisorAsignado(EventoDominio):
    """Se creó la revisión con un revisor asignado (invariante 9).

    Lo consume el banco para pasar la pregunta de ``PENDIENTE_REVISION`` a
    ``EN_REVISION``. Es el evento que se agregó respecto del Taller 1 (ADR 1):
    sin él, el banco no podría distinguir "esperando revisor" de "ya la están
    revisando".

    Contrato: ``contracts/events/RevisorAsignado.v1.schema.json``.
    """

    tipo: str = field(default="RevisorAsignado", init=False)
    routing_key: str = field(default="revision.revisor.asignado", init=False)

    @classmethod
    def crear(
        cls,
        revision_id: UUID,
        pregunta_id: UUID,
        revisor_id: UUID,
        ocurrido_en: datetime,
    ) -> "RevisorAsignado":
        return cls(
            event_id=uuid4(),
            ocurrido_en=ocurrido_en,
            revision_id=revision_id,
            pregunta_id=pregunta_id,
            revisor_id=revisor_id,
        )

    def datos(self) -> dict[str, Any]:
        return {
            "revisionId": str(self.revision_id),
            "preguntaId": str(self.pregunta_id),
            "revisorId": str(self.revisor_id),
        }


@dataclass(frozen=True, slots=True)
class PreguntaAprobadaTecnicamente(EventoDominio):
    """El revisor aprobó la pregunta tras completar su formato de evaluación.

    Lo consume el banco para pasar la pregunta de ``EN_REVISION`` a ``APROBADA``.

    Lleva el ``promedio`` como dato informativo: el banco lo registra en su log
    pero no basa ninguna regla en él, porque la nota pertenece a este Bounded
    Context.

    Contrato: ``contracts/events/PreguntaAprobadaTecnicamente.v1.schema.json``.
    """

    promedio: float = 0.0

    tipo: str = field(default="PreguntaAprobadaTecnicamente", init=False)
    routing_key: str = field(default="revision.pregunta.aprobada", init=False)

    @classmethod
    def crear(
        cls,
        revision_id: UUID,
        pregunta_id: UUID,
        revisor_id: UUID,
        promedio: float,
        ocurrido_en: datetime,
    ) -> "PreguntaAprobadaTecnicamente":
        return cls(
            event_id=uuid4(),
            ocurrido_en=ocurrido_en,
            revision_id=revision_id,
            pregunta_id=pregunta_id,
            revisor_id=revisor_id,
            promedio=promedio,
        )

    def datos(self) -> dict[str, Any]:
        return {
            "revisionId": str(self.revision_id),
            "preguntaId": str(self.pregunta_id),
            "revisorId": str(self.revisor_id),
            "promedio": self.promedio,
        }


@dataclass(frozen=True, slots=True)
class PreguntaRechazadaPorPares(EventoDominio):
    """El revisor rechazó la pregunta.

    Lo consume el banco para devolver la pregunta a ``BORRADOR`` con las
    observaciones visibles para el autor (ADR 2). Por eso el evento lleva las
    observaciones completas y no solo un identificador: el banco tiene que poder
    mostrárselas al autor sin llamar de vuelta a este servicio.

    Siempre lleva al menos una (invariante 11).

    Contrato: ``contracts/events/PreguntaRechazadaPorPares.v1.schema.json``.
    """

    observaciones: tuple["Observacion", ...] = ()

    tipo: str = field(default="PreguntaRechazadaPorPares", init=False)
    routing_key: str = field(default="revision.pregunta.rechazada", init=False)

    @classmethod
    def crear(
        cls,
        revision_id: UUID,
        pregunta_id: UUID,
        revisor_id: UUID,
        observaciones: list["Observacion"],
        ocurrido_en: datetime,
    ) -> "PreguntaRechazadaPorPares":
        return cls(
            event_id=uuid4(),
            ocurrido_en=ocurrido_en,
            revision_id=revision_id,
            pregunta_id=pregunta_id,
            revisor_id=revisor_id,
            observaciones=tuple(observaciones),
        )

    def datos(self) -> dict[str, Any]:
        return {
            "revisionId": str(self.revision_id),
            "preguntaId": str(self.pregunta_id),
            "revisorId": str(self.revisor_id),
            "observaciones": [
                {
                    "observacionId": str(observacion.observacion_id),
                    "revisorId": str(observacion.revisor_id),
                    "texto": observacion.texto,
                    # Formato del contrato: UTC ISO-8601 terminado en Z.
                    "fecha": observacion.fecha.isoformat().replace("+00:00", "Z"),
                }
                for observacion in self.observaciones
            ],
        }
