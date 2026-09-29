"""Value Object ``Observacion``: un comentario del revisor sobre la pregunta."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from uuid import UUID, uuid4

from app.dominio.excepciones import ReglaDeNegocioViolada

#: Longitud mínima de una observación. Un "mal" o un "ok" no le sirven de nada
#: al autor cuando reciba el rechazo y tenga que corregir.
LONGITUD_MINIMA_TEXTO = 10


@dataclass(frozen=True, slots=True)
class Observacion:
    """Value Object: una observación registrada por el revisor.

    **Invariante 11**: toda observación queda asociada a su revisor y a su
    fecha. No es burocracia: cuando la pregunta vuelva al autor con el rechazo,
    él tiene que poder ver quién dijo qué y cuándo, y el coordinador tiene que
    poder auditarlo.

    Las observaciones **no se editan ni se borran** una vez registradas: son la
    evidencia de la revisión. Por eso el objeto es inmutable y el agregado solo
    ofrece "agregar".
    """

    observacion_id: UUID
    revisor_id: UUID
    texto: str
    fecha: datetime

    def __post_init__(self) -> None:
        if self.revisor_id is None:
            raise ReglaDeNegocioViolada(
                "Toda observación debe indicar qué revisor la escribió. (Invariante 11)"
            )
        texto = (self.texto or "").strip()
        if len(texto) < LONGITUD_MINIMA_TEXTO:
            raise ReglaDeNegocioViolada(
                f"La observación es demasiado corta: tiene {len(texto)} caracteres y el "
                f"mínimo es {LONGITUD_MINIMA_TEXTO}. El autor necesita saber qué corregir."
            )
        if self.fecha is None:
            raise ReglaDeNegocioViolada(
                "Toda observación debe tener fecha. (Invariante 11)"
            )
        object.__setattr__(self, "texto", texto)

    @classmethod
    def nueva(cls, revisor_id: UUID, texto: str, ahora: datetime) -> "Observacion":
        """Crea una observación con identificador propio."""
        return cls(
            observacion_id=uuid4(),
            revisor_id=revisor_id,
            texto=texto,
            fecha=ahora,
        )
