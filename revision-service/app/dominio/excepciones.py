"""Excepciones del dominio del BC Ciclo de Vida y Revisión.

Todas llevan el mensaje en español porque llegan tal cual al usuario a través
del manejador global de errores de la capa de interfaces, que las traduce a
Problem Details (RFC 7807) con el mismo formato que el banco-preguntas-service.

El dominio no conoce HTTP: quién decide el código de estado es la capa de
interfaces, según la subclase concreta que se haya lanzado.
"""

from __future__ import annotations


class ExcepcionDominio(Exception):
    """Raíz de las excepciones de negocio de este contexto."""

    def __init__(self, mensaje: str, errores: list[str] | None = None) -> None:
        super().__init__(mensaje)
        self.mensaje = mensaje
        # Detalles adicionales, uno por regla incumplida. Puede ir vacía.
        self.errores: list[str] = list(errores) if errores else []


class ReglaDeNegocioViolada(ExcepcionDominio):
    """El contenido enviado incumple una regla del ciclo de revisión.

    La capa de interfaces la traduce a **400 Bad Request**: la petición está mal
    y hay que cambiarla.
    """


class AccesoNoAutorizado(ExcepcionDominio):
    """Quien hace la operación no tiene derecho a hacerla.

    Cubre las dos formas de no tenerlo: que el rol no lo permita, y que el
    revisor no sea el asignado a esta revisión concreta.

    La capa de interfaces la traduce a **403 Forbidden**. Nótese la diferencia
    con un 401: aquí sí sabemos quién es (llegó en las cabeceras
    ``X-Usuario-Id`` y ``X-Usuario-Rol``), simplemente no puede hacer esto.
    """


class RecursoNoEncontrado(ExcepcionDominio):
    """No existe la revisión, el revisor o la pregunta que se pide.

    La capa de interfaces la traduce a **404 Not Found**.
    """


class TransicionInvalida(ExcepcionDominio):
    """El cambio de estado choca con el estado actual de la revisión.

    La capa de interfaces la traduce a **409 Conflict**: la petición está bien
    formada, pero no cabe en el momento en que llega.
    """


class ConflictoDeDatos(ExcepcionDominio):
    """Se intenta crear algo que ya existe (un revisor o una revisión repetida).

    La capa de interfaces la traduce a **409 Conflict**.
    """
