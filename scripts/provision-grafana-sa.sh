#!/bin/bash
#
# provision-grafana-sa.sh
# Provisions a read-only Grafana service account for MCP server consumption.
#

set -euo pipefail

GRAFANA_URL="${GRAFANA_URL:-http://grafana:3000}"
ADMIN_USER="${GRAFANA_ADMIN_USER:-admin}"
ADMIN_PASSWORD="${GRAFANA_ADMIN_PASSWORD:-admin}"
SA_NAME="${SA_NAME:-mcp-server-sa}"
OUTPUT_FILE="${OUTPUT_FILE:-/tmp/grafana-sa-token.json}"

# Polling interval and timeout for health check
HEALTH_CHECK_INTERVAL=5
HEALTH_CHECK_TIMEOUT=120

echo "==> Waiting for Grafana at ${GRAFANA_URL} to be healthy..."

# Wait for Grafana to be healthy
elapsed=0
until curl -sf -u "${ADMIN_USER}:${ADMIN_PASSWORD}" "${GRAFANA_URL}/api/health" > /dev/null 2>&1; do
    if [ $elapsed -ge $HEALTH_CHECK_TIMEOUT ]; then
        echo "ERROR: Grafana did not become healthy within ${HEALTH_CHECK_TIMEOUT} seconds" >&2
        exit 1
    fi
    echo "    Grafana not ready yet (${elapsed}s/${HEALTH_CHECK_TIMEOUT}s), waiting..."
    sleep $HEALTH_CHECK_INTERVAL
    elapsed=$((elapsed + HEALTH_CHECK_INTERVAL))
done

echo "==> Grafana is healthy."

# Create service account
echo "==> Creating service account: ${SA_NAME}"

SA_RESPONSE=$(curl -sf -X POST \
    -H "Content-Type: application/json" \
    -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
    "${GRAFANA_URL}/api/serviceaccounts" \
    -d "{
        \"name\": \"${SA_NAME}\",
        \"role\": \"Viewer\",
        \"isDisabled\": false
    }" 2>&1) || {
    # Check if service account already exists
    EXISTING=$(curl -sf -u "${ADMIN_USER}:${ADMIN_PASSWORD}" "${GRAFANA_URL}/api/serviceaccounts?perpage=100" 2>/dev/null)
    if echo "$EXISTING" | grep -q "\"name\":\"${SA_NAME}\""; then
        echo "    Service account '${SA_NAME}' already exists."
        SA_ID=$(echo "$EXISTING" | grep -o "\"id\":[0-9]*" | head -1 | cut -d':' -f2)
    else
        echo "ERROR: Failed to create service account: ${SA_RESPONSE}" >&2
        exit 1
    fi
}

# Extract service account ID
if [ -z "${SA_ID:-}" ]; then
    SA_ID=$(echo "$SA_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
fi

echo "    Service account created with ID: ${SA_ID}"

# Create API token for service account
echo "==> Creating API token for service account..."

TOKEN_RESPONSE=$(curl -sf -X POST \
    -H "Content-Type: application/json" \
    -u "${ADMIN_USER}:${ADMIN_PASSWORD}" \
    "${GRAFANA_URL}/api/auth/keys" \
    -d "{
        \"name\": \"${SA_NAME}-token\",
        \"role\": \"Viewer\",
        \"serviceAccountId\": ${SA_ID}
    }" 2>&1) || {
    echo "ERROR: Failed to create API token: ${TOKEN_RESPONSE}" >&2
    exit 1
}

# Extract the token
TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"key":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "ERROR: Failed to extract token from response" >&2
    echo "Response: ${TOKEN_RESPONSE}" >&2
    exit 1
fi

echo "==> Successfully provisioned service account and token."

# Output the full token response
echo "$TOKEN_RESPONSE"

# Optionally write to output file
echo "$TOKEN_RESPONSE" > "$OUTPUT_FILE"
echo "    Token response written to: ${OUTPUT_FILE}"

# Also print the token for easy capture
echo ""
echo "==> Token: ${TOKEN}"