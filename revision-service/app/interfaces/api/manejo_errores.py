"""Traducción de excepciones a Problem Details (RFC 7807).

Es el único sitio del servicio donde se decide qué código HTTP corresponde a
cada fallo. Concentrarlo aquí es lo que permite que el dominio lance excepciones
con nombre de negocio (``TransicionInvalida``) sin saber nada de HTTP, y que los
routers no tengan un solo ``try/except``.

El formato es **el mismo que el del banco-preguntas-service**, campo extra
``errores[]`` incluido, para que un cliente que hable con los dos servicios no
tenga que distinguir de cuál viene el error.
"""

from __future__ import annotations

import logging

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.aplicacion.puertos.puertos import BancoNoDisponible, PreguntaNoExisteEnBanco
from app.interfaces.api.contexto_usuario import (
    CABECERA_ID,
    CABECERA_ROL,
    UsuarioNoIdentificado,
)
from app.dominio.excepciones import (
    AccesoNoAutorizado,
    ConflictoDeDatos,
    ExcepcionDominio,
    RecursoNoEncontrado,
    ReglaDeNegocioViolada,
    TransicionInvalida,
)

log = logging.getLogger(__name__)

BASE_TIPOS = "https://saberpro.unicauca.edu.co/errores/"

#: Correspondencia entre excepción de dominio, código HTTP y tipo del problema.
#:
#: ============================  ====  =====================================
#: Excepción                     HTTP  Por qué
#: ============================  ====  =====================================
#: UsuarioNoIdentificado          401  No sabemos quién hace la petición
#: ReglaDeNegocioViolada          400  Lo enviado no sirve; hay que cambiarlo
#: AccesoNoAutorizado             403  Sabemos quién eres y no puedes hacerlo
#: RecursoNoEncontrado            404  El recurso no existe
#: TransicionInvalida             409  Choca con el estado actual
#: ConflictoDeDatos               409  Ya existe algo igual
#: ============================  ====  =====================================
_CORRESPONDENCIAS: list[tuple[type[ExcepcionDominio], int, str, str]] = [
    (
        ReglaDeNegocioViolada,
        status.HTTP_400_BAD_REQUEST,
        "regla-de-negocio",
        "No se puede completar la operación",
    ),
    (
        AccesoNoAutorizado,
        status.HTTP_403_FORBIDDEN,
        "acceso-no-autorizado",
        "No tienes permiso para esta operación",
    ),
    (
        RecursoNoEncontrado,
        status.HTTP_404_NOT_FOUND,
        "recurso-no-encontrado",
        "Recurso no encontrado",
    ),
    (
        TransicionInvalida,
        status.HTTP_409_CONFLICT,
        "transicion-invalida",
        "Cambio de estado no permitido",
    ),
    (
        ConflictoDeDatos,
        status.HTTP_409_CONFLICT,
        "conflicto-de-datos",
        "El recurso ya existe",
    ),
]


def _problema(
    peticion: Request,
    estado: int,
    tipo: str,
    titulo: str,
    detalle: str,
    errores: list[str] | None = None,
) -> JSONResponse:
    return JSONResponse(
        status_code=estado,
        # El media type de RFC 7807. Es lo que permite a un cliente distinguir
        # un error estructurado de cualquier otro JSON.
        media_type="application/problem+json; charset=utf-8",
        content={
            "type": BASE_TIPOS + tipo,
            "title": titulo,
            "status": estado,
            "detail": detalle,
            "instance": str(peticion.url.path),
            # El campo siempre está presente, aunque venga vacío: así los
            # clientes pueden recorrerlo sin comprobar antes si existe.
            "errores": errores or [],
        },
    )


def registrar_manejadores(app: FastAPI) -> None:
    """Engancha todos los manejadores de error a la aplicación."""

    @app.exception_handler(UsuarioNoIdentificado)
    async def _no_identificado(
        peticion: Request, error: UsuarioNoIdentificado
    ) -> JSONResponse:
        """Faltan las cabeceras de usuario o vienen mal: 401, no 403.

        403 sería "sé quién eres y no puedes"; aquí no sabemos quién es.
        """
        return _problema(
            peticion,
            status.HTTP_401_UNAUTHORIZED,
            "usuario-no-identificado",
            "No se pudo identificar al usuario",
            "Cada petición debe declarar quién la hace con las cabeceras "
            f"{CABECERA_ID} (UUID) y {CABECERA_ROL} (rol).",
            error.errores,
        )

    @app.exception_handler(ExcepcionDominio)
    async def _dominio(peticion: Request, error: ExcepcionDominio) -> JSONResponse:
        for clase, estado, tipo, titulo in _CORRESPONDENCIAS:
            if isinstance(error, clase):
                log.debug("%s: %s", clase.__name__, error.mensaje)
                return _problema(
                    peticion, estado, tipo, titulo, error.mensaje, error.errores
                )

        # Una excepción de dominio sin correspondencia declarada. No debería
        # pasar, pero es mejor un 400 con el mensaje real que un 500 mudo.
        log.warning("Excepción de dominio sin correspondencia: %r", error)
        return _problema(
            peticion,
            status.HTTP_400_BAD_REQUEST,
            "regla-de-negocio",
            "No se puede completar la operación",
            error.mensaje,
            error.errores,
        )

    @app.exception_handler(RequestValidationError)
    async def _validacion(
        peticion: Request, error: RequestValidationError
    ) -> JSONResponse:
        """Falla la validación de Pydantic sobre el cuerpo o los parámetros."""
        errores = []
        for detalle in error.errors():
            # El primer elemento de loc es "body"/"query"/"header": sobra para
            # el usuario, que solo quiere saber qué campo está mal.
            ubicacion = ".".join(str(parte) for parte in detalle.get("loc", [])[1:])
            errores.append(
                f"{ubicacion or 'petición'}: {detalle.get('msg', 'valor inválido')}"
            )

        return _problema(
            peticion,
            status.HTTP_400_BAD_REQUEST,
            "peticion-invalida",
            "Faltan datos o tienen un formato incorrecto",
            "Revisa los campos indicados en 'errores'.",
            errores,
        )

    @app.exception_handler(PreguntaNoExisteEnBanco)
    async def _pregunta_inexistente(
        peticion: Request, error: PreguntaNoExisteEnBanco
    ) -> JSONResponse:
        return _problema(
            peticion,
            status.HTTP_404_NOT_FOUND,
            "pregunta-no-encontrada-en-banco",
            "La pregunta no existe en el banco",
            str(error),
        )

    @app.exception_handler(BancoNoDisponible)
    async def _banco_caido(peticion: Request, error: BancoNoDisponible) -> JSONResponse:
        """El banco no respondió.

        Se traduce a **503 Service Unavailable** y no a 500: el fallo no es de
        este servicio, es de una dependencia, y es transitorio. El cliente puede
        reintentar.
        """
        log.error("El banco-preguntas-service no respondió: %s", error)
        return _problema(
            peticion,
            status.HTTP_503_SERVICE_UNAVAILABLE,
            "banco-no-disponible",
            "El banco de preguntas no está disponible",
            f"{error} Vuelve a intentarlo en unos segundos.",
        )

    @app.exception_handler(Exception)
    async def _inesperado(peticion: Request, error: Exception) -> JSONResponse:
        """Red de seguridad para lo que no se previó.

        Devuelve un mensaje genérico a propósito: un stack trace en la respuesta
        le cuenta a cualquiera cómo está construido el servicio. El detalle queda
        en el log del servidor, que es donde hace falta.
        """
        log.exception("Error inesperado procesando la petición")
        return _problema(
            peticion,
            status.HTTP_500_INTERNAL_SERVER_ERROR,
            "error-interno",
            "Error interno del servidor",
            "Ocurrió un error inesperado. Si persiste, revisa los logs del servicio.",
        )
