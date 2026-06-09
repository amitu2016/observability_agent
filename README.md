# Observability Agent — Banking CQRS/Kafka Demo

A demonstration of end-to-end observability across **traces** (Jaeger), **metrics** (Prometheus exemplars), and **logs** (Loki) using OpenTelemetry auto-instrumentation, combined with a **banking domain** built on **Axon Framework** CQRS/Event Sourcing, **Kafka** event bus, and **PostgreSQL** persistence.

## Architecture

```
         Banking Dashboard (React UI, port 5173)
                   ↓ REST (CORS enabled)
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
         ↓
    Triage Agent (port 8084)        ←  Spring AI MCP client + OpenAI

Metrics: caller/downstream → Prometheus (port 9090) with Exemplar trace_id
Infrastructure: Kafka (port 9092), PostgreSQL (port 5432)
```

### Banking Flow
1. **Banking Dashboard (UI)**: React application providing a visual interface to manage accounts and transfers.
2. **caller-service** exposes `POST /transfers` and `GET /transfers`:
   - Saves a `PENDING` transfer record in PostgreSQL.
   - Calls downstream-service via HTTP to process the transfer.
   - Consumes `TransferCompletedEvent` from Kafka to update status.
3. **downstream-service** handles account management and CQRS read model:
   - `AccountAggregate` handles commands and emits events.
   - `AccountProjection` projects events into a queryable JPA read model.
   - Exposes `POST /accounts` and `GET /accounts`.

## Start the Stack

### 1. Backend Infrastructure
```bash
docker compose up -d --build
```
Starts all backend services (Jaeger, Prometheus, Loki, Grafana, Kafka, Postgres, and the Java microservices).

### 2. Banking Dashboard (UI)
```bash
cd ui
npm install
npm run dev
```
The UI will be available at [http://localhost:5173](http://localhost:5173).

## Features

### Banking Dashboard
A modern React-based UI to interact with the banking system:
- **Create Account**: Provision new accounts with initial balances.
- **Initiate Transfer**: Move funds between accounts.
- **Accounts Overview**: Real-time list of all accounts and their current balances.
- **Transfer History**: Monitor the status of transfers as they transition from `PENDING` to `COMPLETED` via Kafka.

### Triage Agent
The triage agent (`triage-agent`) is a Spring AI MCP client that uses OpenAI GPT-4o to orchestrate incident investigation:
- Receive a symptom (e.g., "High error rate on caller-service").
- Automatically query Prometheus, Jaeger, and Loki.
- Correlate signals and produce a root-cause hypothesis with deep links to Grafana.

## Container URLs

| Service            | URL                                | Credentials         |
|--------------------|------------------------------------|---------------------|
| **Banking UI**     | **http://localhost:5173**          | (no auth)           |
| Kafka UI           | http://localhost:8085              | (no auth)           |
| Grafana            | http://localhost:3000              | admin / admin       |
| Jaeger UI          | http://localhost:16686             | (no auth)           |
| Prometheus         | http://localhost:9090              | (no auth)           |
| Loki               | http://localhost:3100              | (no auth)           |
| caller-service     | http://localhost:8081/transfers    | (no auth)           |
| downstream-service | http://localhost:8082/accounts     | (no auth)           |
| **triage-agent**   | **http://localhost:8084**          | (no auth)           |

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

## Phase 2 — Triage Agent

The triage agent (`triage-agent`) is a Spring AI MCP client that uses OpenAI GPT-4o to orchestrate incident investigation across the observability stack.

### Overview

The agent receives a symptom description via REST API, then uses MCP tools to:
1. List available metrics, labels, and datasources (grounding)
2. Query Prometheus for anomalous metrics in the affected time window
3. Fetch traces via the Jaeger MCP server
4. Correlate evidence across metrics, traces, and logs
5. Return a root-cause hypothesis with deep links

### REST Endpoints

```
POST /api/triage/investigate         — submit investigation (async, returns 202 immediately)
GET  /api/triage/investigation/{id}  — poll investigation status & results
```

Request body (`TriageRequest`):
```json
{
  "question": "error rate for caller-service in the last 15 min"
}
```

Response body (`TriageReport`):
```json
{
  "question": "error rate for caller-service in the last 15 min",
  "service": "caller-service",
  "timeWindow": "15m",
  "rootCause": "Significant drop in request processing for status '202'...",
  "confidence": 0.8,
  "steps": [
    { "stepName": "scope", "status": "success", "summary": "Identified service caller-service and time window 15m", "toolCalls": [], "durationMs": 0 },
    { "stepName": "metrics", "status": "success", "summary": "Queried PromQL and found anomalous results...", "toolCalls": [], "durationMs": 0 },
    { "stepName": "traces", "status": "success", "summary": "Fetched traces via Jaeger...", "toolCalls": [], "durationMs": 0 },
    { "stepName": "logs", "status": "success", "summary": "Retrieved log lines from Loki...", "toolCalls": [], "durationMs": 0 },
    { "stepName": "hypothesis", "status": "success", "summary": "Root cause hypothesis...", "toolCalls": [], "durationMs": 0 }
  ],
  "deepLinks": ["http://localhost:3000/explore?orgId=1&left=%7B%22datasource%22..."],
  "notChecked": ["Trace correlation with Kafka consumer lag", "Database connection pool saturation"]
}
```

### Test Endpoints

**Health check:**
```bash
curl -s http://localhost:8084/actuator/health | jq .
```

**Simple triage question (quick test):**
```bash
curl -X POST http://localhost:8084/api/triage/investigate \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the error rate for caller-service in the last 15 minutes?"}' \
  | jq .
```

**Investigate latency spike:**
```bash
curl -X POST http://localhost:8084/api/triage/investigate \
  -H 'Content-Type: application/json' \
  -d '{"question":"High latency on caller-service"}' \
  | jq .
```

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

## Phase 4b — Async Investigation via Kafka

Investigations can also be triggered asynchronously, decoupling the HTTP request from the long-running LLM pipeline.

### REST Endpoints

**Submit an async investigation:**
```bash
curl -X POST http://localhost:8084/api/triage/investigate \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is the error rate for caller-service in the last 15 minutes?"}' \
  | jq .
```

Response (immediately, while investigation runs in background):
```json
{
  "investigationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "question": "What is the error rate for caller-service in the last 15 minutes?",
  "status": "PENDING"
}
```

**Poll for results:**
```bash
curl -s http://localhost:8084/api/triage/investigation/a1b2c3d4-e5f6-7890-abcd-ef1234567890 | jq .
```

Response when completed:
```json
{
  "investigationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "question": "What is the error rate for caller-service in the last 15 minutes?",
  "status": "COMPLETED",
  "report": {
    "service": "caller-service",
    "timeWindow": "15m",
    "rootCause": "Significant drop in request processing...",
    "confidence": 0.8,
    "steps": [...],
    "deepLinks": [...],
    "notChecked": [...]
  }
}
```

### Architecture

```
POST /api/triage/investigate
  ↓  create PENDING job in PostgreSQL
  ↓  publish to Kafka topic triage.requests
Kafka ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
  ↓
@KafkaListener (triage-agent)
  ↓  load job, set RUNNING
  ↓  TriageOrchestratorService.investigate()
  ↓  save COMPLETED + report JSON
GET /api/triage/investigation/{id}
  ↓  return status + report (if ready)
```

### Implementation

- `InvestigationJob` — JPA entity (`id`, `question`, `status`, `reportJson`, `createdAt`, `completedAt`).
- `InvestigationJobRepository` — Spring Data JPA.
- `InvestigationKafkaProducer` — publishes `InvestigationRequestEvent` to `triage.requests`.
- `InvestigationKafkaConsumer` — consumes, runs investigation, updates job status.
- `TriageController` — exposes `POST /api/triage/investigate` and `GET /api/triage/investigation/{id}`.

## Test Suite

All tests pass (`./gradlew test`):

- `TriageAgentApplicationTest` — Spring context loads successfully (H2 embedded database).
- `TriageControllerTest` — WebMvcTest for REST endpoints (sync + async).
- `TriageOrchestratorTest` — Step ordering and error resilience.
- `InvestigationMapperTest` — State → report mapping.
- `InvestigationKafkaProducerTest` — 6 tests for Kafka publishing.
- `InvestigationKafkaConsumerTest` — 10 tests for Kafka consumption and job lifecycle.
- `TriageServiceTest` — ChatClient prompt construction.

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
- **Spring AI MCP client** — MCP client for tool orchestration
- **OpenAI GPT-4o** — LLM for triage reasoning
