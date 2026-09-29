"""Cableado de dependencias de la API.

Aquí se arman los casos de uso con sus adaptadores concretos. Es el único sitio
donde se juntan las cuatro capas, y por eso es el único que las conoce a todas.
"""

from __future__ import annotations

from typing import Annotated

from fastapi import Depends, Request

from app.aplicacion.casosdeuso.evaluar_revision import (
    AgregarObservacionUseCase,
    ConsultarRevisionesUseCase,
    DecidirRevisionUseCase,
    GuardarFormatoUseCase,
)
from app.aplicacion.casosdeuso.gestionar_revisores import (
    ConsultarRevisoresUseCase,
    RegistrarRevisorUseCase,
)
from app.aplicacion.casosdeuso.iniciar_revision import IniciarRevisionUseCase
from app.dominio.servicios.asignador_revisor import AsignadorRevisor
from app.infraestructura.config.configuracion import Configuracion
from app.infraestructura.persistencia.unidad_de_trabajo_sql import UnidadDeTrabajoSQL


class Contenedor:
    """Contenedor de dependencias del servicio.

    Se construye una vez al arrancar (en el ``lifespan``) y vive mientras vive
    el proceso. Guarda lo caro de crear —el motor de base de datos, el canal
    gRPC, la conexión a RabbitMQ— y fabrica una unidad de trabajo nueva por cada
    petición o mensaje, que es lo que sí tiene que ser de usar y tirar.
    """

    def __init__(
        self,
        configuracion: Configuracion,
        fabrica_sesiones,
        publicador,
        cliente_banco,
    ) -> None:
        self.configuracion = configuracion
        self._fabrica_sesiones = fabrica_sesiones
        self.publicador = publicador
        self.cliente_banco = cliente_banco
        self.asignador = AsignadorRevisor()

    def nueva_uow(self) -> UnidadDeTrabajoSQL:
        """Una unidad de trabajo por petición: una sesión, una transacción."""
        return UnidadDeTrabajoSQL(self._fabrica_sesiones)

    # ── Casos de uso ────────────────────────────────────────────────────────

    def registrar_revisor(self) -> RegistrarRevisorUseCase:
        return RegistrarRevisorUseCase(uow=self.nueva_uow())

    def consultar_revisores(self) -> ConsultarRevisoresUseCase:
        return ConsultarRevisoresUseCase(uow=self.nueva_uow())

    def consultar_revisiones(self) -> ConsultarRevisionesUseCase:
        return ConsultarRevisionesUseCase(uow=self.nueva_uow())

    def guardar_formato(self) -> GuardarFormatoUseCase:
        return GuardarFormatoUseCase(uow=self.nueva_uow())

    def agregar_observacion(self) -> AgregarObservacionUseCase:
        return AgregarObservacionUseCase(uow=self.nueva_uow())

    def decidir_revision(self) -> DecidirRevisionUseCase:
        return DecidirRevisionUseCase(
            uow=self.nueva_uow(),
            publicador=self.publicador,
            promedio_minimo=self.configuracion.promedio_minimo_aprobacion,
        )

    def iniciar_revision(self) -> IniciarRevisionUseCase:
        return IniciarRevisionUseCase(
            uow=self.nueva_uow(),
            cliente_banco=self.cliente_banco,
            publicador=self.publicador,
            asignador=self.asignador,
        )


def obtener_contenedor(peticion: Request) -> Contenedor:
    """Recupera el contenedor que el ``lifespan`` dejó en el estado de la app."""
    return peticion.app.state.contenedor


ContenedorDep = Annotated[Contenedor, Depends(obtener_contenedor)]
