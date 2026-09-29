# Guía de la sustentación

Unos 10 minutos, en 4 partes de dos minutos y medio. Este documento es **lo que
hay que decir**. Lo operativo está en otros dos:

| Para esto | Ve a |
|---|---|
| Preparar el portátil, levantar el sistema, qué petición mandar en cada momento y qué hacer si algo falla | [`GUIA_DEMO.md`](GUIA_DEMO.md) |
| Usar Postman desde cero: importar, environment, mandar peticiones, crear la petición gRPC | [`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) |

La demostración se hace entera con la colección **SaberPro — DEMO sustentación**
(`postman/SaberPro-DEMO.postman_collection.json`): se manda cada petición en
orden, sin escribir nada.

## 1. Los criterios de la rúbrica

Las siglas **C1–C6** aparecen en esta guía, en `GUIA_DEMO.md` y en los nombres de
las carpetas de la colección:

| Sigla | Criterio | Peso | Qué pide el nivel Excelente |
|---|---|---|---|
| **C1** | DDD y Clean Architecture | 20 % | Implementa correctamente el modelo DDD del Taller 1 y separa claramente las capas |
| **C2** | Microservicios y tecnologías | 20 % | Dos o más microservicios independientes, en tecnologías diferentes y con persistencia propia |
| **C3** | API REST y gRPC | 20 % | REST y gRPC funcionan correctamente y están documentadas |
| **C4** | Comunicación asíncrona | 20 % | Eventos con RabbitMQ: productor → broker → consumidor |
| **C5** | Integración y funcionamiento | 10 % | Flujo completo con comunicación síncrona y asíncrona |
| **C6** | Documentación y sustentación | 10 % | Código, arquitectura, API, eventos y ejecución documentados; sustentación clara con participación de todos |

## 2. Reparto

| Integrante | Parte |
|---|---|
| Juan Camilo Benavides Salazar | |
| Henersson Estid Cobo Caicedo | |
| Jhoan Sebastián García Camacho | |
| Brayan Steven Gomes Lasso | |

> **Pendiente:** asignar una parte a cada integrante antes de la sustentación. El
> criterio C6 pide participación de todos, así que esta tabla no puede quedar en
> blanco.

| Parte | Tema | Criterios | Material |
|---|---|---|---|
| 1 | DDD y Clean Architecture | C1 | VS Code + peticiones 05, 06, 07 |
| 2 | Microservicios, REST y gRPC | C2, C3 | Carpeta 1, Swagger y la petición gRPC |
| 3 | Eventos y RabbitMQ | C4 | Carpetas 3 y 4, con logs y consola a la vista |
| 4 | Flujo completo y cierre | C5, C6 | Carpetas 5 y 6, y el diagrama de arquitectura |

## 3. Qué decir en cada parte

El orden exacto de las peticiones y qué pasos se saltan si falta tiempo están en
[`GUIA_DEMO.md`](GUIA_DEMO.md) §3.

### Parte 1 — DDD y Clean Architecture (C1)

| Momento | Qué debe ver el profesor | Qué decir |
|---|---|---|
| `Pregunta.java` en VS Code | El Javadoc que lista las invariantes 1 a 8 y los métodos con nombre de negocio: `enviarARevision`, `publicar`, `archivar`. Ningún setter | «Este es el Aggregate Root. Es el único que puede modificar una pregunta, y cada operación comprueba antes lo que tiene que comprobar» |
| `ValidadorEstructural.java` | El Domain Service que aplica las invariantes 1 a 4 | «Las reglas de contenido están aquí, no en el controlador ni en la base de datos» |
| `revision.py` | El `__post_init__` que impide construir una `Revision` sin revisor | «La invariante 9 se garantiza en el constructor: sin revisor, el objeto no llega a existir» |
| Las 4 carpetas de cada servicio | La misma estructura en los dos, en dos lenguajes distintos | «Las dependencias apuntan al dominio. Y no es una promesa: hay 3 reglas de ArchUnit y 2 contratos de import-linter que hacen fallar el build» |
| Petición **05** | Un `400` con tres errores, cada uno citando su invariante | «El dominio rechaza la pregunta y devuelve los tres problemas juntos, para que el autor corrija en una pasada» |
| Petición **06** | `201` y estado `BORRADOR`, 5 opciones, una correcta | «Ahora sí cumple. Nace en `BORRADOR`: mientras está ahí, solo le importa a su autor» |
| Petición **07** | La pregunta completa | «El identificador se guardó solo en una variable; no copiamos nada a mano» |

### Parte 2 — Microservicios, REST y gRPC (C2, C3)

| Momento | Qué debe ver el profesor | Qué decir |
|---|---|---|
| Petición **01** | `UP`, con su base de datos PostgreSQL y RabbitMQ | «Este microservicio es Java con Spring Boot» |
| Petición **02** | `UP` | «Y este es Python con FastAPI. Tecnologías diferentes, como pide el taller» |
| Petición **04** | Tres revisores | «Estos datos están en la base de datos de revisión. El banco no los conoce: cada servicio es dueño de sus datos» |
| Los dos Swagger | Los 8 endpoints del banco y los 7 de revisión, documentados, con las cabeceras de usuario en cada uno | «Las dos API están documentadas y se pueden probar desde aquí» |
| Petición **09b**, gRPC | La pregunta completa devuelta por gRPC, con `es_correcta` solo en la opción correcta | «REST es la puerta para las personas; gRPC es la puerta para otros servicios. Las dos llaman al mismo caso de uso» |
| gRPC con un id inválido | `INVALID_ARGUMENT` | «Devuelve códigos propios de gRPC, no un 500 genérico» |

### Parte 3 — Eventos y RabbitMQ (C4)

Con la terminal de logs y la consola de RabbitMQ a la vista.

| Momento | Qué debe ver el profesor | Qué decir |
|---|---|---|
| Petición **03** | La tabla de colas, con consumidores en dos de ellas y ninguno en la del tercer servicio | «Un exchange, 3 colas y 3 colas de mensajes fallidos» |
| Petición **08** y los logs | El banco publicando `PreguntaEnviadaARevision` y el servicio de revisión recibiéndolo | «Aquí empieza la coordinación. El banco no llama a nadie: publica un evento y sigue» |
| Petición **09** | Una revisión en `ASIGNADA`, con revisor y con las 5 opciones del snapshot | «Nadie creó esta revisión. El servicio consumió el evento, llamó al banco por gRPC y eligió revisor él solo» |
| Petición **10** | Estado `EN_REVISION` | «El banco cambió solo, al consumir `RevisorAsignado`. Entre los dos estados ha habido un evento, una llamada gRPC y otro evento» |
| Peticiones **11** y **12** | `EN_EVALUACION`, el promedio y la observación con su revisor | «Solo el revisor asignado puede hacer esto, y lo comprueba el agregado» |
| Peticiones **13** y **14** | La revisión `APROBADA` y, sin tocar nada más, la pregunta `APROBADA` en el otro servicio | «La decisión se tomó en un microservicio y el otro la aplicó al recibir el evento» |

### Parte 4 — Flujo completo y cierre (C5, C6)

| Momento | Qué debe ver el profesor | Qué decir |
|---|---|---|
| Petición **15** | Estado `PUBLICADA` | «Publicar es del administrador, no del autor ni del revisor» |
| Petición **16** | Las 5 transiciones en orden, con quién y por qué | «Toda la trazabilidad. Las transiciones del medio las provocaron eventos del otro servicio» |
| Petición **17** | El evento con su `eventId` y la pregunta completa dentro, y que sigue en la cola | «Nadie consume esta cola todavía. Guarda los eventos que el servicio de simulacros encontrará ahí cuando se conecte» |
| Petición **18** | `403` y el rol que haría falta | «Sabemos quién es y no puede: eso es un 403» |
| Petición **19** | `401` con las dos cabeceras que faltan | «Aquí no sabemos quién eres, así que es 401. Son cosas distintas» |
| Peticiones **20** y **21** | `400` citando la invariante 10 | «El agregado no permite decidir con el formato incompleto» |
| El diagrama de [`arquitectura.md`](arquitectura.md) | Los dos servicios, sus bases, RabbitMQ y el tercero punteado, con cada flecha etiquetada | «Este es el sistema completo. El tercer microservicio se conecta leyendo solo la carpeta `contracts`, en la tecnología que elijan» |

**Cierre.** «Dos microservicios, dos tecnologías, dos bases de datos y tres
formas de hablar entre ellos: REST para las personas, gRPC cuando se necesita una
respuesta ahora, y eventos cuando no. Y un tercero que puede entrar sin que
cambiemos una línea de los dos que ya están.»

### Si sobra tiempo

- **Carpeta 7 · Extra: el camino de rechazo (opcional)**: la pregunta vuelve a `BORRADOR` con las
  observaciones del revisor.
- **Carpeta 8 · Extra: un evento que falla no se pierde (opcional)**: se inyecta un evento
  imposible y se ve aparecer en la DLQ. La última petición vacía la DLQ y deja el
  sistema limpio.

## 4. Preguntas probables

| Pregunta | Respuesta corta | Dónde está el detalle |
|---|---|---|
| ¿Por qué gRPC entre revisión y el banco, y no REST? | Es un contrato tipado que se verifica al compilar, y del `.proto` se genera el cliente en cualquier lenguaje. REST queda para las personas | `contracts/proto/` y [`arquitectura.md`](arquitectura.md) |
| ¿Por qué revisión guarda una copia de la pregunta? | Para que el revisor juzgue algo estable: si el autor la edita después, no cambia lo que ya evaluó. Y así no depende del banco en cada pantalla | [`decisiones.md`](decisiones.md), ADR 6 |
| ¿Qué pasa si el mismo evento llega dos veces? | Cada servicio guarda los `eventId` procesados en su tabla `eventos_procesados`, **en la misma transacción** que aplica el cambio. El repetido se descarta | [`eventos.md`](eventos.md) §4 |
| ¿Qué pasa si un evento falla? | Va a la DLQ de su cola y queda guardado. No hay reintentos: reintentar en el momento solo retrasa el problema, y reencolar produce un bucle. Se revisa en la consola de RabbitMQ y se vuelve a publicar | [`eventos.md`](eventos.md) §5, y la carpeta 8 de la DEMO |
| ¿Y el archivado? | Se prueba en los tests del agregado, desde los 3 estados permitidos (`BORRADOR`, `APROBADA` y `PUBLICADA`), y con la petición *Archivar (ADMINISTRADOR)* de la colección original. Publica `PreguntaArchivada` en la misma cola del tercer servicio que `PreguntaPublicada` | `PreguntaTest` y [`eventos.md`](eventos.md) §3 |
| ¿Por qué 401 y 403? | 401 es «no sé quién eres»: faltan las cabeceras o no son válidas. 403 es «sé quién eres y tu rol no alcanza» | [`decisiones.md`](decisiones.md), ADR 8 |
| ¿Por qué no hay `DELETE`? | Una pregunta usada en un simulacro tiene que seguir siendo consultable. La única salida es `ARCHIVADA`, y no existe ninguna operación de borrado: ni endpoint, ni método en el agregado, ni en el repositorio | [`decisiones.md`](decisiones.md), ADR 3 |
| ¿Cómo saben que el dominio no depende de frameworks? | Falla el build si alguien lo rompe: 3 reglas de ArchUnit en Java y 2 contratos de import-linter en Python. Se corren con `.\mvnw.cmd test` y con `lint-imports` | [`arquitectura.md`](arquitectura.md) §3 |
| ¿Cómo se conectará el tercer microservicio? | Leyendo solo `contracts/`: el `.proto` y los JSON Schema. Su cola ya existe y guarda los eventos que nadie ha consumido todavía, y los bloques del `docker-compose.yml` están escritos y comentados | [`GUIA_INTEGRACION.md`](GUIA_INTEGRACION.md) |
| ¿Por qué no hay un estado `RECHAZADA`? | La pregunta vuelve a `BORRADOR` con las observaciones, porque lo que se espera es que el autor la corrija y la reenvíe | [`decisiones.md`](decisiones.md), ADR 2 |
| ¿Comparten código los dos servicios? | Nada. Lo único común es `contracts/` | [`decisiones.md`](decisiones.md), ADR 5 |
| ¿Qué falta por hacer? | El tercer microservicio, el contexto de Usuarios y Roles con JWT real, un API Gateway y el patrón Outbox | [`../README.md`](../README.md) |
