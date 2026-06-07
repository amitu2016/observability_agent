# Phase 0 — Correlation Backbone Implementation Plan

## Goal
Build two instrumented Spring Boot services and a local Docker Compose observability stack so that a single cross-service HTTP request produces a `trace_id` visible in **Jaeger**, referenced by a **Prometheus metric exemplar**, and present in **Loki** log lines.

## Stack
- Java 17, Spring Boot 3.2.x
- Gradle (Groovy DSL) multi-module project
- OpenTelemetry Java agent (auto-instrumentation)
- Micrometer + Prometheus registry with custom `ExemplarSupplier`
- logstash-logback-encoder for JSON logs with MDC `trace_id`/`span_id`
- Docker Compose: Jaeger, Prometheus, Loki, Grafana, Promtail

## Chunks

### Chunk A — Root project + Caller Service
**Files:**
- `settings.gradle`
- `build.gradle` (root)
- `caller-service/build.gradle`
- `caller-service/src/main/java/com/example/caller/CallerApplication.java`
- `caller-service/src/main/java/com/example/caller/HelloController.java`
- `caller-service/src/main/java/com/example/caller/DownstreamClient.java`
- `caller-service/src/main/java/com/example/caller/config/PrometheusExemplarConfig.java`
- `caller-service/src/main/java/com/example/caller/config/TraceIdExemplarSupplier.java`
- `caller-service/src/main/resources/application.yml`
- `caller-service/src/main/resources/logback-spring.xml`
- `caller-service/Dockerfile`

**Requirements:**
1. Root `build.gradle` applies Spring Boot and dependency-management plugins (`apply false`).
2. Caller service depends on `spring-boot-starter-web`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`, `opentelemetry-api`, `logstash-logback-encoder`.
3. `HelloController` exposes `GET /hello` which calls downstream via `RestClient`.
4. `PrometheusExemplarConfig` registers a `MeterRegistryCustomizer<PrometheusMeterRegistry>` that enables exemplars and sets `TraceIdExemplarSupplier`.
5. `TraceIdExemplarSupplier` reads `Span.current().getSpanContext().getTraceId()` and returns `Exemplar.of(traceId, System.currentTimeMillis())` when valid.
6. `application.yml` exposes `prometheus` actuator endpoint and enables exemplars.
7. `logback-spring.xml` uses `RollingFileAppender` with `LoggingEventCompositeJsonEncoder` that writes JSON to `/var/log/app/caller-service.log`, including MDC fields (`trace_id`, `span_id`).
8. `Dockerfile` uses `eclipse-temurin:17-jdk` as builder and runtime, downloads OTel Java agent (v1.32.0) to `/opentelemetry-javaagent.jar`, copies JAR, sets `JAVA_TOOL_OPTIONS=-javaagent:/opentelemetry-javaagent.jar`.

**Acceptance criteria:**
- `./gradlew :caller-service:bootJar` exits 0.
- JAR contains `caller-service-0.0.1-SNAPSHOT.jar`.

### Chunk B — Downstream Service
**Files:**
- `downstream-service/build.gradle`
- `downstream-service/src/main/java/com/example/downstream/DownstreamApplication.java`
- `downstream-service/src/main/java/com/example/downstream/HelloController.java`
- `downstream-service/src/main/java/com/example/downstream/config/PrometheusExemplarConfig.java`
- `downstream-service/src/main/java/com/example/downstream/config/TraceIdExemplarSupplier.java`
- `downstream-service/src/main/resources/application.yml`
- `downstream-service/src/main/resources/logback-spring.xml`
- `downstream-service/Dockerfile`

**Requirements:**
1. Same dependencies and patterns as caller service.
2. `HelloController` exposes `GET /hello` returning a simple string.
3. Same exemplar and logging configuration as caller.
4. `application.yml` uses `server.port=8080` (default) and `management.server.port=8081` (or different port to avoid conflict with caller). Actually, both can run on port 8080 inside Docker but mapped differently on host. Let's use `server.port=8080` for both, and map host ports differently.

**Acceptance criteria:**
- `./gradlew :downstream-service:bootJar` exits 0.

### Chunk C — Docker Compose Stack
**Files:**
- `docker-compose.yml`
- `prometheus.yml`
- `loki.yml`
- `promtail.yml`
- `grafana/provisioning/datasources/datasources.yml` (optional but helpful)

**Requirements:**
1. Services:
   - `jaeger`: `jaegertracing/all-in-one:1.50`, ports `16686`, `4317` (OTLP gRPC), `14250`.
   - `prometheus`: `prom/prometheus:v2.51`, mounts `prometheus.yml`, port `9090`.
   - `loki`: `grafana/loki:2.9.1`, mounts `loki.yml`, port `3100`.
   - `grafana`: `grafana/grafana:10.2.2`, port `3000`, depends on prometheus/loki/jaeger, admin/admin password.
   - `promtail`: `grafana/promtail:2.9.1`, mounts `promtail.yml` and shared log volume `/var/log/app`, depends on loki.
   - `caller-service`: built from `caller-service/Dockerfile`, port host `8081` → container `8080`, shared log volume, env vars for OTel (see below), depends on jaeger.
   - `downstream-service`: built from `downstream-service/Dockerfile`, port host `8082` → container `8080`, shared log volume, env vars for OTel, depends on jaeger.
2. Prometheus `prometheus.yml`:
   - Scrape interval 10s.
   - Job `caller-service` target `caller-service:8081` (or whatever management port). Wait, if management port is same as server port, actuator is on `8080/actuator/prometheus`. Since we map host port differently, inside Docker network services communicate on `8080`. So targets should be `caller-service:8080` and `downstream-service:8080` with `metrics_path=/actuator/prometheus`.
   - Enable exemplar storage: `storage.tsdb.enable_exemplar_storage=true` or `enable_exemplar_storage: true` in yml. In Prometheus v2.29+, the config option is `storage: tsdb: enable_exemplar_storage: true`? Actually, Prometheus config uses `--enable-feature=exemplar-storage` CLI flag. In docker-compose, pass it as `command: ['--config.file=/etc/prometheus/prometheus.yml', '--enable-feature=exemplar-storage']`.
3. Loki `loki.yml`: filesystem storage, single node, no auth.
4. Promtail `promtail.yml`: client `http://loki:3100/loki/api/v1/push`. Scrape config for `java-logs` with `static_configs` targeting `localhost` and `__path__: /var/log/app/*.log`. Pipeline stage: `json` extracting `trace_id`, `span_id`, `level`, `message`, `logger`, `timestamp`. Then `labels` stage promoting `trace_id`, `span_id`, `level` to Loki labels.
5. All services on a shared Docker network `observability`.

**OTel environment variables for service containers:**
- `OTEL_SERVICE_NAME=caller-service` / `downstream-service`
- `OTEL_TRACES_EXPORTER=otlp`
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317`
- `OTEL_METRICS_EXPORTER=none` (we use Micrometer directly)
- `OTEL_LOGS_EXPORTER=none` (we use logback + Promtail)
- `OTEL_JAVAAGENT_ENABLED=true`
- `JAVA_TOOL_OPTIONS=-javaagent:/opentelemetry-javaagent.jar`

**Acceptance criteria:**
- `docker compose up -d --build` exits 0.
- `docker compose ps` shows all 7 containers in `Up` state.

### Chunk D — README & Verification Guide
**Files:**
- `README.md`

**Requirements:**
1. Section: “Start the stack” — `docker compose up -d --build`
2. Section: “Trigger a request” — `curl http://localhost:8081/hello`
3. Section: “Verify traces” — open `http://localhost:16686`, search by service name, find cross-service trace.
4. Section: “Verify exemplars” — open `http://localhost:9090`, query `http_server_requests_seconds_bucket`, click exemplar, find trace_id.
5. Section: “Verify logs” — open `http://localhost:3000`, explore Loki, query `{job="java-logs"} | json | trace_id="<trace_id>"`.
6. List of all container URLs and default credentials.

**Acceptance criteria:**
- README exists and all verification steps are documented.

## Build Order
1. Run Chunk A and Chunk B **in parallel** (independent).
2. Run Chunk C after A and B complete (docker-compose references service Dockerfiles).
3. Run Chunk D after C completes (documents the final stack).

## Verification (end-to-end)
After all chunks:
1. `docker compose up -d --build`
2. `curl -s http://localhost:8081/hello`
3. Extract trace_id from response header or logs (optional: add trace_id to response header for easier manual testing? Actually, we can just look in Jaeger UI. Or we can add a response header `X-Trace-Id` in the controller. This is helpful for manual verification. Let's add `X-Trace-Id` response header in caller controller. This doesn't affect the core requirements but makes testing easier. I should note this in the plan.
   - Add `@RequestHeader` or `Tracer`? With OTel agent, `Span.current().getSpanContext().getTraceId()` returns the trace_id. We can return it in a response header `X-Trace-Id` from both controllers. This is low effort and high value for manual testing.
4. In Jaeger UI: confirm trace spans both services.
5. In Prometheus UI: query `http_server_requests_seconds_exemplar` (or bucket) and confirm `trace_id` label.
6. In Grafana/Loki: query logs with the trace_id and confirm they exist for both services.
