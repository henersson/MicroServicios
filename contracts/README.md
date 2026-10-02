# Contratos de integración

Esta carpeta es **lo único que comparten los microservicios**. No hay librerías
comunes: quien quiera integrarse solo necesita leer estos archivos, que son
estándares y no dependen de ninguna tecnología.

```
contracts/
├── proto/banco_preguntas/v1/banco_preguntas.proto   # el contrato gRPC
└── events/
    ├── <Evento>.v1.schema.json                      # JSON Schema del evento
    └── ejemplos/<Evento>.v1.json                    # un ejemplo realista
```

## `proto/` — gRPC

Define el servicio `BancoPreguntas`, que expone el `banco-preguntas-service` en
el puerto 9091, con dos operaciones:

- **`ObtenerPregunta`**: la usa el `revision-service` para traerse el contenido
  que va a evaluar. Es la comunicación síncrona entre los dos microservicios.
- **`ListarPreguntasPublicadas`**: pensada para el futuro `simulacros-service`.
  Devuelve solo preguntas en estado `PUBLICADA`, con filtros opcionales.

De este archivo se genera el código de los dos lados: el banco lo compila con
Maven y el `revision-service` con `scripts/generar-grpc.ps1`.

## `events/` — RabbitMQ

Un JSON Schema (draft 2020-12) por cada uno de los 6 eventos. El schema valida
el envelope común y el contenido de `data`. La tabla completa de eventos, con su
productor, su consumidor y su routing key, está en
[`../docs/eventos.md`](../docs/eventos.md).

Los ejemplos de `events/ejemplos/` sirven para dos cosas: entender un evento de
un vistazo, y publicarlo a mano desde la consola de RabbitMQ para probar un
consumidor sin montar el flujo completo.

## La regla: solo se agrega, no se modifica

Un contrato publicado es una promesa a quien ya lo consume. Por eso:

- **En el `.proto`, los números de campo nunca se cambian ni se reutilizan.**
  Para agregar un campo se usa el siguiente número libre; para quitar uno se
  marca como `reserved`.
- **En los eventos, agregar un campo opcional no rompe a nadie**: los
  consumidores ignoran lo que no conocen y los schemas no prohíben propiedades
  adicionales. Un cambio incompatible crea un `<Evento>.v2.schema.json`, y la
  versión 1 se mantiene mientras haya consumidores.

Cambios hechos sobre estos contratos:

- El campo `description` de `PreguntaEnviadaARevision.v1.schema.json`, que
  describía un comportamiento que ya no existe (guardar una solicitud pendiente
  cuando no hay revisores). Solo cambió ese texto; la validación es la misma.
- **`PreguntaPublicada.v1.schema.json` pasa de 5 a 4 opciones** (`minItems` y
  `maxItems`), para alinear la invariante 1 con el Taller 1: 3 distractores y 1
  correcta. Es un cambio incompatible y aun así se hizo sobre la versión 1, y no
  en un `v2`, porque la regla de arriba protege a los consumidores existentes y
  este evento todavía no tiene ninguno: el `simulacros-service` no está
  implementado. El ejemplo se actualizó igual.
- En el `.proto` y en `PreguntaRechazadaPorPares.v1.schema.json` solo cambiaron
  comentarios y descripciones: los estados `EN_CONSTRUCCION` y `RECHAZADA` y las 4
  opciones. Ningún número de campo cambió.
