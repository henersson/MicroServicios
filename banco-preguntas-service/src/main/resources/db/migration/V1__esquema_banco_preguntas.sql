-- =============================================================================
-- Esquema del banco-preguntas-service.
--
-- Base de datos PROPIA y exclusiva de este microservicio: ningun otro servicio
-- se conecta aqui. Si el revision-service o el simulacros-service necesitan
-- datos de una pregunta, los piden por gRPC o los reciben por evento.
--
-- Convenciones: nombres en espanol y en singular para las columnas, en plural
-- para las tablas; fechas siempre en UTC (timestamptz).
-- =============================================================================

-- ── Aggregate Root: Pregunta ────────────────────────────────────────────────
CREATE TABLE preguntas (
    id                             UUID         PRIMARY KEY,
    autor_id                       UUID         NOT NULL,

    -- Contenido escrito por el autor (invariante 4: ambos obligatorios).
    contexto                       TEXT         NOT NULL,
    pregunta_directa               TEXT         NOT NULL,
    justificacion                  TEXT         NOT NULL,

    -- Value Object Competencia, aplanado en dos columnas.
    competencia_codigo             VARCHAR(50)  NOT NULL,
    competencia_nombre             VARCHAR(200) NOT NULL,

    tema                           VARCHAR(200) NOT NULL,
    subtema                        VARCHAR(200) NOT NULL,

    -- Los enums se guardan como texto, no como ordinal: un ordinal se rompe en
    -- silencio el dia que alguien reordene el enum en Java.
    nivel_dificultad               VARCHAR(20)  NOT NULL,
    estado                         VARCHAR(30)  NOT NULL,

    -- Observaciones del ultimo rechazo, para que el autor sepa que corregir.
    -- NULL mientras la pregunta no venga de un rechazo (ADR-02).
    observaciones_ultima_revision  TEXT,

    -- Bloqueo optimista: si dos ediciones concurrentes chocan, la segunda falla
    -- con 409 en vez de pisar la primera sin avisar.
    version                        BIGINT       NOT NULL DEFAULT 0,

    creada_en                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizada_en                 TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Invariante 6: la base de datos tambien rechaza un estado inventado, por si
    -- alguien escribiera directamente contra la tabla saltandose el agregado.
    CONSTRAINT ck_preguntas_estado CHECK (estado IN (
        'BORRADOR', 'PENDIENTE_REVISION', 'EN_REVISION',
        'APROBADA', 'PUBLICADA', 'ARCHIVADA')),
    CONSTRAINT ck_preguntas_nivel CHECK (nivel_dificultad IN ('BAJO', 'MEDIO', 'ALTO'))
);

COMMENT ON TABLE preguntas IS
    'Aggregate Root del BC Banco de Preguntas. Garantiza las invariantes 1 a 8.';
COMMENT ON COLUMN preguntas.version IS
    'Bloqueo optimista gestionado por JPA (@Version).';

-- Indices para los filtros del endpoint de listado y del RPC
-- ListarPreguntasPublicadas.
CREATE INDEX ix_preguntas_estado       ON preguntas (estado);
CREATE INDEX ix_preguntas_competencia  ON preguntas (competencia_codigo);
CREATE INDEX ix_preguntas_tema         ON preguntas (tema);
CREATE INDEX ix_preguntas_nivel        ON preguntas (nivel_dificultad);
CREATE INDEX ix_preguntas_autor        ON preguntas (autor_id);
-- El simulacros-service siempre filtra por estado PUBLICADA y luego por
-- competencia, asi que este indice compuesto cubre su consulta tipica.
CREATE INDEX ix_preguntas_estado_competencia ON preguntas (estado, competencia_codigo);


-- ── Value Object Opcion (invariante 1: exactamente 5 por pregunta) ──────────
CREATE TABLE pregunta_opciones (
    id           BIGSERIAL    PRIMARY KEY,
    pregunta_id  UUID         NOT NULL,
    -- El orden importa: es el que ve el estudiante y el que citan las
    -- observaciones del revisor ("la opcion 3 es demasiado corta").
    orden        INT          NOT NULL,
    texto        TEXT         NOT NULL,
    es_correcta  BOOLEAN      NOT NULL,

    CONSTRAINT fk_opciones_pregunta FOREIGN KEY (pregunta_id)
        REFERENCES preguntas (id) ON DELETE CASCADE,
    CONSTRAINT uq_opciones_pregunta_orden UNIQUE (pregunta_id, orden),
    CONSTRAINT ck_opciones_orden CHECK (orden BETWEEN 0 AND 4)
);

COMMENT ON TABLE pregunta_opciones IS
    'Value Object Opcion. El ON DELETE CASCADE existe solo por integridad: las '
    'preguntas no se borran (invariante 8), se archivan.';

CREATE INDEX ix_opciones_pregunta ON pregunta_opciones (pregunta_id);


-- ── Value Object Bibliografia ───────────────────────────────────────────────
CREATE TABLE pregunta_bibliografia (
    id           BIGSERIAL PRIMARY KEY,
    pregunta_id  UUID      NOT NULL,
    orden        INT       NOT NULL,
    referencia   TEXT      NOT NULL,

    CONSTRAINT fk_bibliografia_pregunta FOREIGN KEY (pregunta_id)
        REFERENCES preguntas (id) ON DELETE CASCADE,
    CONSTRAINT uq_bibliografia_pregunta_orden UNIQUE (pregunta_id, orden)
);

CREATE INDEX ix_bibliografia_pregunta ON pregunta_bibliografia (pregunta_id);


-- ── Value Object CambioEstado: la trazabilidad ──────────────────────────────
CREATE TABLE pregunta_historial_estados (
    id               BIGSERIAL    PRIMARY KEY,
    pregunta_id      UUID         NOT NULL,
    orden            INT          NOT NULL,
    -- NULL en la entrada de creacion: antes de existir no habia estado previo.
    estado_anterior  VARCHAR(30),
    estado_nuevo     VARCHAR(30)  NOT NULL,
    usuario_id       UUID,
    fecha            TIMESTAMPTZ  NOT NULL,
    motivo           TEXT,

    CONSTRAINT fk_historial_pregunta FOREIGN KEY (pregunta_id)
        REFERENCES preguntas (id) ON DELETE CASCADE,
    CONSTRAINT uq_historial_pregunta_orden UNIQUE (pregunta_id, orden)
);

COMMENT ON TABLE pregunta_historial_estados IS
    'Historial de cambios de estado. Solo crece: nunca se corrige ni se borra '
    'una entrada, porque es la evidencia ante una reclamacion.';

CREATE INDEX ix_historial_pregunta ON pregunta_historial_estados (pregunta_id);


-- ── Idempotencia de los eventos entrantes ───────────────────────────────────
CREATE TABLE eventos_procesados (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    procesado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE eventos_procesados IS
    'RabbitMQ entrega al menos una vez, asi que el mismo evento puede llegar dos '
    'veces. La clave primaria sobre event_id es lo que hace idempotente al '
    'consumidor: se escribe en la misma transaccion que aplica el cambio.';

CREATE INDEX ix_eventos_procesados_tipo ON eventos_procesados (event_type);
