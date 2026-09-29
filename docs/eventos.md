# Catálogo de eventos

Toda la comunicación asíncrona del sistema pasa por RabbitMQ. Este documento
describe la topología, el formato de los mensajes y los 6 eventos.

## 1. Topología

Un solo exchange de tipo **topic** y durable, `saberpro.eventos`, y un exchange
aparte para los mensajes fallidos, `saberpro.eventos.dlx`.

| Cola | Quién consume | Qué recibe |
|---|---|---|
| `revision.preguntas-enviadas` | `revision-service` | `PreguntaEnviadaARevision` |
| `banco.resultados-revision` | `banco-preguntas-service` | `RevisorAsignado`, `PreguntaAprobadaTecnicamente`, `PreguntaRechazadaPorPares` |
| `simulacros.catalogo-preguntas` | nadie todavía | `PreguntaPublicada`, `PreguntaArchivada` |

Cada cola tiene su **DLQ**: `revision.preguntas-enviadas.dlq`,
`banco.resultados-revision.dlq` y `simulacros.catalogo-preguntas.dlq`. Las colas
declaran `x-dead-letter-exchange` apuntando al DLX, así que un mensaje rechazado
llega a su DLQ sin que nadie tenga que moverlo.

La topología se carga desde
[`../infra/rabbitmq/definitions.json`](../infra/rabbitmq/definitions.json) al
arrancar el broker. Además, cada servicio vuelve a declarar su propia cola al
conectarse, de forma idempotente, por si el broker se levantara sin las
definiciones.

Se puede revisar todo en la consola: <http://localhost:15672>, usuario
`saberpro`, contraseña `saberpro`.

## 2. El envelope

Los 6 eventos comparten la misma envoltura. Lo que cambia es el contenido de
`data`.

```json
{
  "eventId": "9f2e3d4c-5b6a-4f7e-8d9c-1a2b3c4d5e6f",
  "eventType": "RevisorAsignado",
  "eventVersion": 1,
  "occurredAt": "2026-09-06T14:20:07Z",
  "source": "revision-service",
  "data": {
    "revisionId": "c4d5e6f7-a8b9-4c0d-9e1f-2a3b4c5d6e7f",
    "preguntaId": "3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c",
    "revisorId": "b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e"
  }
}
```

| Campo | Qué es |
|---|---|
| `eventId` | UUID único del evento. Es la clave de la idempotencia |
| `eventType` | Nombre del evento, el mismo que da nombre a su schema |
| `eventVersion` | Siempre `1`. Un cambio incompatible crearía la versión 2 |
| `occurredAt` | Cuándo ocurrió, en UTC ISO-8601 terminado en `Z` |
| `source` | Qué servicio lo publicó |
| `data` | Los datos propios del evento |

Cada mensaje viaja con `content_type: application/json`, `delivery_mode: 2`
(persistente, sobrevive a un reinicio del broker), `message_id` igual al
`eventId` y una cabecera `eventType`.

Cada evento tiene su **JSON Schema** en
[`../contracts/events/`](../contracts/events/) y un ejemplo realista en
`../contracts/events/ejemplos/`. Ese es el contrato: un consumidor escrito en
cualquier lenguaje solo necesita esos archivos.

## 3. Los 6 eventos

| Evento | Routing key | Productor | Cola | Campos de `data` |
|---|---|---|---|---|
| `PreguntaEnviadaARevision` | `banco.pregunta.enviada-a-revision` | banco | `revision.preguntas-enviadas` | `preguntaId`, `autorId`, `competenciaCodigo` |
| `RevisorAsignado` | `revision.revisor.asignado` | revisión | `banco.resultados-revision` | `revisionId`, `preguntaId`, `revisorId` |
| `PreguntaAprobadaTecnicamente` | `revision.pregunta.aprobada` | revisión | `banco.resultados-revision` | `revisionId`, `preguntaId`, `revisorId`, `promedio` |
| `PreguntaRechazadaPorPares` | `revision.pregunta.rechazada` | revisión | `banco.resultados-revision` | `revisionId`, `preguntaId`, `revisorId`, `observaciones` |
| `PreguntaPublicada` | `banco.pregunta.publicada` | banco | `simulacros.catalogo-preguntas` | `pregunta` (la pregunta completa) |
| `PreguntaArchivada` | `banco.pregunta.archivada` | banco | `simulacros.catalogo-preguntas` | `preguntaId` |

Dos detalles de diseño que explican la tabla:

- **`PreguntaEnviadaARevision` lleva solo identificadores.** Quien lo consume
  pide el contenido por gRPC. Así el contrato del evento no se rompe cada vez que
  cambia un campo de la pregunta.
- **`PreguntaPublicada` lleva la pregunta completa**, incluida la marca de qué
  opción es la correcta. Es la excepción, y es deliberada: el futuro
  `simulacros-service` puede armar su catálogo solo escuchando, sin llamar al
  banco por cada pregunta.

## 4. Idempotencia

RabbitMQ garantiza entrega **al menos una vez**, así que el mismo evento puede
llegar dos veces: por una reentrega tras un fallo de red, o porque el consumidor
murió justo después de aplicar el cambio y antes de confirmar el ACK.

Los dos servicios lo resuelven igual: una tabla `eventos_procesados` con clave
primaria sobre `event_id`. Antes de aplicar un evento, el caso de uso comprueba
si ese `eventId` ya está; si está, lo descarta.

Lo que hace que funcione de verdad es **dónde** se escribe: en la **misma
transacción** que aplica el cambio. Si fueran dos transacciones habría una
ventana en la que el evento constaría como procesado sin haberse aplicado, y la
reentrega lo descartaría creyendo que ya estaba hecho.

En el banco hay además una segunda barrera: el agregado ignora una transición que
ya está aplicada. Eso cubre el caso de dos eventos **distintos** que piden lo
mismo.

## 5. Cuando un evento falla

El consumidor usa **ACK manual**: el mensaje no se confirma hasta que el cambio
está guardado. Ante **cualquier** error —JSON ilegible, tipo de evento
inesperado, la pregunta no existe en el banco, no hay revisores disponibles, la
base de datos no responde— registra el motivo en el log y rechaza el mensaje con
`requeue=false`, así que va a la DLQ.

**No hay reintentos.** Reintentar en el momento solo retrasaría el problema, y
reencolar el mensaje lo devolvería al frente de la cola y produciría un bucle a
toda velocidad. El evento queda guardado en la DLQ, se ve en la consola de
RabbitMQ y se puede volver a publicar cuando la causa esté resuelta.

La única excepción es la llamada gRPC del `revision-service` al banco: el cliente
hace hasta **2 intentos** separados por 1 segundo, y solo si el banco no está o
tarda demasiado. Eso cubre el caso de que el banco esté todavía arrancando. Si
los dos intentos fallan, el evento va a la DLQ como cualquier otro error.

## 6. Publicar un evento a mano

Sirve para probar un consumidor sin montar el flujo completo. En la consola de
RabbitMQ: **Exchanges → saberpro.eventos → Publish message**, con la routing key
del evento y el contenido de su ejemplo de `../contracts/events/ejemplos/`.

La colección de Postman trae esa petición hecha, en la carpeta *0. Comprobación
inicial*, junto con otra que lee los mensajes de
`simulacros.catalogo-preguntas` **sin consumirlos**.

## 7. El séptimo evento, por implementar

`SimulacroFinalizado` lo publicará el `simulacros-service` cuando exista. No
tiene schema todavía porque su contenido lo define ese servicio; la
[guía de integración](GUIA_INTEGRACION.md) explica cómo debe encajar.
