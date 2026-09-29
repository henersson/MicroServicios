"""Quién hace la petición, leído de las cabeceras ``X-Usuario-*``."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from uuid import UUID

from fastapi import Header

from app.dominio.excepciones import AccesoNoAutorizado

CABECERA_ID = "X-Usuario-Id"
CABECERA_ROL = "X-Usuario-Rol"


class UsuarioNoIdentificado(Exception):
    """No se sabe quién hace la petición: faltan las cabeceras o vienen mal.

    Es distinto de ``AccesoNoAutorizado``: ahí sí sabemos quién es y no puede
    hacer la operación (403); aquí no lo sabemos (401).

    Vive en la capa de interfaces porque las cabeceras son un detalle de
    transporte: el día que exista autenticación real, esto desaparece sin que el
    dominio se entere.
    """

    def __init__(self, errores: list[str]) -> None:
        super().__init__("; ".join(errores))
        self.errores = errores


class RolUsuario(str, Enum):
    """Roles del sistema, simulados con la cabecera ``X-Usuario-Rol``.

    El Bounded Context de Usuarios y Roles no se implementa en este taller. En
    su lugar, cada petición declara quién es y qué rol tiene, y esta capa lo
    comprueba.

    Vive en ``interfaces`` y no en ``dominio`` justamente porque es un apaño de
    transporte: el día que exista autenticación real con JWT, esto desaparece
    sin que el dominio se entere. Lo que sí es del dominio es "solo el revisor
    asignado decide", y eso lo comprueba el agregado.
    """

    AUTOR = "AUTOR"
    REVISOR = "REVISOR"
    ADMINISTRADOR = "ADMINISTRADOR"
    DOCENTE = "DOCENTE"
    ESTUDIANTE = "ESTUDIANTE"
    COORDINADOR = "COORDINADOR"

    @classmethod
    def valores_validos(cls) -> str:
        return ", ".join(rol.value for rol in cls)


@dataclass(frozen=True, slots=True)
class ContextoUsuario:
    """Identidad ya validada de quien hace la petición."""

    usuario_id: UUID
    rol: RolUsuario

    def exigir_rol(self, operacion: str, *permitidos: RolUsuario) -> None:
        """Comprueba que el rol pueda hacer la operación.

        :raises AccesoNoAutorizado: con un mensaje que dice qué rol haría falta
        """
        if self.rol not in permitidos:
            requeridos = " o ".join(rol.value for rol in permitidos)
            raise AccesoNoAutorizado(
                f"El rol {self.rol.value} no puede {operacion}. Se requiere: {requeridos}."
            )


async def obtener_contexto_usuario(
    x_usuario_id: str | None = Header(
        default=None,
        alias=CABECERA_ID,
        description="UUID del usuario que hace la petición.",
        examples=["b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e"],
    ),
    x_usuario_rol: str | None = Header(
        default=None,
        alias=CABECERA_ROL,
        description=(
            "Rol del usuario: AUTOR, REVISOR, ADMINISTRADOR, DOCENTE, ESTUDIANTE "
            "o COORDINADOR."
        ),
        examples=["REVISOR"],
    ),
) -> ContextoUsuario:
    """Dependencia de FastAPI que resuelve y valida las cabeceras de usuario.

    Las cabeceras son opcionales en la firma a propósito: así el error lo decide
    esta función y no la validación de FastAPI, que respondería 422. Se juntan
    todos los problemas para informarlos de una vez.

    :raises UsuarioNoIdentificado: se traduce a 401 en el manejador de errores
    """
    errores: list[str] = []
    identificador: UUID | None = None
    rol: RolUsuario | None = None

    if not x_usuario_id or not x_usuario_id.strip():
        errores.append(f"Falta la cabecera {CABECERA_ID} con el UUID del usuario.")
    else:
        try:
            identificador = UUID(x_usuario_id.strip())
        except ValueError:
            errores.append(
                f"La cabecera {CABECERA_ID} no contiene un UUID válido: "
                f"'{x_usuario_id}'."
            )

    if not x_usuario_rol or not x_usuario_rol.strip():
        errores.append(
            f"Falta la cabecera {CABECERA_ROL}. Valores válidos: "
            f"{RolUsuario.valores_validos()}."
        )
    else:
        try:
            rol = RolUsuario(x_usuario_rol.strip().upper())
        except ValueError:
            errores.append(
                f"Rol '{x_usuario_rol}' desconocido. Valores válidos: "
                f"{RolUsuario.valores_validos()}."
            )

    if errores or identificador is None or rol is None:
        raise UsuarioNoIdentificado(errores)

    return ContextoUsuario(usuario_id=identificador, rol=rol)
