# Banco de Preguntas Saber Pro — Arquitectura de Microservicios

Sistema para gestionar un banco de preguntas de selección múltiple de las Pruebas
Saber Pro. Un autor crea una pregunta, el sistema comprueba que esté bien
construida, la envía a revisión por pares y, si un revisor la aprueba, un
administrador la publica.

Son dos microservicios independientes, cada uno con su propia base de datos, que
se comunican por REST, gRPC y RabbitMQ. Cada uno corresponde a un Bounded
Context del diseño DDD del Taller 1.

| Microservicio | Bounded Context del Taller 1 | De qué se ocupa |
|---|---|---|
| `banco-preguntas-service` | Banco de Preguntas | El ciclo de vida de la pregunta y sus invariantes de contenido |
| `revision-service` | Ciclo de Vida y Revisión | La revisión por pares: asignar revisor, evaluar y decidir |

## Integrantes

- Juan Camilo Benavides Salazar
- Henersson Estid Cobo Caicedo
- Jhoan Sebastián García Camacho
- Brayan Steven Gomes Lasso

**Docente:** Wilson Libardo Pantoja Yepez
Arquitectura de Microservicios · Universidad del Cauca · 2026-2

## Tecnologías y puertos

| Servicio | Tecnología | Puertos (host) | Base de datos |
|---|---|---|---|
| `banco-preguntas-service` | Java 21 · Spring Boot 4.1.1 | 8081 REST, 9091 gRPC | `postgres-banco` en 5433 |
| `revision-service` | Python 3.13 · FastAPI | 8082 REST | `postgres-revision` en 5434 |
| RabbitMQ | 4.1 con management | 5672 AMQP, 15672 consola | — |

Las dos bases son PostgreSQL 16.10, en contenedores y volúmenes separados.
Ningún servicio se conecta a la base del otro.

## Requisitos

Para **ejecutar** el sistema solo hace falta **Docker Desktop**. Todo lo demás
está dentro de las imágenes.

Para **correr los tests en local** hacen falta además:

- JDK 21 (el proyecto usa el Maven Wrapper, no hay que instalar Maven)
- Python 3.13

Si PowerShell bloquea los scripts, se ejecutan con `-ExecutionPolicy Bypass`
como muestran los comandos de abajo, sin cambiar la configuración del equipo.

## Levantar y bajar

```powershell
# Construye las imágenes, levanta los 5 contenedores y espera a que estén sanos
powershell -ExecutionPolicy Bypass -File .\scripts\levantar.ps1

# Levanta sin reconstruir, con las imágenes que ya están en el equipo
powershell -ExecutionPolicy Bypass -File .\scripts\levantar.ps1 -SinConstruir

# Apaga conservando los datos
powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1

# Apaga borrando también los datos, para empezar de cero
powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1 -Volumenes
```

Al terminar, `levantar.ps1` imprime las direcciones de Swagger, la consola de
RabbitMQ y los puertos.

**La primera vez tarda unos 5 minutos y necesita internet**, porque construye las
dos imágenes descargando las dependencias de Maven y de pip. Una vez construidas,
`-SinConstruir` levanta el sistema en unos 35 segundos y **sin conexión**, en el
mismo equipo donde se construyeron. Si falta alguna de las dos imágenes, el
script lo dice y no levanta nada.

`bajar.ps1 -Volumenes` borra los datos pero **no las imágenes**. Aun así, antes de
una demostración conviene no ejecutar `docker system prune` ni borrar imágenes a
mano: sin ellas hay que volver a construir, y eso exige internet.

Para la sustentación hay un script que hace todo esto de una vez —comprueba
Docker, las imágenes y los 7 puertos, arranca limpio, verifica la salud del
sistema y abre Swagger, la consola de RabbitMQ y los logs:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\preparar-sustentacion.ps1
```

El guion completo de la demostración está en
[`docs/GUIA_DEMO.md`](docs/GUIA_DEMO.md).

## Probar

### La prueba integral

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1
```

Recorre **24 comprobaciones** contra el sistema levantado: el camino feliz de
punta a punta, el camino de rechazo, las reglas del dominio y que las tres DLQ
estén vacías. Crea sus propias preguntas, así que se puede repetir. Devuelve
código de salida 0 si todas pasan.

### Postman

Importa los dos archivos de `postman/` y selecciona el environment
*SaberPro - local (Docker)* arriba a la derecha. La colección tiene
**45 peticiones** en 5 carpetas, todas con tests:

| Carpeta | Para qué |
|---|---|
| 0. Comprobación inicial | Que los servicios responden, y dos peticiones para ver los eventos en RabbitMQ |
| 1. Banco - Preguntas | Cada endpoint del banco por separado |
| 2. Revisión - Revisores y revisiones | Cada endpoint de revisión por separado |
| 3. Flujo completo | El recorrido de punta a punta, con esperas automáticas |
| 4. Casos inválidos | 11 peticiones que **deben fallar**, con el código y el mensaje esperados |

Se corre entera con el Collection Runner, en orden.

Hay una segunda colección, **SaberPro — DEMO sustentación**
(`postman/SaberPro-DEMO.postman_collection.json`), preparada para proyectar en la
sustentación: 26 peticiones numeradas, cada una con su documentación, sus pruebas
y un visualizador de letra grande. Se manda una tras otra sin escribir nada; los
pasos que dependen de un evento esperan por su cuenta. Usa el mismo environment.
El guion está en [`docs/sustentacion.md`](docs/sustentacion.md).

### Swagger

- Banco: <http://localhost:8081/swagger-ui.html>
- Revisión: <http://localhost:8082/docs>

Toda petición necesita las cabeceras `X-Usuario-Id` (UUID) y `X-Usuario-Rol`
(`AUTOR`, `REVISOR`, `ADMINISTRADOR`, `DOCENTE`, `ESTUDIANTE` o `COORDINADOR`).
Swagger las pide en cada endpoint.

### gRPC

El banco expone un servidor gRPC en `localhost:9091` con Server Reflection, así
que Postman descubre el servicio sin cargar el `.proto`: **New → gRPC**, URL
`localhost:9091` (sin `http://`), marcar *Use server reflection* y elegir
`saberpro.banco.v1.BancoPreguntas / ObtenerPregunta`. La descripción de la
colección tiene los pasos completos.

Desde la línea de comandos, con grpcurl en Docker:

```powershell
docker run --rm --network saberpro-net fullstorydev/grpcurl -plaintext banco-preguntas-service:9091 list
```

### Consola de RabbitMQ

<http://localhost:15672> con usuario `saberpro` y contraseña `saberpro`. Ahí se
ven el exchange, las 3 colas de negocio, sus 3 DLQ y los mensajes que esperan.

## Tests en local

```powershell
# banco-preguntas-service: 57 tests (dominio + 3 reglas de ArchUnit)
cd banco-preguntas-service
.\mvnw.cmd test

# revision-service: 58 tests + 2 contratos de import-linter
cd ..\revision-service
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
powershell -ExecutionPolicy Bypass -File ..\scripts\generar-grpc.ps1
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\lint-imports.exe
```

`generar-grpc.ps1` genera el cliente gRPC desde `contracts/proto`. Hace falta
antes de `lint-imports` y de arrancar el servicio fuera de Docker; dentro de
Docker el Dockerfile lo genera solo.

## Estructura del repositorio

```
├── banco-preguntas-service/     Java 21 + Spring Boot
│   └── src/main/java/.../banco/
│       ├── dominio/             agregado Pregunta, Value Objects, ValidadorEstructural
│       ├── aplicacion/          casos de uso, puertos y DTO
│       ├── infraestructura/     JPA, RabbitMQ, servidor gRPC, configuración
│       └── interfaces/          API REST y manejo de errores
├── revision-service/            Python 3.13 + FastAPI
│   └── app/
│       ├── dominio/             agregado Revision, Value Objects, AsignadorRevisor
│       ├── aplicacion/          casos de uso, puertos y unidad de trabajo
│       ├── infraestructura/     SQLAlchemy, RabbitMQ, cliente gRPC, configuración
│       └── interfaces/          API REST y manejo de errores
├── contracts/                   lo único compartido: .proto y schemas de eventos
├── infra/rabbitmq/              topología del broker
├── postman/                     colección y environment
├── scripts/                     levantar, bajar, generar-grpc, prueba-e2e, preparar-sustentacion
├── docs/                        arquitectura, flujo, eventos, decisiones, guías
├── simulacros-service/          encargo del tercer microservicio
└── docker-compose.yml
```

## Cómo se cumple el Taller 2

| Requisito del enunciado | Dónde se cumple |
|---|---|
| Mínimo 2 microservicios del Taller 1 | `banco-preguntas-service` y `revision-service` |
| Tecnologías diferentes | Java 21 + Spring Boot · Python 3.13 + FastAPI |
| Correspondencia con los Bounded Contexts | Banco de Preguntas · Ciclo de Vida y Revisión ([context map](docs/arquitectura.md)) |
| DDD: Entities, Value Objects, Aggregates, Domain Services, Domain Events | 2 Aggregate Roots, los 9 Value Objects del Taller 1, 2 Domain Services y 6 eventos ([tabla](docs/arquitectura.md)) |
| Clean Architecture con capas separadas | 4 capas en cada servicio, verificadas por ArchUnit e import-linter |
| Persistencia propia, sin compartir | `postgres-banco` (5 tablas) y `postgres-revision` (5 tablas) |
| API REST en cada microservicio | 8 endpoints en el banco, 7 en revisión, ambos con Swagger |
| Al menos una operación gRPC | `ObtenerPregunta` y `ListarPreguntasPublicadas` |
| Comunicación síncrona entre microservicios | Revisión llama a `ObtenerPregunta` al crear cada revisión |
| RabbitMQ con al menos un evento | 6 eventos ([catálogo](docs/eventos.md)) |
| Productor → broker → consumidor | Dos flujos en direcciones opuestas: banco → revisión y revisión → banco |
| Código en Git | Este repositorio |
| Diagrama de arquitectura | [`docs/arquitectura.md`](docs/arquitectura.md) |
| Pruebas en Postman | `postman/`, 45 peticiones con tests |
| README para ejecutar y probar | Este archivo |

## Problemas técnicos encontrados

- **Windows PowerShell 5.1 y la codificación.** Decodifica las respuestas como
  ISO-8859-1 cuando el `Content-Type` no trae `charset`, y los acentos salían
  destrozados. Los dos servicios declaran ahora `charset=utf-8`.
- **BOM en los scripts.** PowerShell 5.1 lee un `.ps1` sin BOM como ANSI y los
  acentos rompen el parser. Los scripts se guardan en UTF-8 **con BOM**, y
  `.vscode/settings.json` obliga al editor a mantenerlo.
- **protoc en Alpine.** El binario que descarga Maven está enlazado contra
  glibc y Alpine usa musl. La etapa de compilación del Dockerfile usa Debian; la
  de ejecución sigue en Alpine.
- **Imports del código generado por protoc.** protoc emite un import absoluto
  que no funciona dentro de un paquete. `generar-grpc.ps1` lo reescribe a
  relativo, y el Dockerfile hace lo mismo.
- **`SelectorEventLoop` en Windows.** `psycopg` en modo asíncrono no funciona
  con el bucle de eventos que Python usa por defecto en Windows. El servicio lo
  cambia al arrancar; en Linux no hace nada.
- **Healthcheck de RabbitMQ.** `rabbitmq-diagnostics` acepta un solo comando por
  invocación, así que el healthcheck usa `CMD-SHELL` y encadena dos.

## Trabajo futuro

- **Tercer microservicio, `simulacros-service`.** El Bounded Context de
  Simulacros y Reportes. Su cola ya existe y guarda los eventos
  `PreguntaPublicada` y `PreguntaArchivada` que todavía no ha consumido nadie:
  ver [la guía de integración](docs/GUIA_INTEGRACION.md) y
  [el encargo](simulacros-service/README.md).
- **Contexto de Usuarios y Roles con autenticación real (JWT).** Hoy el usuario
  se simula con cabeceras. Ese contexto traería las **invariantes 15 y 16** del
  Taller 1: el correo de un usuario es único, y un usuario tiene exactamente un
  rol activo.
- **API Gateway** delante de los dos servicios, para no exponer sus puertos.
- **Patrón Outbox.** Hoy los eventos se publican después de confirmar la
  transacción; si el proceso muere entre el commit y el envío, el evento se
  pierde. El Outbox cierra esa ventana.
- **Reintentos automáticos desde la DLQ**, con el plugin Shovel de RabbitMQ.
  Hoy el reenvío se hace a mano desde la consola.

## Documentación

| Documento | Qué contiene |
|---|---|
| [`docs/arquitectura.md`](docs/arquitectura.md) | Diagrama general, context map, las 4 capas y el modelo DDD |
| [`docs/flujo-e2e.md`](docs/flujo-e2e.md) | Diagramas de secuencia del camino feliz y del rechazo |
| [`docs/eventos.md`](docs/eventos.md) | Los 6 eventos, el envelope, las colas y la idempotencia |
| [`docs/decisiones.md`](docs/decisiones.md) | Las 8 decisiones de arquitectura y por qué |
| [`docs/GUIA_INTEGRACION.md`](docs/GUIA_INTEGRACION.md) | Cómo conectar el tercer microservicio, en cualquier tecnología |
| [`docs/GUIA_DEMO.md`](docs/GUIA_DEMO.md) | Cómo montar el sistema y hacer la demostración, paso a paso |
| [`docs/GUIA_POSTMAN.md`](docs/GUIA_POSTMAN.md) | Cómo usar Postman desde cero para esta demostración |
| [`docs/sustentacion.md`](docs/sustentacion.md) | Qué decir en la sustentación, el reparto y las preguntas probables |
| [`contracts/README.md`](contracts/README.md) | Los contratos compartidos y su regla de cambio |
