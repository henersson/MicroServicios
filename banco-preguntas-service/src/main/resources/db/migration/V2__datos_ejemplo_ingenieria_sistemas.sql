-- =============================================================================
-- Datos de ejemplo: preguntas de Ingenieria de Sistemas en distintos estados.
--
-- Sirven para poder probar la API y el flujo completo sin tener que crear todo
-- a mano, y para que Swagger UI y Postman tengan contra que trabajar.
--
-- Va en su propia migracion a proposito: en un despliegue real bastaria con no
-- aplicarla (flyway.target) para tener el esquema sin los datos de demostracion.
--
-- La pregunta PUBLICADA usa deliberadamente los MISMOS identificadores que el
-- ejemplo de contracts/events/ejemplos/PreguntaPublicada.v1.json, para que el
-- contrato documentado y la base de datos cuenten la misma historia.
-- =============================================================================

-- ── 1. PUBLICADA — la misma del ejemplo del contrato ────────────────────────
INSERT INTO preguntas (
    id, autor_id, contexto, pregunta_directa, justificacion,
    competencia_codigo, competencia_nombre, tema, subtema,
    nivel_dificultad, estado, version, creada_en, actualizada_en
) VALUES (
    '3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c',
    'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
    'Una universidad está migrando su plataforma académica monolítica hacia una arquitectura de microservicios. El equipo identificó tres contextos delimitados: Matrículas, Notas y Notificaciones. Durante el diseño, un desarrollador propone que los tres servicios compartan la misma base de datos PostgreSQL para evitar duplicar la información del estudiante y ahorrar costos de infraestructura. El arquitecto rechaza la propuesta argumentando que comprometería una propiedad esencial del estilo arquitectónico elegido.',
    '¿De acuerdo con los principios de la arquitectura de microservicios, cuál es la razón principal por la que el arquitecto rechaza compartir la base de datos entre los tres servicios?',
    'La independencia de despliegue y evolución es la propiedad que define a los microservicios, y depende de que cada servicio sea dueño exclusivo de sus datos (database per service). Al compartir el esquema, un cambio en una tabla obliga a coordinar el despliegue de los tres servicios, que vuelven a comportarse como un monolito distribuido. Los distractores son plausibles pero incorrectos: PostgreSQL sí admite múltiples clientes concurrentes; el teorema CAP habla de consistencia, disponibilidad y tolerancia a particiones, no de propiedad de esquemas; el costo es una consecuencia secundaria y no siempre desfavorable; y los microservicios pueden comunicarse tanto de forma síncrona como asíncrona.',
    'ING-SOFT', 'Diseño de Software y Arquitectura',
    'Arquitectura de Software', 'Microservicios',
    'MEDIO', 'PUBLICADA', 3,
    '2026-09-01T09:00:00Z', '2026-09-08T16:03:21Z'
);

INSERT INTO pregunta_opciones (pregunta_id, orden, texto, es_correcta) VALUES
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 0, 'Porque el acoplamiento a nivel de datos impide que cada servicio evolucione y se despliegue de forma independiente, que es la propiedad central del estilo.', TRUE),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 1, 'Porque PostgreSQL no admite conexiones concurrentes desde más de un servicio y se producirían bloqueos permanentes.', FALSE),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 2, 'Porque el teorema CAP prohíbe explícitamente que dos servicios accedan al mismo motor de base de datos relacional.', FALSE),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 3, 'Porque una base de datos compartida siempre resulta más costosa que mantener una instancia separada por cada microservicio.', FALSE),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 4, 'Porque los microservicios solo pueden comunicarse mediante mensajería asíncrona y compartir datos obligaría a usar REST.', FALSE);

INSERT INTO pregunta_bibliografia (pregunta_id, orden, referencia) VALUES
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 0, 'Newman, S. (2021). Building Microservices: Designing Fine-Grained Systems (2nd ed.). O''Reilly Media, cap. 4.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 1, 'Richardson, C. (2018). Microservices Patterns. Manning Publications, patrón Database per Service.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 2, 'Evans, E. (2003). Domain-Driven Design. Addison-Wesley, cap. 14.');

INSERT INTO pregunta_historial_estados (pregunta_id, orden, estado_anterior, estado_nuevo, usuario_id, fecha, motivo) VALUES
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 0, NULL, 'BORRADOR', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2026-09-01T09:00:00Z', 'Creación de la pregunta.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 1, 'BORRADOR', 'PENDIENTE_REVISION', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2026-09-06T14:20:00Z', 'El autor la envió a revisión por pares.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 2, 'PENDIENTE_REVISION', 'EN_REVISION', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-06T14:20:07Z', 'Se asignó un revisor y comenzó la evaluación.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 3, 'EN_REVISION', 'APROBADA', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-07T09:45:30Z', 'Aprobada técnicamente en la revisión por pares.'),
    ('3f9a1c52-8d4e-4b0a-9c77-1f2e3d4a5b6c', 4, 'APROBADA', 'PUBLICADA', '0a0b0c0d-1111-4222-8333-444455556666', '2026-09-08T16:03:21Z', 'El administrador la publicó.');


-- ── 2. APROBADA — lista para que un administrador la publique ───────────────
INSERT INTO preguntas (
    id, autor_id, contexto, pregunta_directa, justificacion,
    competencia_codigo, competencia_nombre, tema, subtema,
    nivel_dificultad, estado, version, creada_en, actualizada_en
) VALUES (
    '7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93',
    'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d',
    'Un estudiante debe procesar un archivo con los registros de 2.000.000 de matrículas para contar cuántas corresponden a cada programa académico. Escribe una primera versión que, por cada registro, recorre la lista completa de programas ya contados buscando si el programa ya apareció; si lo encuentra incrementa su contador y si no, lo agrega al final de la lista. Al ejecutarla sobre el archivo real, el proceso tarda horas. Un compañero le sugiere reemplazar la lista por una tabla hash indexada por el código del programa.',
    '¿Cuál es la complejidad temporal del algoritmo original y a cuál pasa tras aplicar la sugerencia, siendo n la cantidad de registros y p la cantidad de programas distintos?',
    'El algoritmo original hace, por cada uno de los n registros, una búsqueda lineal sobre la lista de programas ya vistos, que en el peor caso tiene p elementos: eso da O(n·p). Con una tabla hash, la búsqueda e inserción pasan a ser O(1) en promedio, así que el recorrido completo queda en O(n). El error típico es responder O(n²) sin notar que la lista interna no crece hasta n sino hasta p, que es mucho menor; y O(n log n) correspondería a una estructura ordenada como un árbol balanceado, no a una tabla hash.',
    'ING-ALG', 'Pensamiento Algorítmico',
    'Análisis de Algoritmos', 'Complejidad Temporal',
    'ALTO', 'APROBADA', 2,
    '2026-09-10T11:15:00Z', '2026-09-14T08:22:10Z'
);

INSERT INTO pregunta_opciones (pregunta_id, orden, texto, es_correcta) VALUES
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 0, 'Pasa de O(n·p) a O(n), porque la tabla hash convierte la búsqueda lineal en una operación de tiempo constante promedio.', TRUE),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 1, 'Pasa de O(n²) a O(n log n), porque la tabla hash mantiene los programas ordenados por su código.', FALSE),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 2, 'Pasa de O(n log n) a O(1), porque contar elementos deja de depender del tamaño del archivo.', FALSE),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 3, 'Se mantiene en O(n) en ambos casos, y la mejora observada se debe únicamente a un mejor uso de la memoria caché.', FALSE),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 4, 'Pasa de O(p²) a O(p), porque el costo depende de la cantidad de programas y no de la cantidad de registros.', FALSE);

INSERT INTO pregunta_bibliografia (pregunta_id, orden, referencia) VALUES
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 0, 'Cormen, T. et al. (2022). Introduction to Algorithms (4th ed.). MIT Press, cap. 11.'),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 1, 'Sedgewick, R. y Wayne, K. (2011). Algorithms (4th ed.). Addison-Wesley, sec. 3.4.');

INSERT INTO pregunta_historial_estados (pregunta_id, orden, estado_anterior, estado_nuevo, usuario_id, fecha, motivo) VALUES
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 0, NULL, 'BORRADOR', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2026-09-10T11:15:00Z', 'Creación de la pregunta.'),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 1, 'BORRADOR', 'PENDIENTE_REVISION', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2026-09-12T10:00:00Z', 'El autor la envió a revisión por pares.'),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 2, 'PENDIENTE_REVISION', 'EN_REVISION', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-12T10:00:05Z', 'Se asignó un revisor y comenzó la evaluación.'),
    ('7c2b9e41-6a3d-4f18-9b52-8d0e4a1c7f93', 3, 'EN_REVISION', 'APROBADA', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-14T08:22:10Z', 'Aprobada técnicamente en la revisión por pares.');


-- ── 3. BORRADOR devuelta por un rechazo — lista para corregir y reenviar ────
INSERT INTO preguntas (
    id, autor_id, contexto, pregunta_directa, justificacion,
    competencia_codigo, competencia_nombre, tema, subtema,
    nivel_dificultad, estado, observaciones_ultima_revision, version,
    creada_en, actualizada_en
) VALUES (
    'e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25',
    'd4e5f6a7-b8c9-4d0e-9f1a-2b3c4d5e6f70',
    'El equipo de desarrollo de una fintech usa una rama principal protegida y ramas de característica de corta duración. Cada vez que un desarrollador abre un pull request, un servidor de integración compila el proyecto, ejecuta la suite de pruebas unitarias y de integración, analiza la calidad del código y, si todo pasa, permite la fusión. Sin embargo, el despliegue a producción sigue siendo un proceso manual que el líder técnico ejecuta los viernes por la tarde.',
    '¿Qué práctica de ingeniería de software describe exactamente la situación del equipo, y cuál le faltaría implementar para poder desplegar de forma automática tras cada fusión?',
    'El equipo practica integración continua: integra cambios pequeños con frecuencia y los valida automáticamente. Lo que le falta es entrega/despliegue continuo, que automatiza el paso a producción. Confundir ambas es el error más común del tema: la integración continua termina en un artefacto validado, el despliegue continuo lo lleva a producción sin intervención manual.',
    'ING-SOFT', 'Diseño de Software y Arquitectura',
    'Ingeniería de Software', 'Integración y Despliegue Continuos',
    'BAJO', 'BORRADOR',
    'El contexto supera las 120 palabras recomendadas para Saber Pro; conviene recortarlo sin perder la situación a analizar.
Dos de los distractores mencionan herramientas concretas (Jenkins, GitLab CI) en vez de prácticas, lo que los hace descartables de inmediato.',
    4,
    '2026-09-05T15:40:00Z', '2026-09-16T17:05:44Z'
);

INSERT INTO pregunta_opciones (pregunta_id, orden, texto, es_correcta) VALUES
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 0, 'Practica integración continua y le falta despliegue continuo, que automatiza la salida a producción tras cada fusión validada.', TRUE),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 1, 'Practica despliegue continuo y le falta integración continua, que validaría los cambios antes de fusionarlos.', FALSE),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 2, 'Practica desarrollo guiado por pruebas y le faltaría adoptar Jenkins como servidor de automatización.', FALSE),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 3, 'Practica integración continua y le faltaría migrar su repositorio a GitLab CI para automatizar el despliegue.', FALSE),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 4, 'Practica revisión por pares y le faltaría incorporar un entorno de preproducción antes de cada despliegue manual.', FALSE);

INSERT INTO pregunta_bibliografia (pregunta_id, orden, referencia) VALUES
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 0, 'Humble, J. y Farley, D. (2010). Continuous Delivery. Addison-Wesley, cap. 1.'),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 1, 'Forsgren, N., Humble, J. y Kim, G. (2018). Accelerate. IT Revolution Press, cap. 4.');

INSERT INTO pregunta_historial_estados (pregunta_id, orden, estado_anterior, estado_nuevo, usuario_id, fecha, motivo) VALUES
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 0, NULL, 'BORRADOR', 'd4e5f6a7-b8c9-4d0e-9f1a-2b3c4d5e6f70', '2026-09-05T15:40:00Z', 'Creación de la pregunta.'),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 1, 'BORRADOR', 'PENDIENTE_REVISION', 'd4e5f6a7-b8c9-4d0e-9f1a-2b3c4d5e6f70', '2026-09-15T09:30:00Z', 'El autor la envió a revisión por pares.'),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 2, 'PENDIENTE_REVISION', 'EN_REVISION', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-15T09:30:12Z', 'Se asignó un revisor y comenzó la evaluación.'),
    ('e5a37f60-2c84-4d91-b7a6-3f9c1e0d8b25', 3, 'EN_REVISION', 'BORRADOR', 'b7c8d9e0-1f2a-4b3c-8d4e-5f6a7b8c9d0e', '2026-09-16T17:05:44Z', 'Rechazada en la revisión por pares; vuelve al autor para corrección.');
