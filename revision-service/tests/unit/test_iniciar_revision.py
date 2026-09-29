"""Tests del caso de uso ``IniciarRevision``, el corazón de la integración."""

from __future__ import annotations

from uuid import uuid4

import pytest

from app.aplicacion.casosdeuso.iniciar_revision import IniciarRevisionUseCase
from app.aplicacion.puertos.puertos import BancoNoDisponible, PreguntaNoExisteEnBanco
from app.dominio.excepciones import ReglaDeNegocioViolada
from app.dominio.modelo.enums import EstadoRevision
from app.dominio.servicios.asignador_revisor import AsignadorRevisor
from tests import datos_de_prueba as datos
from tests.dobles import (
    Almacen,
    ClienteBancoFalso,
    PublicadorEventosEspia,
    UnidadDeTrabajoMemoria,
)


def armar(con_revisores: int = 1, cliente=None):
    """Monta el caso de uso con dobles y, opcionalmente, algunos revisores."""
    almacen = Almacen()
    uow = UnidadDeTrabajoMemoria(almacen)
    publicador = PublicadorEventosEspia(almacen)
    cliente = cliente or ClienteBancoFalso(snapshot=datos.snapshot())

    plantillas = [
        (datos.REVISOR, "Marta Liliana Torres", "mtorres@unicauca.edu.co", 60),
        (datos.OTRO_REVISOR, "Carlos Andrés Muñoz", "camunoz@unicauca.edu.co", 30),
        (datos.TERCER_REVISOR, "Diana Patricia Rojas", "dprojas@unicauca.edu.co", 10),
    ]
    for identificador, nombre, correo, antiguedad in plantillas[:con_revisores]:
        almacen.revisores[identificador] = datos.revisor(
            identificador, nombre, correo, antiguedad
        )

    caso = IniciarRevisionUseCase(
        uow=uow,
        cliente_banco=cliente,
        publicador=publicador,
        asignador=AsignadorRevisor(),
    )
    return caso, almacen, publicador, cliente


class TestCaminoFeliz:
    async def test_crea_la_revision_y_publica_RevisorAsignado(self):
        caso, almacen, publicador, cliente = armar(con_revisores=3)
        event_id = uuid4()

        resultado = await caso.ejecutar(event_id, datos.PREGUNTA, datos.AUTOR)

        assert resultado.aplicado is True
        # Marta es la más antigua y todas tienen carga 0.
        assert resultado.revisor_id == datos.REVISOR

        assert len(almacen.revisiones) == 1
        revision = next(iter(almacen.revisiones.values()))
        assert revision.estado is EstadoRevision.ASIGNADA
        assert revision.snapshot.pregunta_id == datos.PREGUNTA

        assert publicador.tipos() == ["RevisorAsignado"]

    async def test_pide_la_pregunta_al_banco_por_grpc(self):
        caso, _, _, cliente = armar()

        await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)

        assert cliente.llamadas == [datos.PREGUNTA]

    async def test_publica_despues_de_confirmar_la_transaccion(self):
        """ADR 4: ningún evento sale antes del commit."""
        caso, almacen, publicador, _ = armar()

        await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)

        assert publicador.commits_al_publicar, "se publicó algo"
        assert publicador.commits_al_publicar[0] >= 1, (
            "al publicar ya tenía que haber al menos un commit hecho"
        )

    async def test_reparte_la_carga_entre_los_revisores(self):
        caso, almacen, _, _ = armar(con_revisores=3)

        # Tres preguntas distintas, una tras otra.
        for _ in range(3):
            pregunta = uuid4()
            caso._cliente_banco = ClienteBancoFalso(  # type: ignore[attr-defined]
                snapshot=datos.snapshot(pregunta_id=pregunta)
            )
            await caso.ejecutar(uuid4(), pregunta, datos.AUTOR)

        asignados = {revision.revisor_id for revision in almacen.revisiones.values()}
        assert len(asignados) == 3, "cada pregunta fue a un revisor distinto"


class TestInvariante9:
    """Una Revision nunca existe sin revisor: si no hay, el caso de uso falla."""

    async def test_sin_revisores_falla_y_no_crea_nada(self):
        caso, almacen, publicador, _ = armar(con_revisores=0)
        event_id = uuid4()

        with pytest.raises(ReglaDeNegocioViolada) as error:
            await caso.ejecutar(event_id, datos.PREGUNTA, datos.AUTOR)

        assert "No hay revisores disponibles" in error.value.mensaje
        assert len(almacen.revisiones) == 0
        assert publicador.tipos() == [], "sin revisión no se publica nada"
        assert event_id not in almacen.eventos_procesados, (
            "el evento no se marca como procesado: se puede reprocesar desde la DLQ"
        )

    async def test_si_el_unico_revisor_es_el_autor_tambien_falla(self):
        caso, almacen, _, _ = armar(con_revisores=1)

        with pytest.raises(ReglaDeNegocioViolada):
            await caso.ejecutar(uuid4(), datos.PREGUNTA, autor_id=datos.REVISOR)

        assert len(almacen.revisiones) == 0


class TestIdempotencia:
    async def test_el_mismo_evento_repetido_no_crea_dos_revisiones(self):
        caso, almacen, publicador, _ = armar()
        event_id = uuid4()

        primero = await caso.ejecutar(event_id, datos.PREGUNTA, datos.AUTOR)
        segundo = await caso.ejecutar(event_id, datos.PREGUNTA, datos.AUTOR)

        assert primero.aplicado is True
        assert segundo.aplicado is False
        assert "ya procesado" in segundo.motivo
        assert len(almacen.revisiones) == 1
        assert publicador.tipos() == ["RevisorAsignado"], "solo se publicó una vez"

    async def test_un_evento_distinto_para_la_misma_pregunta_tampoco_duplica(self):
        """Segunda barrera: pasa si el autor reenvía la pregunta antes de que se
        decida la revisión anterior."""
        caso, almacen, _, _ = armar()

        await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)
        resultado = await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)

        assert resultado.aplicado is False
        assert "ya tiene una revisión activa" in resultado.motivo
        assert len(almacen.revisiones) == 1


class TestFallosDelBanco:
    async def test_si_la_pregunta_no_existe_es_un_error_permanente(self):
        """El consumidor lo manda a la DLQ: la pregunta no va a aparecer."""
        caso, almacen, _, _ = armar(cliente=ClienteBancoFalso(snapshot=None))

        with pytest.raises(PreguntaNoExisteEnBanco):
            await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)

        assert len(almacen.revisiones) == 0

    async def test_si_el_banco_esta_caido_el_caso_de_uso_falla(self):
        """El consumidor lo manda a la DLQ, desde donde se puede reprocesar."""
        caso, almacen, _, _ = armar(cliente=ClienteBancoFalso.caido())

        with pytest.raises(BancoNoDisponible):
            await caso.ejecutar(uuid4(), datos.PREGUNTA, datos.AUTOR)

        assert len(almacen.revisiones) == 0

    async def test_un_fallo_del_banco_no_marca_el_evento_como_procesado(self):
        """Si se marcara, un reproceso desde la DLQ lo descartaría creyendo que
        ya estaba hecho, y la pregunta se quedaría sin revisión para siempre."""
        caso, almacen, _, _ = armar(cliente=ClienteBancoFalso.caido())
        event_id = uuid4()

        with pytest.raises(BancoNoDisponible):
            await caso.ejecutar(event_id, datos.PREGUNTA, datos.AUTOR)

        assert event_id not in almacen.eventos_procesados
