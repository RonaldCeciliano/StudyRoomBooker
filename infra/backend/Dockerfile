# syntax=docker/dockerfile:1
# Spring Boot backend. Multi-arch base images (amd64 and arm64); build from the repo root:
#   docker build -f infra/backend/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY backend/src src
# The build copies the committed OpenAPI contract into the jar.
COPY docs/openapi/studyroombooker.yaml /workspace/docs/openapi/studyroombooker.yaml
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
    && cp target/backend-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"
# No curl in the JRE image; bash's /dev/tcp is enough to read the health endpoint.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "\"status\":\"UP\"" <&3'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
