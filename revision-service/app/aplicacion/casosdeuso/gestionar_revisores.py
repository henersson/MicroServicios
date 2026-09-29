"""Casos de uso de la proyección local de revisores."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from uuid import UUID, uuid4

from app.aplicacion.puertos.unidad_de_trabajo import UnidadDeTrabajo
from app.dominio.excepciones import ConflictoDeDatos, RecursoNoEncontrado
from app.dominio.modelo.revisor_disponible import RevisorDisponible

log = logging.getLogger(__name__)


class RegistrarRevisorUseCase:
    """Da de alta un revisor en la proyección local de revisores."""

    def __init__(self, uow: UnidadDeTrabajo) -> None:
        self._uow = uow

    async def ejecutar(
        self,
        nombre: str,
        correo: str,
        revisor_id: UUID | None = None,
    ) -> RevisorDisponible:
        """
        :param revisor_id: se puede fijar para que coincida con el identificador
            que el usuario ya tiene en el sistema; si no, se genera uno.
        :raises ConflictoDeDatos: si ese correo o ese identificador ya existen
        """
        ahora = datetime.now(timezone.utc)

        async with self._uow:
            correo_normalizado = (correo or "").strip().lower()
            if await self._uow.revisores.buscar_por_correo(correo_normalizado):
                raise ConflictoDeDatos(
                    f"Ya hay un revisor registrado con el correo {correo_normalizado}."
                )

            identificador = revisor_id or uuid4()
            if await self._uow.revisores.buscar_por_id(identificador):
                raise ConflictoDeDatos(
                    f"Ya hay un revisor registrado con el identificador {identificador}."
                )

            revisor = RevisorDisponible(
                revisor_id=identificador,
                nombre=nombre,
                correo=correo_normalizado,
                registrado_en=ahora,
                activo=True,
            )
            await self._uow.revisores.guardar(revisor)
            await self._uow.commit()

        log.info("Revisor registrado: %s (%s).", revisor.nombre, revisor.revisor_id)
        return revisor


class ConsultarRevisoresUseCase:
    """Consulta de la proyección local de revisores."""

    def __init__(self, uow: UnidadDeTrabajo) -> None:
        self._uow = uow

    async def listar(self, solo_activos: bool = False) -> list[RevisorDisponible]:
        async with self._uow:
            return await self._uow.revisores.listar(solo_activos=solo_activos)

    async def por_id(self, revisor_id: UUID) -> RevisorDisponible:
        async with self._uow:
            revisor = await self._uow.revisores.buscar_por_id(revisor_id)
            if revisor is None:
                raise RecursoNoEncontrado(
                    f"No existe un revisor con el identificador {revisor_id}."
                )
            return revisor
