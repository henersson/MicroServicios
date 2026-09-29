"""Value Object ``FormatoEvaluacion``: la rúbrica que diligencia el revisor."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Mapping

from app.dominio.excepciones import ReglaDeNegocioViolada
from app.dominio.modelo.enums import CriterioEvaluacion

#: Puntaje mínimo y máximo de cada criterio.
PUNTAJE_MINIMO = 1
PUNTAJE_MAXIMO = 5


@dataclass(frozen=True, slots=True)
class FormatoEvaluacion:
    """Value Object del BC Ciclo de Vida y Revisión: los 5 criterios puntuados.

    Es **inmutable**, como todo Value Object: guardar el formato no modifica el
    existente, lo reemplaza por otro. Eso hace imposible que quede a medias por
    un fallo a mitad de una actualización.

    El formato puede estar **parcialmente diligenciado**: el revisor guarda lo
    que lleva y vuelve luego. Lo que no se puede es *decidir* con el formato
    incompleto (invariante 10), y de eso se encarga el agregado ``Revision``.

    :param puntajes: criterio → puntaje entre 1 y 5. Un criterio ausente
        significa "todavía sin puntuar", que es distinto de puntuarlo con 1.
    """

    puntajes: Mapping[CriterioEvaluacion, int] = field(default_factory=dict)

    def __post_init__(self) -> None:
        normalizados: dict[CriterioEvaluacion, int] = {}
        errores: list[str] = []

        for criterio, puntaje in (self.puntajes or {}).items():
            criterio_valido = (
                criterio
                if isinstance(criterio, CriterioEvaluacion)
                else CriterioEvaluacion.desde_texto(str(criterio))
            )
            # bool es subclase de int en Python: se descarta explícitamente para
            # que un True no cuele como puntaje 1.
            if isinstance(puntaje, bool) or not isinstance(puntaje, int):
                errores.append(
                    f"El puntaje de {criterio_valido.value} debe ser un número entero "
                    f"entre {PUNTAJE_MINIMO} y {PUNTAJE_MAXIMO}."
                )
                continue
            if not PUNTAJE_MINIMO <= puntaje <= PUNTAJE_MAXIMO:
                errores.append(
                    f"El puntaje de {criterio_valido.value} es {puntaje} y debe estar "
                    f"entre {PUNTAJE_MINIMO} y {PUNTAJE_MAXIMO}."
                )
                continue
            normalizados[criterio_valido] = puntaje

        if errores:
            raise ReglaDeNegocioViolada(
                "El formato de evaluación tiene puntajes inválidos.", errores
            )

        # Se puede asignar aunque el dataclass sea frozen: es la forma estándar
        # de normalizar en __post_init__.
        object.__setattr__(self, "puntajes", dict(normalizados))

    @classmethod
    def vacio(cls) -> "FormatoEvaluacion":
        """Formato sin ningún criterio puntuado. Es el estado inicial."""
        return cls({})

    @classmethod
    def desde_textos(cls, puntajes: Mapping[str, int] | None) -> "FormatoEvaluacion":
        """Construye el formato desde el diccionario que llega por REST."""
        if not puntajes:
            return cls.vacio()
        return cls({CriterioEvaluacion.desde_texto(k): v for k, v in puntajes.items()})

    def esta_completo(self) -> bool:
        """¿Están puntuados los 5 criterios? (invariante 10)"""
        return len(self.puntajes) == len(CriterioEvaluacion)

    def criterios_faltantes(self) -> list[CriterioEvaluacion]:
        """Los criterios que aún no tienen puntaje, para poder decírselo al revisor."""
        return [c for c in CriterioEvaluacion if c not in self.puntajes]

    def promedio(self) -> float:
        """Promedio de los criterios puntuados, redondeado a 2 decimales.

        Devuelve 0.0 si no hay ninguno. Solo tiene sentido compararlo con el
        umbral de aprobación cuando el formato está completo.
        """
        if not self.puntajes:
            return 0.0
        return round(sum(self.puntajes.values()) / len(self.puntajes), 2)

    def esta_vacio(self) -> bool:
        return not self.puntajes

    def como_textos(self) -> dict[str, int]:
        """Representación plana, para serializar a JSON o guardar en la base."""
        return {criterio.value: puntaje for criterio, puntaje in self.puntajes.items()}
