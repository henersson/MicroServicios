# Decisiones de arquitectura

Las ocho decisiones que no eran obvias, con el motivo. Cada una en formato
contexto → decisión → consecuencia.

---

## ADR 1 — Se agrega el evento `RevisorAsignado`

**Contexto.** La invariante 9 dice que una `Revision` nunca existe sin revisor.
Cuando el banco publica `PreguntaEnviadaARevision`, deja la pregunta en
`PENDIENTE_REVISION` y espera que pase a `EN_REVISION`. Pero el banco no sabe si
el otro servicio consiguió asignar un revisor: eso depende de datos que el banco
no tiene.

**Decisión.** El `revision-service` publica `RevisorAsignado` cuando la revisión
queda creada con su revisor. El banco pasa la pregunta a `EN_REVISION` solo al
recibir ese evento. Este evento no estaba en el modelo del Taller 1.

**Consecuencia.** Si no hay ningún revisor posible, `AsignadorRevisor` falla, no
se crea revisión, no se publica nada y la pregunta se queda correctamente en
`PENDIENTE_REVISION`. El evento fallido queda en la DLQ y se puede reprocesar
cuando haya revisores. El precio es un tercer salto por el broker en el camino
feliz.

---

## ADR 2 — Los 8 estados del Taller 1, con `EN_CONSTRUCCION` y `RECHAZADA`

**Contexto.** El lenguaje ubicuo del Taller 1 define 8 estados para una pregunta:
Borrador, En construcción, Pendiente de revisión, En revisión, Aprobada,
Rechazada, Publicada y Archivada. Una primera versión del banco usaba solo 6: el
rechazo devolvía la pregunta a `BORRADOR` y no existía `EN_CONSTRUCCION`. El
Taller 1 tampoco dice en qué se diferencian Borrador y En construcción.

**Decisión.** Se implementan los 8.

- `BORRADOR` es la pregunta recién creada. Pasa a `EN_CONSTRUCCION` la primera
  vez que el autor la edita, y **solo desde `EN_CONSTRUCCION` se envía a
  revisión**.
- Un rechazo deja la pregunta en `RECHAZADA`, con las observaciones del revisor
  en `observacionesUltimaRevision`. Al editarla, el autor la **reabre**: vuelve a
  `EN_CONSTRUCCION` y desde ahí la reenvía.
- Se edita en `BORRADOR`, `EN_CONSTRUCCION` y `RECHAZADA` (invariante 7). Se
  archiva desde esos tres, desde `APROBADA` y desde `PUBLICADA`.

**Consecuencia.** El modelo coincide con el lenguaje ubicuo, y el estado dice de
un vistazo si una pregunta fue rechazada. Reabrir no necesita un endpoint nuevo:
es la misma edición. El precio es que una pregunta recién creada no se puede
enviar directamente: el autor tiene que editarla al menos una vez, porque la
invariante 6 no permite saltar de `BORRADOR` a `PENDIENTE_REVISION`. Las bases de
datos existentes se adaptan con la migración `V4` de Flyway.

---

## ADR 3 — No hay borrado: solo archivar

**Contexto.** Una pregunta que ya se usó en un simulacro tiene que seguir siendo
consultable, porque los resultados de ese simulacro la referencian. La invariante
8 lo dice: no se elimina físicamente.

**Decisión.** La única salida del ciclo de vida es `ARCHIVADA`, y no existe
ninguna operación de borrado: ni endpoint `DELETE`, ni método en el agregado, ni
método en `PreguntaRepository`.

**Consecuencia.** La invariante se garantiza **por ausencia**, que es la forma más
segura: no se puede llamar a algo que no existe. El futuro `simulacros-service`
recibe `PreguntaArchivada` para dejar de ofrecerla en simulacros nuevos sin
perder los pasados. La tabla de preguntas solo crece.

---

## ADR 4 — Los eventos se publican después del commit

**Contexto.** Si un servicio publica un evento y después su transacción de base
de datos se deshace, ha anunciado un cambio que nunca ocurrió. El otro servicio
actuaría sobre algo que no existe, y nada lo desharía.

**Decisión.** El evento se entrega al publicador dentro del caso de uso, pero el
envío real al broker ocurre **después** de confirmar la transacción. En Java con
`@TransactionalEventListener(AFTER_COMMIT)`; en Python, llamando al publicador
fuera del bloque de la unidad de trabajo.

**Consecuencia.** Nunca se anuncia algo que se deshizo. Queda abierta la ventana
contraria: si el proceso muere entre el commit y el envío, el evento se pierde.
Se asume a conciencia para el alcance del taller. Cerrarla es exactamente para lo
que sirve el **patrón Outbox**, que queda como trabajo futuro: guardar el evento
en una tabla dentro de la misma transacción y que un proceso aparte lo envíe.

---

## ADR 5 — Sin librerías compartidas: lo único común es `/contracts`

**Contexto.** Los dos servicios intercambian mensajes y llaman a operaciones del
otro. La tentación es crear una librería con los DTO y los eventos para no
repetir código.

**Decisión.** No hay ninguna librería compartida. Lo único común es la carpeta
[`../contracts/`](../contracts/): el `.proto` de gRPC y los JSON Schema de los
eventos. Cada servicio genera o escribe sus propias clases a partir de ahí.

**Consecuencia.** El tercer microservicio puede escribirse en cualquier
tecnología, porque solo necesita leer archivos estándar. Y un cambio en el
dominio de un servicio no arrastra al otro a recompilar. El costo es que hay
código equivalente en los dos lados —el envelope se arma dos veces— y que
mantener los contratos alineados es responsabilidad de quien los cambia.

---

## ADR 6 — Revisión guarda un snapshot de la pregunta

**Contexto.** El revisor evalúa el contenido de una pregunta que vive en el otro
servicio. Se podría consultar al banco cada vez que hace falta mostrarla.

**Decisión.** Al crear la revisión, el `revision-service` pide la pregunta una
sola vez por gRPC (`ObtenerPregunta`) y guarda una copia como
`SnapshotPregunta`, en una columna JSONB.

**Consecuencia.** El revisor juzga algo estable: si el autor editara la pregunta
después, no cambiaría lo que el revisor ya evaluó. Y revisión funciona aunque el
banco esté caído, porque no vuelve a preguntar. El costo es que el snapshot puede
quedar desactualizado respecto al banco, lo cual es precisamente lo que se
quiere. Se guarda como JSONB y no en tablas normalizadas porque es una copia
inmutable que nunca se consulta por campos.

---

## ADR 7 — Spring Boot 4

**Contexto.** El diseño inicial preveía Spring Boot 3. Al generar el proyecto con
Spring Initializr, la rama 3.x ya no aparece entre las opciones.

**Decisión.** Se usa **Spring Boot 4.1.1** con **Java 21**, que es lo que
Initializr ofrece hoy y está en versión estable.

**Consecuencia.** Lo que el taller exige se mantiene: Java 21, Spring Boot y una
tecnología distinta a la del otro servicio. A cambio hay que tener en cuenta los
cambios de la versión 4: el conversor de mensajes de AMQP cambió de nombre, y el
proyecto usa Jackson 3 (`tools.jackson`) en el código propio, aunque Jackson 2
sigue en el classpath por dependencias transitivas.

---

## ADR 8 — Usuarios simulados con cabeceras: 401 y 403

**Contexto.** El Bounded Context de Usuarios y Roles no se implementa en este
taller, pero los dos servicios necesitan saber quién hace cada petición y con qué
rol.

**Decisión.** Cada petición declara su usuario con las cabeceras
`X-Usuario-Id` (UUID) y `X-Usuario-Rol`. Si falta alguna, el UUID no es válido o
el rol no existe, la respuesta es **401** con el título *No se pudo identificar al
usuario* y el detalle en `errores[]`. Si el usuario se identifica pero su rol no
puede hacer la operación, es **403**.

**Consecuencia.** Los dos códigos significan cosas distintas y eso se nota al
probar: 401 es «no sé quién eres», 403 es «sé quién eres y no puedes». Ambos
servicios responden igual, con el mismo formato Problem Details (RFC 7807). La
comprobación del **rol** vive en la capa de interfaces, pero la de **propiedad**
(¿es este el autor de esta pregunta?) la hace el agregado, porque es la
invariante 7 y depende del estado del dominio. El día que exista autenticación
real con JWT, solo cambia el borde de cada servicio.
