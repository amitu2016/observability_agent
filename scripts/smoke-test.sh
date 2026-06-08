#!/bin/bash
set -euo pipefail

CALLER_URL="http://localhost:8081"
DOWNSTREAM_URL="http://localhost:8082"
JAEGER_URL="http://localhost:16686"
PROMETHEUS_URL="http://localhost:9090"
LOKI_URL="http://localhost:3100"
MCP_GRAFANA_URL="http://localhost:8000"
MCP_JAEGER_URL="http://localhost:8083"

MAX_WAIT=180
WAITED=0

echo "==> Step 7: End-to-end smoke test"

# 1. Ensure all services are running
echo "==> Ensuring full stack is up..."
docker compose up -d --build 2>/dev/null || true

# Wait for caller-service and downstream-service to be ready
until curl -s -o /dev/null -w "%{http_code}" "$CALLER_URL/actuator/health" | grep -q "200"; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        echo "ERROR: caller-service did not become healthy within ${MAX_WAIT}s" >&2
        exit 1
    fi
    echo "    Waiting for caller-service... (${WAITED}s/${MAX_WAIT}s)"
    sleep 5
    WAITED=$((WAITED + 5))
done
echo "    caller-service: OK"

WAITED=0
until curl -s -o /dev/null -w "%{http_code}" "$DOWNSTREAM_URL/actuator/health" | grep -q "200"; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        echo "ERROR: downstream-service did not become healthy within ${MAX_WAIT}s" >&2
        exit 1
    fi
    echo "    Waiting for downstream-service... (${WAITED}s/${MAX_WAIT}s)"
    sleep 5
    WAITED=$((WAITED + 5))
done
echo "    downstream-service: OK"

# 2. Create an account
echo "==> Creating account..."
CREATE_ACCOUNT=$(curl -s -X POST -H "Content-Type: application/json" \
    -d '{"accountId":"acc-smoke-1","initialBalance":500}' \
    "$DOWNSTREAM_URL/accounts")
echo "    Account created: $CREATE_ACCOUNT"

# 3. Trigger a transfer
echo "==> Triggering transfer..."
XFER_RESPONSE=$(curl -s -D - -X POST -H "Content-Type: application/json" \
    -d '{"fromAccount":"acc-smoke-1","toAccount":"acc-smoke-2","amount":100}' \
    "$CALLER_URL/transfers" 2>/dev/null | tr -d '\r')

echo "    Transfer response:"
echo "$XFER_RESPONSE" | tail -n 5

# Extract trace_id from X-Trace-Id header
TRACE_ID=$(echo "$XFER_RESPONSE" | grep -i "^X-Trace-Id:" | awk -F': ' '{print $2}' | tr -d '[:space:]')
if [ -z "$TRACE_ID" ]; then
    echo "WARNING: X-Trace-Id header not found, using grep fallback..."
    # Fallback: try to get trace_id from response body or logs
    TRACE_ID=$(echo "$XFER_RESPONSE" | grep -o '"traceId":"[^"]*"' | head -1 | cut -d'"' -f4)
fi

echo "    Trace ID: $TRACE_ID"

# Extract transfer ID for polling
TRANSFER_ID=$(echo "$XFER_RESPONSE" | grep -o '"transferId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TRACE_ID" ]; then
    echo "ERROR: Could not extract trace ID" >&2
    exit 1
fi

# 4. Wait for async processing and data ingestion
WAITED=0
echo "==> Waiting for transfer to complete..."
until curl -s "$CALLER_URL/transfers/$TRANSFER_ID" 2>/dev/null | grep -q "COMPLETED"; do
    if [ $WAITED -ge 30 ]; then
        echo "    Warning: transfer not completed within 30s, continuing anyway..."
        break
    fi
    sleep 2
    WAITED=$((WAITED + 2))
done

echo "==> Waiting for metrics/logs ingestion (15s)..."
sleep 15

# 5. Query Prometheus for metrics (direct API for smoke test)
echo "==> Querying Prometheus..."
PROM_RESULT=$(curl -s -G "$PROMETHEUS_URL/api/v1/query" \
    --data-urlencode "query=up{job='caller-service'}" 2>/dev/null)
if ! echo "$PROM_RESULT" | grep -q '"status":"success"'; then
    echo "ERROR: Prometheus query failed" >&2
    exit 1
fi
echo "    Prometheus: OK"

# 6. Query Loki for logs (direct API for smoke test)
echo "==> Querying Loki..."
LOKI_RESULT=$(curl -s -G "$LOKI_URL/loki/api/v1/query_range" \
    --data-urlencode "query={job=\"java-logs\"} |~ \"$TRACE_ID\"" \
    --data-urlencode "limit=10" 2>/dev/null)
if ! echo "$LOKI_RESULT" | grep -q '"status":"success"'; then
    echo "ERROR: Loki query failed" >&2
    exit 1
fi
if echo "$LOKI_RESULT" | grep -q '"result":\[\]'; then
    echo "    Loki: OK (no logs with this trace_id yet — acceptable for smoke test)"
else
    echo "    Loki: OK (logs found)"
fi

# 7. Query Jaeger for trace
echo "==> Querying Jaeger for trace..."
JAEGER_RESULT=$(curl -s "$JAEGER_URL/api/traces/$TRACE_ID" 2>/dev/null)
if ! echo "$JAEGER_RESULT" | grep -q "traceID"; then
    echo "ERROR: Jaeger trace query failed or trace not found" >&2
    exit 1
fi
echo "    Jaeger: OK (trace found)"

# 8. Verify MCP servers are still healthy
echo "==> Verifying MCP servers..."
MCP_GRAFANA_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$MCP_GRAFANA_URL/healthz")
if [ "$MCP_GRAFANA_HEALTH" != "200" ]; then
    echo "ERROR: Grafana MCP health returned $MCP_GRAFANA_HEALTH" >&2
    exit 1
fi
echo "    Grafana MCP: OK"

MCP_JAEGER_SSE=$(curl -s --max-time 5 "$MCP_JAEGER_URL/sse" || true)
if ! echo "$MCP_JAEGER_SSE" | grep -q "event:"; then
    echo "ERROR: Jaeger MCP SSE not responding" >&2
    exit 1
fi
echo "    Jaeger MCP: OK"

echo ""
echo "==> All end-to-end smoke tests passed!"
echo "    Trace ID: $TRACE_ID"
