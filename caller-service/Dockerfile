FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY caller-service/build.gradle ./caller-service/build.gradle
RUN ./gradlew :caller-service:dependencies --no-daemon -q

COPY caller-service/src ./caller-service/src
RUN ./gradlew :caller-service:bootJar --no-daemon -q

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN curl -sL -o /opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.32.0/opentelemetry-javaagent.jar

COPY --from=build /app/caller-service/build/libs/*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=caller-service
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
