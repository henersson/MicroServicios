"""Esquemas Pydantic de la API REST.

Son los DTO de entrada y salida de la capa de interfaces. Nunca se expone el
agregado ni el modelo de SQLAlchemy: si se devolviera el agregado, cualquier
refactor del dominio rompería a los clientes; y los modelos ORM arrastrarían
relaciones perezosas hasta el serializador.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.dominio.modelo.enums import CriterioEvaluacion
from app.dominio.modelo.revision import Revision
from app.dominio.modelo.revisor_disponible import RevisorDisponible


# ── Revisores ───────────────────────────────────────────────────────────────


class PeticionRegistrarRevisor(BaseModel):
    """Alta de un revisor en la proyección local."""

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "nombre": "Martha Liliana Torres",
                "correo": "mtorres@unicauca.edu.co",
            }
        }
    )

    nombre: Annotated[str, Field(min_length=3, max_length=200)] = Field(
        description="Nombre completo del revisor."
    )
    # Se valida como texto y no con EmailStr para no arrastrar la dependencia
    # email-validator: el Value Object RevisorDisponible ya comprueba el formato.
    correo: Annotated[str, Field(min_length=5, max_length=200)] = Field(
        description="Correo institucional; no se puede repetir."
    )
    revisorId: UUID | None = Field(
        default=None,
        description=(
            "Identificador del revisor. Se puede fijar para que coincida con el que "
            "el usuario ya tenga en el sistema; si se omite, se genera uno."
        ),
    )


class RespuestaRevisor(BaseModel):
    """Un revisor de la proyección local."""

    revisorId: UUID
    nombre: str
    correo: str
    registradoEn: datetime
    activo: bool

    @classmethod
    def desde_dominio(cls, revisor: RevisorDisponible) -> "RespuestaRevisor":
        return cls(
            revisorId=revisor.revisor_id,
            nombre=revisor.nombre,
            correo=revisor.correo,
            registradoEn=revisor.registrado_en,
            activo=revisor.activo,
        )


# ── Revisiones ──────────────────────────────────────────────────────────────


class OpcionSnapshotSalida(BaseModel):
    texto: str
    esCorrecta: bool


class SnapshotSalida(BaseModel):
    """La copia de la pregunta que el revisor evalúa.

    Se obtuvo del banco por gRPC al crear la revisión y no vuelve a
    actualizarse: el revisor puntúa una versión concreta del texto.
    """

    preguntaId: UUID
    autorId: UUID
    contexto: str
    preguntaDirecta: str
    opciones: list[OpcionSnapshotSalida]
    justificacion: str
    bibliografia: list[str]
    competenciaCodigo: str
    competenciaNombre: str
    tema: str
    subtema: str
    nivelDificultad: str
    estadoEnBanco: str


class ObservacionSalida(BaseModel):
    observacionId: UUID
    revisorId: UUID
    texto: str
    fecha: datetime


class RespuestaRevision(BaseModel):
    """Una revisión completa."""

    revisionId: UUID
    preguntaId: UUID
    revisorId: UUID
    estado: str
    formato: dict[str, int] = Field(
        description="Criterio → puntaje (1 a 5). Puede estar incompleto mientras el "
        "revisor trabaja."
    )
    formatoCompleto: bool
    criteriosFaltantes: list[str]
    promedio: float
    observaciones: list[ObservacionSalida]
    decision: str | None
    fechaDecision: datetime | None
    creadaEn: datetime | None
    actualizadaEn: datetime | None
    snapshot: SnapshotSalida

    @classmethod
    def desde_dominio(cls, revision: Revision) -> "RespuestaRevision":
        snapshot = revision.snapshot
        return cls(
            revisionId=revision.revision_id,
            preguntaId=revision.pregunta_id,
            revisorId=revision.revisor_id,
            estado=revision.estado.value,
            formato=revision.formato.como_textos(),
            formatoCompleto=revision.formato.esta_completo(),
            criteriosFaltantes=[
                criterio.value for criterio in revision.formato.criterios_faltantes()
            ],
            promedio=revision.promedio,
            observaciones=[
                ObservacionSalida(
                    observacionId=obs.observacion_id,
                    revisorId=obs.revisor_id,
                    texto=obs.texto,
                    fecha=obs.fecha,
                )
                for obs in revision.observaciones
            ],
            decision=revision.decision.value if revision.decision else None,
            fechaDecision=revision.fecha_decision,
            creadaEn=revision.creada_en,
            actualizadaEn=revision.actualizada_en,
            snapshot=SnapshotSalida(
                preguntaId=snapshot.pregunta_id,
                autorId=snapshot.autor_id,
                contexto=snapshot.contexto,
                preguntaDirecta=snapshot.pregunta_directa,
                opciones=[
                    OpcionSnapshotSalida(texto=o.texto, esCorrecta=o.es_correcta)
                    for o in snapshot.opciones
                ],
                justificacion=snapshot.justificacion,
                bibliografia=list(snapshot.bibliografia),
                competenciaCodigo=snapshot.competencia_codigo,
                competenciaNombre=snapshot.competencia_nombre,
                tema=snapshot.tema,
                subtema=snapshot.subtema,
                nivelDificultad=snapshot.nivel_dificultad.value,
                estadoEnBanco=snapshot.estado_en_banco,
            ),
        )


class PeticionFormato(BaseModel):
    """Formato de evaluación que envía el revisor.

    Los 5 criterios se puntúan de 1 a 5. Se puede enviar incompleto: el revisor
    guarda lo que lleva y vuelve luego. Lo que no se puede es *decidir* con el
    formato incompleto (invariante 10).
    """

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "puntajes": {
                    "CLARIDAD_CONTEXTO": 5,
                    "PERTINENCIA_COMPETENCIA": 4,
                    "PLAUSIBILIDAD_DISTRACTORES": 4,
                    "UNICIDAD_RESPUESTA_CORRECTA": 5,
                    "CALIDAD_JUSTIFICACION": 4,
                }
            }
        }
    )

    puntajes: dict[str, int] = Field(
        description=(
            "Criterio → puntaje entre 1 y 5. Criterios válidos: "
            + ", ".join(criterio.value for criterio in CriterioEvaluacion)
            + "."
        )
    )


class PeticionObservacion(BaseModel):
    """Una observación del revisor sobre la pregunta."""

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "texto": (
                    "El contexto supera las 120 palabras recomendadas para Saber Pro; "
                    "conviene recortarlo sin perder la situación a analizar."
                )
            }
        }
    )

    texto: Annotated[str, Field(min_length=10)] = Field(
        description="Qué hay que corregir. El autor lo verá si la pregunta se rechaza, "
        "así que tiene que ser accionable."
    )


class PeticionDecision(BaseModel):
    """La decisión final del revisor."""

    model_config = ConfigDict(json_schema_extra={"example": {"decision": "APROBAR"}})

    decision: str = Field(description="APROBAR o RECHAZAR.")


# ── Errores ─────────────────────────────────────────────────────────────────


class RespuestaError(BaseModel):
    """Error en formato Problem Details (RFC 7807).

    Tiene exactamente la misma forma que la del banco-preguntas-service, campo
    extra ``errores[]`` incluido, para que un cliente que hable con los dos
    servicios no tenga que distinguir de cuál viene el error.
    """

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "type": "https://saberpro.unicauca.edu.co/errores/regla-de-negocio",
                "title": "No se puede completar la operación",
                "status": 400,
                "detail": (
                    "No se puede decidir con el formato de evaluación incompleto: "
                    "faltan 2 de 5 criterios por puntuar. (Invariante 10)"
                ),
                "instance": "/api/v1/revisiones/{id}/decision",
                "errores": ["Falta puntuar el criterio CALIDAD_JUSTIFICACION."],
            }
        }
    )

    type: str
    title: str
    status: int
    detail: str
    instance: str | None = None
    errores: list[str] = Field(default_factory=list)
