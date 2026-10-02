#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MAILHOG_URL="${MAILHOG_URL:-http://localhost:8025}"
SUFFIX="$(date +%s)-$RANDOM"
TENANT_NAME="Stage 1 E2E ${SUFFIX}"
ADMIN_EMAIL="admin-${SUFFIX}@e2e.test"
PASSWORD="E2ePassword-${SUFFIX}"
PERIOD="$(date -u +%Y-%m)"

health_urls=("$BASE_URL/actuator/health")
if [[ "$BASE_URL" == 'http://localhost:8080' || "$BASE_URL" == 'http://127.0.0.1:8080' ]]; then
  health_urls+=(http://localhost:8081/actuator/health http://localhost:8082/actuator/health
    http://localhost:8083/actuator/health http://localhost:8084/actuator/health
    http://localhost:8085/actuator/health)
fi
services_ready=false
for attempt in $(seq 1 60); do
  services_ready=true
  for health_url in "${health_urls[@]}"; do
    if ! curl --fail --silent "$health_url" | jq -e '.status == "UP"' >/dev/null; then
      services_ready=false
      break
    fi
  done
  [[ "$services_ready" == true ]] && break
  sleep 1
done
[[ "$services_ready" == true ]] || {
  echo 'Required application services did not become healthy within 60 seconds' >&2
  exit 1
}

json_request() {
  local method="$1" url="$2" body="${3:-}" token="${4:-}"
  if [[ -n "$token" ]]; then
    curl --fail-with-body --silent --show-error -X "$method" "$url" \
      -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      ${body:+-d "$body"}
  else
    curl --fail-with-body --silent --show-error -X "$method" "$url" \
      -H 'Content-Type: application/json' ${body:+-d "$body"}
  fi
}

printf 'Registering tenant %s\n' "$TENANT_NAME"
registration="$(json_request POST "$BASE_URL/auth/register-tenant" \
  "$(jq -n --arg name "$TENANT_NAME" --arg email "$ADMIN_EMAIL" --arg password "$PASSWORD" \
    '{tenantName:$name,adminEmail:$email,password:$password,planType:"FLAT_RATE"}')")"
tenant_id="$(jq -r '.tenantId' <<<"$registration")"

login="$(json_request POST "$BASE_URL/auth/login" \
  "$(jq -n --arg email "$ADMIN_EMAIL" --arg password "$PASSWORD" '{email:$email,password:$password}')")"
token="$(jq -r '.accessToken' <<<"$login")"

anonymous_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "$BASE_URL/devices")"
[[ "$anonymous_status" == '401' ]] || {
  echo "Expected anonymous device request to return 401, got $anonymous_status" >&2
  exit 1
}
reader_email="reader-${SUFFIX}@e2e.test"
json_request POST "$BASE_URL/auth/users" \
  "$(jq -n --arg email "$reader_email" --arg password "$PASSWORD" \
    '{email:$email,password:$password,role:"ROLE_USER"}')" "$token" >/dev/null
reader_login="$(json_request POST "$BASE_URL/auth/login" \
  "$(jq -n --arg email "$reader_email" --arg password "$PASSWORD" '{email:$email,password:$password}')")"
reader_token="$(jq -r '.accessToken' <<<"$reader_login")"
role_status="$(curl --silent --output /dev/null --write-out '%{http_code}' -X POST \
  "$BASE_URL/billing/invoices/generate?period=$PERIOD" -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $reader_token")"
[[ "$role_status" == '403' ]] || {
  echo "Expected regular user invoice generation to return 403, got $role_status" >&2
  exit 1
}

device="$(json_request POST "$BASE_URL/devices" \
  "$(jq -n --arg name "E2E Device $SUFFIX" '{name:$name,type:"BROWSER",os:"Linux"}')" "$token")"
device_id="$(jq -r '.id' <<<"$device")"
session="$(json_request POST "$BASE_URL/devices/$device_id/sessions/start" '{}' "$token")"
session_id="$(jq -r '.sessionId' <<<"$session")"
json_request POST "$BASE_URL/sessions/$session_id/end" '{}' "$token" >/dev/null

summary=''
for attempt in $(seq 1 30); do
  summary="$(json_request GET "$BASE_URL/usage/summary?period=$PERIOD" '' "$token")"
  if [[ "$(jq -r '.totalSessions' <<<"$summary")" -ge 1 ]]; then
    break
  fi
  sleep 1
done
[[ "$(jq -r '.totalSessions' <<<"$summary")" -ge 1 ]] || {
  echo 'Usage event was not aggregated within 30 seconds' >&2
  exit 1
}

invoice="$(json_request POST "$BASE_URL/billing/invoices/generate?period=$PERIOD" '{}' "$token")"
invoice_id="$(jq -r '.id' <<<"$invoice")"
invoice_repeat="$(json_request POST "$BASE_URL/billing/invoices/generate?period=$PERIOD" '{}' "$token")"
[[ "$(jq -r '.id' <<<"$invoice_repeat")" == "$invoice_id" ]] || {
  echo 'Invoice generation was not idempotent' >&2
  exit 1
}

mail=''
for attempt in $(seq 1 30); do
  mail="$(curl --fail-with-body --silent --show-error --get "$MAILHOG_URL/api/v2/search" \
    --data-urlencode 'kind=to' --data-urlencode "query=$ADMIN_EMAIL")"
  if [[ "$(jq -r '.total' <<<"$mail")" -ge 1 ]]; then
    break
  fi
  sleep 1
done
[[ "$(jq -r '.total' <<<"$mail")" -ge 1 ]] || {
  echo 'Invoice email was not visible in MailHog within 30 seconds' >&2
  exit 1
}

printf 'E2E passed: tenant=%s sessions=%s invoice=%s total=%s %s email=%s\n' \
  "$tenant_id" "$(jq -r '.totalSessions' <<<"$summary")" "$invoice_id" \
  "$(jq -r '.totalAmount' <<<"$invoice")" "$(jq -r '.currency' <<<"$invoice")" "$ADMIN_EMAIL"