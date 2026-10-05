# Servidor de mensajeria: construccion multi-etapa (build + runtime).
# El JAR ya incluye la app; solo necesita Java 21, MySQL y discos persistentes.
# Uso: docker compose build servidor && docker compose up -d
#
# Variables de configuracion (se pasan como args Spring Boot en docker-compose.yml,
# command: del servicio `servidor`; prevalecen sobre application.properties):
#   --db.active=mysql            Motor activo (mysql | postgres | oracle).
#   --db.mysql.url=...           JDBC del MySQL del compose (host interno `mysql:3306`).
#   --db.mysql.user / --db.mysql.pass   Credenciales (desarrollo: root/12345; en
#                                produccion usar secretos, no el compose de ejemplo).
#   --red.puerto=5000            Puerto TCP de mensajeria (clientes -> servidor).
#   --red.tls.puerto=5001        Solo si red.tls.habilitado=true (ver server.properties).
#   --app.consola=false --app.escritorio=false  Sin UI en contenedor.
#   --red.upload-dir=/app/uploads  Directorio persistente (volumen uploads_data).
#   --logging.file.name=/app/logs/server.log   Log persistente (volumen logs_data).

# ---- Etapa 1: compilacion (Maven 3.9 + JDK 21; coincide con pom.xml: java.version=21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Primero los descriptores y fuentes completos; el repo Maven local se cachea
# entre builds con el mount BuildKit (offline-friendly: repetir el build reusa
# dependencias sin volver a descargarlas).
COPY pom.xml .
COPY common-protocol ./common-protocol
COPY server ./server
COPY client ./client
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl server/servidor-app -am package -DskipTests

# ---- Etapa 2: ejecucion (solo JRE 21; sin Maven ni fuentes) ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Nombre exacto del artefacto (parent pom: 1.0.0-SNAPSHOT). Se fija el nombre en
# lugar de `servidor-app-*.jar` porque spring-boot:repackage deja ademas el
# `*.jar.original` y el comodin ambiguo rompe el COPY.
COPY --from=build /build/server/servidor-app/target/servidor-app-1.0.0-SNAPSHOT.jar ./servidor.jar
RUN mkdir -p /app/uploads /app/logs /app/config
VOLUME ["/app/uploads", "/app/logs", "/app/config"]
# 5000 = TCP mensajeria (siempre). 5001 = TLS opcional (solo si red.tls.habilitado=true).
EXPOSE 5000 5001
# Sonda TCP al puerto de mensajeria (bash /dev/tcp, sin paquetes extra).
# start-period amplio: Spring + Flyway tardan en abrir el socket.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/5000'
ENTRYPOINT ["java", "-jar", "/app/servidor.jar", \
  "--app.consola=false", \
  "--app.escritorio=false", \
  "--red.upload-dir=/app/uploads", \
  "--logging.file.name=/app/logs/server.log"]
