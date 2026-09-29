"""Dobles en memoria de los puertos, para probar los casos de uso sin Docker.

Que esto sea posible es la prueba de que la Clean Architecture está bien hecha:
los casos de uso solo conocen interfaces, así que se pueden ejercitar completos
—con sus transacciones, su idempotencia y su publicación de eventos— sin
PostgreSQL, sin RabbitMQ y sin el banco levantado.
"""

from __future__ import annotations

from uuid import UUID

from app.aplicacion.puertos.puertos import (
    BancoNoDisponible,
    ClienteBancoPreguntas,
    PreguntaNoExisteEnBanco,
    PublicadorEventos,
)
from app.aplicacion.puertos.unidad_de_trabajo import UnidadDeTrabajo
from app.dominio.eventos.eventos import EventoDominio
from app.dominio.modelo.enums import EstadoRevision
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible
from app.dominio.modelo.snapshot_pregunta import SnapshotPregunta
from app.dominio.repositorios.repositorios import (
    RegistroEventosProcesados,
    RevisionRepository,
    RevisorRepository,
)
from app.dominio.servicios.asignador_revisor import CargaRevisor


class Almacen:
    """Los datos compartidos por todos los repositorios en memoria.

    Vive fuera de la unidad de trabajo para que los datos sobrevivan entre
    bloques ``async with``, igual que sobreviven en una base de datos real.
    """

    def __init__(self) -> None:
        self.revisiones: dict[UUID, Revision] = {}
        self.revisores: dict[UUID, RevisorDisponible] = {}
        self.eventos_procesados: dict[UUID, str] = {}
        #: Cuántas veces se llamó a commit(). Sirve para comprobar que los
        #: eventos se publican DESPUÉS de confirmar, y no antes.
        self.commits = 0


class RevisionRepositoryMemoria(RevisionRepository):
    def __init__(self, almacen: Almacen) -> None:
        self._almacen = almacen

    async def guardar(self, revision: Revision) -> Revision:
        self._almacen.revisiones[revision.revision_id] = revision
        return revision

    async def buscar_por_id(self, revision_id: UUID) -> Revision | None:
        return self._almacen.revisiones.get(revision_id)

    async def buscar_por_pregunta(self, pregunta_id: UUID) -> Revision | None:
        candidatas = [
            revision
            for revision in self._almacen.revisiones.values()
            if revision.pregunta_id == pregunta_id
        ]
        if not candidatas:
            return None
        return max(candidatas, key=lambda revision: revision.creada_en)

    async def buscar_por_filtros(
        self,
        revisor_id: UUID | None = None,
        pregunta_id: UUID | None = None,
        estado: EstadoRevision | None = None,
    ) -> list[Revision]:
        return [
            revision
            for revision in self._almacen.revisiones.values()
            if (revisor_id is None or revision.revisor_id == revisor_id)
            and (pregunta_id is None or revision.pregunta_id == pregunta_id)
            and (estado is None or revision.estado is estado)
        ]

    async def contar_activas_por_revisor(self) -> dict[UUID, int]:
        conteo: dict[UUID, int] = {}
        for revision in self._almacen.revisiones.values():
            if revision.esta_activa:
                conteo[revision.revisor_id] = conteo.get(revision.revisor_id, 0) + 1
        return conteo


class RevisorRepositoryMemoria(RevisorRepository):
    def __init__(self, almacen: Almacen) -> None:
        self._almacen = almacen

    async def guardar(self, revisor: RevisorDisponible) -> RevisorDisponible:
        self._almacen.revisores[revisor.revisor_id] = revisor
        return revisor

    async def buscar_por_id(self, revisor_id: UUID) -> RevisorDisponible | None:
        return self._almacen.revisores.get(revisor_id)

    async def buscar_por_correo(self, correo: str) -> RevisorDisponible | None:
        objetivo = correo.strip().lower()
        for revisor in self._almacen.revisores.values():
            if revisor.correo == objetivo:
                return revisor
        return None

    async def listar(self, solo_activos: bool = False) -> list[RevisorDisponible]:
        revisores = list(self._almacen.revisores.values())
        if solo_activos:
            revisores = [revisor for revisor in revisores if revisor.activo]
        return sorted(revisores, key=lambda revisor: revisor.registrado_en)

    async def cargas(self) -> list[CargaRevisor]:
        conteo = await RevisionRepositoryMemoria(self._almacen).contar_activas_por_revisor()
        return [
            CargaRevisor(revisor, conteo.get(revisor.revisor_id, 0))
            for revisor in await self.listar(solo_activos=True)
        ]


class RegistroEventosProcesadosMemoria(RegistroEventosProcesados):
    def __init__(self, almacen: Almacen) -> None:
        self._almacen = almacen

    async def ya_fue_procesado(self, event_id: UUID) -> bool:
        return event_id in self._almacen.eventos_procesados

    async def marcar_como_procesado(self, event_id: UUID, tipo_evento: str) -> None:
        self._almacen.eventos_procesados[event_id] = tipo_evento


class UnidadDeTrabajoMemoria(UnidadDeTrabajo):
    """Unidad de trabajo en memoria. Cuenta los commits para poder verificarlos."""

    def __init__(self, almacen: Almacen | None = None) -> None:
        self.almacen = almacen or Almacen()
        self.revisiones = RevisionRepositoryMemoria(self.almacen)
        self.revisores = RevisorRepositoryMemoria(self.almacen)
        self.eventos_procesados = RegistroEventosProcesadosMemoria(self.almacen)

    async def __aenter__(self) -> "UnidadDeTrabajoMemoria":
        return self

    async def __aexit__(self, tipo_exc, valor_exc, traza) -> None:
        return None

    async def commit(self) -> None:
        self.almacen.commits += 1

    async def rollback(self) -> None:
        return None


class PublicadorEventosEspia(PublicadorEventos):
    """Guarda lo publicado y anota cuántos commits había en ese momento.

    Eso último es lo que permite comprobar la garantía de ADR 4: que ningún
    evento sale antes de confirmar la transacción.
    """

    def __init__(self, almacen: Almacen | None = None) -> None:
        self.publicados: list[EventoDominio] = []
        self.commits_al_publicar: list[int] = []
        self._almacen = almacen

    async def publicar(self, eventos: list[EventoDominio]) -> None:
        self.publicados.extend(eventos)
        if self._almacen is not None and eventos:
            self.commits_al_publicar.append(self._almacen.commits)

    def tipos(self) -> list[str]:
        return [evento.tipo for evento in self.publicados]


class ClienteBancoFalso(ClienteBancoPreguntas):
    """Doble del cliente gRPC. Se le puede pedir que falle a propósito."""

    def __init__(
        self,
        snapshot: SnapshotPregunta | None = None,
        fallar_con: Exception | None = None,
    ) -> None:
        self._snapshot = snapshot
        self._fallar_con = fallar_con
        self.llamadas: list[UUID] = []

    async def obtener_pregunta(self, pregunta_id: UUID) -> SnapshotPregunta:
        self.llamadas.append(pregunta_id)
        if self._fallar_con is not None:
            raise self._fallar_con
        if self._snapshot is None:
            raise PreguntaNoExisteEnBanco(f"No existe la pregunta {pregunta_id}.")
        return self._snapshot

    @staticmethod
    def caido() -> "ClienteBancoFalso":
        return ClienteBancoFalso(
            fallar_con=BancoNoDisponible("El banco no respondió tras 3 intentos.")
        )
