"""Domain Service ``AsignadorRevisor``: a quién le toca revisar."""

from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID

from app.dominio.excepciones import ReglaDeNegocioViolada
from app.dominio.modelo.revisor_disponible import RevisorDisponible


@dataclass(frozen=True, slots=True)
class CargaRevisor:
    """Cuántas revisiones activas tiene un revisor ahora mismo.

    "Activas" son las que están en ``ASIGNADA`` o ``EN_EVALUACION``: las ya
    decididas no ocupan a nadie, aunque sigan existiendo.
    """

    revisor: RevisorDisponible
    revisiones_activas: int


class AsignadorRevisor:
    """Domain Service: elige qué revisor se lleva una pregunta.

    Es un Domain Service y no un método de ``Revision`` porque la decisión no
    pertenece a ninguna revisión concreta: depende del conjunto de revisores y
    de la carga de todos ellos. Ponerlo en el agregado obligaría a que una
    revisión conociera a las demás, que es justo lo que un agregado no debe
    hacer.

    Reglas, en orden:

    1. **Nunca el autor de la pregunta.** Es lo primero que se descarta: una
       revisión por pares en la que el autor se revisa a sí mismo no es una
       revisión. Es la regla no negociable.
    2. **El que menos revisiones activas tenga**, para repartir la carga.
    3. **A igualdad de carga, el que lleve más tiempo registrado.** Desempatar
       por antigüedad, y no al azar, hace que la asignación sea reproducible:
       con los mismos datos siempre sale el mismo revisor, y eso se puede probar.

    Los revisores inactivos no se consideran: darlos de baja no borra sus
    revisiones pasadas, pero sí deja de mandarles trabajo nuevo.
    """

    def elegir(self, cargas: list[CargaRevisor], autor_id: UUID) -> RevisorDisponible:
        """Devuelve el revisor elegido.

        :raises ReglaDeNegocioViolada: si no hay ningún revisor posible. Es la
            **invariante 9**: antes que crear una revisión sin revisor, se falla.
        """
        candidatos = [
            carga
            for carga in cargas
            if carga.revisor.activo and carga.revisor.revisor_id != autor_id
        ]

        if not candidatos:
            raise ReglaDeNegocioViolada(
                "No hay revisores disponibles para revisar la pregunta (invariante 9)."
            )

        # Menos carga primero; a igual carga, el más antiguo. Ambos criterios en
        # una sola clave para que el orden sea total y, por tanto, determinista.
        elegido = min(
            candidatos,
            key=lambda carga: (carga.revisiones_activas, carga.revisor.registrado_en),
        )
        return elegido.revisor
