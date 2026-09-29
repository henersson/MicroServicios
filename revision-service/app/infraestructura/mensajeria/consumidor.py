"""Consumidor de la cola ``revision.preguntas-enviadas``."""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Awaitable, Callable
from uuid import UUID

import aio_pika

from app.infraestructura.config.configuracion import Configuracion

log = logging.getLogger(__name__)


class ConsumidorPreguntasEnviadas:
    """Consume ``PreguntaEnviadaARevision`` y arranca el ciclo de revisión.

    Usa **ACK manual**: el mensaje no se confirma hasta que el cambio está
    guardado. Si el proceso muere antes, RabbitMQ lo vuelve a entregar.

    Ante **cualquier** fallo el mensaje va a la DLQ con ``reject(requeue=False)``.
    No hay reintentos: reencolar devolvería el mensaje al frente de la cola y
    produciría un bucle a toda velocidad, y volver a intentarlo aquí mismo solo
    retrasaría el problema. En la DLQ el evento no se pierde y se puede
    reprocesar cuando la causa esté resuelta.

    La **idempotencia** no está aquí sino en el caso de uso, que consulta la
    tabla ``eventos_procesados`` dentro de la misma transacción que aplica el
    cambio. Aquí sería inútil: el proceso podría morir entre marcar y aplicar.
    """

    def __init__(
        self,
        configuracion: Configuracion,
        manejar_pregunta_enviada: Callable[[UUID, UUID, UUID], Awaitable[object]],
    ) -> None:
        self._config = configuracion
        self._manejar = manejar_pregunta_enviada
        self._conexion: aio_pika.abc.AbstractRobustConnection | None = None
        self._canal: aio_pika.abc.AbstractChannel | None = None
        self._cola: aio_pika.abc.AbstractQueue | None = None
        self._tarea: asyncio.Task | None = None

    # ── Ciclo de vida ───────────────────────────────────────────────────────

    async def iniciar(self) -> None:
        """Conecta, declara la topología y se pone a consumir."""
        self._conexion = await aio_pika.connect_robust(self._config.rabbitmq_url)
        self._canal = await self._conexion.channel()

        # prefetch=1: un mensaje a la vez, para que el orden de proceso sea
        # predecible y los logs se puedan seguir.
        await self._canal.set_qos(prefetch_count=1)

        # La topología ya viene de infra/rabbitmq/definitions.json; se redeclara
        # de forma idempotente por si el broker arrancó sin ella. Los argumentos
        # tienen que coincidir EXACTAMENTE con los del archivo, o RabbitMQ
        # responde PRECONDITION_FAILED y el canal se cierra.
        exchange = await self._canal.declare_exchange(
            self._config.exchange_eventos, aio_pika.ExchangeType.TOPIC, durable=True
        )
        await self._canal.declare_exchange(
            self._config.exchange_dlx, aio_pika.ExchangeType.TOPIC, durable=True
        )

        nombre_cola = self._config.cola_preguntas_enviadas
        nombre_dlq = f"{nombre_cola}.dlq"

        self._cola = await self._canal.declare_queue(
            nombre_cola,
            durable=True,
            arguments={
                "x-dead-letter-exchange": self._config.exchange_dlx,
                "x-dead-letter-routing-key": nombre_dlq,
            },
        )
        dlq = await self._canal.declare_queue(nombre_dlq, durable=True)

        await self._cola.bind(exchange, routing_key="banco.pregunta.enviada-a-revision")
        await dlq.bind(self._config.exchange_dlx, routing_key=nombre_dlq)

        self._tarea = asyncio.create_task(self._consumir())
        log.info("Consumidor escuchando la cola '%s'.", nombre_cola)

    async def detener(self) -> None:
        if self._tarea is not None:
            self._tarea.cancel()
            try:
                await self._tarea
            except asyncio.CancelledError:
                pass
            self._tarea = None
        if self._conexion is not None and not self._conexion.is_closed:
            await self._conexion.close()
        self._conexion = None
        log.info("Consumidor detenido.")

    async def _consumir(self) -> None:
        assert self._cola is not None
        async with self._cola.iterator() as mensajes:
            async for mensaje in mensajes:
                await self.procesar_mensaje(mensaje)

    # ── Procesamiento ───────────────────────────────────────────────────────

    async def procesar_mensaje(self, mensaje: aio_pika.abc.AbstractIncomingMessage) -> None:
        """Procesa un mensaje con ACK manual. Cualquier fallo lo manda a la DLQ.

        Es público para poder probarlo sin levantar RabbitMQ.
        """
        cuerpo = mensaje.body.decode("utf-8", errors="replace")
        event_id: UUID | None = None
        tipo_evento = "desconocido"

        try:
            envelope = json.loads(cuerpo)
            tipo_evento = self._texto(envelope, "eventType")
            event_id = UUID(self._texto(envelope, "eventId"))
            datos = envelope.get("data") or {}
            pregunta_id = UUID(self._texto(datos, "preguntaId"))
            autor_id = UUID(self._texto(datos, "autorId"))

            if tipo_evento != "PreguntaEnviadaARevision":
                raise ValueError(
                    f"tipo de evento inesperado en esta cola: '{tipo_evento}'"
                )

            log.info("Evento recibido: eventId=%s eventType=%s", event_id, tipo_evento)

            resultado = await self._manejar(event_id, pregunta_id, autor_id)
            await mensaje.ack()
            log.info(
                "Evento %s procesado: %s",
                event_id,
                getattr(resultado, "motivo", "sin detalle"),
            )

        except Exception as error:  # noqa: BLE001 - todo fallo termina en la DLQ
            motivo = getattr(error, "mensaje", None) or str(error) or type(error).__name__
            log.error(
                "Evento eventId=%s eventType=%s no se pudo procesar: %s. "
                "Se envía a la DLQ. Cuerpo: %s",
                event_id if event_id is not None else "ilegible",
                tipo_evento,
                motivo,
                cuerpo[:500],
            )
            await mensaje.reject(requeue=False)

    @staticmethod
    def _texto(origen: dict, campo: str) -> str:
        valor = origen.get(campo)
        if valor is None or not str(valor).strip():
            raise ValueError(f"falta el campo obligatorio '{campo}'")
        return str(valor)
