-- =============================================================================
-- Elimina la columna version de preguntas.
--
-- El bloqueo optimista se retira: en este sistema una pregunta la edita solo su
-- autor y solo mientras esta en BORRADOR (invariante 7), asi que dos ediciones
-- simultaneas de la misma pregunta no son un escenario real.
--
-- El 409 por transicion de estado invalida (invariante 6) NO cambia: eso lo
-- decide la maquina de estados del agregado, no la version de la fila.
-- =============================================================================

ALTER TABLE preguntas DROP COLUMN version;
