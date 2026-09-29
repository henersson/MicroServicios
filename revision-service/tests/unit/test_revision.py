"""Tests del Aggregate Root ``Revision``: invariantes 9, 10 y 11."""

from __future__ import annotations

from uuid import uuid4

import pytest

from app.dominio.eventos.eventos import (
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
from app.dominio.modelo.revision import Revision
from tests import datos_de_prueba as datos


class TestInvariante9:
    """La revisión nunca existe sin revisor."""

    def test_no_se_puede_crear_sin_revisor(self):
        with pytest.raises(ReglaDeNegocioViolada) as error:
            Revision(
                revision_id=uuid4(),
                pregunta_id=datos.PREGUNTA,
                revisor_id=None,  # type: ignore[arg-type]
                snapshot=datos.snapshot(),
            )
        assert "sin revisor asignado" in str(error.value)
        assert "Invariante 9" in str(error.value)

    def test_no_se_puede_crear_sin_snapshot(self):
        with pytest.raises(ReglaDeNegocioViolada) as error:
            Revision(
                revision_id=uuid4(),
                pregunta_id=datos.PREGUNTA,
                revisor_id=datos.REVISOR,
                snapshot=None,  # type: ignore[arg-type]
            )
        assert "snapshot" in str(error.value)

    def test_al_iniciarse_registra_RevisorAsignado(self):
        revision = datos.revision()

        assert revision.estado is EstadoRevision.ASIGNADA
        assert len(revision.eventos_pendientes) == 1
        evento = revision.eventos_pendientes[0]
        assert isinstance(evento, RevisorAsignado)
        assert evento.tipo == "RevisorAsignado"
        assert evento.routing_key == "revision.revisor.asignado"
        assert evento.revisor_id == datos.REVISOR


class TestSoloElRevisorAsignado:
    """Solo el revisor asignado evalúa, observa y decide."""

    def test_otro_revisor_no_puede_guardar_formato(self):
        revision = datos.revision()

        with pytest.raises(AccesoNoAutorizado) as error:
            revision.guardar_formato(
                datos.OTRO_REVISOR, datos.formato_completo(), datos.AHORA
            )
        assert "Solo el revisor asignado" in str(error.value)

    def test_otro_revisor_no_puede_observar(self):
        revision = datos.revision()

        with pytest.raises(AccesoNoAutorizado):
            revision.agregar_observacion(
                datos.OTRO_REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA
            )

    def test_otro_revisor_no_puede_decidir(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(), datos.AHORA)

        with pytest.raises(AccesoNoAutorizado) as error:
            revision.decidir(datos.OTRO_REVISOR, Decision.APROBAR, datos.AHORA)
        assert "Solo el revisor asignado" in str(error.value)


class TestEstados:
    """La máquina de estados: ASIGNADA → EN_EVALUACION → APROBADA | RECHAZADA."""

    def test_guardar_formato_pasa_a_EN_EVALUACION(self):
        revision = datos.revision()
        assert revision.estado is EstadoRevision.ASIGNADA

        revision.guardar_formato(datos.REVISOR, datos.formato_incompleto(), datos.AHORA)

        assert revision.estado is EstadoRevision.EN_EVALUACION

    def test_agregar_observacion_pasa_a_EN_EVALUACION(self):
        revision = datos.revision()

        revision.agregar_observacion(datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA)

        assert revision.estado is EstadoRevision.EN_EVALUACION

    def test_una_revision_decidida_no_se_reabre(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(), datos.AHORA)
        revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA)

        for accion in (
            lambda: revision.guardar_formato(
                datos.REVISOR, datos.formato_completo(5), datos.AHORA
            ),
            lambda: revision.agregar_observacion(
                datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA
            ),
            lambda: revision.decidir(datos.REVISOR, Decision.RECHAZAR, datos.AHORA),
        ):
            with pytest.raises(TransicionInvalida) as error:
                accion()
            assert "APROBADA" in str(error.value)

    def test_el_estado_activo_distingue_la_carga_de_trabajo(self):
        revision = datos.revision()
        assert revision.esta_activa is True

        revision.guardar_formato(datos.REVISOR, datos.formato_completo(), datos.AHORA)
        assert revision.esta_activa is True

        revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA)
        assert revision.esta_activa is False


class TestInvariante10:
    """No se decide con el formato incompleto, ni se aprueba bajo el mínimo."""

    def test_no_se_puede_aprobar_con_formato_incompleto(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_incompleto(), datos.AHORA)

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA)

        assert "formato de evaluación incompleto" in str(error.value)
        assert "Invariante 10" in str(error.value)
        # Dice exactamente qué falta, no solo que falta algo.
        assert len(error.value.errores) == 2

    def test_no_se_puede_rechazar_con_formato_incompleto(self):
        revision = datos.revision()
        revision.agregar_observacion(datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA)
        revision.guardar_formato(datos.REVISOR, datos.formato_incompleto(), datos.AHORA)

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.decidir(datos.REVISOR, Decision.RECHAZAR, datos.AHORA)
        assert "Invariante 10" in str(error.value)

    def test_no_se_puede_decidir_sin_haber_guardado_nada(self):
        revision = datos.revision()

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA)
        assert "faltan 5 de 5 criterios" in str(error.value)

    def test_no_se_puede_aprobar_con_promedio_bajo_el_minimo(self):
        revision = datos.revision()
        # Promedio 2.8, por debajo del 3.0 exigido.
        revision.guardar_formato(
            datos.REVISOR,
            datos.formato_con(
                CLARIDAD_CONTEXTO=3,
                PERTINENCIA_COMPETENCIA=3,
                PLAUSIBILIDAD_DISTRACTORES=2,
                UNICIDAD_RESPUESTA_CORRECTA=3,
                CALIDAD_JUSTIFICACION=3,
            ),
            datos.AHORA,
        )
        assert revision.promedio == 2.8

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.decidir(
                datos.REVISOR, Decision.APROBAR, datos.AHORA, promedio_minimo=3.0
            )

        assert "el promedio del formato es 2.8" in str(error.value)
        assert "Invariante 10" in str(error.value)
        assert revision.estado is EstadoRevision.EN_EVALUACION, (
            "si no se pudo aprobar, la revisión se queda donde estaba"
        )

    def test_el_promedio_minimo_es_configurable(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(3), datos.AHORA)

        # Con mínimo 3.0 pasa; con 4.0 no.
        revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA, promedio_minimo=3.0)
        assert revision.estado is EstadoRevision.APROBADA

        otra = datos.revision()
        otra.guardar_formato(datos.REVISOR, datos.formato_completo(3), datos.AHORA)
        with pytest.raises(ReglaDeNegocioViolada):
            otra.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA, promedio_minimo=4.0)

    def test_se_puede_rechazar_aunque_el_promedio_sea_alto(self):
        """Rechazar no mira el promedio: el revisor puede detectar un problema
        que la rúbrica no captura."""
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(5), datos.AHORA)
        revision.agregar_observacion(
            datos.REVISOR,
            "La respuesta correcta aparece textualmente en el contexto.",
            datos.AHORA,
        )

        revision.decidir(datos.REVISOR, Decision.RECHAZAR, datos.AHORA)

        assert revision.estado is EstadoRevision.RECHAZADA


class TestInvariante11:
    """Observaciones con autor y fecha; para rechazar, al menos una."""

    def test_no_se_puede_rechazar_sin_observaciones(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(2), datos.AHORA)

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.decidir(datos.REVISOR, Decision.RECHAZAR, datos.AHORA)

        assert "sin al menos una observación" in str(error.value)
        assert "Invariante 11" in str(error.value)
        assert revision.estado is EstadoRevision.EN_EVALUACION

    def test_la_observacion_guarda_revisor_y_fecha(self):
        revision = datos.revision()

        observacion = revision.agregar_observacion(
            datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA
        )

        assert observacion.revisor_id == datos.REVISOR
        assert observacion.fecha == datos.AHORA
        assert observacion.observacion_id is not None

    def test_una_observacion_demasiado_corta_se_rechaza(self):
        revision = datos.revision()

        with pytest.raises(ReglaDeNegocioViolada) as error:
            revision.agregar_observacion(datos.REVISOR, "mal", datos.AHORA)
        assert "demasiado corta" in str(error.value)

    def test_las_observaciones_se_acumulan_en_orden(self):
        revision = datos.revision()
        revision.agregar_observacion(datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA)
        revision.agregar_observacion(
            datos.REVISOR,
            "El distractor sobre el teorema CAP es demasiado evidente.",
            datos.AHORA,
        )

        assert len(revision.observaciones) == 2
        assert "120 palabras" in revision.observaciones[0].texto
        assert "teorema CAP" in revision.observaciones[1].texto


class TestEventosDeDecision:
    """Los eventos que salen hacia el banco."""

    def test_aprobar_registra_PreguntaAprobadaTecnicamente_con_el_promedio(self):
        revision = datos.revision()
        revision.extraer_eventos_pendientes()  # se descarta el RevisorAsignado
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(4), datos.AHORA)

        revision.decidir(datos.REVISOR, Decision.APROBAR, datos.AHORA)

        eventos = revision.extraer_eventos_pendientes()
        assert len(eventos) == 1
        evento = eventos[0]
        assert isinstance(evento, PreguntaAprobadaTecnicamente)
        assert evento.routing_key == "revision.pregunta.aprobada"
        assert evento.datos()["promedio"] == 4.0
        assert evento.datos()["preguntaId"] == str(datos.PREGUNTA)

    def test_rechazar_registra_PreguntaRechazadaPorPares_con_las_observaciones(self):
        revision = datos.revision()
        revision.extraer_eventos_pendientes()
        revision.guardar_formato(datos.REVISOR, datos.formato_completo(2), datos.AHORA)
        revision.agregar_observacion(datos.REVISOR, datos.OBSERVACION_VALIDA, datos.AHORA)

        revision.decidir(datos.REVISOR, Decision.RECHAZAR, datos.AHORA)

        eventos = revision.extraer_eventos_pendientes()
        evento = eventos[0]
        assert isinstance(evento, PreguntaRechazadaPorPares)
        assert evento.routing_key == "revision.pregunta.rechazada"

        observaciones = evento.datos()["observaciones"]
        assert len(observaciones) == 1
        # El banco necesita el texto completo para mostrárselo al autor.
        assert observaciones[0]["texto"] == datos.OBSERVACION_VALIDA
        assert observaciones[0]["revisorId"] == str(datos.REVISOR)
        assert observaciones[0]["fecha"].endswith("Z"), "el contrato exige UTC con Z"

    def test_extraer_los_eventos_los_entrega_una_sola_vez(self):
        revision = datos.revision()

        assert len(revision.extraer_eventos_pendientes()) == 1
        assert revision.extraer_eventos_pendientes() == [], (
            "un evento no se puede publicar dos veces"
        )


class TestFormatoEvaluacion:
    """El Value Object del formato."""

    def test_el_formato_vacio_no_esta_completo(self):
        formato = FormatoEvaluacion.vacio()

        assert formato.esta_completo() is False
        assert formato.promedio() == 0.0
        assert len(formato.criterios_faltantes()) == 5

    def test_el_formato_con_los_cinco_criterios_esta_completo(self):
        formato = datos.formato_completo()

        assert formato.esta_completo() is True
        assert formato.criterios_faltantes() == []

    @pytest.mark.parametrize("puntaje", [0, 6, -1, 100])
    def test_rechaza_puntajes_fuera_del_rango(self, puntaje):
        with pytest.raises(ReglaDeNegocioViolada) as error:
            FormatoEvaluacion({CriterioEvaluacion.CLARIDAD_CONTEXTO: puntaje})

        # El mensaje resume; el detalle de cada criterio va en errores[], igual
        # que en las respuestas Problem Details de la API.
        assert "puntajes inválidos" in str(error.value)
        assert any("entre 1 y 5" in detalle for detalle in error.value.errores)
        assert any("CLARIDAD_CONTEXTO" in detalle for detalle in error.value.errores)

    def test_rechaza_un_criterio_desconocido(self):
        with pytest.raises(ReglaDeNegocioViolada) as error:
            FormatoEvaluacion.desde_textos({"CRITERIO_INVENTADO": 4})
        assert "desconocido" in str(error.value)

    def test_el_promedio_se_redondea_a_dos_decimales(self):
        formato = datos.formato_con(
            CLARIDAD_CONTEXTO=5,
            PERTINENCIA_COMPETENCIA=4,
            PLAUSIBILIDAD_DISTRACTORES=4,
            UNICIDAD_RESPUESTA_CORRECTA=5,
            CALIDAD_JUSTIFICACION=3,
        )

        assert formato.promedio() == 4.2

    def test_es_inmutable(self):
        formato = datos.formato_completo()

        with pytest.raises(Exception):
            formato.puntajes = {}  # type: ignore[misc]

    def test_guardar_el_formato_lo_reemplaza_entero(self):
        revision = datos.revision()
        revision.guardar_formato(datos.REVISOR, datos.formato_incompleto(), datos.AHORA)
        assert len(revision.formato.puntajes) == 3

        revision.guardar_formato(datos.REVISOR, datos.formato_completo(), datos.AHORA)
        assert len(revision.formato.puntajes) == 5
