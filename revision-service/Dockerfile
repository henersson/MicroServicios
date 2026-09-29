# =============================================================================
# revision-service — imagen multi-etapa.
#
# IMPORTANTE: el contexto de build es la RAÍZ del repositorio, no esta carpeta:
#
#   docker build -f revision-service/Dockerfile .
#
# Hace falta porque el servicio genera su cliente gRPC a partir de
# contracts/proto/, que es el contrato compartido y vive fuera del módulo.
# En docker-compose queda declarado como:
#   build: { context: ., dockerfile: revision-service/Dockerfile }
# =============================================================================

# ── Etapa 1: dependencias y código gRPC generado ─────────────────────────────
FROM python:3.13-slim AS construccion

WORKDIR /construccion

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Las dependencias van primero y en su propia capa: mientras requirements.txt no
# cambie, Docker reutiliza la caché y no vuelve a descargarlas en cada build.
COPY revision-service/requirements.txt ./
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

ENV PATH="/opt/venv/bin:$PATH"

# El contrato compartido y el código de la aplicación.
COPY contracts/proto/banco_preguntas/v1/banco_preguntas.proto ./proto/
COPY revision-service/app ./app
COPY revision-service/migrations ./migrations
COPY revision-service/alembic.ini ./

# Se genera el cliente gRPC dentro de la imagen, igual que hace
# scripts/generar-grpc.ps1 en local: el código generado es un artefacto
# derivado del contrato y no se versiona.
#
# El sed corrige el import absoluto que emite protoc
# (`import banco_preguntas_pb2 as ...`), que falla dentro de un paquete. Es el
# mismo arreglo que hace el script de PowerShell; ver su documentación.
RUN mkdir -p app/infraestructura/grpc_cliente/generado \
    && python -m grpc_tools.protoc \
        --proto_path=./proto \
        --python_out=app/infraestructura/grpc_cliente/generado \
        --grpc_python_out=app/infraestructura/grpc_cliente/generado \
        banco_preguntas.proto \
    && sed -i 's/^import banco_preguntas_pb2 as /from . import banco_preguntas_pb2 as /' \
        app/infraestructura/grpc_cliente/generado/banco_preguntas_pb2_grpc.py \
    && printf '"""Codigo gRPC generado automaticamente desde contracts/proto."""\n' \
        > app/infraestructura/grpc_cliente/generado/__init__.py \
    && python -c "from app.infraestructura.grpc_cliente.generado import banco_preguntas_pb2_grpc; print('cliente gRPC generado y verificado')"

# ── Etapa 2: ejecutar ────────────────────────────────────────────────────────
FROM python:3.13-slim AS ejecucion

# curl para el healthcheck del compose. --no-install-recommends mantiene la
# imagen pequeña; el rm del final evita dejar los índices de apt dentro.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system saberpro \
    && useradd --system --gid saberpro --home /aplicacion saberpro

WORKDIR /aplicacion

COPY --from=construccion /opt/venv /opt/venv
COPY --from=construccion /construccion/app ./app
COPY --from=construccion /construccion/migrations ./migrations
COPY --from=construccion /construccion/alembic.ini ./
COPY revision-service/entrypoint.sh ./entrypoint.sh

RUN chmod +x entrypoint.sh && chown -R saberpro:saberpro /aplicacion

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    ENTORNO=docker \
    REVISION_HTTP_PORT=8082

# Nunca como root: si alguien consigue ejecutar código dentro del contenedor,
# que sea con los mínimos privilegios posibles.
USER saberpro

EXPOSE 8082

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8082/health || exit 1

ENTRYPOINT ["./entrypoint.sh"]
