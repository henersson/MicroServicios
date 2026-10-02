"""Casos de uso del trabajo del revisor: formato, observaciones y decisión."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Mapping
from uuid import UUID

from app.aplicacion.puertos.puertos import PublicadorEventos
from app.aplicacion.puertos.unidad_de_trabajo import UnidadDeTrabajo
from app.dominio.excepciones import RecursoNoEncontrado
from app.dominio.modelo.enums import Decision, EstadoRevision
from app.dominio.modelo.formato_evaluacion import FormatoEvaluacion
from app.dominio.modelo.observacion import Observacion
from app.dominio.modelo.revision import Revision

log = logging.getLogger(__name__)


class _CasoDeUsoSobreRevision:
    """Base con lo común: cargar la revisión o fallar con 404."""

    def __init__(self, uow: UnidadDeTrabajo) -> None:
        self._uow = uow

    async def _cargar(self, revision_id: UUID) -> Revision:
        revision = await self._uow.revisiones.buscar_por_id(revision_id)
        if revision is None:
            raise RecursoNoEncontrado(
                f"No existe una revisión con el identificador {revision_id}."
            )
        return revision


class GuardarFormatoUseCase(_CasoDeUsoSobreRevision):
    """El revisor asignado guarda (o actualiza) su formato de evaluación.

    Se puede guardar incompleto: el revisor puntúa lo que lleva y vuelve luego.
    La comprobación de completitud es cosa de la decisión, no del guardado
    (invariante 10).
    """

    async def ejecutar(
        self, revision_id: UUID, revisor_id: UUID, puntajes: Mapping[str, int]
    ) -> Revision:
        ahora = datetime.now(timezone.utc)

        async with self._uow:
            revision = await self._cargar(revision_id)
            formato = FormatoEvaluacion.desde_textos(puntajes)
            revision.guardar_formato(revisor_id, formato, ahora)
            await self._uow.revisiones.guardar(revision)
            await self._uow.commit()

        log.info(
            "Revisión %s: formato guardado con %d/%d criterios (promedio %.2f).",
            revision_id,
            len(revision.formato.puntajes),
            len(revision.formato.puntajes) + len(revision.formato.criterios_faltantes()),
            revision.promedio,
        )
        return revision


class AgregarObservacionUseCase(_CasoDeUsoSobreRevision):
    """El revisor asignado agrega una observación (invariante 11).

    Las observaciones solo se agregan; no hay forma de editarlas ni de
    borrarlas, porque son la evidencia de la revisión y lo que el autor verá si
    la pregunta se rechaza.
    """

    async def ejecutar(
        self, revision_id: UUID, revisor_id: UUID, texto: str
    ) -> tuple[Revision, Observacion]:
        ahora = datetime.now(timezone.utc)

        async with self._uow:
            revision = await self._cargar(revision_id)
            observacion = revision.agregar_observacion(revisor_id, texto, ahora)
            await self._uow.revisiones.guardar(revision)
            await self._uow.commit()

        log.info(
            "Revisión %s: observación %s agregada por el revisor %s.",
            revision_id,
            observacion.observacion_id,
            revisor_id,
        )
        return revision, observacion


class DecidirRevisionUseCase(_CasoDeUsoSobreRevision):
    """El revisor asignado cierra la revisión: aprueba o rechaza.

    Es el punto en que este Bounded Context vuelve a hablar con el banco:
    publica ``PreguntaAprobadaTecnicamente`` o ``PreguntaRechazadaPorPares``, y
    con eso la pregunta pasa a ``APROBADA`` o a ``RECHAZADA`` con las
    observaciones.

    Las reglas duras (formato completo, promedio mínimo, al menos una
    observación para rechazar) las impone el agregado; aquí solo se orquesta.
    """

    def __init__(
        self,
        uow: UnidadDeTrabajo,
        publicador: PublicadorEventos,
        promedio_minimo: float,
    ) -> None:
        super().__init__(uow)
        self._publicador = publicador
        self._promedio_minimo = promedio_minimo

    async def ejecutar(
        self, revision_id: UUID, revisor_id: UUID, decision: Decision
    ) -> Revision:
        ahora = datetime.now(timezone.utc)

        async with self._uow:
            revision = await self._cargar(revision_id)
            revision.decidir(
                revisor_id=revisor_id,
                decision=decision,
                ahora=ahora,
                promedio_minimo=self._promedio_minimo,
            )
            await self._uow.revisiones.guardar(revision)
            await self._uow.commit()

            eventos = revision.extraer_eventos_pendientes()

        # Publicación SOLO después del commit (ADR 4).
        await self._publicador.publicar(eventos)

        log.info(
            "Revisión %s: decisión %s por el revisor %s (promedio %.2f, %d observación(es)).",
            revision_id,
            decision.value,
            revisor_id,
            revision.promedio,
            len(revision.observaciones),
        )
        return revision


class ConsultarRevisionesUseCase(_CasoDeUsoSobreRevision):
    """Consultas sobre las revisiones."""

    async def por_id(self, revision_id: UUID) -> Revision:
        async with self._uow:
            return await self._cargar(revision_id)

    async def por_filtros(
        self,
        revisor_id: UUID | None = None,
        pregunta_id: UUID | None = None,
        estado: EstadoRevision | None = None,
    ) -> list[Revision]:
        async with self._uow:
            return await self._uow.revisiones.buscar_por_filtros(
                revisor_id=revisor_id, pregunta_id=pregunta_id, estado=estado
            )
