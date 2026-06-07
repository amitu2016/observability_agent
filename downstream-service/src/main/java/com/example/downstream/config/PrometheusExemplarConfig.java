package com.example.downstream.config;

import io.opentelemetry.api.trace.Span;
import io.prometheus.metrics.tracer.common.SpanContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PrometheusExemplarConfig {

    @Bean
    public SpanContext spanContext() {
        return new OtelSpanContext();
    }

    static class OtelSpanContext implements SpanContext {

        @Override
        public String getCurrentTraceId() {
            Span span = Span.current();
            if (span == null) {
                return null;
            }
            var ctx = span.getSpanContext();
            return ctx.isValid() ? ctx.getTraceId() : null;
        }

        @Override
        public String getCurrentSpanId() {
            Span span = Span.current();
            if (span == null) {
                return null;
            }
            var ctx = span.getSpanContext();
            return ctx.isValid() ? ctx.getSpanId() : null;
        }

        @Override
        public boolean isCurrentSpanSampled() {
            Span span = Span.current();
            if (span == null) {
                return false;
            }
            var ctx = span.getSpanContext();
            return ctx.isValid() && ctx.isSampled();
        }

        @Override
        public void markCurrentSpanAsExemplar() {
            // no-op
        }
    }
}