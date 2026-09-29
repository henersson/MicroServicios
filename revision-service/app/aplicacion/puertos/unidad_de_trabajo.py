"""Puerto ``UnidadDeTrabajo``: el límite transaccional de un caso de uso."""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.dominio.repositorios.repositorios import (
    RegistroEventosProcesados,
    RevisionRepository,
    RevisorRepository,
)


class UnidadDeTrabajo(ABC):
    """Agrupa los repositorios bajo una única transacción.

    Existe por la idempotencia: marcar el evento como procesado tiene que
    ocurrir en la misma transacción que aplica el cambio. Si fueran dos
    transacciones, habría una ventana en la que el evento constaría como
    procesado sin haberse aplicado, y una reentrega lo descartaría creyendo que
    ya estaba hecho.

    Se usa como gestor de contexto asíncrono::

        async with uow:
            revision = await uow.revisiones.buscar_por_id(id)
            ...
            await uow.commit()
        # solo aquí, con la transacción ya confirmada, se publican los eventos
        await publicador.publicar(eventos)

    Si el bloque termina sin ``commit()``, se hace ``rollback()``: lo que no se
    confirma explícitamente, no queda.
    """

    revisiones: RevisionRepository
    revisores: RevisorRepository
    eventos_procesados: RegistroEventosProcesados

    @abstractmethod
    async def __aenter__(self) -> "UnidadDeTrabajo":
        ...

    @abstractmethod
    async def __aexit__(self, tipo_exc, valor_exc, traza) -> None:
        ...

    @abstractmethod
    async def commit(self) -> None:
        ...

    @abstractmethod
    async def rollback(self) -> None:
        ...
