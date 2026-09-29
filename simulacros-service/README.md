# `simulacros-service` — Bounded Context Simulacros y Reportes

Este microservicio **no está implementado**. El Taller 2 exige mínimo dos, y los
dos que están son el banco de preguntas y la revisión. Este documento describe el
encargo para quien lo implemente.

Los pasos técnicos para conectarlo están en
[`../docs/GUIA_INTEGRACION.md`](../docs/GUIA_INTEGRACION.md).

## De qué se ocupa

Armar simulacros con preguntas ya publicadas, recibir las respuestas de los
estudiantes, calificarlos automáticamente y producir reportes.

## Modelo de dominio del Taller 1

### Aggregate Root: `Simulacro`

Atributos: identificador, estudiante, la lista de preguntas incluidas, las
respuestas registradas, el estado (`EN_CURSO`, `FINALIZADO`, `VENCIDO`), el
momento de inicio, el tiempo límite y la calificación.

### Value Objects

`RespuestaEstudiante` (pregunta, opción elegida, si acertó), `Calificacion`
(puntaje y porcentaje) y `TiempoLimite`.

### Invariantes

| # | Invariante | Dónde se comprueba |
|---|---|---|
| **12** | Un simulacro **solo contiene preguntas PUBLICADAS**. Nunca una en borrador, en revisión, aprobada sin publicar ni archivada | Al armar el simulacro, filtrando el catálogo local |
| **13** | **No se aceptan respuestas una vez vencido el tiempo límite.** El simulacro pasa a `VENCIDO` y se califica con lo que haya | Al registrar cada respuesta, comparando con `iniciadoEn + tiempoLimite` |
| **14** | La **calificación es automática y no se modifica a mano.** No hay ningún endpoint que permita cambiarla | Solo `CalculadoraSimulacro` la produce; el agregado no expone forma de escribirla |

La invariante 12 se cumple casi por construcción: si el catálogo local se
alimenta solo de los eventos `PreguntaPublicada` y se marca como no disponible lo
que llega por `PreguntaArchivada`, el catálogo **solo puede contener publicadas**.

### Domain Service: `CalculadoraSimulacro`

Calcula la calificación a partir de las respuestas. Es un servicio de dominio y
no un método del agregado porque la fórmula puede cambiar (ponderar por
competencia, por nivel de dificultad) sin que cambie el agregado.

### Domain Event: `SimulacroFinalizado`

Se publica cuando un simulacro termina, con el identificador del simulacro, el
estudiante, la calificación y el resumen por competencia. Hoy nadie lo consume;
serviría para un contexto de reportes o de seguimiento académico.

## Qué guardar de cada `PreguntaPublicada`

El evento llega con la pregunta completa. Conviene guardar: el identificador, el
contexto y la pregunta directa, las 5 opciones con su marca de correcta, la
competencia, el tema y el nivel de dificultad. Con eso se arma un simulacro y se
califica sin volver a preguntar al banco.

Al recibir `PreguntaArchivada`, marcar esa pregunta como no disponible para
simulacros nuevos, **sin borrarla**: los simulacros pasados la referencian.

## Endpoints sugeridos

| Método | Ruta | Para qué |
|---|---|---|
| `POST` | `/api/v1/simulacros` | Crear un simulacro para un estudiante, con filtros de competencia y cantidad |
| `GET` | `/api/v1/simulacros/{id}` | Consultar su estado y sus preguntas |
| `POST` | `/api/v1/simulacros/{id}/respuestas` | Registrar una respuesta |
| `POST` | `/api/v1/simulacros/{id}/finalizar` | Cerrarlo y calificarlo |
| `GET` | `/api/v1/reportes/estudiantes/{id}` | Reporte de un estudiante |

## Recursos reservados

| Recurso | Valor |
|---|---|
| Puerto de la API | 8083 |
| Puerto de su PostgreSQL | 5435 |
| Cola que ya existe, con eventos sin consumir | `simulacros.catalogo-preguntas` |
| Bloques en `docker-compose.yml` y `.env.example` | ya escritos y comentados |
| Rama de trabajo | `feature/simulacros-service` |

La cola ya existe y guarda los eventos `PreguntaPublicada` y `PreguntaArchivada`
que todavía no ha consumido nadie. Cuando este servicio se conecte, los
encontrará ahí.
