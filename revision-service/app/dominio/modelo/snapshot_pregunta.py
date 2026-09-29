"""Value Object ``SnapshotPregunta``: copia local de la pregunta a evaluar."""

from __future__ import annotations

from dataclasses import dataclass, field
from uuid import UUID

from app.dominio.excepciones import ReglaDeNegocioViolada
from app.dominio.modelo.enums import NivelDificultad


@dataclass(frozen=True, slots=True)
class OpcionSnapshot:
    """Una de las opciones de respuesta, tal como llegó del banco."""

    texto: str
    es_correcta: bool

    def __post_init__(self) -> None:
        if not self.texto or not self.texto.strip():
            raise ReglaDeNegocioViolada("El texto de una opción no puede estar vacío.")
        object.__setattr__(self, "texto", self.texto.strip())


@dataclass(frozen=True, slots=True)
class SnapshotPregunta:
    """Value Object: el contenido de la pregunta en el momento de crear la revisión.

    Es una **copia**, no una referencia, y esa es la decisión importante. Se
    obtiene una sola vez por gRPC (``ObtenerPregunta``) al crear la revisión, y
    a partir de ahí este contexto no vuelve a preguntarle nada al banco.

    Tres razones:

    1. El revisor evalúa una versión concreta del texto. Si la pregunta cambiara
       mientras la revisa, su puntaje dejaría de significar nada.
    2. El ``revision-service`` sigue funcionando aunque el banco esté caído: solo
       depende de él en el instante de crear la revisión.
    3. Cada microservicio es dueño de sus datos. El snapshot es *nuestro* dato,
       derivado de un contrato publicado, no una ventana a la base del banco.

    Incluye ``es_correcta`` porque el revisor tiene que poder comprobar el
    criterio ``UNICIDAD_RESPUESTA_CORRECTA``: sin saber cuál es la correcta, ese
    criterio no se puede puntuar.
    """

    pregunta_id: UUID
    autor_id: UUID
    contexto: str
    pregunta_directa: str
    opciones: tuple[OpcionSnapshot, ...]
    justificacion: str
    competencia_codigo: str
    competencia_nombre: str
    tema: str
    subtema: str
    nivel_dificultad: NivelDificultad
    estado_en_banco: str
    bibliografia: tuple[str, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if not self.contexto or not self.contexto.strip():
            raise ReglaDeNegocioViolada(
                "El snapshot no tiene contexto: la pregunta llegó incompleta del banco."
            )
        if not self.pregunta_directa or not self.pregunta_directa.strip():
            raise ReglaDeNegocioViolada(
                "El snapshot no tiene pregunta directa: llegó incompleta del banco."
            )
        if not self.opciones:
            raise ReglaDeNegocioViolada(
                "El snapshot no tiene opciones: no hay nada que evaluar."
            )
        object.__setattr__(self, "opciones", tuple(self.opciones))
        object.__setattr__(self, "bibliografia", tuple(self.bibliografia or ()))

    @property
    def cantidad_opciones(self) -> int:
        return len(self.opciones)

    @property
    def cantidad_correctas(self) -> int:
        """Cuántas opciones están marcadas como correctas.

        El banco garantiza que sea 1 (su invariante 1), pero el revisor lo
        comprueba de todas formas: para eso existe el criterio
        ``UNICIDAD_RESPUESTA_CORRECTA``.
        """
        return sum(1 for opcion in self.opciones if opcion.es_correcta)
