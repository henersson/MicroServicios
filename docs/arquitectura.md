# Arquitectura del sistema

## 1. Diagrama general

Los dos microservicios, sus bases de datos, RabbitMQ y el tercer servicio que
queda pendiente. Cada flecha dice con qué tecnología viaja.

```mermaid
flowchart TB
    subgraph clientes[Clientes]
        UI[Postman / Swagger / scripts]
    end

    subgraph banco[banco-preguntas-service · Java 21 + Spring Boot]
        BAPI[API REST :8081]
        BGRPC[Servidor gRPC :9091]
    end

    subgraph revision[revision-service · Python 3.13 + FastAPI]
        RAPI[API REST :8082]
    end

    subgraph simulacros[simulacros-service · pendiente]
        SAPI[API REST :8083]
    end

    BDB[(postgres-banco<br/>:5433)]
    RDB[(postgres-revision<br/>:5434)]
    SDB[(postgres-simulacros<br/>:5435)]

    subgraph broker[RabbitMQ :5672 · exchange saberpro.eventos]
        Q1[[revision.preguntas-enviadas]]
        Q2[[banco.resultados-revision]]
        Q3[[simulacros.catalogo-preguntas]]
    end

    UI -->|REST| BAPI
    UI -->|REST| RAPI

    BAPI --- BDB
    RAPI --- RDB
    SAPI -.- SDB

    BAPI -->|RabbitMQ<br/>PreguntaEnviadaARevision| Q1
    Q1 -->|RabbitMQ| RAPI

    RAPI -->|gRPC<br/>ObtenerPregunta| BGRPC

    RAPI -->|RabbitMQ<br/>RevisorAsignado<br/>PreguntaAprobadaTecnicamente<br/>PreguntaRechazadaPorPares| Q2
    Q2 -->|RabbitMQ| BAPI

    BAPI -->|RabbitMQ<br/>PreguntaPublicada<br/>PreguntaArchivada| Q3
    Q3 -.->|RabbitMQ| SAPI

    SAPI -.->|gRPC<br/>ListarPreguntasPublicadas| BGRPC

    classDef pendiente stroke-dasharray: 5 5
    class simulacros,SAPI,SDB pendiente
```

Lo que conviene mirar en el diagrama:

- **Cada servicio tiene su propia base de datos** y nadie toca la del otro. Los
  datos que revisión necesita del banco los pide por gRPC y guarda una copia.
- **Los dos sentidos de la asincronía.** El banco avisa a revisión con
  `PreguntaEnviadaARevision`, y revisión le contesta con tres eventos por la
  cola `banco.resultados-revision`.
- **La única llamada síncrona entre servicios es gRPC.** Ningún servicio llama
  al REST del otro.
- **La cola del tercer servicio ya existe.** Guarda los eventos
  `PreguntaPublicada` y `PreguntaArchivada` que todavía no ha consumido nadie.
  Cuando el tercer microservicio se conecte, los encontrará ahí.

## 2. Context map

```mermaid
flowchart LR
    BP[Banco de Preguntas<br/>implementado]
    CR[Ciclo de Vida y Revisión<br/>implementado]
    SR[Simulacros y Reportes<br/>pendiente]
    UR[Usuarios y Roles<br/>simulado con cabeceras]

    BP -->|"Customer-Supplier<br/>RabbitMQ + gRPC"| CR
    CR -->|"Published Language<br/>RabbitMQ"| BP
    BP -->|"Published Language<br/>RabbitMQ"| SR
    SR -.->|"Open Host Service<br/>gRPC"| BP
    UR -.->|"cabeceras X-Usuario-*"| BP
    UR -.->|"cabeceras X-Usuario-*"| CR

    classDef pendiente stroke-dasharray: 5 5
    class SR,UR pendiente
```

| Relación | Patrón DDD | Tecnología | Por qué |
|---|---|---|---|
| Banco → Revisión | Customer-Supplier | RabbitMQ (`PreguntaEnviadaARevision`) | El banco decide cuándo empieza una revisión; revisión se adapta a lo que el banco publica |
| Revisión → Banco (datos) | — | gRPC (`ObtenerPregunta`) | Revisión necesita el contenido a evaluar y el banco lo sirve |
| Revisión → Banco (decisión) | Published Language | RabbitMQ (3 eventos) | El contrato de los eventos es el lenguaje común; ninguno depende del código del otro |
| Banco → Simulacros | Published Language | RabbitMQ (`PreguntaPublicada`, `PreguntaArchivada`) | El catálogo se arma escuchando, sin preguntar |
| Simulacros → Banco | Open Host Service | gRPC (`ListarPreguntasPublicadas`) | El banco ofrece una operación pensada para cualquier consumidor |
| Usuarios y Roles → los dos | — | Cabeceras `X-Usuario-Id` y `X-Usuario-Rol` | Ese contexto no se implementa; se simula en el borde de cada servicio |

## 3. Las 4 capas

Los dos servicios tienen la misma estructura, con los nombres en el idioma de
cada uno.

| Capa | Qué va aquí | Qué **no** puede conocer |
|---|---|---|
| `dominio` | Los agregados, los Value Objects, los servicios de dominio, los eventos y las interfaces de repositorio | Nada: ni framework, ni base de datos, ni broker, ni gRPC |
| `aplicacion` | Los casos de uso, los puertos de salida y los DTO | Infraestructura e interfaces |
| `infraestructura` | JPA o SQLAlchemy, RabbitMQ, gRPC y la configuración | — |
| `interfaces` | La API REST, los esquemas de entrada y salida y el manejo de errores | — |

La regla es una sola: **las dependencias apuntan hacia el dominio**. El dominio
declara qué necesita (por ejemplo `PreguntaRepository`) y la infraestructura lo
implementa.

### Cómo se verifica

No es una regla escrita en un documento: falla el build.

**En Java, 3 reglas de ArchUnit** (`ArquitecturaTest`):

1. Las capas van en orden: nadie accede a `interfaces`; a `infraestructura` solo
   desde `interfaces`; a `aplicacion` solo desde `interfaces` e
   `infraestructura`.
2. El dominio no depende de `org.springframework`, `jakarta.persistence`,
   `org.springframework.amqp`, `io.grpc` ni `com.fasterxml.jackson`.
3. La aplicación no depende de `infraestructura` ni de `interfaces`.

**En Python, 2 contratos de import-linter** (`pyproject.toml`):

1. Las capas van en orden: `interfaces` → `infraestructura` → `aplicacion` →
   `dominio`.
2. El dominio no importa `fastapi`, `sqlalchemy`, `pydantic`, `aio_pika` ni
   `grpc`.

En Python el contrato de capas está **apilado** y no con `interfaces` e
`infraestructura` como hermanas independientes. La razón es concreta:
`app/interfaces/api/dependencias.py` es el punto de composición del servicio, el
sitio donde se cablean los casos de uso con las implementaciones reales, y por
tanto importa de `infraestructura` a propósito. Declararlas hermanas convertiría
ese cableado en una violación. Lo que el contrato sí impide es lo que importa:
que `aplicacion` mire hacia afuera y que el `dominio` dependa de cualquier cosa.
Las 3 reglas de ArchUnit expresan exactamente lo mismo en Java.

## 4. Correspondencia con el modelo DDD del Taller 1

### `banco-preguntas-service`

| Elemento | Concepto DDD | Dónde está |
|---|---|---|
| `Pregunta` | **Aggregate Root** | `dominio/modelo/Pregunta.java` |
| `Opcion`, `Justificacion`, `Bibliografia`, `Competencia`, `Tema`, `Subtema` | Value Objects | `dominio/modelo/` |
| `EstadoPregunta`, `NivelDificultad` | Value Objects (enum) | `dominio/modelo/` |
| `ContenidoPregunta` | Value Object | `dominio/modelo/` |
| `CambioEstado` | Value Object (historial) | `dominio/modelo/` |
| `ValidadorEstructural` | **Domain Service** | `dominio/servicios/` |
| `PreguntaEnviadaARevision`, `PreguntaPublicada`, `PreguntaArchivada` | **Domain Events** | `dominio/eventos/` |
| `PreguntaRepository` | Repository | `dominio/repositorios/` |

Garantiza las **invariantes 1 a 8**: las cuatro de contenido las comprueba
`ValidadorEstructural`; la 5 y la 6 el propio agregado al cambiar de estado; la 7
al editar; y la 8 por ausencia, porque no existe ninguna operación de borrado.

### `revision-service`

| Elemento | Concepto DDD | Dónde está |
|---|---|---|
| `Revision` | **Aggregate Root** | `dominio/modelo/revision.py` |
| `FormatoEvaluacion`, `Observacion` | Value Objects | `dominio/modelo/` |
| `EstadoRevision`, `Decision`, `CriterioEvaluacion`, `NivelDificultad` | Value Objects (enum) | `dominio/modelo/enums.py` |
| `SnapshotPregunta`, `OpcionSnapshot` | Value Objects | `dominio/modelo/snapshot_pregunta.py` |
| `RevisorDisponible` | Proyección local | `dominio/modelo/revisor_disponible.py` |
| `AsignadorRevisor` | **Domain Service** | `dominio/servicios/` |
| `RevisorAsignado`, `PreguntaAprobadaTecnicamente`, `PreguntaRechazadaPorPares` | **Domain Events** | `dominio/eventos/eventos.py` |
| `RevisionRepository`, `RevisorRepository` | Repositories | `dominio/repositorios/` |

Garantiza las **invariantes 9 a 11**: la 9 en el constructor, que no deja
construir una `Revision` sin revisor; la 10 y la 11 al decidir.

### Qué se agregó respecto al Taller 1

| Elemento | Por qué |
|---|---|
| Evento **`RevisorAsignado`** | El Taller 1 no lo tenía, y sin él el banco no puede saber si la revisión empezó de verdad. Si no hay revisores disponibles no se crea ninguna revisión, y la pregunta debe quedarse en `PENDIENTE_REVISION` en vez de pasar a `EN_REVISION`. Es lo que hace cumplible la invariante 9 entre dos contextos separados |
| **`SnapshotPregunta`** | Revisión guarda una copia del contenido que va a evaluar, obtenida por gRPC. Así el revisor juzga algo estable: una edición posterior no cambia lo que ya evaluó, y revisión no tiene que consultar al banco en cada pantalla |
| **`RevisorDisponible`** | El Bounded Context de Usuarios y Roles no se implementa, así que revisión mantiene su propia lista de revisores |
| **`CambioEstado`** y el historial | Trazabilidad: quién movió la pregunta, cuándo y por qué |
| **`ContenidoPregunta`** | Agrupa todo lo que el autor puede escribir, para poder validarlo antes de que el agregado exista y para que crear y editar compartan la misma validación |

Los dos primeros están explicados en [`decisiones.md`](decisiones.md) como ADR 1
y ADR 6.
