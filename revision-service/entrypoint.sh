#!/bin/sh
# Arranque del revision-service dentro del contenedor.
#
# Las migraciones se aplican aquí, antes de levantar la API, por la misma razón
# por la que el banco usa Flyway al arrancar: el esquema y el código se
# despliegan juntos, así que nunca hay una versión del servicio corriendo contra
# un esquema que no conoce.
#
# Este archivo DEBE tener finales de línea LF, no CRLF: con CRLF, Linux busca un
# intérprete llamado "/bin/sh\r" y falla con "no such file or directory".
# El .gitattributes de la raíz lo fuerza (*.sh text eol=lf).
set -e

echo "==> Aplicando migraciones de Alembic..."
alembic upgrade head

echo "==> Arrancando la API en el puerto ${REVISION_HTTP_PORT:-8082}..."
# exec para que uvicorn quede como proceso 1 y reciba las señales de parada de
# Docker; si no, el contenedor tardaría 10 s en morir en cada `docker compose down`.
exec uvicorn app.main:app \
    --host 0.0.0.0 \
    --port "${REVISION_HTTP_PORT:-8082}" \
    --log-level info \
    --no-access-log
