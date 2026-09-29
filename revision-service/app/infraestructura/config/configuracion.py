"""Configuración del revision-service, toda por variables de entorno."""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Configuracion(BaseSettings):
    """Parámetros del servicio.

    Nada de IPs ni de ``localhost`` quemados en el código (sección 9 del
    contexto): todo sale de variables de entorno, con valores por defecto que
    solo sirven para desarrollo local contra la infraestructura en Docker.

    En Docker se sobrescriben desde ``docker-compose.yml``, donde los nombres de
    host son los de los contenedores (``postgres-revision``, ``rabbitmq``,
    ``banco-preguntas-service``).
    """

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ── Identidad ───────────────────────────────────────────────────────────
    nombre_servicio: str = "revision-service"
    entorno: str = Field(default="local", alias="ENTORNO")

    # ── Base de datos PROPIA de este microservicio ──────────────────────────
    # Formato SQLAlchemy con el driver asíncrono de psycopg 3.
    database_url: str = Field(
        default="postgresql+psycopg://revision:revision@localhost:5434/revision",
        alias="REVISION_DATABASE_URL",
    )
    sql_echo: bool = Field(default=False, alias="SQL_ECHO")

    # ── RabbitMQ ────────────────────────────────────────────────────────────
    rabbitmq_url: str = Field(
        default="amqp://saberpro:saberpro@localhost:5672/",
        alias="RABBITMQ_URL",
    )
    exchange_eventos: str = Field(default="saberpro.eventos", alias="EVENTOS_EXCHANGE")
    exchange_dlx: str = Field(default="saberpro.eventos.dlx", alias="EVENTOS_DLX")
    cola_preguntas_enviadas: str = "revision.preguntas-enviadas"

    # ── Cliente gRPC hacia el banco-preguntas-service ───────────────────────
    banco_grpc_addr: str = Field(default="localhost:9091", alias="BANCO_GRPC_ADDR")
    #: Timeout de cada llamada gRPC, en segundos.
    banco_grpc_timeout_segundos: float = Field(
        default=3.0, alias="BANCO_GRPC_TIMEOUT_SEGUNDOS"
    )
    #: Intentos de la llamada gRPC dentro del propio cliente.
    banco_grpc_reintentos: int = Field(default=2, alias="BANCO_GRPC_REINTENTOS")
    #: Espera fija entre intentos, en segundos.
    banco_grpc_espera_segundos: float = Field(
        default=1.0, alias="BANCO_GRPC_ESPERA_SEGUNDOS"
    )

    # ── API ─────────────────────────────────────────────────────────────────
    http_port: int = Field(default=8082, alias="REVISION_HTTP_PORT")

    # ── Reglas de negocio configurables ─────────────────────────────────────
    #: Invariante 10: promedio mínimo del formato para poder aprobar.
    promedio_minimo_aprobacion: float = Field(
        default=3.0, alias="REVISION_PROMEDIO_MINIMO_APROBACION"
    )

    #: Si es True, el consumidor de RabbitMQ arranca junto con la API. Se apaga
    #: en los tests y cuando se quiere depurar solo la API.
    consumidor_activo: bool = Field(default=True, alias="CONSUMIDOR_ACTIVO")


@lru_cache
def obtener_configuracion() -> Configuracion:
    """Configuración del proceso, leída una sola vez.

    La caché no es por rendimiento: es para que todo el servicio vea exactamente
    los mismos valores durante toda su vida, aunque alguien cambie una variable
    de entorno a mitad de ejecución.
    """
    return Configuracion()
