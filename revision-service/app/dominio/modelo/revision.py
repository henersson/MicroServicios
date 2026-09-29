"""Aggregate Root ``Revision`` del BC Ciclo de Vida y Revisión."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from uuid import UUID, uuid4

from app.dominio.eventos.eventos import (
    EventoDominio,
    PreguntaAprobadaTecnicamente,
    PreguntaRechazadaPorPares,
    RevisorAsignado,
)
from app.dominio.excepciones import (
    AccesoNoAutorizado,
    ReglaDeNegocioViolada,
    TransicionInvalida,
)
from app.dominio.modelo.enums import CriterioEvaluacion, Decision, EstadoRevision
from app.dominio.modelo.formato_evaluacion import FormatoEvaluacion
from app.dominio.modelo.observacion import Observacion
from app.dominio.modelo.snapshot_pregunta import SnapshotPregunta

#: Promedio mínimo para poder aprobar (invariante 10). Es configurable: el valor
#: real llega desde la configuración del servicio, este es solo el de respaldo.
PROMEDIO_MINIMO_APROBACION_POR_DEFECTO = 3.0


@dataclass
class Revision:
    """**Aggregate Root** del BC Ciclo de Vida y Revisión. Garantiza las
    invariantes 9 a 11.

    Es el único punto por el que se modifica una revisión. No se tocan sus
    atributos desde fuera: se llama a operaciones con nombre de negocio
    (:meth:`guardar_formato`, :meth:`agregar_observacion`, :meth:`decidir`) y
    cada una comprueba antes lo que tenga que comprobar.

    Esta clase es **Python puro**: no importa FastAPI, SQLAlchemy, Pydantic,
    aio-pika ni grpc. El contrato de import-linter lo verifica en cada build,
    igual que ArchUnit hace en el banco.

    Invariantes
    -----------
    9. La ``Revision`` **nunca existe sin revisor**. Se garantiza en el
       constructor: sin ``revisor_id`` no se puede construir el objeto. Si al
       llegar ``PreguntaEnviadaARevision`` no hay revisores disponibles,
       ``AsignadorRevisor`` falla y no se crea ninguna revisión.
    10. No se aprueba ni se rechaza con el formato incompleto, y no se aprueba
        si el promedio está por debajo del mínimo. → :meth:`decidir`
    11. Toda observación queda asociada a su revisor y su fecha, y para rechazar
        tiene que existir al menos una. → ``Observacion`` y :meth:`decidir`

    Además, y de forma transversal: **solo el revisor asignado** evalúa, observa
    y decide.
    """

    revision_id: UUID
    pregunta_id: UUID
    revisor_id: UUID
    snapshot: SnapshotPregunta
    estado: EstadoRevision = EstadoRevision.ASIGNADA
    formato: FormatoEvaluacion = field(default_factory=FormatoEvaluacion.vacio)
    observaciones: list[Observacion] = field(default_factory=list)
    decision: Decision | None = None
    fecha_decision: datetime | None = None
    creada_en: datetime | None = None
    actualizada_en: datetime | None = None

    #: Eventos generados y todavía sin publicar. El caso de uso los recoge tras
    #: guardar y se los entrega al puerto ``PublicadorEventos``.
    eventos_pendientes: list[EventoDominio] = field(default_factory=list, repr=False)

    def __post_init__(self) -> None:
        # Invariante 9, aplicada en el único sitio donde puede garantizarse de
        # verdad: si no hay revisor, el objeto no llega a existir.
        if self.revisor_id is None:
            raise ReglaDeNegocioViolada(
                "Una revisión no puede existir sin revisor asignado. (Invariante 9)"
            )
        if self.pregunta_id is None:
            raise ReglaDeNegocioViolada("La revisión debe referirse a una pregunta.")
        if self.snapshot is None:
            raise ReglaDeNegocioViolada(
                "La revisión necesita el snapshot de la pregunta: sin él el revisor no "
                "tiene nada que evaluar."
            )

    # ─────────────────────────────────────────────────────────────────────────
    # Construcción
    # ─────────────────────────────────────────────────────────────────────────

    @classmethod
    def iniciar(
        cls,
        pregunta_id: UUID,
        revisor_id: UUID,
        snapshot: SnapshotPregunta,
        ahora: datetime,
        revision_id: UUID | None = None,
    ) -> "Revision":
        """Crea una revisión ya asignada y registra ``RevisorAsignado``.

        Ese evento es el que permite al banco pasar la pregunta de
        ``PENDIENTE_REVISION`` a ``EN_REVISION`` (ADR 1). Sin él, el banco no
        tendría forma de saber que la revisión empezó de verdad.
        """
        revision = cls(
            revision_id=revision_id or uuid4(),
            pregunta_id=pregunta_id,
            revisor_id=revisor_id,
            snapshot=snapshot,
            estado=EstadoRevision.ASIGNADA,
            creada_en=ahora,
            actualizada_en=ahora,
        )
        revision.eventos_pendientes.append(
            RevisorAsignado.crear(
                revision_id=revision.revision_id,
                pregunta_id=pregunta_id,
                revisor_id=revisor_id,
                ocurrido_en=ahora,
            )
        )
        return revision

    # ─────────────────────────────────────────────────────────────────────────
    # Operaciones de negocio
    # ─────────────────────────────────────────────────────────────────────────

    def guardar_formato(
        self, revisor_id: UUID, formato: FormatoEvaluacion, ahora: datetime
    ) -> None:
        """Guarda (o reemplaza) el formato de evaluación.

        Se puede guardar incompleto: el revisor puntúa lo que lleva y vuelve
        luego. Lo que no se puede es *decidir* con el formato incompleto.

        :raises AccesoNoAutorizado: si no es el revisor asignado
        :raises TransicionInvalida: si la revisión ya está decidida
        """
        self._exigir_revisor_asignado(revisor_id, "guardar el formato de evaluación")
        self._exigir_que_no_este_decidida("guardar el formato de evaluación")

        self.formato = formato
        self._marcar_en_evaluacion(ahora)

    def agregar_observacion(
        self, revisor_id: UUID, texto: str, ahora: datetime
    ) -> Observacion:
        """Agrega una observación. Las observaciones solo se añaden, nunca se
        editan ni se borran (invariante 11).

        :raises AccesoNoAutorizado: si no es el revisor asignado
        :raises TransicionInvalida: si la revisión ya está decidida
        """
        self._exigir_revisor_asignado(revisor_id, "agregar observaciones")
        self._exigir_que_no_este_decidida("agregar observaciones")

        observacion = Observacion.nueva(revisor_id=revisor_id, texto=texto, ahora=ahora)
        self.observaciones.append(observacion)
        self._marcar_en_evaluacion(ahora)
        return observacion

    def decidir(
        self,
        revisor_id: UUID,
        decision: Decision,
        ahora: datetime,
        promedio_minimo: float = PROMEDIO_MINIMO_APROBACION_POR_DEFECTO,
    ) -> None:
        """Cierra la revisión aprobando o rechazando la pregunta.

        Aquí se concentran las comprobaciones más importantes del agregado,
        porque esta decisión sale de este Bounded Context y cambia el estado de
        la pregunta en el banco:

        - **Invariante 10**: el formato tiene que estar completo, y para aprobar
          el promedio no puede estar por debajo del mínimo configurado.
        - **Invariante 11**: para rechazar tiene que haber al menos una
          observación. Un rechazo sin motivo deja al autor sin nada que
          corregir.

        Registra ``PreguntaAprobadaTecnicamente`` o ``PreguntaRechazadaPorPares``.

        :raises AccesoNoAutorizado: si no es el revisor asignado
        :raises TransicionInvalida: si la revisión ya está decidida
        :raises ReglaDeNegocioViolada: si incumple la invariante 10 u 11
        """
        self._exigir_revisor_asignado(revisor_id, "decidir sobre la revisión")
        self._exigir_que_no_este_decidida("decidir")

        # Invariante 10: sin formato completo no hay decisión, ni en un sentido
        # ni en el otro.
        if not self.formato.esta_completo():
            faltantes = self.formato.criterios_faltantes()
            raise ReglaDeNegocioViolada(
                f"No se puede decidir con el formato de evaluación incompleto: faltan "
                f"{len(faltantes)} de {len(CriterioEvaluacion)} criterios por puntuar. "
                "(Invariante 10)",
                [f"Falta puntuar el criterio {criterio.value}." for criterio in faltantes],
            )

        promedio = self.formato.promedio()

        if decision is Decision.APROBAR:
            # Invariante 10: el umbral de calidad.
            if promedio < promedio_minimo:
                raise ReglaDeNegocioViolada(
                    f"No se puede aprobar: el promedio del formato es {promedio} y el "
                    f"mínimo exigido es {promedio_minimo}. Si la pregunta no alcanza la "
                    "calidad requerida, la decisión correcta es rechazarla con "
                    "observaciones. (Invariante 10)"
                )
            self.estado = EstadoRevision.APROBADA
            self.eventos_pendientes.append(
                PreguntaAprobadaTecnicamente.crear(
                    revision_id=self.revision_id,
                    pregunta_id=self.pregunta_id,
                    revisor_id=self.revisor_id,
                    promedio=promedio,
                    ocurrido_en=ahora,
                )
            )
        else:
            # Invariante 11: un rechazo sin observaciones no le sirve al autor.
            if not self.observaciones:
                raise ReglaDeNegocioViolada(
                    "No se puede rechazar sin al menos una observación: el autor necesita "
                    "saber qué corregir. (Invariante 11)"
                )
            self.estado = EstadoRevision.RECHAZADA
            self.eventos_pendientes.append(
                PreguntaRechazadaPorPares.crear(
                    revision_id=self.revision_id,
                    pregunta_id=self.pregunta_id,
                    revisor_id=self.revisor_id,
                    observaciones=list(self.observaciones),
                    ocurrido_en=ahora,
                )
            )

        self.decision = decision
        self.fecha_decision = ahora
        self.actualizada_en = ahora

    # ─────────────────────────────────────────────────────────────────────────
    # Reglas transversales
    # ─────────────────────────────────────────────────────────────────────────

    def _exigir_revisor_asignado(self, revisor_id: UUID, accion: str) -> None:
        """Solo el revisor asignado evalúa, observa y decide.

        Que el rol sea REVISOR lo comprueba la capa de interfaces; que sea *este*
        revisor concreto es una regla del dominio, porque depende del estado del
        agregado.
        """
        if revisor_id != self.revisor_id:
            raise AccesoNoAutorizado(
                f"Solo el revisor asignado a esta revisión puede {accion}."
            )

    def _exigir_que_no_este_decidida(self, accion: str) -> None:
        if self.estado.es_final():
            raise TransicionInvalida(
                f"La revisión ya está {self.estado.value} y no se puede {accion}. "
                "Una decisión de revisión por pares no se deshace: si la pregunta se "
                "corrige y se reenvía, se abre una revisión nueva."
            )

    def _marcar_en_evaluacion(self, ahora: datetime) -> None:
        """ASIGNADA → EN_EVALUACION la primera vez que el revisor la toca."""
        if self.estado is EstadoRevision.ASIGNADA:
            self.estado = EstadoRevision.EN_EVALUACION
        self.actualizada_en = ahora

    # ─────────────────────────────────────────────────────────────────────────
    # Eventos de dominio
    # ─────────────────────────────────────────────────────────────────────────

    def extraer_eventos_pendientes(self) -> list[EventoDominio]:
        """Devuelve los eventos pendientes y los vacía.

        Lo llama el caso de uso después de guardar, para entregárselos al
        publicador exactamente una vez.
        """
        eventos = list(self.eventos_pendientes)
        self.eventos_pendientes.clear()
        return eventos

    # ─────────────────────────────────────────────────────────────────────────
    # Consultas
    # ─────────────────────────────────────────────────────────────────────────

    @property
    def esta_activa(self) -> bool:
        """¿Cuenta como carga de trabajo del revisor? Lo usa el AsignadorRevisor."""
        return self.estado.esta_activa()

    @property
    def promedio(self) -> float:
        return self.formato.promedio()

    def textos_observaciones(self) -> list[str]:
        return [observacion.texto for observacion in self.observaciones]

    def __eq__(self, otro: object) -> bool:
        """Dos revisiones son la misma si comparten identificador: es una Entity."""
        return isinstance(otro, Revision) and self.revision_id == otro.revision_id

    def __hash__(self) -> int:
        return hash(self.revision_id)
