"""Publicador de eventos de integración sobre RabbitMQ."""

from __future__ import annotations

import json
import logging
from datetime import timezone

import aio_pika

from app.aplicacion.puertos.puertos import PublicadorEventos
from app.dominio.eventos.eventos import ORIGEN, EventoDominio

log = logging.getLogger(__name__)

#: Versión del contrato de eventos. Un cambio incompatible subiría a 2.
VERSION_EVENTO = 1


def ensamblar_envelope(evento: EventoDominio) -> dict:
    """Construye el envelope JSON que define ``contracts/events``.

    Se arma un diccionario con claves explícitas en vez de volcar el dataclass:
    así el JSON que sale es exactamente el del contrato, y un refactor de un
    evento no puede romper en silencio a los consumidores de otro lenguaje.

    La fecha se formatea a mano en UTC ISO-8601 terminado en ``Z``, tal como
    exige el contrato y como lo emite el banco.
    """
    ocurrido = evento.ocurrido_en
    if ocurrido.tzinfo is None:
        ocurrido = ocurrido.replace(tzinfo=timezone.utc)
    ocurrido_utc = ocurrido.astimezone(timezone.utc)

    return {
        "eventId": str(evento.event_id),
        "eventType": evento.tipo,
        "eventVersion": VERSION_EVENTO,
        "occurredAt": ocurrido_utc.isoformat().replace("+00:00", "Z"),
        "source": ORIGEN,
        "data": evento.datos(),
    }


class PublicadorEventosRabbitMQ(PublicadorEventos):
    """Adaptador del puerto ``PublicadorEventos`` sobre RabbitMQ.

    Los casos de uso llaman a :meth:`publicar` **después** de confirmar la
    transacción, nunca antes (ADR 4). Este adaptador no tiene forma de
    comprobarlo, así que la garantía está en el orden de las llamadas dentro de
    cada caso de uso; los tests unitarios lo verifican.

    Queda abierta la ventana "transacción confirmada pero evento no publicado"
    si el proceso muere justo en medio. Se asume conscientemente: cerrarla es
    para lo que sirve el patrón Outbox, anotado como trabajo futuro.
    """

    def __init__(self, url: str, nombre_exchange: str) -> None:
        self._url = url
        self._nombre_exchange = nombre_exchange
        self._conexion: aio_pika.abc.AbstractRobustConnection | None = None
        self._canal: aio_pika.abc.AbstractChannel | None = None
        self._exchange: aio_pika.abc.AbstractExchange | None = None

    async def conectar(self) -> None:
        """Abre la conexión y declara el exchange de forma idempotente.

        Se usa ``connect_robust``: si el broker se cae y vuelve, aio-pika
        reconecta solo y no hay que reiniciar el servicio.

        El exchange se declara aquí aunque ya exista en
        ``infra/rabbitmq/definitions.json``. Declarar uno que ya está, con los
        mismos parámetros, no hace nada; y si el broker se levantó sin las
        definiciones, el servicio funciona igual.
        """
        if self._conexion is not None and not self._conexion.is_closed:
            return

        self._conexion = await aio_pika.connect_robust(self._url)
        self._canal = await self._conexion.channel(publisher_confirms=True)
        self._exchange = await self._canal.declare_exchange(
            self._nombre_exchange,
            aio_pika.ExchangeType.TOPIC,
            durable=True,
        )
        log.info(
            "Publicador conectado a RabbitMQ; exchange '%s' listo.", self._nombre_exchange
        )

    async def cerrar(self) -> None:
        if self._conexion is not None and not self._conexion.is_closed:
            await self._conexion.close()
        self._conexion = None
        self._canal = None
        self._exchange = None

    async def publicar(self, eventos: list[EventoDominio]) -> None:
        if not eventos:
            return
        if self._exchange is None:
            await self.conectar()

        for evento in eventos:
            envelope = ensamblar_envelope(evento)
            mensaje = aio_pika.Message(
                body=json.dumps(envelope, ensure_ascii=False).encode("utf-8"),
                content_type="application/json",
                content_encoding="utf-8",
                # Persistente: sobrevive a un reinicio del broker.
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                message_id=str(evento.event_id),
                headers={"eventType": evento.tipo},
            )

            try:
                # mandatory=True: si la routing key no llega a ninguna cola, el
                # broker devuelve el mensaje en vez de descartarlo en silencio.
                await self._exchange.publish(
                    mensaje, routing_key=evento.routing_key, mandatory=True
                )
                log.info(
                    "Evento publicado: eventId=%s eventType=%s routingKey=%s preguntaId=%s",
                    evento.event_id,
                    evento.tipo,
                    evento.routing_key,
                    evento.pregunta_id,
                )
            except Exception:
                # No se relanza: la transacción ya confirmó y relanzar no la
                # desharía. Se registra al nivel más alto para que el fallo sea
                # visible y el evento se pueda reenviar a mano.
                log.exception(
                    "No se pudo publicar el evento eventId=%s eventType=%s preguntaId=%s. "
                    "El cambio SÍ quedó guardado; el evento habrá que reenviarlo "
                    "manualmente.",
                    evento.event_id,
                    evento.tipo,
                    evento.pregunta_id,
                )
