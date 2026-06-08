#!/bin/bash
set -euo pipefail

echo "==> Verifying MCP servers expose expected tools..."

# Check Grafana MCP health
echo "==> Checking Grafana MCP health..."
GRAFANA_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/healthz)
if [ "$GRAFANA_HEALTH" != "200" ]; then
    echo "ERROR: Grafana MCP health returned $GRAFANA_HEALTH" >&2
    exit 1
fi
echo "    Grafana MCP health: OK (200)"

# Check Jaeger MCP actuator health
echo "==> Checking Jaeger MCP actuator health..."
JAEGER_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/actuator/health)
if [ "$JAEGER_HEALTH" != "200" ]; then
    echo "ERROR: Jaeger MCP actuator health returned $JAEGER_HEALTH" >&2
    exit 1
fi
echo "    Jaeger MCP actuator health: OK (200)"

# Check Jaeger MCP SSE endpoint responds (SSE is long-lived; check for event data in response)
echo "==> Checking Jaeger MCP SSE endpoint..."
JAEGER_SSE_RESPONSE=$(curl -s --max-time 5 http://localhost:8083/sse || true)
if ! echo "$JAEGER_SSE_RESPONSE" | grep -q "event:"; then
    echo "ERROR: Jaeger MCP SSE did not return expected event stream" >&2
    exit 1
fi
echo "    Jaeger MCP SSE: OK (event stream received)"

# Check Jaeger MCP container logs show "Registered tools: 2"
echo "==> Checking Jaeger MCP registered tools..."
TOOL_COUNT=$(docker logs observability_agent-jaeger-mcp-service-1 2>/dev/null | grep -c "Registered tools: 2" || true)
if [ "$TOOL_COUNT" -eq 0 ]; then
    echo "ERROR: Jaeger MCP did not register expected tools" >&2
    exit 1
fi
echo "    Jaeger MCP registered tools: OK (2 tools found)"

echo "==> All MCP server verifications passed!"
