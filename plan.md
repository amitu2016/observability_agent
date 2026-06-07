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
- **Model:** an Anthropic Claude model via Spring AI (final model choice is an open decision).

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

## Phase 0 — Correlation backbone ✅ COMPLETED

Nothing downstream works without this. Implemented in the Java services, not the agent.

### Implementation
Two sample Spring Boot services (`caller-service` and `downstream-service`) were created as a Gradle (Groovy DSL) multi-module project. They run together with a local Docker Compose stack (Jaeger, Prometheus, Loki, Grafana, Promtail) and demonstrate full metric→trace→log correlation via `trace_id`.

**Key files created:**
- `settings.gradle`, `build.gradle` (root)
- `caller-service/` — Spring Boot app with `RestTemplate` calling downstream, `PrometheusExemplarConfig` (`OtelSpanContext` bean), JSON logback via `logstash-logback-encoder`, `RequestLoggingFilter`, `Dockerfile`
- `downstream-service/` — Same instrumentation patterns, simple `GET /hello` endpoint, returns `X-Trace-Id` header
- `docker-compose.yml` — 7 services: `jaeger`, `prometheus`, `loki`, `grafana`, `promtail`, `caller-service`, `downstream-service`
- `prometheus.yml` — Scrapes both apps at `/actuator/prometheus`; exemplar storage enabled (`--enable-feature=exemplar-storage`)
- `loki.yml` / `promtail.yml` — File-based storage; Promtail tails `/var/log/app/*.log`, parses JSON, promotes `trace_id`, `span_id`, `level` to Loki labels
- `README.md` — Run & verify instructions

### Acceptance (verified)
- [x] **Build:** Both services compile and produce runnable JARs (`./gradlew bootJar` passes).
- [x] **OTel traces:** Jaeger UI shows cross-service traces after `curl localhost:8081/hello`.
- [x] **Log correlation:** Logback JSON output includes `trace_id`/`span_id` from OTel MDC; Promtail successfully ships them to Loki with labels.
- [x] **Prometheus exemplars:** `http_server_requests_seconds_count` carries `# {trace_id="…"}` exemplars (visible in OpenMetrics format and `/api/v1/query_exemplars`).
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

## Phase 1 — Read-only query layer

- [ ] Stand up Loki and register it as a Grafana datasource.
- [ ] Register Prometheus and Jaeger as Grafana datasources.
- [ ] Deploy the Grafana MCP server (`mcp-grafana`) with `--disable-write`.
      Requires Grafana 9.0+. Authenticate with a scoped, read-only service account
      token (datasource query + dashboard read scopes only).
- [ ] Confirm the MCP server exposes: PromQL query, LogQL query, dashboard search,
      alert-rule status, Sift investigations (error patterns in Loki, slow requests),
      and deep-link generation.
- [ ] Build a **custom Jaeger trace tool** (Spring AI tool / MCP tool) wrapping the
      Jaeger query API — fetch trace by `trace_id`, search traces by service + time
      window + tags. This fills the Jaeger-vs-Tempo gap.

## Phase 2 — Agent skeleton

- [ ] Create a Spring Boot service with Spring AI configured as an **MCP client**
      pointed at the Grafana MCP server (and registering the custom Jaeger tool).
- [ ] Wire the Anthropic Claude model through Spring AI.
- [ ] Implement **grounding tools/context**: list available metric names, label
      names/values, and the service inventory; supply them to the model before
      query generation.
- [ ] Add a smoke test: ask the agent a trivial scoped question ("error rate for
      service X in the last 15 min") and confirm it produces a valid PromQL call
      and a correct answer.

## Phase 3 — Triage playbook (the investigation loop)

Implement as a guided, ordered flow (state machine or structured prompt), not open ReAct.

- [ ] **Scope:** from the alert/symptom, identify the affected service and time window.
- [ ] **Metrics:** run scoped PromQL for that service/window; pull exemplar `trace_id`s
      off the anomalous samples. Lean on Sift to detect error spikes / slow requests.
- [ ] **Traces:** fetch those exact traces via the Jaeger tool; identify the failing
      span or slow dependency.
- [ ] **Logs:** pull Loki lines filtered by the same `trace_id`(s) around the error.
- [ ] **Hypothesize:** rank likely root causes, each tied to specific evidence.
- [ ] Make each step's inputs/outputs explicit so a run is reproducible and auditable.

## Phase 4 — Output

- [ ] Produce a structured root-cause report: what happened, where (service/span),
      when (window), most-likely cause(s) with confidence, and the evidence chain.
- [ ] Include **Grafana deep links** (generated via the MCP server) to the relevant
      dashboards/panels/Explore queries so a human can verify in one click.
- [ ] Explicitly state what the agent did **not** check / could not determine.

## Phase 5 — Guardrails & self-observability

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
- **Model selection** (which Claude model) and cost/latency budget per triage run.
- **Trigger mechanism:** does the agent run on alert webhook (Alertmanager/Grafana),
  on a chat command, or both?
- **Deployment target** for the agent and the MCP server (e.g. Kubernetes namespace).

---

## Out of scope (explicitly)

- Automated remediation or any state-changing action.
- Personalized/financial/business decisions.
- Writing alert rules or dashboards autonomously (read + deep-link only for now).
