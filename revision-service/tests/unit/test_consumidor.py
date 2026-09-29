"""Tests del consumidor de ``revision.preguntas-enviadas``.

El consumidor tiene un único criterio: si el evento se procesa, ``ack``; si algo
falla, ``reject(requeue=False)`` y el mensaje queda en la DLQ. No hay reintentos.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from uuid import uuid4

from app.dominio.excepciones import ReglaDeNegocioViolada
from app.infraestructura.config.configuracion import Configuracion
from app.infraestructura.mensajeria.consumidor import ConsumidorPreguntasEnviadas
from tests import datos_de_prueba as datos


@dataclass
class MensajeFalso:
    """Doble de un mensaje de aio-pika: solo anota qué se hizo con él."""

    body: bytes
    confirmados: list[bool] = field(default_factory=list)
    rechazados: list[bool] = field(default_factory=list)

    async def ack(self) -> None:
        self.confirmados.append(True)

    async def reject(self, requeue: bool = False) -> None:
        self.rechazados.append(requeue)


def envelope(event_id=None, tipo="PreguntaEnviadaARevision", datos_evento=None) -> bytes:
    cuerpo = {
        "eventId": str(event_id or uuid4()),
        "eventType": tipo,
        "eventVersion": 1,
        "occurredAt": "2026-09-25T10:00:00Z",
        "source": "banco-preguntas-service",
        "data": datos_evento
        if datos_evento is not None
        else {
            "preguntaId": str(datos.PREGUNTA),
            "autorId": str(datos.AUTOR),
            "competenciaCodigo": "ING-SOFT",
        },
    }
    return json.dumps(cuerpo).encode("utf-8")


def armar(manejar):
    configuracion = Configuracion(_env_file=None)  # type: ignore[call-arg]
    return ConsumidorPreguntasEnviadas(configuracion, manejar)


class TestConsumidor:
    async def test_un_mensaje_valido_se_confirma(self):
        llamadas = []

        async def manejar(event_id, pregunta_id, autor_id):
            llamadas.append((event_id, pregunta_id, autor_id))
            return None

        consumidor = armar(manejar)
        mensaje = MensajeFalso(envelope())

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.confirmados == [True]
        assert mensaje.rechazados == []
        assert len(llamadas) == 1

    async def test_un_json_invalido_va_a_la_dlq(self):
        async def manejar(event_id, pregunta_id, autor_id):
            raise AssertionError("no debería llegar aquí")

        consumidor = armar(manejar)
        mensaje = MensajeFalso(b"esto no es json")

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.rechazados == [False], "reject(requeue=False): a la DLQ"
        assert mensaje.confirmados == []

    async def test_un_campo_obligatorio_ausente_va_a_la_dlq(self):
        async def manejar(event_id, pregunta_id, autor_id):
            raise AssertionError("no debería llegar aquí")

        consumidor = armar(manejar)
        mensaje = MensajeFalso(envelope(datos_evento={"autorId": str(datos.AUTOR)}))

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.rechazados == [False]

    async def test_un_tipo_de_evento_inesperado_va_a_la_dlq(self):
        async def manejar(event_id, pregunta_id, autor_id):
            raise AssertionError("no debería llegar aquí")

        consumidor = armar(manejar)
        mensaje = MensajeFalso(envelope(tipo="PreguntaPublicada"))

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.rechazados == [False]

    async def test_una_regla_de_negocio_incumplida_va_a_la_dlq(self):
        """Por ejemplo, la invariante 9: no hay revisores disponibles."""

        async def manejar(event_id, pregunta_id, autor_id):
            raise ReglaDeNegocioViolada(
                "No hay revisores disponibles para revisar la pregunta (invariante 9)."
            )

        consumidor = armar(manejar)
        mensaje = MensajeFalso(envelope())

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.rechazados == [False]
        assert mensaje.confirmados == []

    async def test_un_error_inesperado_tambien_va_a_la_dlq(self):
        """No hay reintentos: cualquier fallo termina en la DLQ."""

        async def manejar(event_id, pregunta_id, autor_id):
            raise RuntimeError("la base de datos no responde")

        consumidor = armar(manejar)
        mensaje = MensajeFalso(envelope())

        await consumidor.procesar_mensaje(mensaje)

        assert mensaje.rechazados == [False]
        assert mensaje.confirmados == []
