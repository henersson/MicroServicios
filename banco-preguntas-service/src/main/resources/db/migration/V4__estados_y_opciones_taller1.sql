-- =============================================================================
-- Alinea el banco con el modelo del Taller 1.
--
--   1. Estados: se agregan EN_CONSTRUCCION y RECHAZADA, hasta completar los 8
--      estados del lenguaje ubicuo (ADR 2).
--   2. Opciones: una pregunta pasa de 5 opciones (4 distractores y 1 correcta)
--      a 4 (3 distractores y 1 correcta). Invariante 1.
--   3. Datos de ejemplo de V2: se ajustan al nuevo ciclo de vida.
--
-- Va como migracion nueva y no editando V1 o V2: una migracion ya aplicada no
-- se modifica, porque Flyway compara su checksum y una base existente dejaria
-- de arrancar.
--
-- Las preguntas que no son de ejemplo conservan su estado: los 6 estados
-- anteriores siguen existiendo, y su historial es un registro de lo que
-- ocurrio con las reglas de entonces.
-- =============================================================================


-- ── 1. Los 8 estados del Taller 1 ───────────────────────────────────────────
ALTER TABLE preguntas DROP CONSTRAINT ck_preguntas_estado;
ALTER TABLE preguntas ADD CONSTRAINT ck_preguntas_estado CHECK (estado IN (
    'BORRADOR', 'EN_CONSTRUCCION', 'PENDIENTE_REVISION', 'EN_REVISION',
    'APROBADA', 'RECHAZADA', 'PUBLICADA', 'ARCHIVADA'));


-- ── 2. Cuatro opciones por pregunta ─────────────────────────────────────────
-- La restriccion de orden se quita primero: la renumeracion pasa por valores
-- temporales fuera del rango.
ALTER TABLE pregunta_opciones DROP CONSTRAINT ck_opciones_orden;

-- A cada pregunta que todavia tiene 4 distractores se le quita el ultimo (el de
-- mayor orden). La opcion correcta nunca se toca.
DELETE FROM pregunta_opciones o
USING (
    SELECT pregunta_id, MAX(orden) AS orden
    FROM pregunta_opciones
    WHERE es_correcta = FALSE
    GROUP BY pregunta_id
    HAVING COUNT(*) = 4
) ultimo
WHERE o.pregunta_id = ultimo.pregunta_id
  AND o.orden = ultimo.orden;

-- Renumera 0..3 sin huecos. En dos pasos para no chocar con la restriccion
-- unica (pregunta_id, orden) a mitad del UPDATE.
UPDATE pregunta_opciones SET orden = orden + 100;
UPDATE pregunta_opciones o
SET orden = r.nuevo
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY pregunta_id ORDER BY orden) - 1 AS nuevo
    FROM pregunta_opciones
) r
WHERE o.id = r.id;

ALTER TABLE pregunta_opciones ADD CONSTRAINT ck_opciones_orden CHECK (orden BETWEEN 0 AND 3);


-- ── 3. Datos de ejemplo de V2 ───────────────────────────────────────────────

-- 3a. La justificacion de la pregunta publicada comentaba el distractor que se
--     acaba de quitar.
UPDATE preguntas
SET justificacion = 'La independencia de despliegue y evolución es la propiedad que define a los microservicios, y depende de que cada servicio sea dueño exclusivo de sus datos (database per service). Al compartir el esquema, un cambio en una tabla obliga a coordinar el despliegue de los tres servicios, que vuelven a comportarse como un monolito distribuido. Los distractores son plausibles pero incorrectos: PostgreSQL sí admite múltiples clientes concurrentes; el teorema CAP habla de consistencia, disponibilidad y tolerancia a particiones, no de propiedad de esquemas; y el costo es una consecuencia secundaria y no siempre desfavorable.'
WHERE id = '3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c';

-- 3b. En el historial, entre la creacion y el envio a revision, cada pregunta de
--     ejemplo pasa ahora por EN_CONSTRUCCION (invariante 6: sin saltos). Solo
--     se tocan las que siguen teniendo el historial original de V2.
CREATE TEMPORARY TABLE ejemplos_v2 ON COMMIT DROP AS
SELECT pregunta_id
FROM pregunta_historial_estados
WHERE pregunta_id IN ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c',
                      '7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93',
                      'e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25')
  AND orden = 1
  AND estado_anterior = 'BORRADOR'
  AND estado_nuevo = 'PENDIENTE_REVISION';

UPDATE pregunta_historial_estados SET orden = orden + 100
WHERE pregunta_id IN (SELECT pregunta_id FROM ejemplos_v2) AND orden >= 1;
UPDATE pregunta_historial_estados SET orden = orden - 99
WHERE pregunta_id IN (SELECT pregunta_id FROM ejemplos_v2) AND orden >= 101;

INSERT INTO pregunta_historial_estados
    (pregunta_id, orden, estado_anterior, estado_nuevo, usuario_id, fecha, motivo)
SELECT pregunta_id, 1, 'BORRADOR', 'EN_CONSTRUCCION', usuario_id,
       fecha + INTERVAL '1 hour', 'El autor empezó a trabajar la pregunta.'
FROM pregunta_historial_estados
WHERE pregunta_id IN (SELECT pregunta_id FROM ejemplos_v2) AND orden = 0;

UPDATE pregunta_historial_estados SET estado_anterior = 'EN_CONSTRUCCION'
WHERE pregunta_id IN (SELECT pregunta_id FROM ejemplos_v2)
  AND orden = 2
  AND estado_anterior = 'BORRADOR'
  AND estado_nuevo = 'PENDIENTE_REVISION';

-- 3c. La pregunta de ejemplo devuelta por un rechazo queda en RECHAZADA, con
--     las observaciones del revisor, en vez de en BORRADOR (ADR 2).
UPDATE preguntas SET estado = 'RECHAZADA'
WHERE id = 'e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25'
  AND estado = 'BORRADOR'
  AND observaciones_ultima_revision IS NOT NULL;

UPDATE pregunta_historial_estados
SET estado_nuevo = 'RECHAZADA',
    motivo = 'Rechazada en la revisión por pares; el autor debe corregirla.'
WHERE pregunta_id = 'e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25'
  AND estado_anterior = 'EN_REVISION'
  AND estado_nuevo = 'BORRADOR';
