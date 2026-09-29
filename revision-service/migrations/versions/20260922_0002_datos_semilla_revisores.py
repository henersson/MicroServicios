"""Datos semilla: tres revisores de la Facultad de Ingenieria.

Van en su propia migracion a proposito: en un despliegue real bastaria con no
aplicarla para tener el esquema sin los datos de demostracion.

Son tres y no uno porque con un solo revisor no se puede ver funcionar el
AsignadorRevisor: hacen falta al menos dos para que haya reparto de carga, y un
tercero para que el desempate por antiguedad sea observable.

Los identificadores son fijos y no aleatorios, para que las pruebas de Postman y
los ejemplos de la documentacion siempre apunten a los mismos revisores. El
primero coincide con el revisor que aparece en los ejemplos de
contracts/events/, de modo que el contrato y la base de datos cuenten la misma
historia.

Revision ID: 0002_semilla
Revises: 0001_esquema
Create Date: 2026-09-22
"""

from __future__ import annotations

from typing import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002_semilla"
down_revision: str | None = "0001_esquema"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# El orden de registro importa: es el criterio de desempate del AsignadorRevisor
# cuando dos revisores tienen la misma carga. Marta va primero, asi que con todo
# a cero es la que recibe la primera pregunta.
REVISORES = [
    {
        "revisor_id": "b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e",
        "nombre": "Marta Liliana Torres",
        "correo": "mtorres@unicauca.edu.co",
        "registrado_en": "2026-08-01T08:00:00+00:00",
    },
    {
        "revisor_id": "c8d9e0f1-2a3b-4c5d-9e6f-7a8b9c0d1e2f",
        "nombre": "Carlos Andres Munoz",
        "correo": "camunoz@unicauca.edu.co",
        "registrado_en": "2026-08-05T08:00:00+00:00",
    },
    {
        "revisor_id": "d9e0f1a2-3b4c-4d5e-8f6a-7b8c9d0e1f2a",
        "nombre": "Diana Patricia Rojas",
        "correo": "dprojas@unicauca.edu.co",
        "registrado_en": "2026-08-12T08:00:00+00:00",
    },
]


def upgrade() -> None:
    tabla = sa.table(
        "revisores",
        sa.column("revisor_id", sa.dialects.postgresql.UUID(as_uuid=False)),
        sa.column("nombre", sa.String),
        sa.column("correo", sa.String),
        sa.column("registrado_en", sa.DateTime(timezone=True)),
        sa.column("activo", sa.Boolean),
    )
    op.bulk_insert(
        tabla,
        [{**revisor, "activo": True} for revisor in REVISORES],
    )


def downgrade() -> None:
    correos = ", ".join(f"'{revisor['correo']}'" for revisor in REVISORES)
    op.execute(f"DELETE FROM revisores WHERE correo IN ({correos})")
