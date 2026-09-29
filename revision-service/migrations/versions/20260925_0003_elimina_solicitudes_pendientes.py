"""Elimina la tabla solicitudes_pendientes.

La cola de espera de la invariante 9 se retira. La invariante sigue garantizada
donde de verdad importa: el constructor de Revision exige revisor, asi que una
revision no puede existir sin el. Si al llegar PreguntaEnviadaARevision no hay
ningun revisor disponible, AsignadorRevisor falla y el evento queda en la DLQ,
desde donde se puede reprocesar cuando haya revisores.

Revision ID: 0003_sin_pendientes
Revises: 0002_semilla
Create Date: 2026-09-25
"""

from __future__ import annotations

from typing import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects.postgresql import JSONB, UUID as PgUUID

revision: str = "0003_sin_pendientes"
down_revision: str | None = "0002_semilla"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_index("ix_pendientes_recibida_en", table_name="solicitudes_pendientes")
    op.drop_table("solicitudes_pendientes")


def downgrade() -> None:
    op.create_table(
        "solicitudes_pendientes",
        sa.Column("solicitud_id", PgUUID(as_uuid=True), primary_key=True),
        sa.Column("pregunta_id", PgUUID(as_uuid=True), nullable=False, unique=True),
        sa.Column("snapshot", JSONB, nullable=False),
        sa.Column("recibida_en", sa.DateTime(timezone=True), nullable=False),
        sa.Column("event_id_origen", PgUUID(as_uuid=True), nullable=True),
    )
    op.create_index(
        "ix_pendientes_recibida_en", "solicitudes_pendientes", ["recibida_en"]
    )
