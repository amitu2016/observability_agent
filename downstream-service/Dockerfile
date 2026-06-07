FROM eclipse-temurin:17-jre

WORKDIR /app

RUN curl -sL -o /opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.32.0/opentelemetry-javaagent.jar

COPY build/libs/*.jar app.jar

ENV JAVA_TOOL_OPTIONS="-javaagent:/opentelemetry-javaagent.jar"
ENV OTEL_SERVICE_NAME=downstream-service
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
