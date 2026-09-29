"""Entorno de Alembic para el revision-service.

Corre las migraciones sobre el **motor asíncrono** de SQLAlchemy, que es el
mismo que usa el servicio. Alembic es síncrono por dentro, así que se apoya en
``connection.run_sync`` para ejecutar sus pasos dentro de la conexión async.

La URL no se lee de ``alembic.ini``: se toma de la configuración del servicio,
que a su vez la toma de las variables de entorno. Así no hay credenciales en un
archivo versionado y las migraciones funcionan igual en local y en Docker.
"""

from __future__ import annotations

import asyncio
from logging.config import fileConfig

from alembic import context
from sqlalchemy.ext.asyncio import async_engine_from_config
from sqlalchemy.pool import NullPool

from app.infraestructura.config.bucle_eventos import ajustar_bucle_para_windows
from app.infraestructura.config.configuracion import obtener_configuracion
from app.infraestructura.persistencia.modelos import Base

# Antes de crear ningún bucle: en Windows, psycopg async no funciona sobre el
# ProactorEventLoop que viene por defecto.
ajustar_bucle_para_windows()

config = context.config

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

# La metadata de los modelos: es lo que Alembic compara con la base de datos
# cuando se autogenera una revisión.
target_metadata = Base.metadata

# La URL real, desde la configuración del servicio.
config.set_main_option("sqlalchemy.url", obtener_configuracion().database_url)


def run_migrations_offline() -> None:
    """Genera el SQL sin conectarse, para poder revisarlo antes de aplicarlo."""
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def _ejecutar_migraciones(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        # Detecta también los cambios de tipo de columna, que por defecto
        # Alembic ignora y son justo los que más silenciosamente rompen cosas.
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


async def run_migrations_online() -> None:
    """Aplica las migraciones contra la base de datos."""
    motor = async_engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        # NullPool: las migraciones son un proceso corto que se conecta una vez.
        # Un pool aquí solo dejaría conexiones colgando al terminar.
        poolclass=NullPool,
    )

    async with motor.connect() as conexion:
        await conexion.run_sync(_ejecutar_migraciones)

    await motor.dispose()


if context.is_offline_mode():
    run_migrations_offline()
else:
    asyncio.run(run_migrations_online())
