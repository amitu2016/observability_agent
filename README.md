# Observability Agent — Distributed Tracing Demo

A demonstration of end-to-end observability: trace correlation across Jaeger (traces), Prometheus (exemplars), and Loki (logs) using OpenTelemetry auto-instrumentation and Micrometer with custom exemplar support.

## Architecture

```
caller-service (port 8081) → downstream-service (port 8082)
         ↓                         ↓
    OpenTelemetry Jaeger OTLP Exporter
         ↓
       Jaeger (port 16686 / 4317)
         ↓
      Promtail ← JSON logs with trace_id/span_id → Loki (port 3100)
         ↓
      Grafana (port 3000)

Metrics: caller/downstream → Prometheus (port 9090) with Exemplar trace_id
```

## Start the Stack

```bash
docker compose up -d --build
```

This starts all 7 containers:
- `jaeger` — Jaeger all-in-one (traces)
- `prometheus` — Prometheus with exemplar storage enabled
- `loki` — Loki log aggregation
- `grafana` — Dashboards
- `promtail` — Log scraper
- `caller-service` — Instrumented Spring Boot service
- `downstream-service` — Instrumented Spring Boot service

## Trigger a Request

```bash
curl http://localhost:8081/hello
```

The response includes an `X-Trace-Id` header you can use for correlation.

## Verify Traces (Jaeger UI)

Open [http://localhost:16686](http://localhost:16686) and:

1. Select service `caller-service` from the dropdown
2. Click **Find Traces**
3. You should see a trace that spans both `caller-service` and `downstream-service`
4. Click on the trace to see the individual spans

## Verify Exemplars (Prometheus UI)

Open [http://localhost:9090](http://localhost:9090) and:

1. Query for: `http_server_requests_seconds_bucket`
2. Click on any bucket value to open the exemplar panel
3. Confirm the exemplar contains a `trace_id` label
4. Use that `trace_id` to jump to Jaeger

## Verify Logs (Grafana → Loki)

Open [http://localhost:3000](http://localhost:3000) (login: `admin` / `admin`) and:

1. Navigate to **Explore**
2. Select **Loki** as the data source
3. Run the query:
   ```
   {job="java-logs"} | json | trace_id="<your_trace_id>"
   ```
4. You should see log lines from both `caller-service` and `downstream-service` sharing the same `trace_id`

## Container URLs

| Service            | URL                        | Credentials         |
|--------------------|----------------------------|---------------------|
| Grafana            | http://localhost:3000      | admin / admin       |
| Jaeger UI          | http://localhost:16686     | (no auth)           |
| Prometheus         | http://localhost:9090      | (no auth)           |
| Loki               | http://localhost:3100      | (no auth)           |
| caller-service     | http://localhost:8081/hello | (no auth)          |
| downstream-service | http://localhost:8082/hello | (no auth)          |
| Promtail           | http://localhost:9080      | (no auth)           |

## Environment Variables

Both services are configured with:

| Variable                      | Value                              |
|-------------------------------|------------------------------------|
| `OTEL_SERVICE_NAME`           | `caller-service` / `downstream-service` |
| `OTEL_TRACES_EXPORTER`        | `otlp`                             |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4317`               |
| `OTEL_METRICS_EXPORTER`       | `none`                             |
| `OTEL_LOGS_EXPORTER`          | `none`                             |
| `JAVA_TOOL_OPTIONS`           | `-javaagent:/opentelemetry-javaagent.jar` |