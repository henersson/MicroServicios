"""Arranque del revision-service: API REST y consumidor de eventos.

El servicio hace dos cosas a la vez: atiende peticiones HTTP y consume la cola
``revision.preguntas-enviadas``. Las dos viven en el mismo proceso y en el mismo
bucle de eventos, que es lo natural con FastAPI y aio-pika y evita coordinar dos
despliegues para un microservicio que es uno solo.
"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from uuid import UUID

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.infraestructura.config.bucle_eventos import ajustar_bucle_para_windows
from app.infraestructura.config.configuracion import Configuracion, obtener_configuracion
from app.infraestructura.grpc_cliente.cliente_banco import ClienteBancoGrpc
from app.infraestructura.mensajeria.consumidor import ConsumidorPreguntasEnviadas
from app.infraestructura.mensajeria.publicador import PublicadorEventosRabbitMQ
from app.infraestructura.persistencia.unidad_de_trabajo_sql import (
    crear_fabrica_sesiones,
    crear_motor,
)
from app.interfaces.api import router_revisiones, router_revisores
from app.interfaces.api.dependencias import Contenedor
from app.interfaces.api.manejo_errores import registrar_manejadores

# Antes de que uvicorn cree su bucle: en Windows, psycopg async no funciona
# sobre el ProactorEventLoop que viene por defecto. En Linux no hace nada.
ajustar_bucle_para_windows()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
)
log = logging.getLogger("revision-service")

DESCRIPCION = """
Microservicio del Bounded Context **Ciclo de Vida y Revisión** del sistema de
gestión de preguntas de selección múltiple para las Pruebas Saber Pro.

Gestiona la **revisión por pares**: asigna revisor a cada pregunta que llega,
recoge el formato de evaluación y las observaciones, y devuelve la decisión al
banco de preguntas.

### Cómo se integra

| Con quién | Cómo | Para qué |
|---|---|---|
| `banco-preguntas-service` | **RabbitMQ** (consume) | Recibe `PreguntaEnviadaARevision` |
| `banco-preguntas-service` | **gRPC** (llama) | `ObtenerPregunta`, para traerse el contenido a evaluar |
| `banco-preguntas-service` | **RabbitMQ** (publica) | `RevisorAsignado`, `PreguntaAprobadaTecnicamente`, `PreguntaRechazadaPorPares` |

### Autenticación

No hay autenticación real (queda como trabajo futuro). El usuario se simula con
dos cabeceras **obligatorias**:

- `X-Usuario-Id`: UUID del usuario.
- `X-Usuario-Rol`: `AUTOR`, `REVISOR`, `ADMINISTRADOR`, `DOCENTE`, `ESTUDIANTE`
  o `COORDINADOR`.

### Errores

Todos siguen el formato **Problem Details (RFC 7807)**, con un campo adicional
`errores[]`. Es exactamente el mismo formato que usa el `banco-preguntas-service`.

| Código | Cuándo |
|---|---|
| 400 | Se incumple una regla del ciclo de revisión (invariantes 10 y 11) |
| 403 | El rol no aplica, o no eres el revisor asignado a esa revisión |
| 404 | La revisión o el revisor no existen |
| 409 | La revisión ya está decidida, o el revisor ya estaba registrado |
| 503 | El banco de preguntas no responde |
"""


def construir_contenedor(configuracion: Configuracion) -> Contenedor:
    """Arma las dependencias caras: motor de base de datos, publicador y gRPC."""
    motor = crear_motor(configuracion.database_url, echo=configuracion.sql_echo)
    fabrica_sesiones = crear_fabrica_sesiones(motor)

    publicador = PublicadorEventosRabbitMQ(
        url=configuracion.rabbitmq_url,
        nombre_exchange=configuracion.exchange_eventos,
    )
    cliente_banco = ClienteBancoGrpc(
        direccion=configuracion.banco_grpc_addr,
        timeout_segundos=configuracion.banco_grpc_timeout_segundos,
        reintentos=configuracion.banco_grpc_reintentos,
        espera_segundos=configuracion.banco_grpc_espera_segundos,
    )

    contenedor = Contenedor(
        configuracion=configuracion,
        fabrica_sesiones=fabrica_sesiones,
        publicador=publicador,
        cliente_banco=cliente_banco,
    )
    contenedor.motor = motor  # type: ignore[attr-defined]
    return contenedor


@asynccontextmanager
async def ciclo_de_vida(app: FastAPI):
    """Abre y cierra ordenadamente todo lo que el servicio necesita.

    El orden de arranque importa: primero el publicador y el canal gRPC, y solo
    después el consumidor. Si el consumidor arrancara antes, podría recibir un
    mensaje y encontrarse sin forma de llamar al banco ni de publicar la
    respuesta.
    """
    configuracion = obtener_configuracion()
    log.info(
        "Arrancando %s (entorno=%s, banco gRPC=%s)",
        configuracion.nombre_servicio,
        configuracion.entorno,
        configuracion.banco_grpc_addr,
    )

    contenedor = construir_contenedor(configuracion)
    app.state.contenedor = contenedor
    app.state.configuracion = configuracion

    await contenedor.publicador.conectar()
    await contenedor.cliente_banco.conectar()

    consumidor: ConsumidorPreguntasEnviadas | None = None
    if configuracion.consumidor_activo:

        async def manejar(event_id: UUID, pregunta_id: UUID, autor_id: UUID):
            # Una unidad de trabajo nueva por mensaje, igual que por petición.
            caso_de_uso = contenedor.iniciar_revision()
            return await caso_de_uso.ejecutar(event_id, pregunta_id, autor_id)

        consumidor = ConsumidorPreguntasEnviadas(configuracion, manejar)
        await consumidor.iniciar()
        app.state.consumidor = consumidor
    else:
        log.warning("El consumidor de RabbitMQ está desactivado (CONSUMIDOR_ACTIVO=false).")

    log.info("%s listo.", configuracion.nombre_servicio)

    try:
        yield
    finally:
        log.info("Apagando %s...", configuracion.nombre_servicio)
        if consumidor is not None:
            await consumidor.detener()
        await contenedor.cliente_banco.cerrar()
        await contenedor.publicador.cerrar()
        await contenedor.motor.dispose()  # type: ignore[attr-defined]
        log.info("%s apagado.", configuracion.nombre_servicio)


class RespuestaJsonUtf8(JSONResponse):
    """Respuesta JSON que declara ``charset=utf-8`` en el Content-Type.

    El RFC 8259 ya obliga a que todo JSON sea UTF-8, así que FastAPI no lo
    declara. Se declara igualmente porque hay clientes que, sin ese parámetro,
    decodifican el cuerpo como ISO-8859-1 y destrozan los acentos del español.
    """

    media_type = "application/json; charset=utf-8"


def crear_app() -> FastAPI:
    """Fábrica de la aplicación. Los tests la usan para armar variantes."""
    app = FastAPI(
        title="Revisión de Preguntas — API REST",
        description=DESCRIPCION,
        version="1.0.0",
        lifespan=ciclo_de_vida,
        docs_url="/docs",
        redoc_url="/redoc",
        openapi_url="/openapi.json",
        contact={
            "name": "Henersson — Arquitectura de Microservicios, Universidad del Cauca"
        },
        license_info={"name": "Uso académico"},
        default_response_class=RespuestaJsonUtf8,
    )

    registrar_manejadores(app)
    app.include_router(router_revisores.router)
    app.include_router(router_revisiones.router)

    @app.get(
        "/health",
        tags=["Operación"],
        summary="Estado del servicio",
        description=(
            "Sonda de vida que usan Docker y el `depends_on` del compose. "
            "Equivale al `/actuator/health` del banco-preguntas-service."
        ),
    )
    async def health() -> dict:
        return {
            "status": "UP",
            "servicio": app.state.configuracion.nombre_servicio
            if hasattr(app.state, "configuracion")
            else "revision-service",
        }

    return app


app = crear_app()
