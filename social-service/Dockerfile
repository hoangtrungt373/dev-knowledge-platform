# syntax=docker/dockerfile:1
#
# Build context MUST be the repo root (not this directory) — the Maven reactor build needs
# the sibling modules social-service depends on (common, infra). Build from repo root, e.g.:
#   docker build -f social-service/Dockerfile -t dkp-social-service .

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Poms only first, so dependency resolution is cached across source-only changes. Every module's
# pom must be present for Maven to parse the root reactor's <modules> list, even though only
# common/infra/social-service sources get copied below.
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY infra/pom.xml infra/pom.xml
COPY content-service/pom.xml content-service/pom.xml
COPY ai-service/pom.xml ai-service/pom.xml
COPY task-service/pom.xml task-service/pom.xml
COPY social-service/pom.xml social-service/pom.xml
COPY identity-service/pom.xml identity-service/pom.xml
COPY ecommerce-service/pom.xml ecommerce-service/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN mvn -B -pl social-service -am dependency:go-offline

# Real sources for social-service and everything it depends on.
COPY common common
COPY infra infra
COPY social-service social-service
RUN mvn -B -pl social-service -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=build /workspace/social-service/target/social-service-*.jar app.jar
USER spring
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
