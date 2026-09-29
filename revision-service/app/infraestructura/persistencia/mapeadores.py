"""Traducción entre los modelos de SQLAlchemy y el dominio.

Es el precio de mantener el dominio limpio de anotaciones de persistencia, y se
paga con gusto: gracias a este módulo el agregado puede cambiar de forma sin
arrastrar una migración, y la base de datos puede reorganizarse sin tocar las
reglas de negocio.
"""

from __future__ import annotations

from typing import Any
from uuid import UUID

from app.dominio.modelo.enums import (
    CriterioEvaluacion,
    Decision,
    EstadoRevision,
    NivelDificultad,
)
from app.dominio.modelo.formato_evaluacion import FormatoEvaluacion
from app.dominio.modelo.observacion import Observacion
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible
from app.dominio.modelo.snapshot_pregunta import OpcionSnapshot, SnapshotPregunta
from app.infraestructura.persistencia.modelos import (
    CriterioORM,
    ObservacionORM,
    RevisionORM,
    RevisorORM,
)


# ── Snapshot ⇄ JSONB ────────────────────────────────────────────────────────
# El snapshot se guarda como JSON en una sola columna. Se serializa a mano, con
# claves explícitas, en vez de volcar el dataclass: así el formato guardado es
# el que decidimos nosotros y un refactor del Value Object no deja ilegibles las
# filas que ya están en la base.


def snapshot_a_json(snapshot: SnapshotPregunta) -> dict[str, Any]:
    return {
        "preguntaId": str(snapshot.pregunta_id),
        "autorId": str(snapshot.autor_id),
        "contexto": snapshot.contexto,
        "preguntaDirecta": snapshot.pregunta_directa,
        "opciones": [
            {"texto": opcion.texto, "esCorrecta": opcion.es_correcta}
            for opcion in snapshot.opciones
        ],
        "justificacion": snapshot.justificacion,
        "bibliografia": list(snapshot.bibliografia),
        "competenciaCodigo": snapshot.competencia_codigo,
        "competenciaNombre": snapshot.competencia_nombre,
        "tema": snapshot.tema,
        "subtema": snapshot.subtema,
        "nivelDificultad": snapshot.nivel_dificultad.value,
        "estadoEnBanco": snapshot.estado_en_banco,
    }


def snapshot_desde_json(datos: dict[str, Any]) -> SnapshotPregunta:
    return SnapshotPregunta(
        pregunta_id=UUID(datos["preguntaId"]),
        autor_id=UUID(datos["autorId"]),
        contexto=datos["contexto"],
        pregunta_directa=datos["preguntaDirecta"],
        opciones=tuple(
            OpcionSnapshot(texto=o["texto"], es_correcta=bool(o["esCorrecta"]))
            for o in datos.get("opciones", [])
        ),
        justificacion=datos.get("justificacion", ""),
        bibliografia=tuple(datos.get("bibliografia", [])),
        competencia_codigo=datos.get("competenciaCodigo", ""),
        competencia_nombre=datos.get("competenciaNombre", ""),
        tema=datos.get("tema", ""),
        subtema=datos.get("subtema", ""),
        nivel_dificultad=NivelDificultad(datos.get("nivelDificultad", "MEDIO")),
        estado_en_banco=datos.get("estadoEnBanco", ""),
    )


# ── Revision ⇄ RevisionORM ──────────────────────────────────────────────────


def revision_a_dominio(fila: RevisionORM) -> Revision:
    formato = FormatoEvaluacion(
        {
            CriterioEvaluacion(criterio.criterio): criterio.puntaje
            for criterio in fila.criterios
        }
    )
    observaciones = [
        Observacion(
            observacion_id=obs.observacion_id,
            revisor_id=obs.revisor_id,
            texto=obs.texto,
            fecha=obs.fecha,
        )
        for obs in fila.observaciones
    ]

    return Revision(
        revision_id=fila.revision_id,
        pregunta_id=fila.pregunta_id,
        revisor_id=fila.revisor_id,
        snapshot=snapshot_desde_json(fila.snapshot),
        estado=EstadoRevision(fila.estado),
        formato=formato,
        observaciones=observaciones,
        decision=Decision(fila.decision) if fila.decision else None,
        fecha_decision=fila.fecha_decision,
        creada_en=fila.creada_en,
        actualizada_en=fila.actualizada_en,
    )


def volcar_revision(revision: Revision, fila: RevisionORM) -> RevisionORM:
    """Vuelca el estado del agregado sobre una fila (nueva o existente).

    Las colecciones hijas se actualizan **en su sitio** en lugar de vaciarlas y
    volver a llenarlas: si se hiciera lo segundo, SQLAlchemy podría emitir los
    INSERT antes que los DELETE dentro del mismo flush y chocar con la
    restricción ``UNIQUE (revision_id, criterio)``.
    """
    fila.revision_id = revision.revision_id
    fila.pregunta_id = revision.pregunta_id
    fila.revisor_id = revision.revisor_id
    fila.estado = revision.estado.value
    fila.snapshot = snapshot_a_json(revision.snapshot)
    fila.decision = revision.decision.value if revision.decision else None
    fila.fecha_decision = revision.fecha_decision
    if revision.creada_en:
        fila.creada_en = revision.creada_en
    if revision.actualizada_en:
        fila.actualizada_en = revision.actualizada_en

    _volcar_criterios(revision.formato, fila)
    _volcar_observaciones(revision.observaciones, fila)
    return fila


def _volcar_criterios(formato: FormatoEvaluacion, fila: RevisionORM) -> None:
    existentes = {criterio.criterio: criterio for criterio in fila.criterios}
    deseados = formato.como_textos()

    for nombre, puntaje in deseados.items():
        if nombre in existentes:
            existentes[nombre].puntaje = puntaje
        else:
            fila.criterios.append(CriterioORM(criterio=nombre, puntaje=puntaje))

    # Un criterio que ya no está en el formato se quita: pasa si el revisor
    # reenvía el formato con menos criterios de los que había guardado.
    for nombre, criterio in existentes.items():
        if nombre not in deseados:
            fila.criterios.remove(criterio)


def _volcar_observaciones(observaciones: list[Observacion], fila: RevisionORM) -> None:
    """Las observaciones solo se agregan; las existentes no se tocan nunca."""
    ya_guardadas = {obs.observacion_id for obs in fila.observaciones}
    for observacion in observaciones:
        if observacion.observacion_id in ya_guardadas:
            continue
        fila.observaciones.append(
            ObservacionORM(
                observacion_id=observacion.observacion_id,
                revisor_id=observacion.revisor_id,
                texto=observacion.texto,
                fecha=observacion.fecha,
            )
        )


# ── RevisorDisponible ⇄ RevisorORM ──────────────────────────────────────────


def revisor_a_dominio(fila: RevisorORM) -> RevisorDisponible:
    return RevisorDisponible(
        revisor_id=fila.revisor_id,
        nombre=fila.nombre,
        correo=fila.correo,
        registrado_en=fila.registrado_en,
        activo=fila.activo,
    )


def revisor_a_fila(revisor: RevisorDisponible, fila: RevisorORM | None = None) -> RevisorORM:
    fila = fila or RevisorORM()
    fila.revisor_id = revisor.revisor_id
    fila.nombre = revisor.nombre
    fila.correo = revisor.correo
    fila.registrado_en = revisor.registrado_en
    fila.activo = revisor.activo
    return fila
