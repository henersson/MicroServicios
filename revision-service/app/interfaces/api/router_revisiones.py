"""Endpoints del trabajo del revisor sobre las revisiones."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Path, Query, status

from app.dominio.excepciones import AccesoNoAutorizado
from app.dominio.modelo.enums import Decision, EstadoRevision
from app.interfaces.api.contexto_usuario import (
    ContextoUsuario,
    RolUsuario,
    obtener_contexto_usuario,
)
from app.interfaces.api.dependencias import ContenedorDep
from app.interfaces.api.esquemas import (
    PeticionDecision,
    PeticionFormato,
    PeticionObservacion,
    RespuestaError,
    RespuestaRevision,
)

router = APIRouter(prefix="/api/v1/revisiones", tags=["Revisiones"])

UsuarioDep = Annotated[ContextoUsuario, Depends(obtener_contexto_usuario)]

RESPUESTAS_ERROR = {
    400: {"model": RespuestaError, "description": "Se incumple una regla del ciclo de revisión"},
    403: {"model": RespuestaError, "description": "No es el revisor asignado, o el rol no aplica"},
    404: {"model": RespuestaError, "description": "La revisión no existe"},
    409: {"model": RespuestaError, "description": "La revisión ya está decidida"},
}

IdRevision = Annotated[UUID, Path(description="Identificador de la revisión.")]


@router.get(
    "",
    response_model=list[RespuestaRevision],
    summary="Listar revisiones por filtros",
    responses=RESPUESTAS_ERROR,
    description="""
Lista revisiones, de la más reciente a la más antigua. Todos los filtros son
opcionales y se combinan; omitir uno significa "no filtrar por ese criterio".

Un `REVISOR` solo puede ver **sus propias** revisiones: si no envía `revisorId`,
se le aplica el suyo automáticamente, y si envía el de otro, recibe 403. Un
`ADMINISTRADOR` las ve todas.

Roles permitidos: `REVISOR`, `ADMINISTRADOR`.
""",
)
async def listar_revisiones(
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
    revisorId: Annotated[UUID | None, Query(description="Filtrar por revisor.")] = None,
    preguntaId: Annotated[UUID | None, Query(description="Filtrar por pregunta.")] = None,
    estado: Annotated[
        str | None,
        Query(description="ASIGNADA, EN_EVALUACION, APROBADA o RECHAZADA."),
    ] = None,
) -> list[RespuestaRevision]:
    usuario.exigir_rol("consultar revisiones", RolUsuario.REVISOR, RolUsuario.ADMINISTRADOR)

    # Un revisor no puede fisgar el trabajo de otro: si no dice por quién
    # filtrar, se filtra por él mismo.
    if usuario.rol is RolUsuario.REVISOR:
        if revisorId is None:
            revisorId = usuario.usuario_id
        elif revisorId != usuario.usuario_id:
            raise AccesoNoAutorizado(
                "Un revisor solo puede consultar sus propias revisiones."
            )

    estado_filtro = EstadoRevision(estado.strip().upper()) if estado else None

    revisiones = await contenedor.consultar_revisiones().por_filtros(
        revisor_id=revisorId, pregunta_id=preguntaId, estado=estado_filtro
    )
    return [RespuestaRevision.desde_dominio(revision) for revision in revisiones]


@router.get(
    "/{revision_id}",
    response_model=RespuestaRevision,
    summary="Consultar una revisión",
    responses=RESPUESTAS_ERROR,
    description="""
Devuelve la revisión completa, incluido el **snapshot** de la pregunta que el
revisor tiene que evaluar.

Roles permitidos: el `REVISOR` asignado, o un `ADMINISTRADOR`.
""",
)
async def consultar_revision(
    revision_id: IdRevision,
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
) -> RespuestaRevision:
    usuario.exigir_rol("consultar una revisión", RolUsuario.REVISOR, RolUsuario.ADMINISTRADOR)

    revision = await contenedor.consultar_revisiones().por_id(revision_id)

    if usuario.rol is RolUsuario.REVISOR and revision.revisor_id != usuario.usuario_id:
        raise AccesoNoAutorizado(
            "Solo el revisor asignado a esta revisión puede consultarla."
        )

    return RespuestaRevision.desde_dominio(revision)


@router.put(
    "/{revision_id}/formato",
    response_model=RespuestaRevision,
    summary="Guardar el formato de evaluación",
    responses=RESPUESTAS_ERROR,
    description="""
Guarda o reemplaza el formato de evaluación de la revisión.

Los 5 criterios se puntúan de **1 a 5**. Se puede guardar **incompleto**: el
revisor puntúa lo que lleva y vuelve luego. Lo que no se puede es *decidir* con
el formato incompleto (invariante 10).

La primera vez que se guarda algo, la revisión pasa de `ASIGNADA` a
`EN_EVALUACION`.

Rol requerido: el `REVISOR` asignado a esta revisión.
""",
)
async def guardar_formato(
    revision_id: IdRevision,
    peticion: PeticionFormato,
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
) -> RespuestaRevision:
    usuario.exigir_rol("guardar el formato de evaluación", RolUsuario.REVISOR)

    revision = await contenedor.guardar_formato().ejecutar(
        revision_id=revision_id,
        revisor_id=usuario.usuario_id,
        puntajes=peticion.puntajes,
    )
    return RespuestaRevision.desde_dominio(revision)


@router.post(
    "/{revision_id}/observaciones",
    status_code=status.HTTP_201_CREATED,
    response_model=RespuestaRevision,
    summary="Agregar una observación",
    responses=RESPUESTAS_ERROR,
    description="""
Agrega una observación a la revisión.

Cada observación queda asociada a su revisor y su fecha (invariante 11). Las
observaciones **solo se agregan**: no se editan ni se borran, porque son la
evidencia de la revisión y lo que verá el autor si la pregunta se rechaza.

Para **rechazar** hace falta al menos una observación.

Rol requerido: el `REVISOR` asignado a esta revisión.
""",
)
async def agregar_observacion(
    revision_id: IdRevision,
    peticion: PeticionObservacion,
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
) -> RespuestaRevision:
    usuario.exigir_rol("agregar observaciones", RolUsuario.REVISOR)

    revision, _ = await contenedor.agregar_observacion().ejecutar(
        revision_id=revision_id,
        revisor_id=usuario.usuario_id,
        texto=peticion.texto,
    )
    return RespuestaRevision.desde_dominio(revision)


@router.post(
    "/{revision_id}/decision",
    response_model=RespuestaRevision,
    summary="Decidir la revisión (aprobar o rechazar)",
    responses=RESPUESTAS_ERROR,
    description="""
Cierra la revisión y publica el resultado hacia el `banco-preguntas-service`.

- **APROBAR** → se publica `PreguntaAprobadaTecnicamente` y la pregunta pasa a
  `APROBADA` en el banco. Exige el formato completo y un promedio no inferior al
  mínimo configurado (3.0 por defecto).
- **RECHAZAR** → se publica `PreguntaRechazadaPorPares` y la pregunta vuelve a
  `BORRADOR` en el banco, con las observaciones visibles para el autor. Exige el
  formato completo y **al menos una observación**.

La decisión es definitiva: una revisión decidida no se reabre. Si la pregunta se
corrige y se reenvía, se abre una revisión nueva.

Rol requerido: el `REVISOR` asignado a esta revisión.
""",
)
async def decidir(
    revision_id: IdRevision,
    peticion: PeticionDecision,
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
) -> RespuestaRevision:
    usuario.exigir_rol("decidir sobre una revisión", RolUsuario.REVISOR)

    revision = await contenedor.decidir_revision().ejecutar(
        revision_id=revision_id,
        revisor_id=usuario.usuario_id,
        decision=Decision.desde_texto(peticion.decision),
    )
    return RespuestaRevision.desde_dominio(revision)
