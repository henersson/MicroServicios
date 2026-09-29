"""Puertos de salida de la capa de aplicación.

La capa de aplicación solo conoce estas interfaces; qué haya al otro lado
(RabbitMQ, gRPC) es un detalle de infraestructura. Eso permite probar los casos
de uso con dobles en memoria, sin levantar broker ni servidor gRPC.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from uuid import UUID

from app.dominio.eventos.eventos import EventoDominio
from app.dominio.modelo.snapshot_pregunta import SnapshotPregunta


class PublicadorEventos(ABC):
    """Por aquí salen los eventos de dominio hacia el mundo.

    **Garantía que debe cumplir toda implementación**: los eventos se envían al
    broker únicamente *después* de que la transacción de base de datos haya
    confirmado. Nunca se anuncia algo que podría deshacerse (ADR 4).
    """

    @abstractmethod
    async def publicar(self, eventos: list[EventoDominio]) -> None:
        """Publica los eventos ya extraídos del agregado. La lista puede ir vacía."""


class ClienteBancoPreguntas(ABC):
    """Cliente del servicio gRPC del banco-preguntas-service.

    Es la **comunicación síncrona** entre microservicios: este contexto la usa
    para traerse el contenido de la pregunta que va a evaluar, al consumir
    ``PreguntaEnviadaARevision``.

    Se declara como puerto y no se usa directamente el stub generado para que la
    capa de aplicación no dependa de grpc, y para poder sustituirlo por un doble
    en los tests.
    """

    @abstractmethod
    async def obtener_pregunta(self, pregunta_id: UUID) -> SnapshotPregunta:
        """Trae la pregunta completa desde el banco.

        :raises PreguntaNoExisteEnBanco: la pregunta no existe en el banco
        :raises BancoNoDisponible: no se pudo contactar al banco
        """


class BancoNoDisponible(Exception):
    """El banco no respondió: está caído, saturado o hay un problema de red.

    El cliente gRPC ya lo intento 2 veces antes de lanzarla. El consumidor manda
    el mensaje a la DLQ, para que no se pierda y se pueda reprocesar.
    """


class PreguntaNoExisteEnBanco(Exception):
    """El banco respondió NOT_FOUND: esa pregunta no existe.

    No se reintenta: la pregunta no va a aparecer por insistir. El mensaje va
    derecho a la DLQ.
    """
