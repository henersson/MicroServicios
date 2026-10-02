# Guía de Postman para la demostración

Para alguien que nunca ha usado Postman. Al terminar esta guía podrás importar
las colecciones, mandar cualquier petición y crear la petición gRPC.

Los nombres de los botones están en inglés, como aparecen en la aplicación de
escritorio. Donde el nombre cambia según la versión, lo avisamos.

## 1. Instalar Postman

1. Descarga la aplicación de escritorio desde <https://www.postman.com/downloads/>
   y instálala. **No uses la versión web**: no puede llamar a `localhost`. Si ya
   la tenías instalada, ábrela y con eso basta.
2. Postman pide iniciar sesión. Se puede trabajar **sin cuenta**: en la pantalla
   de bienvenida busca el enlace pequeño **Continue without an account** o
   **Skip and go to the app** (el texto cambia según la versión) al final del
   formulario. Si no lo encuentras, crea una cuenta gratis con un correo: es más
   rápido que buscarlo.

## 2. Importar las colecciones y el environment

Hay que importar **3 archivos**, todos en la carpeta `postman/` del repositorio:

| Archivo | Qué es |
|---|---|
| `SaberPro-DEMO.postman_collection.json` | La colección de la demostración: 26 peticiones numeradas |
| `SaberPro.postman_collection.json` | La colección completa de pruebas: 48 peticiones |
| `SaberPro-local.postman_environment.json` | El environment: las direcciones y los usuarios simulados |

Pasos:

1. En la barra lateral izquierda, arriba, haz clic en **Import** (en algunas
   versiones es un icono de flecha hacia abajo junto al botón **New**).
2. Se abre una ventana con un área que dice *Drop anywhere to import* o
   *Drag and drop files*. **Arrastra los 3 archivos ahí de una vez.** También
   puedes hacer clic en **files** / **Choose files** y seleccionarlos.
3. Haz clic en **Import**.
4. Comprueba el resultado en la barra lateral:
   - En **Collections** deben aparecer **SaberPro — DEMO sustentación** y
     **SaberPro - Banco de Preguntas (Taller 2)**.
   - En **Environments** debe aparecer **SaberPro - local (Docker)**.

Si ya habías importado una versión anterior, Postman puede preguntar si quieres
**Replace** o crear una copia. Elige **Replace**: así no quedan dos colecciones
con el mismo nombre.

## 3. Seleccionar el environment

**Este es el error más común. Sin este paso, todas las peticiones fallan.**

1. Mira la esquina **superior derecha** de la ventana. Hay un desplegable que
   normalmente dice **No Environment**.
2. Haz clic y elige **SaberPro - local (Docker)**.
3. Comprobación: el desplegable ya no dice *No Environment*, sino
   *SaberPro - local (Docker)*.

Otra comprobación: abre cualquier petición y pasa el cursor por encima de
`{{urlBanco}}` en la dirección. Debe aparecer un globo con `http://localhost:8081`;
si dice *unresolved variable*, el environment no está puesto. La colección tiene
además una red de seguridad: sin environment, la petición se detiene con el
mensaje *«Selecciona el environment "SaberPro - local (Docker)" arriba a la
derecha»* en lugar de dar un error confuso.

## 4. Mandar una petición y leer el resultado

1. En la barra lateral, despliega **SaberPro — DEMO sustentación**.
2. Despliega la carpeta **1 · El sistema está arriba (C2)**.
3. Haz clic en **01 · Health del banco de preguntas**.
4. Haz clic en el botón azul **Send**, a la derecha de la dirección.

Dónde mirar el resultado, en el panel de abajo:

| Dónde | Qué muestra |
|---|---|
| Arriba a la derecha del panel de respuesta | El código HTTP: **200 OK**, **201 Created**, **400 Bad Request**… y el tiempo que tardó |
| Pestaña **Visualize** | **La vista que se le muestra al profesor:** letra grande, con lo importante resaltado. Está dentro del panel de respuesta, junto a *Pretty*, *Raw* y *Preview* |
| Pestaña **Test Results** | Las comprobaciones automáticas. Debe decir *Passed* en todas y ninguna *Failed* |
| Pestaña **Body → Pretty** | El JSON completo de la respuesta |

Para leer **qué hace la petición, qué debe ver el profesor y qué decir**: con la
petición abierta, busca el icono de documentación (un libro) en la barra vertical
de la **derecha** y haz clic. Se abre un panel con las tres secciones. En algunas
versiones está en la pestaña **Documentation** del propio panel de la petición.

> Consejo para proyectar: deja la pestaña **Visualize** activa. Postman la
> recuerda de una petición a otra, así que no hay que hacer clic cada vez.

## 5. Las peticiones que esperan un evento

Ocho peticiones de la DEMO no responden al instante: antes de mandarse, esperan
a que un evento viaje por RabbitMQ y el otro servicio lo consuma. Son las
**09, 10, 14, 17, 20, 22, 23 y 25**.

- Normalmente resuelven en **menos de un segundo**.
- Esperan como máximo **20 segundos**, sondeando cada segundo.
- Mientras esperan, el botón **Send** se queda en *Sending...*. Es normal.

**Si una agota los 20 segundos:** vuelve a darle **Send**. La espera empieza de
nuevo y casi siempre funciona a la segunda. Si falla otra vez, mira los logs del
`revision-service` (ver `GUIA_DEMO.md`, sección *Si algo falla en vivo*).

## 6. El orden importa

De la **01 a la 07** puedes repetir cuantas veces quieras.

De la **08 en adelante hay un orden**: cada petición usa el identificador que
guardó la anterior en una variable de la colección. Si te la saltas, la siguiente
recibe un identificador vacío y falla con un **404**.

**Cómo volver a empezar:** manda otra vez la **06 · Crear la pregunta válida** y
sigue desde ahí en orden. La 06 crea una pregunta nueva y reescribe las
variables, así que la cadena queda reparada sin tener que reiniciar nada.

Para ver las variables: haz clic en el nombre de la colección
**SaberPro — DEMO sustentación**, y luego en la pestaña **Variables**. Ahí están
`demoPreguntaId`, `demoRevisionId` y las demás, con su valor actual.

## 7. Correr la colección entera con el Runner

Sirve para comprobar, **antes de entrar**, que todo pasa. No se usa durante la
demostración: en vivo se manda una petición cada vez.

1. Pasa el cursor por el nombre de la colección en la barra lateral y haz clic
   en los tres puntos **...** → **Run collection**. (En algunas versiones hay un
   botón **Run** en la vista general de la colección.)
2. Se abre el **Runner**. Deja todo como está y haz clic en **Run SaberPro —
   DEMO sustentación**.
3. Al terminar debe decir **79 passed** y **0 failed**.

La colección completa de pruebas (`SaberPro - Banco de Preguntas`) se corre
igual, y debe dar **107 passed, 0 failed**.

## 8. Crear la petición gRPC

Postman **no puede importar peticiones gRPC desde un archivo de colección**, así
que hay que crearla a mano. Se hace **una sola vez** y queda guardada.

1. Manda la petición **09** de la DEMO. Después abre la pestaña **Variables** de
   la colección y copia el valor de **`demoPreguntaId`**.
2. Haz clic en **New** (botón azul, arriba a la izquierda) → **gRPC**. Puede
   aparecer como **gRPC Request**.
3. En el campo de la dirección escribe **`localhost:9091`**.
   **Sin `http://` delante**: con `http://` no conecta.
4. Marca la casilla **Use server reflection**. El servidor publica su propio
   contrato, así que no hay que cargar ningún archivo `.proto`.
5. En el desplegable **Method** (puede decir *Select a method*) elige
   **`saberpro.banco.v1.BancoPreguntas / ObtenerPregunta`**.
6. Abre la pestaña **Message** y pega esto, con el identificador del paso 1:
   ```json
   { "pregunta_id": "PEGA_AQUI_EL_ID" }
   ```
7. Haz clic en **Invoke**. Debe devolver la pregunta completa con sus 4 opciones.
8. Haz clic en **Save**. Guárdala dentro de la carpeta
   **3 · Evento + gRPC** de la colección DEMO, justo después de la 09, con el
   nombre **`09b · gRPC ObtenerPregunta`**.

Para mostrar también los errores, cambia el mensaje y vuelve a **Invoke**:

| Message | Código que debe devolver |
|---|---|
| `{ "pregunta_id": "no-soy-un-uuid" }` | `INVALID_ARGUMENT` |
| `{ "pregunta_id": "00000000-0000-4000-8000-000000000000" }` | `NOT_FOUND` |

> En proto3, un `false` y una cadena vacía **no se serializan**. Por eso las
> opciones incorrectas llegan **sin** el campo `es_correcta`, y solo la correcta
> lo trae con `true`. Es el comportamiento normal de protobuf, no un error.

**Si Postman falla con gRPC**, el mismo resultado se obtiene desde la terminal:

```powershell
docker run --rm --network saberpro-net fullstorydev/grpcurl -plaintext `
  banco-preguntas-service:9091 list saberpro.banco.v1.BancoPreguntas
```

Eso lista las dos operaciones. Para llamar a una, con un identificador real:

```powershell
docker run --rm --network saberpro-net fullstorydev/grpcurl -plaintext `
  -d '{\"pregunta_id\":\"PEGA_AQUI_EL_ID\"}' `
  banco-preguntas-service:9091 saberpro.banco.v1.BancoPreguntas/ObtenerPregunta
```

## 9. Ver los eventos de RabbitMQ desde Postman

Tres peticiones hablan con RabbitMQ y no con los microservicios:

| Petición | Qué muestra |
|---|---|
| **03 · RabbitMQ: las 3 colas de negocio y sus 3 DLQ** | Las 6 colas con sus mensajes y sus consumidores |
| **17 · El evento PreguntaPublicada en la cola, sin consumirlo** | El evento completo que espera al tercer microservicio. Lo lee y lo devuelve a la cola, así que **no lo consume** |
| **25 · El evento acabó en la DLQ** | Un evento fallido esperando en su cola de mensajes muertos |

## 10. Problemas comunes

| Síntoma | Causa | Qué hacer |
|---|---|---|
| El mensaje *«Selecciona el environment…»* | El environment no está seleccionado | Ponlo en el desplegable de arriba a la derecha (sección 3) |
| **Could not send request** o *Error: connect ECONNREFUSED* | El sistema no está levantado | Ejecuta `powershell -ExecutionPolicy Bypass -File .\scripts\preparar-sustentacion.ps1` |
| Todas las peticiones dan *unresolved variable* | Igual que el primero | Selecciona el environment |
| Una petición con espera agota los 20 s | El evento tardó o un servicio está caído | Vuelve a darle **Send**. Si insiste, mira los logs |
| Un **404** inesperado | Te saltaste una petición y el identificador viene vacío | Manda otra vez la **06** y sigue en orden (sección 6) |
| Un **401** inesperado | Falta la cabecera de usuario, o el environment no está puesto | Selecciona el environment. Las cabeceras ya vienen en cada petición |
| Un **403** inesperado | El rol de la petición no puede hacer esa operación | Es correcto en las peticiones 18 y 21: **deben** dar 403 y 400 |
| Postman insiste en iniciar sesión | Es su pantalla de bienvenida | Busca **Continue without an account**, o crea una cuenta gratis |
| La petición gRPC no conecta | La dirección lleva `http://` | Debe ser `localhost:9091`, sin `http://` |
