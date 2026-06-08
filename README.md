# Observability Agent — Banking CQRS/Kafka Demo

A demonstration of end-to-end observability across **traces** (Jaeger), **metrics** (Prometheus exemplars), and **logs** (Loki) using OpenTelemetry auto-instrumentation, combined with a **banking domain** built on **Axon Framework** CQRS/Event Sourcing, **Kafka** event bus, and **PostgreSQL** persistence.

## Architecture

```
caller-service (port 8081) → downstream-service (port 8082)
         ↓ REST + Kafka               ↓ Axon Aggregate
    Transfer API                Account Command Processing
    PostgreSQL (read model)     PostgreSQL (event store)
         ↓                         ↓
    Kafka event bus ← ← ← ← ← ← ←
         ↓
    OpenTelemetry Jaeger OTLP Exporter
         ↓
       Jaeger (port 16686 / 4317)
         ↓
      Promtail ← JSON logs with trace_id/span_id → Loki (port 3100)
         ↓
      Grafana (port 3000)
         ↓
    Grafana MCP Server (port 8000)  ←  mcp-grafana --disable-write
         ↓
    Jaeger MCP Server (port 8083)   ←  custom Spring AI MCP server

Metrics: caller/downstream → Prometheus (port 9090) with Exemplar trace_id
Infrastructure: Kafka (port 9092), PostgreSQL (port 5432)
```

### Banking Flow
1. **caller-service** exposes `POST /transfers` and `GET /transfers/{id}`
2. On transfer initiation, caller-service:
   - Saves a `PENDING` transfer record in PostgreSQL
   - Calls downstream-service via HTTP to process the transfer
   - Consumes `TransferCompletedEvent` from Kafka
   - Updates transfer status to `COMPLETED` (or `FAILED`)
3. **downstream-service** handles Axon commands:
   - `CreateAccountCommand` → creates an AccountAggregate
   - `TransferFundsCommand` → debits account, emits `FundsTransferredEvent` and `TransferCompletedEvent`
   - Events stored in Axon JPA event store (PostgreSQL)
   - Events published to Kafka via Axon Kafka extension

## Start the Stack

```bash
docker compose up -d --build
```

This starts all 11 containers:
- `jaeger` — Jaeger all-in-one (traces)
- `prometheus` — Prometheus with exemplar storage enabled
- `loki` — Loki log aggregation
- `grafana` — Dashboards
- `mcp-grafana` — Grafana MCP server (read-only)
- `promtail` — Log scraper
- `postgres` — PostgreSQL (Axon event store + caller read model)
- `kafka` — Kafka (Confluent KRaft mode, event bus)
- `caller-service` — Banking Transfer API with Kafka consumer
- `downstream-service` — Axon Account Aggregate with Kafka publisher
- `jaeger-mcp-service` — Custom Jaeger trace MCP server

## Create an Account

Transfers require a source account with sufficient balance.

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"accountId":"acc-1","initialBalance":500}' \
  http://localhost:8082/accounts
```

## Initiate a Transfer

```bash
curl -D - -X POST -H 'Content-Type: application/json' \
  -d '{"fromAccount":"acc-1","toAccount":"acc-2","amount":100}' \
  http://localhost:8081/transfers
```

The response includes:
- `202 Accepted`
- `X-Trace-Id` header for trace correlation
- JSON body with `transferId` and `status: PENDING`

## Poll Transfer Status

```bash
curl http://localhost:8081/transfers/{transferId}
```

Status transitions: `PENDING` → `COMPLETED` (typically within a few seconds, once the Kafka event is consumed).

## Verify Traces (Jaeger UI)

Open [http://localhost:16686](http://localhost:16686) and:

1. Select service `caller-service` from the dropdown
2. Click **Find Traces**
3. You should see a trace that spans both `caller-service` and `downstream-service`
4. Click on the trace to see individual spans: `POST /transfers`, `TransferController.initiateTransfer`, `AccountAggregate.handle`, `EventBus.publishEvent`, `bank.events publish`, `bank.events process`

## Verify Exemplars & Metrics (Prometheus & Grafana)

### Option A: Use the JVM & Application Metrics Dashboard (Recommended)
Open [http://localhost:3000](http://localhost:3000) (login: `admin` / `admin`) and:
1. Go to **Dashboards** in the left sidebar.
2. Select the **Observability** folder, and open **JVM & Application Metrics Dashboard**.
3. Use the **Service** dropdown at the top to filter metrics for `caller-service` or `downstream-service`.
4. In the **HTTP Latency Buckets (with Prometheus Exemplars)** panel:
   - Hover over the plotted dots to view HTTP request durations.
   - Any requests recorded with traces will display green **Exemplar** dots.
   - Hover over an exemplar dot to view its `trace_id` and click the link to jump directly to the Jaeger trace viewer.

### Option B: Explore directly via Prometheus UI
Open [http://localhost:9090](http://localhost:9090) and:

1. Query for: `http_server_requests_seconds_bucket`
2. Switch to the **Graph** or **Table** view
3. Use `curl` to inspect OpenMetrics format directly:
   ```bash
   curl -H 'Accept: application/openmetrics-text' \
     http://localhost:8081/actuator/prometheus | grep trace_id
   ```
4. Confirm histogram bucket lines carry `# {trace_id="…"}` exemplars

## Verify Logs (Grafana → Loki)

Open [http://localhost:3000](http://localhost:3000) (login: `admin` / `admin`) and:

### Option A: Use the Service Logs Dashboard (Recommended)
1. Go to **Dashboards** in the left sidebar.
2. Select the **Observability** folder, and open **Service Logs Dashboard**.
3. Use the dropdown filters at the top:
   - **Service**: Filter logs by `caller-service` or `downstream-service`.
   - **Log Level**: Filter logs by `INFO`, `WARN`, `ERROR`, or `DEBUG`.
   - **Trace ID**: Input a specific trace ID (default is `.*` to show all) to see related logs across both services.
4. *Tip:* Any log line containing a `"trace_id"` will show the trace ID as a clickable link (**TraceID**). Clicking it opens the corresponding Jaeger trace details directly inside Grafana.

### Option B: Explore directly via Loki
1. Navigate to **Explore**
2. Select **Loki** as the data source
3. Run the query:
   ```
   {job="java-logs"} | json | trace_id="<your_trace_id>"
   ```
4. You should see log lines from both `caller-service` and `downstream-service` sharing the same `trace_id`

## Verify Kafka Messages (Kafka UI)

Open [http://localhost:8085](http://localhost:8085) and:

1. Click on the **local** cluster.
2. Select **Topics** on the left menu.
3. Click on the `bank.events` topic.
4. Go to the **Messages** tab.
5. Click **Seek to** / **Submit** to view incoming event messages (like `FundsTransferredEvent`, `TransferCompletedEvent`).

*Alternatively, to view via CLI:*
```bash
docker exec -it observability_agent-kafka-1 kafka-console-consumer --bootstrap-server localhost:9092 --topic bank.events --from-beginning
```

## Container URLs

| Service            | URL                                | Credentials         |
|--------------------|------------------------------------|---------------------|
| Kafka UI           | http://localhost:8085              | (no auth)           |
| Grafana            | http://localhost:3000              | admin / admin       |
| Jaeger UI          | http://localhost:16686             | (no auth)           |
| Prometheus         | http://localhost:9090              | (no auth)           |
| Loki               | http://localhost:3100              | (no auth)           |
| caller-service     | http://localhost:8081/transfers    | (no auth)           |
| downstream-service | http://localhost:8082/accounts     | (no auth)           |
| Promtail           | http://localhost:9080              | (no auth)           |
| Kafka              | localhost:9092                     | (no auth)           |
| PostgreSQL         | localhost:5432                     | appuser / secret    |
| **mcp-grafana**    | **http://localhost:8000**          | (no auth)           |
| **jaeger-mcp-service** | **http://localhost:8083**      | (no auth)           |

## Environment Variables

Both services are configured with:

| Variable                      | Value                                     |
|-------------------------------|-------------------------------------------|
| `OTEL_SERVICE_NAME`           | `caller-service` / `downstream-service`   |
| `OTEL_TRACES_EXPORTER`        | `otlp`                                    |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://jaeger:4317`                      |
| `OTEL_METRICS_EXPORTER`       | `none`                                    |
| `OTEL_LOGS_EXPORTER`          | `none`                                    |
| `JAVA_TOOL_OPTIONS`           | `-javaagent:/opentelemetry-javaagent.jar` |
| `POSTGRES_URL`                | `jdbc:postgresql://postgres:5432/appdb`  |
| `POSTGRES_USER`               | `appuser`                                 |
| `POSTGRES_PASSWORD`           | `secret`                                  |
| `KAFKA_BOOTSTRAP_SERVERS`     | `kafka:29092`                             |

## Phase 1 — Read-only Query Layer

Phase 1 deploys two MCP (Model Context Protocol) servers that expose query tools to the observability stack. These are standalone services added to `docker-compose.yml`.

### Grafana MCP Server

- **Container:** `mcp-grafana`
- **Port:** `8000`
- **Image:** `grafana/mcp-grafana:latest`
- **Transport:** SSE (`--address :8000`)
- **Authentication:** Grafana admin credentials (`admin/admin`)
- **Read-only:** `--disable-write` flag enforces read-only mode

Exposed tools (verified):
- `query_prometheus` — PromQL queries
- `query_loki_logs` — LogQL queries
- `search_dashboards` — Dashboard search
- `alerting_manage_rules` — Alert rule status (read-only operations)
- `generate_deeplink` — Deep-link generation
- `list_sift_investigations` / `get_sift_investigation` — Sift read tools

**Note:** Sift write tools (`find_error_pattern_logs`, `find_slow_requests`) are disabled by `--disable-write`. Manual PromQL/LogQL anomaly detection is used instead.

### Jaeger MCP Server

- **Container:** `jaeger-mcp-service`
- **Port:** `8083`
- **Framework:** Spring Boot 3.4 + Spring AI MCP Server WebMVC SSE
- **Base URL:** `http://jaeger:16686`

Exposed tools:
- `get_trace_by_id` — Fetch a single trace by `trace_id`
- `search_traces` — Search traces by service, time window, and limit

**Implementation note:** Spring AI 1.1.4 does **not** auto-scan `@Tool` annotations for MCP server registration. Tools are registered via an explicit `MethodToolCallbackProvider` bean in `JaegerToolConfiguration.java`.

### Verify MCP Servers

```bash
bash scripts/verify-mcp-tools.sh
```

This script checks:
1. Grafana MCP health (`http://localhost:8000/healthz` → 200)
2. Jaeger MCP actuator health (`http://localhost:8083/actuator/health` → 200)
3. Jaeger MCP SSE stream responds with event data
4. Jaeger container logs show `Registered tools: 2`

### End-to-End Smoke Test

```bash
bash scripts/smoke-test.sh
```

This script:
1. Starts the full stack
2. Creates a bank account (via `downstream-service`)
3. Initiates a transfer (via `caller-service`)
4. Extracts the `trace_id` from the `X-Trace-Id` response header
5. Queries **Prometheus** for metrics, **Loki** for logs, and **Jaeger** for the trace
6. Verifies both MCP servers are still healthy

All queries must return non-empty data for the smoke test to pass.

## Technology Stack

- **Spring Boot 3.4** with Java 17
- **Axon Framework 4.11** — CQRS/Event Sourcing aggregates
- **Axon Kafka Extension 4.11** — distributed event bus
- **Spring Data JPA** — PostgreSQL read models
- **Spring Kafka** — event consumption
- **PostgreSQL** — Axon event store + caller transfer tracking
- **Kafka 3.7** (Confluent, KRaft mode) — event bus
- **OpenTelemetry Java Agent 1.32** — auto-tracing
- **Micrometer + Prometheus** — metrics with exemplar trace correlation
- **Logstash Logback Encoder** — JSON logs with trace context
- **Grafana + Loki + Promtail** — log aggregation
- **Jaeger** — distributed tracing
