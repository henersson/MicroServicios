"""Implementación de los repositorios del dominio sobre SQLAlchemy.

Aquí se cumple la inversión de dependencias de la Clean Architecture: las
interfaces las declara el dominio y las implementa la infraestructura, así que
la flecha de dependencia apunta hacia adentro y no al revés.
"""

from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.dominio.modelo.enums import EstadoRevision
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible
from app.dominio.repositorios.repositorios import (
    RegistroEventosProcesados,
    RevisionRepository,
    RevisorRepository,
)
from app.dominio.servicios.asignador_revisor import CargaRevisor
from app.infraestructura.persistencia import mapeadores
from app.infraestructura.persistencia.modelos import (
    EventoProcesadoORM,
    RevisionORM,
    RevisorORM,
)

#: Estados que cuentan como carga de trabajo de un revisor.
ESTADOS_ACTIVOS = (EstadoRevision.ASIGNADA.value, EstadoRevision.EN_EVALUACION.value)


class RevisionRepositorySQL(RevisionRepository):
    def __init__(self, sesion: AsyncSession) -> None:
        self._sesion = sesion

    async def guardar(self, revision: Revision) -> Revision:
        fila = await self._sesion.get(RevisionORM, revision.revision_id)
        if fila is None:
            fila = RevisionORM()
            mapeadores.volcar_revision(revision, fila)
            self._sesion.add(fila)
        else:
            mapeadores.volcar_revision(revision, fila)
        # flush y no commit: quien decide confirmar es la unidad de trabajo. El
        # flush sirve para que un fallo de integridad salte aquí, donde todavía
        # se puede traducir a un error con sentido.
        await self._sesion.flush()
        return revision

    async def buscar_por_id(self, revision_id: UUID) -> Revision | None:
        fila = await self._sesion.get(RevisionORM, revision_id)
        return mapeadores.revision_a_dominio(fila) if fila else None

    async def buscar_por_pregunta(self, pregunta_id: UUID) -> Revision | None:
        # La más reciente primero: si una pregunta se rechazó y se reenvió, nos
        # interesa la revisión actual, no la de la vuelta anterior.
        consulta = (
            select(RevisionORM)
            .where(RevisionORM.pregunta_id == pregunta_id)
            .order_by(RevisionORM.creada_en.desc())
            .limit(1)
        )
        fila = (await self._sesion.execute(consulta)).scalar_one_or_none()
        return mapeadores.revision_a_dominio(fila) if fila else None

    async def buscar_por_filtros(
        self,
        revisor_id: UUID | None = None,
        pregunta_id: UUID | None = None,
        estado: EstadoRevision | None = None,
    ) -> list[Revision]:
        consulta = select(RevisionORM)
        # Un filtro en None simplemente no agrega su condición.
        if revisor_id is not None:
            consulta = consulta.where(RevisionORM.revisor_id == revisor_id)
        if pregunta_id is not None:
            consulta = consulta.where(RevisionORM.pregunta_id == pregunta_id)
        if estado is not None:
            consulta = consulta.where(RevisionORM.estado == estado.value)
        consulta = consulta.order_by(RevisionORM.creada_en.desc())

        filas = (await self._sesion.execute(consulta)).scalars().all()
        return [mapeadores.revision_a_dominio(fila) for fila in filas]

    async def contar_activas_por_revisor(self) -> dict[UUID, int]:
        consulta = (
            select(RevisionORM.revisor_id, func.count())
            .where(RevisionORM.estado.in_(ESTADOS_ACTIVOS))
            .group_by(RevisionORM.revisor_id)
        )
        filas = (await self._sesion.execute(consulta)).all()
        return {revisor_id: total for revisor_id, total in filas}


class RevisorRepositorySQL(RevisorRepository):
    def __init__(self, sesion: AsyncSession) -> None:
        self._sesion = sesion

    async def guardar(self, revisor: RevisorDisponible) -> RevisorDisponible:
        fila = await self._sesion.get(RevisorORM, revisor.revisor_id)
        fila = mapeadores.revisor_a_fila(revisor, fila)
        if fila not in self._sesion:
            self._sesion.add(fila)
        await self._sesion.flush()
        return revisor

    async def buscar_por_id(self, revisor_id: UUID) -> RevisorDisponible | None:
        fila = await self._sesion.get(RevisorORM, revisor_id)
        return mapeadores.revisor_a_dominio(fila) if fila else None

    async def buscar_por_correo(self, correo: str) -> RevisorDisponible | None:
        consulta = select(RevisorORM).where(RevisorORM.correo == correo.strip().lower())
        fila = (await self._sesion.execute(consulta)).scalar_one_or_none()
        return mapeadores.revisor_a_dominio(fila) if fila else None

    async def listar(self, solo_activos: bool = False) -> list[RevisorDisponible]:
        consulta = select(RevisorORM)
        if solo_activos:
            consulta = consulta.where(RevisorORM.activo.is_(True))
        consulta = consulta.order_by(RevisorORM.registrado_en)
        filas = (await self._sesion.execute(consulta)).scalars().all()
        return [mapeadores.revisor_a_dominio(fila) for fila in filas]

    async def cargas(self) -> list[CargaRevisor]:
        """Revisores activos con su carga, en dos consultas y no en N+1."""
        revisores = await self.listar(solo_activos=True)

        conteo = await RevisionRepositorySQL(self._sesion).contar_activas_por_revisor()

        return [
            CargaRevisor(revisor=revisor, revisiones_activas=conteo.get(revisor.revisor_id, 0))
            for revisor in revisores
        ]


class RegistroEventosProcesadosSQL(RegistroEventosProcesados):
    """Idempotencia del consumidor, sobre la tabla ``eventos_procesados``."""

    def __init__(self, sesion: AsyncSession) -> None:
        self._sesion = sesion

    async def ya_fue_procesado(self, event_id: UUID) -> bool:
        return await self._sesion.get(EventoProcesadoORM, event_id) is not None

    async def marcar_como_procesado(self, event_id: UUID, tipo_evento: str) -> None:
        self._sesion.add(
            EventoProcesadoORM(
                event_id=event_id,
                event_type=tipo_evento,
                procesado_en=datetime.now(timezone.utc),
            )
        )
        await self._sesion.flush()
