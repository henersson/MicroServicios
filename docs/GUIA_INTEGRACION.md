# Guía de integración del tercer microservicio

Esta guía es para quien implemente el `simulacros-service`, el Bounded Context de
Simulacros y Reportes. Está escrita para **cualquier tecnología**: lo único que
hay que leer del repositorio son los archivos de
[`../contracts/`](../contracts/), que son estándares (Protocol Buffers y JSON
Schema).

El encargo del dominio está en
[`../simulacros-service/README.md`](../simulacros-service/README.md).

## 1. Mapa del sistema

Los servicios corren en Docker, en una red con nombre fijo: **`saberpro-net`**.
Las direcciones cambian según desde dónde se llame.

| Recurso | Desde otro contenedor | Desde el host (tu máquina) |
|---|---|---|
| Banco, REST | `banco-preguntas-service:8081` | `localhost:8081` |
| Banco, gRPC | `banco-preguntas-service:9091` | `localhost:9091` |
| Revisión, REST | `revision-service:8082` | `localhost:8082` |
| RabbitMQ, AMQP | `rabbitmq:5672` | `localhost:5672` |
| RabbitMQ, consola | `rabbitmq:15672` | `localhost:15672` |

Credenciales de desarrollo de RabbitMQ: `saberpro` / `saberpro`.

Lo que ya está reservado para el nuevo servicio: el puerto **8083** para su API,
el **5435** para su base de datos y la cola
**`simulacros.catalogo-preguntas`**, que **ya existe**. Guarda los eventos
`PreguntaPublicada` y `PreguntaArchivada` que todavía no ha consumido nadie.
Cuando el nuevo servicio se conecte, los encontrará ahí.

## 2. Qué se espera del nuevo servicio

Para que encaje con los otros dos:

- **Un `Dockerfile`** y su bloque en `docker-compose.yml`. El contexto de build
  es la raíz del repositorio, porque hace falta `contracts/proto`.
- **Un endpoint de salud** que devuelva `200` cuando el servicio esté listo, para
  el `healthcheck` del compose.
- **Toda la configuración por variables de entorno**, sin nada hardcodeado. En
  `.env.example` ya están las variables comentadas.
- **Su propia base de datos** en el puerto 5435. No se conecta a
  `postgres-banco` ni a `postgres-revision`.
- **Rutas versionadas**: `/api/v1/simulacros/**` y `/api/v1/reportes/**`.
- **Errores en formato Problem Details (RFC 7807)** con el campo extra
  `errores[]`, igual que los otros dos. Así un cliente que hable con los tres no
  tiene que distinguir de cuál viene el error.
- **Usuario simulado con cabeceras**: `X-Usuario-Id` y `X-Usuario-Rol`. Si faltan
  o no son válidas, **401**; si el rol no puede hacer la operación, **403**.
- **Las 4 capas** de Clean Architecture: dominio, aplicación, infraestructura e
  interfaces, con las dependencias apuntando al dominio. Conviene añadir una
  comprobación automática, como hacen los otros dos servicios (ArchUnit en Java,
  import-linter en Python).

## 3. Consumir la cola `simulacros.catalogo-preguntas`

Es lo primero que conviene hacer, porque se puede probar sin escribir nada más.

La cola recibe dos eventos: `PreguntaPublicada` (la pregunta completa) y
`PreguntaArchivada` (solo el identificador). El formato del envelope y la tabla
de eventos están en [`eventos.md`](eventos.md); los JSON Schema y un ejemplo de
cada uno, en `../contracts/events/`.

Para saber qué evento llegó, mira `eventType` en el cuerpo o la cabecera AMQP
`eventType`.

Las reglas que hay que cumplir:

1. **Lector tolerante.** Ignora los campos que no conozcas. Los schemas no
   prohíben propiedades adicionales, así que agregar un campo opcional no rompe a
   nadie.
2. **Idempotencia.** Guarda los `eventId` ya procesados en tu propia tabla
   `eventos_procesados(event_id)` y descarta los repetidos, **en la misma
   transacción** que aplica el cambio. RabbitMQ entrega al menos una vez.
3. **ACK manual.** Confirma el mensaje cuando el cambio esté guardado, no antes.
4. **DLQ.** Ante cualquier error, rechaza con `requeue=false`: el mensaje irá a
   `simulacros.catalogo-preguntas.dlq` y no se perderá. No reencoles, porque
   produces un bucle.
5. **Fechas en UTC.** `occurredAt` viene en ISO-8601 terminado en `Z`.

Qué guardar de cada `PreguntaPublicada`: el identificador, el contenido, las 5
opciones con su marca de correcta, la competencia, el tema y el nivel de
dificultad. Con eso puedes armar un simulacro sin volver a preguntar al banco. Al
recibir `PreguntaArchivada`, marca esa pregunta como no disponible para
simulacros nuevos, pero **no la borres**: los simulacros pasados la referencian.

## 4. Llamar al banco cuando haga falta

### Opción A: gRPC (recomendada)

El banco expone `ListarPreguntasPublicadas`, pensada precisamente para este
servicio. Devuelve solo preguntas en estado `PUBLICADA`, con filtros opcionales:

```protobuf
message ListarPreguntasPublicadasRequest {
  string competencia_codigo = 1;
  string tema = 2;
  string nivel_dificultad = 3;
  int32 limite = 4;
}
```

Un filtro vacío significa *no filtrar por ese criterio*, y `limite: 0` aplica el
valor por defecto de 50.

El cliente se genera desde
[`../contracts/proto/banco_preguntas/v1/banco_preguntas.proto`](../contracts/proto/banco_preguntas/v1/banco_preguntas.proto)
con las herramientas de tu lenguaje. El servidor tiene **Server Reflection**
activado, así que también se puede explorar sin el `.proto`:

```powershell
docker run --rm --network saberpro-net fullstorydev/grpcurl -plaintext banco-preguntas-service:9091 list
```

Errores: `NOT_FOUND` si la pregunta no existe, `INVALID_ARGUMENT` si el
identificador no es un UUID. En proto3 un `false` y una cadena vacía no se
serializan, así que las opciones incorrectas llegan sin el campo `es_correcta`:
es el comportamiento normal, no un error.

### Opción B: REST

El banco publica su documento OpenAPI en vivo en
`http://localhost:8081/v3/api-docs` (Swagger UI en `/swagger-ui.html`). De ahí se
puede generar un cliente. El endpoint útil es
`GET /api/v1/preguntas?estado=PUBLICADA`, con paginación.

## 5. Publicar `SimulacroFinalizado`

Cuando un simulacro termine, publica el evento en el exchange
`saberpro.eventos`. Los cuatro pasos:

1. Arma el envelope con los 6 campos: `eventId` (UUID nuevo), `eventType`
   (`SimulacroFinalizado`), `eventVersion` (`1`), `occurredAt` (UTC con `Z`),
   `source` (`simulacros-service`) y `data`.
2. Publícalo con routing key `simulacros.simulacro.finalizado`, y con las
   propiedades AMQP `content_type: application/json`, `delivery_mode: 2` y
   `message_id` igual al `eventId`.
3. **Publica después de confirmar tu transacción**, nunca antes.
4. Crea su JSON Schema en `../contracts/events/` y un ejemplo en
   `../contracts/events/ejemplos/`, siguiendo el formato de los otros seis.

Hoy nadie consume ese evento, así que también hará falta declarar su binding.

## 6. Probar sin montar el flujo completo

Para probar tu consumidor no hace falta crear una pregunta, enviarla a revisión,
evaluarla y publicarla. Publica el evento a mano desde la consola de RabbitMQ:
**Exchanges → saberpro.eventos → Publish message**, con routing key
`banco.pregunta.publicada` y el contenido de
`../contracts/events/ejemplos/PreguntaPublicada.v1.json`.

La colección de Postman (`../postman/`) trae esa petición hecha, en la carpeta
*0. Comprobación inicial*, junto con otra que lee los mensajes de tu cola **sin
consumirlos**.

Para ver cuántos mensajes te esperan: **Queues → simulacros.catalogo-preguntas**
en la consola.

## 7. Agregar tu servicio al sistema

En `docker-compose.yml` ya están escritos y comentados los bloques de
`postgres-simulacros`, `simulacros-service` y el volumen
`postgres-simulacros-datos`. En `.env.example` están sus variables, también
comentadas. Hay que descomentarlos y ajustar lo que cambie según la tecnología
que elijas.

Trabaja en una rama: `feature/simulacros-service`.

## 8. Librerías por lenguaje

| Lenguaje | gRPC | RabbitMQ |
|---|---|---|
| TypeScript / Node | `@grpc/grpc-js` + `@grpc/proto-loader` | `amqplib` |
| Go | `google.golang.org/grpc` + `protoc-gen-go` | `github.com/rabbitmq/amqp091-go` |
| C# / .NET | `Grpc.Net.Client` + `Grpc.Tools` | `RabbitMQ.Client` |
| PHP | `grpc/grpc` + `google/protobuf` | `php-amqplib/php-amqplib` |

## 9. Checklist de integración

- [ ] `docker compose up --build` levanta tu servicio y llega a `healthy`
- [ ] Tu base de datos es propia y aplica sus migraciones al arrancar
- [ ] Consumes `simulacros.catalogo-preguntas` con ACK manual e idempotencia
- [ ] Un evento que falla acaba en tu DLQ, no se pierde y no reencola
- [ ] Llamas al banco por gRPC o por REST, sin tocar su base de datos
- [ ] Publicas `SimulacroFinalizado` después de confirmar tu transacción, con su
      schema y su ejemplo en `../contracts/events/`
- [ ] Tu API usa `/api/v1/simulacros/**`, Problem Details y 401 / 403
- [ ] Tienes las 4 capas y una comprobación automática de la regla de dependencias
- [ ] Tus invariantes 12, 13 y 14 están implementadas y probadas
- [ ] Añadiste tus peticiones a la colección de Postman
