# Flujo de punta a punta

Cómo viaja una pregunta entre los dos microservicios. Los dos diagramas están en
`docs/` y se pueden ver también ejecutando el sistema y corriendo
`prueba-e2e.ps1`, que recorre exactamente estos dos caminos.

## 1. Camino feliz: de BORRADOR a PUBLICADA

```mermaid
sequenceDiagram
    actor Autor
    actor Admin as Administrador
    actor Revisor
    participant B as banco-preguntas-service
    participant MQ as RabbitMQ
    participant R as revision-service

    Autor->>B: POST /api/v1/preguntas
    Note over B: ValidadorEstructural comprueba<br/>las invariantes 1 a 4
    B-->>Autor: 201 BORRADOR

    Autor->>B: PUT /api/v1/preguntas/{id}
    B-->>Autor: 200 EN_CONSTRUCCION

    Autor->>B: POST /{id}/enviar-a-revision
    B-->>Autor: 200 PENDIENTE_REVISION
    B->>MQ: PreguntaEnviadaARevision

    MQ->>R: consume el evento
    R->>B: gRPC ObtenerPregunta
    B-->>R: la pregunta completa
    Note over R: guarda el snapshot y<br/>AsignadorRevisor elige revisor
    R->>MQ: RevisorAsignado
    MQ->>B: consume el evento
    Note over B: PENDIENTE_REVISION → EN_REVISION

    Revisor->>R: PUT /{id}/formato
    R-->>Revisor: 200 EN_EVALUACION
    Revisor->>R: POST /{id}/observaciones
    R-->>Revisor: 201
    Revisor->>R: POST /{id}/decision APROBAR
    Note over R: invariante 10: formato completo<br/>(6 criterios) y promedio ≥ 3.0
    R-->>Revisor: 200 APROBADA
    R->>MQ: PreguntaAprobadaTecnicamente

    MQ->>B: consume el evento
    Note over B: EN_REVISION → APROBADA

    Admin->>B: POST /{id}/publicar
    B-->>Admin: 200 PUBLICADA
    B->>MQ: PreguntaPublicada
    Note over MQ: queda en simulacros.catalogo-preguntas<br/>esperando al tercer servicio
```

Lo importante de este diagrama:

- **Una pregunta recién creada no se envía directamente.** Primero el autor la
  edita y pasa a `EN_CONSTRUCCION`; solo desde ahí se envía (invariante 6).
- **Entre `PENDIENTE_REVISION` y `EN_REVISION` el autor no hace nada.** El cambio
  lo provocan un evento, una llamada gRPC y otro evento. En la práctica tarda
  un par de segundos.
- **La llamada gRPC ocurre fuera de la transacción de base de datos.** Una
  llamada de red no debe mantener abierta una transacción.
- **Los eventos se publican después de confirmar la transacción.** Nunca se
  anuncia un cambio que podría deshacerse.
- **El estado lo cambia siempre el agregado**, comprobando su máquina de estados.
  Un evento que pida una transición imposible se rechaza.

## 2. Camino de rechazo: el autor la reabre y la reenvía

```mermaid
sequenceDiagram
    actor Autor
    actor Revisor
    participant B as banco-preguntas-service
    participant MQ as RabbitMQ
    participant R as revision-service

    Note over B,R: la pregunta ya llegó a EN_REVISION<br/>igual que en el camino feliz

    Revisor->>R: PUT /{id}/formato
    Revisor->>R: POST /{id}/decision RECHAZAR
    Note over R: invariante 11: hace falta<br/>al menos una observación
    R-->>Revisor: 400 si no hay ninguna

    Revisor->>R: POST /{id}/observaciones (×2)
    Revisor->>R: POST /{id}/decision RECHAZAR
    R-->>Revisor: 200 RECHAZADA
    R->>MQ: PreguntaRechazadaPorPares<br/>con las observaciones

    MQ->>B: consume el evento
    Note over B: EN_REVISION → RECHAZADA<br/>guarda observacionesUltimaRevision

    Autor->>B: GET /api/v1/preguntas/{id}
    B-->>Autor: RECHAZADA + las observaciones

    Autor->>B: PUT /api/v1/preguntas/{id}
    B-->>Autor: 200 EN_CONSTRUCCION (la reabre)
    Autor->>B: POST /{id}/enviar-a-revision
    Note over B: vuelve a empezar el ciclo,<br/>con una revisión nueva
```

**Una pregunta rechazada no se descarta.** Queda en `RECHAZADA` con las
observaciones del revisor, y el autor la reabre al editarla: pasa a
`EN_CONSTRUCCION` y desde ahí la reenvía. Está explicado en
[`decisiones.md`](decisiones.md) como ADR 2.

## 3. Qué pasa cuando algo falla

Si al procesar un evento ocurre cualquier error —el JSON no se entiende, la
pregunta no existe en el banco, no hay revisores disponibles, la base de datos no
responde— el consumidor registra el motivo en el log y manda el mensaje a la
**DLQ** (*dead-letter queue*) de su cola. No hay reintentos: reintentar en el
momento solo retrasaría el problema, y reencolar el mensaje produciría un bucle
a toda velocidad.

Lo importante es que **el evento no se pierde**. Queda guardado en la DLQ, se ve
en la consola de RabbitMQ (<http://localhost:15672>) y se puede volver a publicar
cuando la causa esté resuelta. Cada cola de negocio tiene su `<cola>.dlq` a
través del exchange `saberpro.eventos.dlx`.

Los detalles de las colas y de la idempotencia están en
[`eventos.md`](eventos.md).
