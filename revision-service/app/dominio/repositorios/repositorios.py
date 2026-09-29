"""Repositorios del BC Ciclo de Vida y Revisión, declarados en el dominio.

Son clases abstractas y no interfaces de SQLAlchemy a propósito: el dominio
declara *qué* necesita guardar y buscar, y la capa de infraestructura decide
*cómo* (aquí, SQLAlchemy sobre PostgreSQL). Esa es la inversión de dependencias
que sostiene la Clean Architecture: la flecha apunta hacia adentro.

Los métodos son ``async`` porque todo el servicio lo es (FastAPI, aio-pika,
SQLAlchemy asyncio). Que sean corrutinas no mete ninguna tecnología concreta en
el dominio: ``async`` es sintaxis del lenguaje, no una librería.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from uuid import UUID

from app.dominio.modelo.enums import EstadoRevision
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible
from app.dominio.servicios.asignador_revisor import CargaRevisor


class RevisionRepository(ABC):
    """Repositorio del Aggregate Root ``Revision``.

    No hay ninguna operación de borrado: una revisión es la evidencia de que la
    evaluación ocurrió, y borrarla dejaría a la pregunta con un historial que no
    cuadra.
    """

    @abstractmethod
    async def guardar(self, revision: Revision) -> Revision:
        """Guarda la revisión, sea nueva o existente."""

    @abstractmethod
    async def buscar_por_id(self, revision_id: UUID) -> Revision | None:
        ...

    @abstractmethod
    async def buscar_por_pregunta(self, pregunta_id: UUID) -> Revision | None:
        """Devuelve la revisión activa o más reciente de una pregunta.

        Se usa para no abrir dos revisiones de la misma pregunta si el evento
        ``PreguntaEnviadaARevision`` llegara repetido con otro ``eventId``.
        """

    @abstractmethod
    async def buscar_por_filtros(
        self,
        revisor_id: UUID | None = None,
        pregunta_id: UUID | None = None,
        estado: EstadoRevision | None = None,
    ) -> list[Revision]:
        """Busca revisiones. Un filtro en ``None`` significa "no filtrar por eso"."""

    @abstractmethod
    async def contar_activas_por_revisor(self) -> dict[UUID, int]:
        """Cuántas revisiones activas tiene cada revisor.

        Devuelve el conteo agregado en una sola consulta, en vez de que el
        asignador pregunte revisor por revisor: con pocos revisores da igual,
        pero es la diferencia entre una consulta y N.
        """


class RevisorRepository(ABC):
    """Repositorio de la proyección local ``RevisorDisponible``."""

    @abstractmethod
    async def guardar(self, revisor: RevisorDisponible) -> RevisorDisponible:
        ...

    @abstractmethod
    async def buscar_por_id(self, revisor_id: UUID) -> RevisorDisponible | None:
        ...

    @abstractmethod
    async def buscar_por_correo(self, correo: str) -> RevisorDisponible | None:
        """Para no registrar dos veces a la misma persona."""

    @abstractmethod
    async def listar(self, solo_activos: bool = False) -> list[RevisorDisponible]:
        ...

    @abstractmethod
    async def cargas(self) -> list[CargaRevisor]:
        """Revisores activos con su carga actual, listos para el asignador."""


class RegistroEventosProcesados(ABC):
    """La memoria de qué eventos entrantes ya se aplicaron.

    Es lo que hace idempotente al consumidor. RabbitMQ garantiza entrega *al
    menos una vez*, así que el mismo evento puede llegar dos veces: por un
    reintento tras un fallo de red, o porque el consumidor murió justo después
    de aplicar el cambio y antes de confirmar el ACK.

    Se escribe **dentro de la misma transacción** que aplica el cambio: o se
    hacen las dos cosas, o ninguna.
    """

    @abstractmethod
    async def ya_fue_procesado(self, event_id: UUID) -> bool:
        ...

    @abstractmethod
    async def marcar_como_procesado(self, event_id: UUID, tipo_evento: str) -> None:
        ...
