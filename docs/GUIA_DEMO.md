# Guía de la demostración

Cómo montar el sistema y demostrarlo en vivo. Si sigues esta guía al pie de la
letra no hay que improvisar nada.

Para aprender a usar Postman, ver [`GUIA_POSTMAN.md`](GUIA_POSTMAN.md).
Para el reparto, qué decir y las preguntas probables, ver
[`sustentacion.md`](sustentacion.md).

## 1. Una sola vez

Esto se hace en **el portátil que se va a llevar a la sustentación**.

**Requisitos:**

- **Docker Desktop** instalado y funcionando: <https://www.docker.com/products/docker-desktop/>
- **Postman** de escritorio: ver [`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) §1

**Pasos:**

1. Abre Docker Desktop y espera a que el icono de la ballena deje de animarse.
2. Asegúrate de tener la **última versión del repositorio** en el portátil. Si lo
   clonaste desde GitHub, actualízalo con `git pull` antes de seguir.
3. **Construye las imágenes.** Tarda unos **5 minutos** y **necesita internet**,
   porque descarga las dependencias de Maven y de pip:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\levantar.ps1
   ```
   Termina cuando dice *Los 5 contenedores están sanos*.
4. **Comprueba que todo pasa:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1
   ```
   Debe terminar en *LA PRUEBA INTEGRAL PASÓ COMPLETA* con **24 de 24**.
5. **Importa las colecciones en Postman** y córrelas con el Runner:
   ver [`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) §2, §3 y §7. La DEMO debe dar
   **79 passed, 0 failed**.
6. **Crea la petición gRPC `09b`**: ver [`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) §8.
   Es el único paso manual y sin él no se puede demostrar gRPC en Postman.
7. **Baja el sistema conservando los datos y las imágenes:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1
   ```

> **NO BORRES LAS IMÁGENES.** No ejecutes `docker system prune` ni borres
> imágenes a mano. Sin ellas hay que volver a construir, y eso exige internet.
> `bajar.ps1` y `bajar.ps1 -Volumenes` son seguros: conservan las imágenes.

## 2. El día de la sustentación, 15 minutos antes

1. **Abre Docker Desktop** y espera a que esté listo (1 o 2 minutos).
2. **Un solo comando lo deja todo preparado:**
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\preparar-sustentacion.ps1
   ```
   Tarda unos **35 segundos** y **no necesita internet**. Comprueba Docker, las
   imágenes y los 7 puertos, hace un arranque limpio, verifica la salud de los
   dos servicios y de las 6 colas, y abre las ventanas que hacen falta.
   Debe terminar en **TODO LISTO** con todas las líneas en `[OK]`.
3. **Abre Postman**, elige la colección **SaberPro — DEMO sustentación** y el
   environment **SaberPro - local (Docker)** arriba a la derecha.
4. **Manda las 4 peticiones de la carpeta 1.** Si las cuatro salen en verde,
   estás listo.

**Cómo organizar la pantalla.** El script deja abiertas 4 ventanas. Lo que se
proyecta es Postman; el resto se trae al frente cuando toca:

| Ventana | Cuándo se muestra |
|---|---|
| **Postman**, pestaña **Visualize** activa | Casi todo el tiempo. Es lo que se proyecta |
| **Terminal con los logs** (título *LOGS - banco y revision*) | En la carpeta 3, para ver los eventos viajar |
| **Consola de RabbitMQ** en <http://localhost:15672> (pestaña *Queues*) | En las carpetas 1 y 5 |
| **Navegador con los dos Swagger** | En la carpeta 2 |

## 3. La demostración paso a paso

Se manda **una petición cada vez** con **Send**, en orden. No se usa el Runner.

La columna **Imp.** dice si el paso es imprescindible: los que dicen **Sí** se
hacen siempre; los que dicen *No* se saltan si falta tiempo. Con solo los
imprescindibles la demostración cabe en **10 minutos**.

### Carpeta 1 · El sistema está arriba

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **01 · Health del banco de preguntas** | **Sí** | Send | `UP` en el servicio, en su PostgreSQL y en RabbitMQ | — | C2 |
| **02 · Health del servicio de revisión** | **Sí** | Send | `UP`. Es otro servicio, en otra tecnología | — | C2 |
| **03 · RabbitMQ: las 3 colas de negocio y sus 3 DLQ** | **Sí** | Send | Las 6 colas; dos con consumidor y la del tercer servicio sin ninguno | Consola de RabbitMQ, pestaña *Queues* | C4 |
| **04 · Revisores registrados en el servicio de revisión** | No | Send | Tres revisores que solo existen en la base de revisión | — | C2 |

### Carpeta 2 · REST y reglas del dominio

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **05 · Crear una pregunta inválida → 400** | **Sí** | Send | Un `400` con tres errores, cada uno citando su invariante | — | C1, C3 |
| **06 · Crear la pregunta válida → 201 BORRADOR** | **Sí** | Send | `201` y estado `BORRADOR`, con 4 opciones y una correcta | — | C1, C3 |
| **07 · El autor trabaja la pregunta → EN_CONSTRUCCION** | **Sí** | Send | Estado `EN_CONSTRUCCION`: sin ese paso no se puede enviar a revisión (invariante 6) | — | C1, C3 |
| *(abrir Swagger)* | **Sí** | Trae al frente el navegador | Los 8 endpoints del banco y los 7 de revisión, documentados | Las dos pestañas de Swagger | C3 |

### Carpeta 3 · Evento + gRPC: los servicios se coordinan solos

**Trae al frente la ventana de logs antes de empezar.** Es el momento más
importante de la demostración.

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **08 · Enviar a revisión → PENDIENTE_REVISION** | **Sí** | Send | `PENDIENTE_REVISION`. El banco publica un evento y sigue, sin llamar a nadie | **Logs:** `Evento publicado … PreguntaEnviadaARevision` | C4 |
| **09 · La revisión que se creó sola, con el snapshot de gRPC** | **Sí** | Send (espera unos segundos) | Una revisión en `ASIGNADA`, con revisor y con las 4 opciones del snapshot traído por gRPC | **Logs:** `gRPC ObtenerPregunta … obtenida del banco` y `revisión … creada` | C3, C4, C5 |
| **09b · gRPC ObtenerPregunta** | **Sí** | Invoke | La pregunta devuelta por gRPC, con `es_correcta` solo en la correcta | — | C3 |
| **10 · La pregunta ya está EN_REVISION en el banco** | **Sí** | Send (espera unos segundos) | `EN_REVISION`. El banco cambió solo al consumir `RevisorAsignado` | **Logs:** `pasó a EN_REVISION` | C4, C5 |

### Carpeta 4 · La revisión por pares

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **11 · Guardar el formato de evaluación → EN_EVALUACION** | **Sí** | Send | `EN_EVALUACION` y el promedio de los 6 criterios | — | C1 |
| **12 · Agregar una observación** | No | Send | La observación con su revisor y su fecha | — | C1 |
| **13 · Aprobar → revisión APROBADA** | **Sí** | Send | La revisión queda `APROBADA` | **Logs:** `PreguntaAprobadaTecnicamente` | C4 |
| **14 · La pregunta queda APROBADA en el banco** | **Sí** | Send (espera unos segundos) | `APROBADA` en el otro servicio, sin tocar nada más | **Logs:** el banco aplicando el evento | C4, C5 |

### Carpeta 5 · Publicación y el evento para el tercer microservicio

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **15 · Publicar como ADMINISTRADOR → PUBLICADA** | **Sí** | Send | `PUBLICADA`. Publicar es del administrador, no del autor | — | C1, C3 |
| **16 · Historial completo de la pregunta** | No | Send | Las 5 transiciones en orden, con quién y por qué | — | C1 |
| **17 · El evento PreguntaPublicada en la cola, sin consumirlo** | **Sí** | Send (espera unos segundos) | El evento con su `eventId` y la pregunta completa dentro, y que sigue en la cola | Consola de RabbitMQ: `simulacros.catalogo-preguntas` con 1 mensaje | C4, C5 |

### Carpeta 6 · Seguridad y reglas del dominio

| Petición | Imp. | Qué haces | Qué debe ver el profesor | Dónde mirar además | Rúbrica |
|---|---|---|---|---|---|
| **18 · Un ESTUDIANTE intenta publicar → 403** | **Sí** | Send | `403` y el rol que haría falta | — | C1, C3 |
| **19 · Petición sin cabeceras de usuario → 401** | **Sí** | Send | `401` con las dos cabeceras que faltan | — | C3 |
| **20 · Preparar: una segunda pregunta que llega a revisión** | No | Send (espera unos segundos) | `PENDIENTE_REVISION`. Es material para la siguiente | — | — |
| **21 · Aprobar sin el formato diligenciado → 400 (invariante 10)** | No | Send | `400` diciendo cuántos criterios faltan, citando la invariante 10 | — | C1 |

### Carpetas 7 y 8 · Opcionales

Se muestran solo si sobra tiempo o si el profesor pregunta:

- **7 · Extra: el camino de rechazo (opcional)** (peticiones 22 y 23): la pregunta queda en
  `RECHAZADA` con las observaciones del revisor.
- **8 · Extra: un evento que falla no se pierde (opcional)** (24, 25 y 26): se inyecta un evento
  imposible y se ve aparecer en la DLQ. La 26 vacía la DLQ y deja el sistema
  limpio.

## 4. Qué enseñar fuera de Postman

| Qué | Cómo | Qué demuestra |
|---|---|---|
| Los 5 contenedores | `docker compose ps` | Dos microservicios, dos bases y el broker, independientes (C2) |
| Las dos bases separadas | `docker compose exec postgres-banco psql -U banco -d banco_preguntas -c "\dt"` y `docker compose exec postgres-revision psql -U revision -d revision -c "\dt"` | 5 tablas de negocio en cada una, sin nada compartido (C2) |
| Las colas y las DLQ | Consola de RabbitMQ, <http://localhost:15672> (`saberpro` / `saberpro`), pestaña *Queues* | Un exchange, 3 colas de negocio y 3 DLQ (C4) |
| El diagrama del sistema | Abre [`arquitectura.md`](arquitectura.md) en VS Code y pulsa la vista previa | Los dos servicios, sus bases, REST, gRPC y RabbitMQ (C6, entregable E2) |

## 5. Si algo falla en vivo

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El script dice *Docker Desktop no responde* | Docker Desktop está cerrado | Ábrelo desde el menú Inicio, espera a la ballena y vuelve a ejecutar el script |
| El script dice *puerto N lo usa X* | Otro programa ocupa un puerto | Cierra ese programa, o `Stop-Process -Id <PID> -Force` con el PID que imprime el script |
| El script dice *Faltan imágenes* | Se borraron las imágenes | Hace falta internet: responde `s` para construirlas (5 minutos) |
| Un contenedor no llega a `healthy` | La base tardó en arrancar | `docker compose ps` para ver cuál y `docker compose logs <servicio>` para el motivo. Vuelve a ejecutar el script |
| Todas las peticiones fallan a la vez | El environment no está seleccionado | Ponlo arriba a la derecha en Postman ([`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) §3) |
| Una petición con espera agota los 20 s | El evento tardó, o el consumidor está caído | Vuelve a darle **Send**. Si insiste, mira los logs del `revision-service` |
| Un **404** inesperado | Se saltó una petición y el id viene vacío | Manda otra vez la **06** y sigue en orden |
| La petición gRPC no conecta | La dirección lleva `http://` | Debe ser `localhost:9091`, sin `http://`. Si aun así falla, usa grpcurl ([`GUIA_POSTMAN.md`](GUIA_POSTMAN.md) §8) |
| No hay internet | — | No hace falta: `preparar-sustentacion.ps1` no descarga nada mientras las imágenes estén construidas |

**Plan B, si la demostración en Postman falla.** Un solo comando recorre lo mismo
y lo imprime en pantalla:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1
```

Son **29 comprobaciones** en unos 15 segundos: el camino feliz completo con sus
eventos y su llamada gRPC, el camino de rechazo con la reapertura y el reenvío,
las reglas del dominio, el 401, el 403 y que las 3 DLQ están vacías. Sirve perfectamente como demostración.

**Plan C, si Docker no arranca.** No se puede mostrar el sistema en vivo, así que
se explica con los diagramas: los de secuencia de
[`flujo-e2e.md`](flujo-e2e.md) y el general de
[`arquitectura.md`](arquitectura.md), que recorren el mismo flujo paso a paso.

Conviene además llevar guardada la salida de una corrida que sí pasó. Se genera
así, con el sistema levantado, el día antes:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prueba-e2e.ps1 > prueba-e2e.txt
```

## 6. Después de la sustentación

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bajar.ps1
```

Apaga los 5 contenedores conservando los datos y las imágenes. Con `-Volumenes`
borra también los datos, y sigue conservando las imágenes.
