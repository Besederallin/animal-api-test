# syntax=docker/dockerfile:1
# Builds either version of the service:
#   docker build --build-arg MODULE=animal-pictures-postgres -t animal-pictures-postgres .
#   docker build --build-arg MODULE=animal-pictures-embedded -t animal-pictures-embedded .
ARG MODULE=animal-pictures-postgres

# ---- build stage: compile with the Maven Wrapper (no local Maven or JDK needed) ----
FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY animal-pictures-core/ animal-pictures-core/
COPY animal-pictures-postgres/ animal-pictures-postgres/
COPY animal-pictures-embedded/ animal-pictures-embedded/
# Tests run in CI / `./mvnw verify`; the PostgreSQL tests need Docker, which is not available here.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${MODULE} -am package -DskipTests \
 && cp ${MODULE}/target/${MODULE}.jar /app.jar

# ---- runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app \
 && mkdir -p /app/data && chown app:app /app/data
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
# Used by the embedded (H2) version to store its database file.
ENV DATA_DIR=/app/data
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
