# OpenTelemetry Java Agent: Axon Framework 4.x & Spring Kafka Compatibility Research

**Date:** 2026-06-07  
**Sources:** OpenTelemetry Java Instrumentation GitHub, AxonFramework official repositories, OTel supported libraries documentation.

---

## 1. Axon Framework 4.x – Does the OTel Java agent trace it?

### Finding: NOT automatically
- **Axon Framework is NOT listed** in the [OpenTelemetry Java Agent Supported Libraries](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md).
- No GitHub issues or PRs exist in `opentelemetry-java-instrumentation` adding Axon-specific instrumentation.
- **The OTel Java agent does NOT auto-instrument** Axon command handlers, event handlers, sagas, or the command/query/event buses.

### What DOES work – with manual setup
AxonIQ provides an **official OpenTelemetry integration sample** at `AxonFramework/opentelemetry-samples` that demonstrates end-to-end tracing with the OTel Java agent.

Key dependencies from the official sample:
```xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-tracing-opentelemetry</artifactId>
    <version>4.7.2-SNAPSHOT</version>
</dependency>
```

The official sample README states:
> "There is no Java configuration required because the OpenTelemetry auto-instrumentation does everything out of the box."

**Important context:** This "out of the box" claim assumes you have:
1. The `axon-tracing-opentelemetry` extension on the classpath.
2. The OTel Java agent attached.
3. Axon Framework **4.6+** (the sample targets 4.7.x).

### Axon internal messaging tracing
Axon’s command bus, query bus, and event processor tracing is handled by **Axon’s own `tracing-opentelemetry` module**, not by the OTel agent itself. The module installs `MessageDispatchInterceptor` and `MessageHandlerInterceptor` implementations that bridge Axon’s `UnitOfWork` and messaging metadata to OpenTelemetry spans and context.

Without this extension, Axon internal messages are invisible to the OTel agent. Standard OTel agent instrumentation (e.g., Servlet/WebFlux, JPA, Quartz) will still produce spans around the *edges* of Axon handlers (HTTP entry points, scheduled jobs), but the Axon message flows themselves will be untraced black boxes.

### Spring Boot 3.4 compatibility
- The official Axon OTel sample uses **Spring Boot 3.0.2** with Axon 4.7.x successfully.
- There is no known incompatibility between Spring Boot 3.4 and the OTel Java agent. The agent instruments at the bytecode level and is generally Spring version-agnostic for supported frameworks.
- **Caveat:** Axon Framework extensions (including tracing) sometimes lag behind Spring Boot releases. Verify that `axon-tracing-opentelemetry` has a release compatible with your specific Axon 4.x patch version and Spring Boot 3.4 before upgrading.

---

## 2. Spring Kafka (spring-kafka) – Does the OTel Java agent trace it?

### Finding: YES — auto-instrumented by the agent
- **Spring Kafka 2.7+** is explicitly listed as a supported library in the OTel Java agent.
- [Supported Libraries doc reference](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md)
- Javaagent instrumentation module: `instrumentation/spring/spring-kafka-2.7/javaagent`

### What the agent instruments automatically
The javaagent injects telemetry by intercepting:
1. **`AbstractMessageListenerContainer.getRecordInterceptor()`** — wraps the `RecordInterceptor` with an OTel instrumented version.
2. **`KafkaMessageListenerContainer$ListenerConsumer.invokeBatchOnMessageWithRecordsOrList()`** — creates batch `CONSUMER` spans.
3. The agent also suppresses duplicate consumer spans that would otherwise be created by the lower-level `kafka-clients` instrumentation when Spring Kafka wraps the consumer.

### Producer tracing
The lower-level **Apache Kafka Producer API** is also auto-instrumented by the agent (`kafka-clients-2.6` module). When Spring Kafka uses `KafkaTemplate` or `KafkaProducer`, the agent automatically:
- Creates `PRODUCER` spans.
- Injects trace context into Kafka record headers (`traceparent`).

### Trace context propagation through Kafka messages
**Yes, propagation works automatically.** The OTel Kafka instrumentation injects W3C `traceparent` headers into produced records and extracts them on consumption. When `spring-kafka` is used, the Spring Kafka javaagent instrumentation ensures that extracted context becomes the parent of the consumer span.

### Configuration options (all optional)
- `otel.instrumentation.messaging.experimental.receive-telemetry.enabled` — default `false`. Enables receive telemetry (creates a new trace on consume with span link to producer).
- `otel.instrumentation.messaging.experimental.capture-headers` — comma-separated list of Kafka headers to capture as span attributes.
- `otel.instrumentation.kafka.experimental-span-attributes` — enables `kafka.record.queue_time_ms` and `messaging.kafka.bootstrap.servers` attributes.

### Spring Boot 3.4 / Spring Kafka 3.x note
The agent targets `spring-kafka-2.7` as a minimum version. Spring Kafka 3.x is the version shipped with Spring Boot 3.x. Based on ByteBuddy matcher logic in the source (`named("org.springframework.kafka.listener.AbstractMessageListenerContainer")`), the instrumentation relies on class names that have remained stable across Spring Kafka 2.7 → 3.x. **No manual action should be required** for Spring Boot 3.4, but this has not been explicitly confirmed in the latest agent release notes.

---

## 3. End-to-end trace propagation: HTTP → caller-service → Kafka → downstream-service → response

### Required components
| Step | Instrumentation | Who provides it |
|------|----------------|-----------------|
| HTTP request ingress | Servlet / WebFlux server span | OTel Java agent (auto) |
| Caller-service business logic | Your code / Axon handlers | OTel agent + Axon tracing extension |
| Kafka Producer send | Kafka PRODUCER span + header injection | OTel Java agent (auto) |
| Kafka message transit | — | No span (message queue gap) |
| Kafka Consumer receive | Kafka CONSUMER span + header extraction | OTel Java agent (auto) |
| Downstream-service processing | Your code / Axon handlers | OTel agent + Axon tracing extension |
| HTTP response egress | HTTP CLIENT span (if callee calls another service) | OTel Java agent (auto) |

### Does this appear as ONE distributed trace in Jaeger?
**By default:** Yes, it appears as a single trace with connected spans (HTTP server → Kafka producer → Kafka consumer → HTTP client, etc.).

**Exception:** If you enable `otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true`, the Kafka consumer will start a **new trace** and only create a **span link** back to the producer trace. In that case, Jaeger will show two separate traces connected by a link, not one unified trace.
- **Recommendation:** Leave this setting at its default `false` if your goal is a single unified trace.

### Code changes needed for propagation
**None.** As long as the OTel Java agent is attached to **both** services and you are using instrumented HTTP clients / Spring Kafka / Kafka clients, trace context propagates automatically via:
- HTTP: W3C `traceparent` header.
- Kafka: W3C `traceparent` record header.

No manual `Tracer` or `Context` propagation code is required for the HTTP → Kafka → service chain.

---

## 4. Axon internal messaging (command bus, query bus) – traced automatically?

### Finding: NO — requires Axon tracing extension
The OTel Java agent does **not** instrument Axon’s:
- `CommandBus` / `CommandGateway`
- `QueryBus` / `QueryGateway`
- `EventBus` / `EventStore`
- `Saga` lifecycle
- `EventProcessor` / `TrackingEventProcessor`

Axon’s messaging is abstracted behind its own `Message` API and does not use standard HTTP, JMS, or Kafka APIs directly (even when Axon Server or Axon Connector is the transport). Therefore, the agent has no hooks to intercept.

### How to enable tracing
Add the Axon OpenTelemetry tracing extension (artifact coordinates may vary by exact Axon version):
```xml
<dependency>
    <groupId>org.axonframework</groupId>
    <artifactId>axon-tracing-opentelemetry</artifactId>
    <version>${axon.version}</version>
</dependency>
```

This extension:
- Implements `MessageDispatchInterceptor` and `MessageHandlerInterceptor`.
- Creates spans for command dispatch, command handling, event publication, event handling, query dispatch, and query handling.
- Propagates OpenTelemetry context through Axon’s `MetaData` map.
- Works with both Axon Server and non-Axon Server (e.g., embedded/JPA event store) configurations.

### Manual alternative if the extension is unavailable
If the official extension is not released for your Axon version, you can write custom interceptors:
1. Implement `CommandDispatchInterceptor`, `EventDispatchInterceptor`, `QueryDispatchInterceptor`.
2. In each, capture `Context.current()`, start a span, and attach the span context to `MetaData`.
3. In corresponding handler interceptors, extract the context from `MetaData` and make it current.

---

## Concrete Summary Table

| Capability | Out-of-the-box with OTel Agent? | What is needed |
|-----------|----------------------------------|----------------|
| HTTP server request tracing | **Yes** | Only the agent |
| HTTP client request tracing | **Yes** | Only the agent (RestTemplate, WebClient, Feign, etc.) |
| Spring Kafka producer tracing | **Yes** | Only the agent |
| Spring Kafka consumer tracing (single record) | **Yes** | Only the agent |
| Spring Kafka consumer tracing (batch) | **Yes** | Only the agent |
| Trace propagation through Kafka | **Yes** | Only the agent (W3C traceparent headers) |
| Axon command dispatch / handling span | **No** | `axon-tracing-opentelemetry` extension |
| Axon event publish / handling span | **No** | `axon-tracing-opentelemetry` extension |
| Axon query dispatch / handling span | **No** | `axon-tracing-opentelemetry` extension |
| Axon saga tracing | **No** | `axon-tracing-opentelemetry` extension |
| Axon Server connector tracing | **No** | `axon-tracing-opentelemetry` extension |
| Unified trace HTTP → Kafka → service | **Yes** | Agent only (set receive-telemetry to `false` which is default) |

---

## Version Compatibility Notes

| Component | Compatible Version | Notes |
|-----------|-------------------|-------|
| OpenTelemetry Java Agent | 1.23+ (tested in Axon sample), latest recommended | Generally backward compatible |
| Axon Framework | 4.6+ for `axon-tracing-opentelemetry` | Check Maven Central for exact artifact availability |
| Spring Boot | 3.0+ confirmed; 3.4 presumed compatible | Agent is Spring version agnostic for supported libs |
| Spring Kafka | 2.7+ (agent module name); 3.x presumed compatible | ByteBuddy matchers target stable class names |
| Apache Kafka clients | 2.6+ | Auto-instrumented independently of Spring layer |

---

## Key References

1. [OpenTelemetry Java Instrumentation – Supported Libraries](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md)
2. [OTel Java Agent – Spring Kafka 2.7 Instrumentation Source](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/spring/spring-kafka-2.7/javaagent/src/main/java/io/opentelemetry/javaagent/instrumentation/spring/kafka/v2_7)
3. [Axon Framework – Official OpenTelemetry Samples](https://github.com/AxonFramework/opentelemetry-samples) – demonstrates agent + `axon-tracing-opentelemetry` working together
4. [Axon Framework – Tracing Extension (OpenTracing)](https://github.com/AxonFramework/extension-tracing) – older OpenTracing-based extension
5. [OTel Spring Kafka Library Instrumentation README](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/spring/spring-kafka-2.7/library) – manual setup if not using the agent
