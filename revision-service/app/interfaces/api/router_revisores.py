"""Endpoints de la proyección local de revisores."""

from __future__ import annotations

from typing import Annotated

from fastapi import APIRouter, Depends, Query, status

from app.interfaces.api.contexto_usuario import (
    ContextoUsuario,
    RolUsuario,
    obtener_contexto_usuario,
)
from app.interfaces.api.dependencias import ContenedorDep
from app.interfaces.api.esquemas import (
    PeticionRegistrarRevisor,
    RespuestaError,
    RespuestaRevisor,
)

router = APIRouter(prefix="/api/v1/revisores", tags=["Revisores"])

UsuarioDep = Annotated[ContextoUsuario, Depends(obtener_contexto_usuario)]

RESPUESTAS_ERROR = {
    400: {"model": RespuestaError, "description": "Datos inválidos"},
    403: {"model": RespuestaError, "description": "El rol no puede hacer esta operación"},
    409: {"model": RespuestaError, "description": "Ya existe un revisor con ese correo"},
}


@router.post(
    "",
    status_code=status.HTTP_201_CREATED,
    response_model=RespuestaRevisor,
    summary="Registrar un revisor",
    responses=RESPUESTAS_ERROR,
    description="""
Da de alta un revisor en la **proyección local** de este servicio.

Es una proyección, no el agregado Usuario: el Bounded Context de Usuarios y Roles
no se implementa en este taller. Hoy se alimenta por aquí y con datos semilla; el
día que exista ese servicio, se alimentará consumiendo `UsuarioRegistrado`.

**Efecto secundario importante:** al registrar un revisor se asignan
automáticamente las solicitudes que estaban esperando (invariante 9), de la más
antigua a la más reciente. La respuesta dice cuántas se desatascaron.

Rol requerido: `ADMINISTRADOR`.
""",
)
async def registrar_revisor(
    peticion: PeticionRegistrarRevisor,
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
) -> RespuestaRevisor:
    usuario.exigir_rol("registrar revisores", RolUsuario.ADMINISTRADOR)

    caso_de_uso = contenedor.registrar_revisor()
    revisor = await caso_de_uso.ejecutar(
        nombre=peticion.nombre,
        correo=peticion.correo,
        revisor_id=peticion.revisorId,
    )
    return RespuestaRevisor.desde_dominio(revisor)


@router.get(
    "",
    response_model=list[RespuestaRevisor],
    summary="Listar revisores",
    responses=RESPUESTAS_ERROR,
    description="""
Lista los revisores de la proyección local, del más antiguo al más reciente.

Ese orden no es casual: es el mismo que usa el `AsignadorRevisor` para desempatar
cuando dos revisores tienen la misma carga.

Rol requerido: `ADMINISTRADOR`.
""",
)
async def listar_revisores(
    usuario: UsuarioDep,
    contenedor: ContenedorDep,
    soloActivos: Annotated[
        bool, Query(description="Excluir los revisores dados de baja.")
    ] = False,
) -> list[RespuestaRevisor]:
    usuario.exigir_rol("consultar revisores", RolUsuario.ADMINISTRADOR)

    revisores = await contenedor.consultar_revisores().listar(solo_activos=soloActivos)
    return [RespuestaRevisor.desde_dominio(revisor) for revisor in revisores]
