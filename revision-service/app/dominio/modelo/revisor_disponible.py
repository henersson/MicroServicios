"""Proyección local ``RevisorDisponible``."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from app.dominio.excepciones import ReglaDeNegocioViolada


@dataclass(frozen=True, slots=True)
class RevisorDisponible:
    """Proyección local de los revisores que este contexto puede asignar.

    Es una **proyección**, no el agregado Usuario: el Bounded Context de
    Usuarios y Roles no existe en este taller. Este servicio guarda su propia
    tabla con lo poco que necesita saber de un revisor: quién es, cómo se llama
    y desde cuándo está disponible.

    Hoy se alimenta por REST (``POST /api/v1/revisores``) y con datos semilla.
    El día que exista el servicio de usuarios, se alimentará consumiendo el
    evento ``UsuarioRegistrado`` y esta clase no cambiará: solo cambiará quién
    la escribe.

    :param activo: permite dar de baja a un revisor sin borrarlo, para no
        perder la traza de las revisiones que ya hizo.
    :param registrado_en: se usa para desempatar en el ``AsignadorRevisor``: a
        igualdad de carga, le toca al que lleva más tiempo esperando.
    """

    revisor_id: UUID
    nombre: str
    correo: str
    registrado_en: datetime
    activo: bool = True

    def __post_init__(self) -> None:
        if self.revisor_id is None:
            raise ReglaDeNegocioViolada("El identificador del revisor es obligatorio.")
        nombre = (self.nombre or "").strip()
        if not nombre:
            raise ReglaDeNegocioViolada("El nombre del revisor es obligatorio.")
        correo = (self.correo or "").strip().lower()
        if "@" not in correo:
            raise ReglaDeNegocioViolada(
                f"El correo '{self.correo}' no parece válido."
            )
        object.__setattr__(self, "nombre", nombre)
        object.__setattr__(self, "correo", correo)
