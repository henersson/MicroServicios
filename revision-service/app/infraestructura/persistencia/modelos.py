"""Modelos de SQLAlchemy: el reflejo del dominio en la base de datos.

**No son el dominio.** Viven solo en la capa de infraestructura y
``mapeadores.py`` traduce entre unos y otros. Mantenerlos separados cuesta un
mapeador, pero evita que las necesidades del ORM (constructor vacío, atributos
mutables, relaciones perezosas) deformen el modelo de dominio, y garantiza que
nunca se filtre un modelo de persistencia a la API.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID as PgUUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    """Base declarativa. Alembic la usa como metadata para las migraciones."""


class RevisionORM(Base):
    """Tabla ``revisiones``: el Aggregate Root ``Revision``."""

    __tablename__ = "revisiones"

    revision_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True)
    pregunta_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False, index=True)

    # Invariante 9: NOT NULL. La base de datos también impide que exista una
    # revisión sin revisor, por si alguien escribiera saltándose el agregado.
    revisor_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False, index=True)

    estado: Mapped[str] = mapped_column(String(20), nullable=False, index=True)

    # El snapshot va como JSONB y no en tablas aparte: es un Value Object que se
    # escribe una vez y se lee entero, nunca se consulta por sus partes. Darle
    # tablas propias sería normalizar algo que jamás se va a filtrar.
    snapshot: Mapped[dict] = mapped_column(JSONB, nullable=False)

    decision: Mapped[str | None] = mapped_column(String(20))
    fecha_decision: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    creada_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    actualizada_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    criterios: Mapped[list["CriterioORM"]] = relationship(
        back_populates="revision",
        cascade="all, delete-orphan",
        lazy="selectin",
        order_by="CriterioORM.criterio",
    )
    observaciones: Mapped[list["ObservacionORM"]] = relationship(
        back_populates="revision",
        cascade="all, delete-orphan",
        lazy="selectin",
        order_by="ObservacionORM.fecha",
    )

    __table_args__ = (
        CheckConstraint(
            "estado IN ('ASIGNADA', 'EN_EVALUACION', 'APROBADA', 'RECHAZADA')",
            name="ck_revisiones_estado",
        ),
        CheckConstraint(
            "decision IS NULL OR decision IN ('APROBAR', 'RECHAZAR')",
            name="ck_revisiones_decision",
        ),
    )


class CriterioORM(Base):
    """Tabla ``revision_criterios``: un criterio puntuado del formato.

    Se guarda en filas y no como JSON porque sí se consulta por sus partes: es
    lo que permite responder "¿qué criterio suspenden más las preguntas de
    Pensamiento Algorítmico?" con SQL corriente.
    """

    __tablename__ = "revision_criterios"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    revision_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True),
        ForeignKey("revisiones.revision_id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    criterio: Mapped[str] = mapped_column(String(50), nullable=False)
    puntaje: Mapped[int] = mapped_column(Integer, nullable=False)

    revision: Mapped[RevisionORM] = relationship(back_populates="criterios")

    __table_args__ = (
        UniqueConstraint("revision_id", "criterio", name="uq_criterio_por_revision"),
        CheckConstraint("puntaje BETWEEN 1 AND 5", name="ck_criterios_puntaje"),
    )


class ObservacionORM(Base):
    """Tabla ``revision_observaciones``: una observación del revisor.

    Solo crece. No hay ningún camino en el código que actualice o borre una
    fila: son la evidencia de la revisión (invariante 11).
    """

    __tablename__ = "revision_observaciones"

    observacion_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True)
    revision_id: Mapped[UUID] = mapped_column(
        PgUUID(as_uuid=True),
        ForeignKey("revisiones.revision_id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    revisor_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), nullable=False)
    texto: Mapped[str] = mapped_column(Text, nullable=False)
    fecha: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    revision: Mapped[RevisionORM] = relationship(back_populates="observaciones")


class RevisorORM(Base):
    """Tabla ``revisores``: la proyección local ``RevisorDisponible``."""

    __tablename__ = "revisores"

    revisor_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True)
    nombre: Mapped[str] = mapped_column(String(200), nullable=False)
    correo: Mapped[str] = mapped_column(String(200), nullable=False, unique=True)
    registrado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    # Dar de baja en vez de borrar: así no se pierden las revisiones que ya hizo.
    activo: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)


class EventoProcesadoORM(Base):
    """Tabla ``eventos_procesados``: la idempotencia del consumidor.

    La clave primaria sobre ``event_id`` es la garantía de verdad: aunque dos
    hilos procesaran el mismo evento a la vez, el segundo fallaría al insertar y
    su transacción se desharía entera, cambio incluido.
    """

    __tablename__ = "eventos_procesados"

    event_id: Mapped[UUID] = mapped_column(PgUUID(as_uuid=True), primary_key=True)
    event_type: Mapped[str] = mapped_column(String(100), nullable=False, index=True)
    procesado_en: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
