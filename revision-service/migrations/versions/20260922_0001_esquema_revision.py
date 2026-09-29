"""Esquema inicial del revision-service.

Base de datos PROPIA y exclusiva de este microservicio: ningún otro servicio se
conecta aquí. Si el banco necesita saber algo de una revisión, se entera por los
eventos que este servicio publica.

Revision ID: 0001_esquema
Revises:
Create Date: 2026-09-22
"""

from __future__ import annotations

from typing import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001_esquema"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # ── Aggregate Root: Revision ────────────────────────────────────────────
    op.create_table(
        "revisiones",
        sa.Column("revision_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("pregunta_id", postgresql.UUID(as_uuid=True), nullable=False),
        # Invariante 9: NOT NULL. La base de datos también impide que exista una
        # revisión sin revisor, por si alguien escribiera saltándose el agregado.
        sa.Column("revisor_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("estado", sa.String(20), nullable=False),
        # El snapshot va como JSONB: es un Value Object que se escribe una vez y
        # se lee entero, nunca se consulta por sus partes.
        sa.Column("snapshot", postgresql.JSONB, nullable=False),
        sa.Column("decision", sa.String(20), nullable=True),
        sa.Column("fecha_decision", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "creada_en",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "actualizada_en",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.CheckConstraint(
            "estado IN ('ASIGNADA', 'EN_EVALUACION', 'APROBADA', 'RECHAZADA')",
            name="ck_revisiones_estado",
        ),
        sa.CheckConstraint(
            "decision IS NULL OR decision IN ('APROBAR', 'RECHAZAR')",
            name="ck_revisiones_decision",
        ),
        comment=(
            "Aggregate Root del BC Ciclo de Vida y Revision. "
            "Garantiza las invariantes 9 a 11."
        ),
    )
    op.create_index("ix_revisiones_pregunta", "revisiones", ["pregunta_id"])
    op.create_index("ix_revisiones_revisor", "revisiones", ["revisor_id"])
    op.create_index("ix_revisiones_estado", "revisiones", ["estado"])
    # El AsignadorRevisor cuenta revisiones activas por revisor: este indice
    # compuesto cubre exactamente esa consulta.
    op.create_index(
        "ix_revisiones_revisor_estado", "revisiones", ["revisor_id", "estado"]
    )

    # ── Value Object FormatoEvaluacion, criterio a criterio ─────────────────
    op.create_table(
        "revision_criterios",
        sa.Column("id", sa.Integer, primary_key=True, autoincrement=True),
        sa.Column(
            "revision_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("revisiones.revision_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("criterio", sa.String(50), nullable=False),
        sa.Column("puntaje", sa.Integer, nullable=False),
        sa.UniqueConstraint("revision_id", "criterio", name="uq_criterio_por_revision"),
        sa.CheckConstraint("puntaje BETWEEN 1 AND 5", name="ck_criterios_puntaje"),
        comment="Criterios puntuados del formato de evaluacion (1 a 5).",
    )
    op.create_index("ix_criterios_revision", "revision_criterios", ["revision_id"])

    # ── Value Object Observacion (invariante 11) ────────────────────────────
    op.create_table(
        "revision_observaciones",
        sa.Column("observacion_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "revision_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("revisiones.revision_id", ondelete="CASCADE"),
            nullable=False,
        ),
        # Invariante 11: toda observacion queda asociada a su revisor y su fecha.
        sa.Column("revisor_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("texto", sa.Text, nullable=False),
        sa.Column("fecha", sa.DateTime(timezone=True), nullable=False),
        comment=(
            "Observaciones del revisor. Esta tabla solo crece: son la evidencia "
            "de la revision y lo que vera el autor si la pregunta se rechaza."
        ),
    )
    op.create_index("ix_observaciones_revision", "revision_observaciones", ["revision_id"])

    # ── Proyeccion local RevisorDisponible ──────────────────────────────────
    op.create_table(
        "revisores",
        sa.Column("revisor_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("nombre", sa.String(200), nullable=False),
        sa.Column("correo", sa.String(200), nullable=False, unique=True),
        sa.Column(
            "registrado_en",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column("activo", sa.Boolean, nullable=False, server_default=sa.true()),
        comment=(
            "Proyeccion local de revisores, no el agregado Usuario. Hoy se alimenta "
            "por REST y con datos semilla; en el futuro, del evento UsuarioRegistrado."
        ),
    )

    # ── Cola de espera de la invariante 9 ───────────────────────────────────
    op.create_table(
        "solicitudes_pendientes",
        sa.Column("solicitud_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column(
            "pregunta_id", postgresql.UUID(as_uuid=True), nullable=False, unique=True
        ),
        # El snapshot viaja con la solicitud para poder asignarla mas tarde sin
        # volver a llamar al banco, aunque el banco este caido en ese momento.
        sa.Column("snapshot", postgresql.JSONB, nullable=False),
        sa.Column("recibida_en", sa.DateTime(timezone=True), nullable=False),
        sa.Column("event_id_origen", postgresql.UUID(as_uuid=True), nullable=True),
        comment=(
            "Preguntas que esperan revisor. Se asignan de la mas antigua a la mas "
            "reciente cuando se registra un revisor (invariante 9)."
        ),
    )
    # El indice por fecha es el que sostiene el orden FIFO del desatasco.
    op.create_index(
        "ix_pendientes_recibida_en", "solicitudes_pendientes", ["recibida_en"]
    )

    # ── Idempotencia del consumidor ─────────────────────────────────────────
    op.create_table(
        "eventos_procesados",
        sa.Column("event_id", postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column("event_type", sa.String(100), nullable=False),
        sa.Column(
            "procesado_en",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        comment=(
            "RabbitMQ entrega al menos una vez, asi que el mismo evento puede llegar "
            "dos veces. La clave primaria sobre event_id es lo que hace idempotente "
            "al consumidor."
        ),
    )
    op.create_index("ix_eventos_procesados_tipo", "eventos_procesados", ["event_type"])


def downgrade() -> None:
    # En orden inverso, para respetar las claves foraneas.
    op.drop_table("eventos_procesados")
    op.drop_table("solicitudes_pendientes")
    op.drop_table("revisores")
    op.drop_table("revision_observaciones")
    op.drop_table("revision_criterios")
    op.drop_table("revisiones")
