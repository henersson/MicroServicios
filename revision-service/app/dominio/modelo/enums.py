"""Value Objects de tipo enumerado del BC Ciclo de Vida y Revisión."""

from __future__ import annotations

from enum import Enum


class EstadoRevision(str, Enum):
    """Estado del ciclo de vida de una ``Revision``.

    La máquina de estados es deliberadamente sencilla, porque una revisión es
    una tarea, no un documento::

        ASIGNADA ──► EN_EVALUACION ──┬──► APROBADA   (estado final)
                                     └──► RECHAZADA  (estado final)

    El paso a ``EN_EVALUACION`` ocurre solo cuando el revisor toca la revisión
    por primera vez (guarda formato o agrega una observación). Sirve para
    distinguir "se la asignaron y no la ha mirado" de "está trabajando en ella",
    que es justo lo que necesita saber el ``AsignadorRevisor`` para repartir
    carga.

    Hereda de ``str`` para que se serialice sola a JSON y se guarde como texto
    en la base de datos, nunca como un ordinal: un ordinal se rompe en silencio
    el día que alguien reordene el enum.
    """

    ASIGNADA = "ASIGNADA"
    EN_EVALUACION = "EN_EVALUACION"
    APROBADA = "APROBADA"
    RECHAZADA = "RECHAZADA"

    def es_final(self) -> bool:
        """¿Ya se decidió? De APROBADA y RECHAZADA no se sale."""
        return self in (EstadoRevision.APROBADA, EstadoRevision.RECHAZADA)

    def esta_activa(self) -> bool:
        """¿Cuenta como carga de trabajo del revisor?

        Lo usa el ``AsignadorRevisor``: una revisión ya decidida no ocupa al
        revisor, aunque siga existiendo.
        """
        return not self.es_final()


class Decision(str, Enum):
    """Lo que el revisor decide al terminar su evaluación."""

    APROBAR = "APROBAR"
    RECHAZAR = "RECHAZAR"

    @classmethod
    def desde_texto(cls, texto: str | None) -> "Decision":
        from app.dominio.excepciones import ReglaDeNegocioViolada

        if not texto or not texto.strip():
            raise ReglaDeNegocioViolada("La decisión es obligatoria: APROBAR o RECHAZAR.")
        try:
            return cls(texto.strip().upper())
        except ValueError:
            raise ReglaDeNegocioViolada(
                f"Decisión '{texto}' desconocida. Valores válidos: APROBAR, RECHAZAR."
            ) from None


class CriterioEvaluacion(str, Enum):
    """Los 5 criterios del formato de evaluación por pares (sección 5.2).

    Son fijos y forman parte del contrato: el formato está completo cuando los
    cinco tienen puntaje, ni uno menos (invariante 10).
    """

    CLARIDAD_CONTEXTO = "CLARIDAD_CONTEXTO"
    PERTINENCIA_COMPETENCIA = "PERTINENCIA_COMPETENCIA"
    PLAUSIBILIDAD_DISTRACTORES = "PLAUSIBILIDAD_DISTRACTORES"
    UNICIDAD_RESPUESTA_CORRECTA = "UNICIDAD_RESPUESTA_CORRECTA"
    CALIDAD_JUSTIFICACION = "CALIDAD_JUSTIFICACION"

    @classmethod
    def desde_texto(cls, texto: str) -> "CriterioEvaluacion":
        from app.dominio.excepciones import ReglaDeNegocioViolada

        try:
            return cls(texto.strip().upper())
        except (ValueError, AttributeError):
            validos = ", ".join(c.value for c in cls)
            raise ReglaDeNegocioViolada(
                f"Criterio '{texto}' desconocido. Criterios válidos: {validos}."
            ) from None


class NivelDificultad(str, Enum):
    """Nivel de dificultad de la pregunta, tal como llega del banco por gRPC.

    Se replica aquí porque el snapshot lo necesita, pero es un valor del otro
    Bounded Context: este servicio no toma ninguna decisión con él, solo lo
    muestra al revisor.
    """

    BAJO = "BAJO"
    MEDIO = "MEDIO"
    ALTO = "ALTO"
