# =============================================================================
# banco-preguntas-service — imagen multi-etapa.
#
# IMPORTANTE: el contexto de build es la RAÍZ del repositorio, no esta carpeta:
#
#   docker build -f banco-preguntas-service/Dockerfile .
#
# Hace falta porque el servicio genera su código gRPC a partir de
# contracts/proto/, que es el contrato compartido y vive fuera del módulo.
# En docker-compose queda declarado como:
#   build: { context: ., dockerfile: banco-preguntas-service/Dockerfile }
# =============================================================================

# ── Etapa 1: compilar ────────────────────────────────────────────────────────
# Imagen basada en Debian, NO en Alpine: el protobuf-maven-plugin descarga el
# binario de `protoc` desde Maven Central y ese binario está enlazado contra
# glibc. En Alpine, que usa musl, falla con "PROTOC FAILED" nada más ejecutarse.
# La etapa de ejecución sí puede ser Alpine, porque allí ya no hace falta protoc.
FROM eclipse-temurin:21-jdk AS construccion

WORKDIR /construccion

# El Maven Wrapper y el pom van primero y en su propia capa: mientras el pom no
# cambie, Docker reutiliza la caché de dependencias y no vuelve a descargar
# medio Maven Central en cada build.
COPY banco-preguntas-service/.mvn/ .mvn/
COPY banco-preguntas-service/mvnw banco-preguntas-service/mvnw.cmd ./
COPY banco-preguntas-service/pom.xml ./

# El .proto tiene que estar antes de compilar porque el protobuf-maven-plugin
# genera de él las clases del servicio gRPC. La ruta absoluta /contracts/proto
# es justo la que resuelve ${project.basedir}/../contracts/proto del pom, al
# estar el proyecto en /construccion.
COPY contracts/proto/ /contracts/proto/

RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Ahora sí el código fuente, que es lo que cambia en cada commit.
COPY banco-preguntas-service/src/ src/

# Los tests ya se corrieron en el build del proyecto; repetirlos aquí alargaría
# la imagen sin aportar nada, y además los de integración necesitarían
# PostgreSQL y RabbitMQ dentro del contenedor de build.
RUN ./mvnw -B -q clean package -DskipTests

# ── Etapa 2: ejecutar ────────────────────────────────────────────────────────
# Solo el JRE: la imagen final no lleva ni compilador ni Maven ni el código
# fuente, que es tanto más ligera como menos expuesta.
FROM eclipse-temurin:21-jre-alpine AS ejecucion

# curl para el healthcheck del compose.
RUN apk add --no-cache curl \
    && addgroup -S saberpro \
    && adduser -S -G saberpro saberpro

WORKDIR /aplicacion

COPY --from=construccion /construccion/target/*.jar aplicacion.jar

# Nunca como root: si alguien consigue ejecutar código dentro del contenedor,
# que sea con los mínimos privilegios posibles.
RUN chown -R saberpro:saberpro /aplicacion
USER saberpro

# 8081 REST, 9091 gRPC.
EXPOSE 8081 9091

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# Forma "exec" con sh -c para que $JAVA_OPTS se expanda y, aun así, la JVM
# quede como proceso 1 y reciba las señales de parada de Docker.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar aplicacion.jar"]
