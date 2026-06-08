# Observability Incident-Triage Agent — Build Spec & Checklist

> Hand-off document for the coding agent. This is self-contained; you do not need
> any prior conversation. Read "Goal", "Key decisions", and "Design principles"
> before writing code, then work the phases top to bottom.

---

## Goal

Build an AI agent that performs **incident triage** by correlating metrics,
traces, and logs across our observability stack. The agent is **read-only**: it
investigates and produces a root-cause hypothesis with evidence. It does **not**
take remediation actions (no restarts, scaling, rollbacks) and does not modify
configuration.

Primary user flow: an alert fires (or a human pastes a symptom) → the agent scopes
the problem, pulls the relevant signals, correlates them, and returns a written
root-cause report with deep links to the supporting data.

---

## Stack

- **Language / platform:** Java (Spring Boot).
- **Agent framework:** Spring AI (1.1+) used as an **MCP client**.
- **Metrics:** Prometheus (PromQL).
- **Traces:** Jaeger (current). See "Open decisions" — there is a seam here.
- **Logs:** Loki (LogQL). **This must be added** — see "Key decisions".
- **Instrumentation:** OpenTelemetry (OTel) Java agent + SDK.
- **Dashboards / query gateway:** Grafana, fronted by the **Grafana MCP server**
  (`mcp-grafana`, run with `--disable-write`).
- **Model:** OpenAI GPT-4o via `spring-ai-starter-model-openai` (initially planned for Anthropic Claude model, switched after human direction).

---

## Key decisions (read these — they shaped the design)

1. **Triage is a correlation problem, not an LLM problem.** The agent is only as
   good as how *correlatable* the telemetry is. If a metric spike cannot be tied
   to specific traces, and those traces cannot be tied to specific log lines, the
   agent degrades to guessing by time-window overlap. The correlation plumbing
   (Phase 0) is the highest-priority work.

2. **A logs backend is required and not yet in the stack.** Prometheus/Jaeger/OTel/
   Grafana cover metrics, traces, collection, and visualization — but nothing
   stores queryable logs. **Loki** is the chosen backend (first-class Grafana
   datasource, queried with LogQL, covered by the Grafana MCP server).

3. **Jaeger sits slightly outside the easy path.** The Grafana MCP server's
   trace tooling (including its "Sift" slow-request detector) targets **Tempo**,
   not Jaeger. Jaeger works as a Grafana datasource for queries, but is not a
   first-class citizen of the MCP trace tools. Decision for now: **keep Jaeger and
   write one custom Spring AI tool wrapping the Jaeger query API.** Migrating
   traces to Tempo later would let everything flow through the one MCP server.

4. **Do not make the model do statistics.** PromQL and the Grafana MCP server's
   Sift investigations detect anomalies, error spikes, and slow requests
   deterministically. The LLM's job is to sequence the investigation, correlate
   across signals, and write a human-readable hypothesis — not to compute rates,
   percentiles, or thresholds itself.

5. **Use a guided triage playbook, not free-form ReAct.** Triage has a natural
   investigative order; a structured flow is more reproducible and easier to debug.

---

## Architecture (data flow)

```
Java services (OTel-instrumented)
        │ emit
        ▼
┌─────────────┐  ┌──────────────┐  ┌──────────────────┐
│ Prometheus  │  │   Jaeger     │  │      Loki        │
│ metrics +   │  │   traces     │  │ logs carry       │
│ exemplars   │  │              │  │ trace_id         │
└─────────────┘  └──────────────┘  └──────────────────┘
        └────── correlated by trace_id + exemplars ──────┘
        │ queried by (read-only)
        ▼
   Grafana MCP server  +  custom Jaeger tool
        │ exposed as MCP tools
        ▼
   Triage agent  (Spring AI · MCP client)
        │ produces
        ▼
   Root-cause report  (evidence + Grafana deep links)
```

The correlation backbone is the join key across all three pillars: a metric
exemplar carries a `trace_id`; that `trace_id` identifies a trace; and log lines
emitted during that request also carry the same `trace_id`.

---

## Design principles & hard constraints (apply to all phases)

- [ ] **Read-only by default.** Run the Grafana MCP server with `--disable-write`.
      The agent must never restart, scale, roll back, or change configuration.
- [ ] **Any write is a side effect and must be gated behind explicit human
      approval** (e.g. posting to an incident timeline, creating an incident).
      Do not let the agent perform these autonomously.
- [ ] **Ground every query.** Before generating PromQL/LogQL, fetch the available
      metric names, label names/values (Prometheus metadata + label endpoints) and
      the service list, and pass them to the model so it cannot invent identifiers.
- [ ] **Scope every query.** Always constrain by service + time window. Never run
      unbounded queries that pull large result sets into context.
- [ ] **Deterministic detection, narrative correlation.** Statistics/thresholds
      come from PromQL and Sift; the model only sequences, correlates, and explains.
- [ ] **Instrument the agent itself with OTel** so its own tool calls and model
      calls are traced (needed to debug wrong conclusions and measure latency).
- [ ] **Output is a report, not an action.** The deliverable is text + deep links.

---

## Phase 0 — Correlation backbone ✅ COMPLETED (refactored to banking domain)

Nothing downstream works without this. Implemented in the Java services, not the agent.

### Implementation
Two Spring Boot services (`caller-service` and `downstream-service`) form a **banking CQRS/Event Sourcing** demo with **Axon Framework**, **Kafka**, and **PostgreSQL**, fully preserving the original observability correlation backbone.

**caller-service** — Banking Transfer API + Kafka Consumer
- `TransferEntity` (JPA) with `TransferStatus` enum (`PENDING`/`COMPLETED`/`FAILED`)
- `TransferRepository` (Spring Data JPA)
- `TransferController` — `POST /transfers`, `GET /transfers/{id}`
- `TransferEventListener` — `@KafkaListener` on `bank.events`, deserializes `TransferCompletedEvent`, updates read model
- `PrometheusExemplarConfig` (`OtelSpanContext` bean), JSON logback, `RequestLoggingFilter`, OTel agent

**downstream-service** — Axon Account Aggregate + Kafka Publisher
- `AccountAggregate` with `@Aggregate`, `@CommandHandler`, `@EventSourcingHandler`
- `CreateAccountCommand`, `TransferFundsCommand` (with `@TargetAggregateIdentifier`)
- `AccountCreatedEvent`, `FundsTransferredEvent`, `TransferCompletedEvent`
- `AccountController` — `POST /accounts`, `POST /accounts/{id}/transfer`
- `AxonSerializerConfig` — `JacksonSerializer` overrides default XStream (produces JSON in Kafka)
- Axon Kafka publisher enabled; Axon Server disabled (JPA event store in PostgreSQL)
- `PrometheusExemplarConfig`, JSON logback, OTel agent

**Infrastructure:**
- `docker-compose.yml` — 9 services: `jaeger`, `prometheus`, `loki`, `grafana`, `promtail`, `postgres`, `kafka`, `caller-service`, `downstream-service`
- `prometheus.yml` — Scrapes both apps at `/actuator/prometheus`; exemplar storage enabled
- `loki.yml` / `promtail.yml` — Tails `/var/log/app/*.log`, parses JSON, promotes `trace_id`, `span_id`, `level`
- `application.yml` — PostgreSQL datasource, JPA `ddl-auto: update`, Kafka bootstrap, Axon config

### Banking E2E Flow
```
POST /transfers (caller-service)
  ↓  create PENDING transfer in PostgreSQL
  ↓  HTTP POST /accounts/{id}/transfer (downstream-service)
       ↓  Axon CommandGateway → AccountAggregate.handle(TransferFundsCommand)
       ↓  Axon event store (PostgreSQL) + Kafka publish TransferCompletedEvent
Kafka ← ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
  ↓
Spring Kafka @KafkaListener (caller-service)
  ↓  UPDATE transfer SET status = COMPLETED
GET /transfers/{id} → COMPLETED
```

### Acceptance (verified)
- [x] **Build:** Both services compile and produce runnable JARs (`./gradlew bootJar` passes).
- [x] **E2E transfer flow:** `POST /transfers` returns 202 with `transferId` and `status=PENDING`. `GET /transfers/{id}` eventually returns `COMPLETED` (Kafka async event consumed within ~3s).
- [x] **Axon event sourcing:** `downstream-service` stores events in PostgreSQL `domain_event_entry` and publishes JSON events to Kafka.
- [x] **OTel traces:** Jaeger UI shows cross-service traces spanning `POST /transfers` → `AccountAggregate.handle` → `bank.events publish` → `bank.events process`.
- [x] **Log correlation:** Logback JSON output includes `trace_id`/`span_id` from OTel MDC; Promtail ships them to Loki with labels.
- [x] **Prometheus exemplars:** `http_server_requests_seconds_bucket` carries `# {trace_id="…"}` exemplars (OpenMetrics format required).
- [x] **End-to-end round-trip:** A single `trace_id` from a transfer request is queryable in Jaeger, referenced by Prometheus exemplars, and present in Loki log lines from both services.

### Key bugs fixed during integration
1. `TransferFundsCommand` missing `@TargetAggregateIdentifier` → command routing failed.
2. Axon default XStream produced XML events in Kafka → caller Jackson parser failed → fixed with `JacksonSerializer` bean override.
3. `transferId` mismatch: downstream generated its own UUID → fixed `AccountController` to propagate caller's `transferId`.
4. Prometheus histogram buckets not enabled → exemplars missing → added `management.metrics.distribution.percentiles-histogram.http.server.requests: true`.…"}` exemplars (visible in OpenMetrics format and `/api/v1/query_exemplars`).
- [x] **End-to-end round-trip:** A single `trace_id` from a request is queryable in Jaeger, referenced by Prometheus exemplars, and present in Loki log lines from both services.

### Original checklist
- [x] Add the OTel Java agent (auto-instrumentation) to each service.
- [x] Configure OTel export of traces to Jaeger and metrics to Prometheus.
- [x] Inject `trace_id` and `span_id` into logs via logback MDC so every log line
      emitted inside a request carries trace context.
- [x] Ship logs to Loki (with the trace context fields preserved/queryable).
- [x] Enable **Prometheus exemplars**: attach `trace_id` to relevant samples
      (e.g. latency histogram buckets) so a metric spike points to specific traces.
- [x] Verify the end-to-end join manually: pick a slow request, confirm you can go
      metric exemplar → `trace_id` → trace in Jaeger → log lines in Loki with the
      same `trace_id`. **Do not proceed until this round-trip works.**

## Phase 1 — Read-only query layer ✅ COMPLETED

- [x] Stand up Loki and register it as a Grafana datasource. *(Already provisioned in Grafana `datasources.yml` from Phase 0.)*
- [x] Register Prometheus and Jaeger as Grafana datasources. *(Already provisioned in Grafana `datasources.yml` from Phase 0.)*
- [x] Deploy the Grafana MCP server (`mcp-grafana`) with `--disable-write`.
      Uses Grafana admin credentials (`admin/admin`) via env vars; read-only enforced by `--disable-write` flag.
- [x] Confirm the MCP server exposes: PromQL query, LogQL query, dashboard search,
      alert-rule status, and deep-link generation.
      **Note:** Sift write tools (`find_error_pattern_logs`, `find_slow_requests`) are disabled
      by `--disable-write` per read-only design constraint. Read-only Sift tools still verified.
- [x] Build a **custom Jaeger trace tool** (Spring AI MCP server) wrapping the
      Jaeger query API — `get_trace_by_id` and `search_traces`.
      **Note:** Spring AI 1.1.4 does **not** auto-scan `@Tool` annotations for MCP server tools.
      A manual `MethodToolCallbackProvider` bean is required to register tools with the MCP server.

### Phase 1 artifacts

- `docker-compose.yml` — added `mcp-grafana` (port 8000) and `jaeger-mcp-service` (port 8083)
- `jaeger-mcp-service/` — new Gradle submodule
- `scripts/provision-grafana-sa.sh` — service account provisioning utility
- `scripts/verify-mcp-tools.sh` — health + SSE + tool registration checks
- `scripts/smoke-test.sh` — end-to-end PromQL/LogQL/trace verification

## Phase 2 — Agent skeleton ✅ COMPLETED

### Implementation

The **triage-agent** is a new Spring Boot service acting as an MCP client that orchestrates investigation across the observability stack.

**New Gradle module:** `triage-agent/`

**Dependencies:**
- `spring-ai-starter-mcp-client` — MCP client transport (SSE)
- `spring-ai-starter-model-openai` — OpenAI GPT-4o model (switched from initial Anthropic Claude plan per human direction)

**Configuration** (`triage-agent/src/main/resources/application.yml`):
- SSE connection to Grafana MCP: `http://mcp-grafana:8000`
- SSE connection to Jaeger MCP: `http://jaeger-mcp-service:8080`
- `spring.ai.mcp.client.toolcallback.enabled: true`
- `OPENAI_API_KEY` environment variable required

**Core classes:**
- `TriageService` — Orchestrates the investigation; uses a grounding system prompt that pre-populates available discovery tools (`list_prometheus_metric_names`, `list_prometheus_label_names`, `list_prometheus_label_values`, `list_datasources`) so the model cannot hallucinate metric/datasource names.
- `TriageController` — Exposes `POST /api/triage/investigate` REST endpoint.

**Known workaround:** `OpenAiExtraBodyWorkaround` strips `extra_body` from outgoing OpenAI JSON requests due to [spring-ai#5196](https://github.com/spring-projects/spring-ai/issues/5196), required for Spring AI 1.1.4.

**Docker:** `triage-agent/Dockerfile` (eclipse-temurin:17-jre), added to `docker-compose.yml` (port `8084:8080`, depends on `mcp-grafana` and `jaeger-mcp-service`).

**Tests:**
- `TriageAgentApplicationTest` — Verifies Spring context loads successfully
- `TriageControllerTest` — WebMvcTest verifying `/api/triage/investigate` endpoint

**Smoke test:** `scripts/triage-agent-smoke-test.sh` — Posts a sample investigation request and validates response structure.

### Checklist
- [x] Create a Spring Boot service with Spring AI configured as an **MCP client**
      pointed at the Grafana MCP server (and registering the custom Jaeger tool).
- [x] Wire the OpenAI GPT-4o model through Spring AI (via `spring-ai-starter-model-openai`; switched from initial Anthropic Claude plan per human direction).
- [x] Implement **grounding tools/context**: list available metric names, label
      names/values, and the service inventory; supply them to the model before
      query generation.
- [x] Add a smoke test: ask the agent a trivial scoped question ("error rate for
      service X in the last 15 min") and confirm it produces a valid PromQL call
      and a correct answer.

### Verification (executed)
1. **Build:** `./gradlew :triage-agent:bootJar` produces a runnable JAR.
2. **Docker:** `docker compose up -d --build triage-agent` starts the container on port `8084`.
3. **Health:** `curl http://localhost:8084/actuator/health` returns `200`.
4. **MCP connections:** Container logs show successful SSE initialization to both Grafana MCP and Jaeger MCP servers.
5. **Smoke test:** `bash scripts/triage-agent-smoke-test.sh` passes (HTTP 200 from `/api/triage/investigate` with non-empty response body).
6. **Unit tests:** `./gradlew :triage-agent:test` passes (`TriageAgentApplicationTest`, `TriageControllerTest`).

### Known issues
- **Spring AI 1.1.4 `extra_body` bug:** OpenAI rejects requests with an empty `extra_body` field. Worked around via `OpenAiExtraBodyWorkaround` (`RestClientCustomizer` that strips `extra_body` from outgoing JSON). Remove after upgrading to Spring AI ≥1.1.6 (see [spring-projects/spring-ai#5196](https://github.com/spring-projects/spring-ai/issues/5196)).

## Phase 3 — Triage playbook (the investigation loop) ✅ COMPLETED

Implement as a guided, ordered flow (state machine or structured prompt), not open ReAct.

- [x] **Scope:** from the alert/symptom, identify the affected service and time window.
- [x] **Metrics:** run scoped PromQL for that service/window; pull exemplar `trace_id`s
      off the anomalous samples. Lean on Sift to detect error spikes / slow requests.
- [x] **Traces:** fetch those exact traces via the Jaeger tool; identify the failing
      span or slow dependency.
- [x] **Logs:** pull Loki lines filtered by the same `trace_id`(s) around the error.
- [x] **Hypothesize:** rank likely root causes, each tied to specific evidence.
- [x] Make each step's inputs/outputs explicit so a run is reproducible and auditable.

## Phase 4 — Structured output ✅ COMPLETED

- [x] Produce a structured root-cause report: what happened, where (service/span),
      when (window), most-likely cause(s) with confidence, and the evidence chain.
- [x] Include **Grafana deep links** (generated via the MCP server) to the relevant
      dashboards/panels/Explore queries so a human can verify in one click.
- [x] Explicitly state what the agent did **not** check / could not determine.

### Async Investigation (Kafka)

Investigations can be triggered asynchronously via Kafka, decoupling the HTTP request from the LLM pipeline.

- `InvestigationJob` — JPA entity (`id`, `question`, `status`, `reportJson`, `createdAt`, `completedAt`).
- `InvestigationJobRepository` — Spring Data JPA repository.
- `InvestigationKafkaProducer` — `sendInvestigationRequest()` serializes and publishes to `triage.requests`.
- `InvestigationKafkaConsumer` — `@KafkaListener` that sets status to `RUNNING`, executes `TriageOrchestratorService`, then `COMPLETED`/`FAILED`.
- `TriageController` — `POST /api/triage/investigations` and `GET /api/triage/investigations/{id}`.
- Unit tests: `InvestigationKafkaProducerTest` (6 tests) and `InvestigationKafkaConsumerTest` (10 tests).

## Phase 5 — Guardrails & self-observability ⏳ NOT STARTED

- [ ] Confirm `--disable-write` is enforced and the agent has no action tools.
- [ ] Gate any incident-timeline/incident-creation write behind explicit human
      approval (these notify people — never automatic).
- [ ] Instrument the agent with OTel: trace each tool call and model call; record
      triage latency and tool error rates.
- [ ] Add evaluation cases: known past incidents with known root causes, to measure
      whether the agent's hypothesis matches reality.

---

## Open decisions for the human (not for the coding agent to choose)

- **Jaeger vs Tempo for traces.** Staying on Jaeger (custom tool) for now; revisit
  migrating to Tempo to unify everything under the Grafana MCP server.
- ~~**Model selection** (which Claude model)~~ — Resolved: OpenAI GPT-4o selected (per human direction). Cost/latency budget still open.
- **Trigger mechanism:** does the agent run on alert webhook (Alertmanager/Grafana),
  on a chat command, or both?
- **Deployment target** for the agent and the MCP server (e.g. Kubernetes namespace).

---

## Out of scope (explicitly)

- Automated remediation or any state-changing action.
- Personalized/financial/business decisions.
- Writing alert rules or dashboards autonomously (read + deep-link only for now).
