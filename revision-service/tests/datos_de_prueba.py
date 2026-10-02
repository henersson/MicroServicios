"""Fábrica de datos válidos para los tests.

Los tests parten siempre de una revisión que cumple todas las invariantes y
rompen exactamente una cosa. Así, cuando un test falla, el motivo es lo que el
test rompió y no un descuido al construir el caso.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import UUID

from app.dominio.modelo.enums import CriterioEvaluacion, NivelDificultad
from app.dominio.modelo.formato_evaluacion import FormatoEvaluacion
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible
from app.dominio.modelo.snapshot_pregunta import OpcionSnapshot, SnapshotPregunta

AHORA = datetime(2026, 9, 22, 10, 0, 0, tzinfo=timezone.utc)

AUTOR = UUID("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")
REVISOR = UUID("b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e")
OTRO_REVISOR = UUID("c8d9e0f1-2a3b-4c5d-9e6f-7a8b9c0d1e2f")
TERCER_REVISOR = UUID("d9e0f1a2-3b4c-4d5e-8f6a-7b8c9d0e1f2a")
PREGUNTA = UUID("3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c")

OBSERVACION_VALIDA = (
    "El contexto supera las 120 palabras recomendadas para Saber Pro; conviene "
    "recortarlo sin perder la situación a analizar."
)


def snapshot(pregunta_id: UUID = PREGUNTA, autor_id: UUID = AUTOR) -> SnapshotPregunta:
    """Snapshot válido de una pregunta de Ingeniería de Sistemas."""
    return SnapshotPregunta(
        pregunta_id=pregunta_id,
        autor_id=autor_id,
        contexto=(
            "Una universidad migra su plataforma monolítica a microservicios y un "
            "desarrollador propone que tres servicios compartan la misma base de datos."
        ),
        pregunta_directa=(
            "¿Por qué el arquitecto rechaza compartir la base de datos entre los "
            "servicios?"
        ),
        opciones=(
            OpcionSnapshot("El acoplamiento de datos impide el despliegue independiente.", True),
            OpcionSnapshot("PostgreSQL no admite varios clientes concurrentes.", False),
            OpcionSnapshot("El teorema CAP prohíbe compartir un motor relacional.", False),
            OpcionSnapshot("Una base compartida siempre es más costosa de operar.", False),
        ),
        justificacion=(
            "La independencia de despliegue depende de que cada servicio sea dueño "
            "exclusivo de sus datos."
        ),
        bibliografia=("Newman, S. (2021). Building Microservices. O'Reilly.",),
        competencia_codigo="ING-SOFT",
        competencia_nombre="Diseño de Software y Arquitectura",
        tema="Arquitectura de Software",
        subtema="Microservicios",
        nivel_dificultad=NivelDificultad.MEDIO,
        estado_en_banco="PENDIENTE_REVISION",
    )


def revision(revisor_id: UUID = REVISOR, pregunta_id: UUID = PREGUNTA) -> Revision:
    """Revisión recién iniciada, en estado ASIGNADA."""
    return Revision.iniciar(
        pregunta_id=pregunta_id,
        revisor_id=revisor_id,
        snapshot=snapshot(pregunta_id=pregunta_id),
        ahora=AHORA,
    )


def formato_completo(puntaje: int = 4) -> FormatoEvaluacion:
    """Formato con los 6 criterios puntuados igual."""
    return FormatoEvaluacion({criterio: puntaje for criterio in CriterioEvaluacion})


def formato_con(**puntajes_por_criterio: int) -> FormatoEvaluacion:
    """Formato a medida: ``formato_con(CLARIDAD_CONTEXTO=5, ...)``."""
    return FormatoEvaluacion.desde_textos(puntajes_por_criterio)


def formato_incompleto() -> FormatoEvaluacion:
    """Formato con solo 3 de los 6 criterios puntuados."""
    criterios = list(CriterioEvaluacion)[:3]
    return FormatoEvaluacion({criterio: 4 for criterio in criterios})


def revisor(
    revisor_id: UUID = REVISOR,
    nombre: str = "Marta Liliana Torres",
    correo: str = "mtorres@unicauca.edu.co",
    dias_de_antiguedad: int = 30,
    activo: bool = True,
) -> RevisorDisponible:
    return RevisorDisponible(
        revisor_id=revisor_id,
        nombre=nombre,
        correo=correo,
        registrado_en=AHORA - timedelta(days=dias_de_antiguedad),
        activo=activo,
    )
