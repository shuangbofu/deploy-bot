# syntax=docker/dockerfile:1.7

FROM node:24-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-17 AS backend-build
WORKDIR /workspace

COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY plugin/pom.xml plugin/pom.xml
COPY plugin/api/pom.xml plugin/api/pom.xml
COPY plugin/common/pom.xml plugin/common/pom.xml
COPY plugin/runtime/pom.xml plugin/runtime/pom.xml
COPY plugin/starter-builtin/pom.xml plugin/starter-builtin/pom.xml
COPY plugin/springboot/pom.xml plugin/springboot/pom.xml
COPY plugin/node-static/pom.xml plugin/node-static/pom.xml
COPY plugin/fullstack/pom.xml plugin/fullstack/pom.xml
COPY backend/pom.xml backend/pom.xml
RUN mvn -B -pl backend -am dependency:go-offline

COPY common common
COPY plugin plugin
COPY backend backend
COPY --from=frontend-build /workspace/frontend/dist backend/src/main/resources/static
RUN mvn -B -pl backend -am package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        git \
        openssh-client \
        rsync \
        tar \
        gzip \
    && rm -rf /var/lib/apt/lists/*

COPY --from=backend-build /workspace/backend/target/backend-*.jar /app/deploy-bot.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="" \
    DEPLOYBOT_BASE_URL=http://localhost:8080 \
    DEPLOYBOT_WORKSPACE_ROOT=/app/runtime \
    SPRING_DATASOURCE_URL=jdbc:h2:file:/app/runtime/deploy-bot-db \
    LOGGING_FILE_NAME=/app/runtime/logs/deploy-bot-app.log

RUN mkdir -p /app/runtime/logs

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/deploy-bot.jar"]
