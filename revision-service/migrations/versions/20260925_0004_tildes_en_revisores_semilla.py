"""Corrige los nombres de los revisores semilla con sus tildes reales.

La migracion 0002 los guardo sin tildes ("Carlos Andres Munoz"). Los datos se
guardan y se leen en UTF-8 sin ningun problema, asi que escribirlos bien es
solo cuestion de hacerlo: los nombres propios llevan tilde.

Solo cambian los nombres. Los identificadores y los correos no se tocan, porque
los usan la documentacion, la coleccion de Postman y los ejemplos de
contracts/events/.

Revision ID: 0004_tildes_revisores
Revises: 0003_sin_pendientes
Create Date: 2026-09-25
"""

from __future__ import annotations

from typing import Sequence

from alembic import op

revision: str = "0004_tildes_revisores"
down_revision: str | None = "0003_sin_pendientes"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# correo -> (nombre con tildes, nombre sin tildes que puso la 0002)
NOMBRES = {
    "mtorres@unicauca.edu.co": ("Marta Liliana Torres", "Marta Liliana Torres"),
    "camunoz@unicauca.edu.co": ("Carlos Andrés Muñoz", "Carlos Andres Munoz"),
    "dprojas@unicauca.edu.co": ("Diana Patricia Rojas", "Diana Patricia Rojas"),
}


def upgrade() -> None:
    for correo, (con_tildes, _) in NOMBRES.items():
        op.execute(
            "UPDATE revisores SET nombre = '%s' WHERE correo = '%s'"
            % (con_tildes.replace("'", "''"), correo)
        )


def downgrade() -> None:
    for correo, (_, sin_tildes) in NOMBRES.items():
        op.execute(
            "UPDATE revisores SET nombre = '%s' WHERE correo = '%s'"
            % (sin_tildes.replace("'", "''"), correo)
        )
