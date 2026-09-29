"""Tests del Domain Service ``AsignadorRevisor``."""

from __future__ import annotations

import pytest

from app.dominio.excepciones import ReglaDeNegocioViolada
from app.dominio.servicios.asignador_revisor import AsignadorRevisor, CargaRevisor
from tests import datos_de_prueba as datos


class TestAsignadorRevisor:
    """Reglas: nunca el autor, menos carga primero, desempate por antigüedad."""

    def setup_method(self) -> None:
        self.asignador = AsignadorRevisor()
        # Marta es la más antigua (60 días), Diana la más nueva (10).
        self.marta = datos.revisor(
            datos.REVISOR, "Marta Liliana Torres", "mtorres@unicauca.edu.co", 60
        )
        self.carlos = datos.revisor(
            datos.OTRO_REVISOR, "Carlos Andrés Muñoz", "camunoz@unicauca.edu.co", 30
        )
        self.diana = datos.revisor(
            datos.TERCER_REVISOR, "Diana Patricia Rojas", "dprojas@unicauca.edu.co", 10
        )

    def test_elige_al_que_menos_carga_tiene(self):
        cargas = [
            CargaRevisor(self.marta, 5),
            CargaRevisor(self.carlos, 1),
            CargaRevisor(self.diana, 3),
        ]

        elegido = self.asignador.elegir(cargas, autor_id=datos.AUTOR)

        assert elegido == self.carlos

    def test_a_igual_carga_elige_al_mas_antiguo(self):
        cargas = [
            CargaRevisor(self.diana, 2),
            CargaRevisor(self.carlos, 2),
            CargaRevisor(self.marta, 2),
        ]

        elegido = self.asignador.elegir(cargas, autor_id=datos.AUTOR)

        assert elegido == self.marta, "con la misma carga, le toca al que lleva más tiempo"

    def test_el_orden_de_la_lista_no_cambia_el_resultado(self):
        """La asignación tiene que ser reproducible: con los mismos datos, el
        mismo revisor, sin importar en qué orden lleguen."""
        base = [
            CargaRevisor(self.marta, 2),
            CargaRevisor(self.carlos, 2),
            CargaRevisor(self.diana, 2),
        ]

        elegidos = {
            self.asignador.elegir(orden, autor_id=datos.AUTOR)
            for orden in (base, list(reversed(base)), [base[1], base[2], base[0]])
        }

        assert len(elegidos) == 1

    def test_nunca_le_asigna_la_pregunta_a_su_propio_autor(self):
        """La regla no negociable: una revisión por pares en la que el autor se
        revisa a sí mismo no es una revisión."""
        cargas = [
            # Marta es la autora Y la que menos carga tiene: aun así, no le toca.
            CargaRevisor(self.marta, 0),
            CargaRevisor(self.carlos, 4),
        ]

        elegido = self.asignador.elegir(cargas, autor_id=self.marta.revisor_id)

        assert elegido == self.carlos

    def test_ignora_a_los_revisores_dados_de_baja(self):
        inactiva = datos.revisor(
            datos.REVISOR, "Marta Liliana Torres", "mtorres@unicauca.edu.co", 60,
            activo=False,
        )
        cargas = [CargaRevisor(inactiva, 0), CargaRevisor(self.carlos, 7)]

        elegido = self.asignador.elegir(cargas, autor_id=datos.AUTOR)

        assert elegido == self.carlos

    def test_sin_revisores_lanza_la_excepcion_de_la_invariante_9(self):
        """Sin revisor no se crea revisión: el asignador falla y el consumidor
        manda el evento a la DLQ."""
        with pytest.raises(ReglaDeNegocioViolada) as error:
            self.asignador.elegir([], autor_id=datos.AUTOR)

        assert error.value.mensaje == (
            "No hay revisores disponibles para revisar la pregunta (invariante 9)."
        )

    def test_si_el_unico_revisor_es_el_autor_lanza_la_excepcion(self):
        cargas = [CargaRevisor(self.marta, 0)]

        with pytest.raises(ReglaDeNegocioViolada):
            self.asignador.elegir(cargas, autor_id=self.marta.revisor_id)

    def test_si_todos_estan_inactivos_lanza_la_excepcion(self):
        cargas = [
            CargaRevisor(datos.revisor(datos.REVISOR, activo=False), 0),
            CargaRevisor(
                datos.revisor(
                    datos.OTRO_REVISOR, "Carlos", "c@unicauca.edu.co", 5, activo=False
                ),
                0,
            ),
        ]

        with pytest.raises(ReglaDeNegocioViolada):
            self.asignador.elegir(cargas, autor_id=datos.AUTOR)
