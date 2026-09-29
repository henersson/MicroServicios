"""Unidad de trabajo sobre SQLAlchemy: una sesión, una transacción."""

from __future__ import annotations

import logging

from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine

from app.aplicacion.puertos.unidad_de_trabajo import UnidadDeTrabajo
from app.dominio.excepciones import ConflictoDeDatos
from app.infraestructura.persistencia.repositorios_sql import (
    RegistroEventosProcesadosSQL,
    RevisionRepositorySQL,
    RevisorRepositorySQL,
)

log = logging.getLogger(__name__)


def crear_motor(database_url: str, echo: bool = False) -> AsyncEngine:
    """Crea el motor asíncrono de SQLAlchemy.

    ``pool_pre_ping`` comprueba la conexión antes de usarla: sin él, tras un
    reinicio de PostgreSQL el pool serviría conexiones muertas y la primera
    petición de cada una fallaría sin motivo aparente.
    """
    return create_async_engine(
        database_url,
        echo=echo,
        pool_pre_ping=True,
        pool_size=5,
        max_overflow=5,
    )


def crear_fabrica_sesiones(motor: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(
        motor,
        expire_on_commit=False,
        autoflush=False,
    )


class UnidadDeTrabajoSQL(UnidadDeTrabajo):
    """Agrupa los repositorios bajo una sesión y una transacción.

    Se puede reentrar: un caso de uso que abre la unidad dos veces seguidas
    (como ``IniciarRevision``, que suelta la transacción para hacer la llamada
    gRPC y la vuelve a abrir) obtiene una sesión nueva cada vez. Eso es
    deliberado: mantener una transacción abierta durante una llamada de red
    retendría la conexión varios segundos sin necesidad.
    """

    def __init__(self, fabrica_sesiones: async_sessionmaker[AsyncSession]) -> None:
        self._fabrica = fabrica_sesiones
        self._sesion: AsyncSession | None = None
        self._confirmada = False

    async def __aenter__(self) -> "UnidadDeTrabajoSQL":
        self._sesion = self._fabrica()
        self._confirmada = False

        self.revisiones = RevisionRepositorySQL(self._sesion)
        self.revisores = RevisorRepositorySQL(self._sesion)
        self.eventos_procesados = RegistroEventosProcesadosSQL(self._sesion)
        return self

    async def __aexit__(self, tipo_exc, valor_exc, traza) -> None:
        if self._sesion is None:
            return
        try:
            # Lo que no se confirma explícitamente, no queda. Así una excepción
            # a mitad del caso de uso no deja medio cambio aplicado.
            if not self._confirmada:
                await self._sesion.rollback()
        finally:
            await self._sesion.close()
            self._sesion = None

    async def commit(self) -> None:
        if self._sesion is None:
            raise RuntimeError("La unidad de trabajo no está abierta.")
        try:
            await self._sesion.commit()
            self._confirmada = True
        except IntegrityError as error:
            await self._sesion.rollback()
            # Una violación de unicidad es un conflicto de negocio (409), no un
            # error interno: pasa cuando dos peticiones intentan registrar el
            # mismo revisor a la vez, o cuando el mismo evento se procesa dos
            # veces en paralelo.
            log.warning("Conflicto de integridad al confirmar: %s", error.orig)
            raise ConflictoDeDatos(
                "La operación choca con datos que ya existen. Si estabas registrando un "
                "revisor, puede que otra petición lo haya creado al mismo tiempo."
            ) from error

    async def rollback(self) -> None:
        if self._sesion is not None:
            await self._sesion.rollback()

    @property
    def sesion(self) -> AsyncSession:
        """Acceso a la sesión, solo para los tests y las tareas de arranque."""
        if self._sesion is None:
            raise RuntimeError("La unidad de trabajo no está abierta.")
        return self._sesion
