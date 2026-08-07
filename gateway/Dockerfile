# syntax=docker/dockerfile:1
#
# Build context MUST be the repo root (not this directory) — the Maven reactor build needs
# every sibling module gateway depends on. Build from repo root, e.g.:
#   docker build -f gateway/Dockerfile -t dkp-gateway .

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Poms only first, so dependency resolution is cached across source-only changes.
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
RUN mvn -B -pl gateway -am dependency:go-offline

# Real sources for gateway and everything it depends on. No identity-service or ecommerce-service
# here — both are standalone apps now with no Maven dependency from gateway (see root CLAUDE.md);
# their poms above are only needed for Maven to parse the reactor's full <modules> list.
COPY common common
COPY infra infra
COPY content-service content-service
COPY ai-service ai-service
COPY task-service task-service
COPY social-service social-service
COPY gateway gateway
RUN mvn -B -pl gateway -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=build /workspace/gateway/target/gateway-*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
