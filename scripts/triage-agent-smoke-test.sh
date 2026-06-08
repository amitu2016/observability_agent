#!/bin/bash
set -euo pipefail

TRIAGE_AGENT_URL="http://localhost:8084"
MAX_WAIT=120
WAITED=0

echo "==> Triage Agent Smoke Test"

# Wait for triage-agent to be healthy
echo "==> Waiting for triage-agent health..."
until curl -s -o /dev/null -w "%{http_code}" "$TRIAGE_AGENT_URL/actuator/health" | grep -q "200"; do
    if [ $WAITED -ge $MAX_WAIT ]; then
        echo "ERROR: triage-agent did not become healthy within ${MAX_WAIT}s" >&2
        exit 1
    fi
    echo "    Waiting for triage-agent... (${WAITED}s/${MAX_WAIT}s)"
    sleep 5
    WAITED=$((WAITED + 5))
done
echo "    triage-agent health: OK (200)"

# Send a sample triage question
echo "==> Sending sample triage question..."
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"question":"What is the error rate for caller-service in the last 15 minutes?"}' \
    "$TRIAGE_AGENT_URL/api/triage/investigate")

HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
    echo "ERROR: triage-agent returned HTTP $HTTP_CODE" >&2
    echo "Body: $BODY" >&2
    exit 1
fi

if [ -z "$BODY" ] || [ "$BODY" = "{}" ]; then
    echo "ERROR: triage-agent returned empty response body" >&2
    exit 1
fi

echo "    triage-agent responded with HTTP 200 and non-empty body"
echo "==> Smoke test passed"
