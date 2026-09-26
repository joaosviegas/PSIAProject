#!/usr/bin/env bash
# End-to-end smoke test through the gateway. Extend it as features land
set -euo pipefail

BASE="${BASE_URL:-http://localhost:${GATEWAY_PORT:-8080}}"
fail() { echo "FAIL: $*" >&2; exit 1; }
ok()   { echo "ok   $*"; }

echo "Smoke test against $BASE"

# 1. Every container that has a healthcheck is healthy
unhealthy=$(docker compose ps --format '{{.Service}} {{.Health}}' | awk '$2 != "" && $2 != "healthy" {print $1}')
[ -z "$unhealthy" ] || fail "not healthy: $unhealthy"
ok "all containers healthy"

# 2. Gateway serves the Vue app
curl -fsS "$BASE/" | grep -qi '<div id="app"' || fail "gateway did not return the Vue index.html"
ok "gateway serves the frontend"

# 3. Gateway routes to each service (ping endpoints added in phase 1)
for path in /api/auth/ping /api/profiles/ping /api/movies/ping /api/feed/ping; do
  curl -fsS "$BASE$path" >/dev/null || fail "GET $path"
  ok "GET $path"
done

# 4. Internal endpoints are NOT reachable from outside
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/internal/anything")
[ "$code" = "404" ] || fail "/api/internal/* should be 404, got $code"
ok "internal paths not exposed"

# 5. Auth: register, login, me
RAND_USER="smoke_$(date +%s)"
REG=$(curl -s -w '\n%{http_code}' -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$RAND_USER\",\"password\":\"smoketest123\"}")
REG_CODE=$(echo "$REG" | tail -1)
[ "$REG_CODE" = "201" ] || fail "register returned $REG_CODE"
ok "POST /api/auth/register -> 201"

LOGIN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$RAND_USER\",\"password\":\"smoketest123\"}")
TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
[ -n "$TOKEN" ] || fail "login did not return a token"
ok "POST /api/auth/login -> token"

ME=$(curl -s "$BASE/api/auth/me" -H "Authorization: Bearer $TOKEN")
echo "$ME" | grep -q "$RAND_USER" || fail "/api/auth/me did not return username"
ok "GET /api/auth/me -> username matches"

# 6. Token works on other services (should NOT get 401)
PROFILE_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/profiles/me" -H "Authorization: Bearer $TOKEN")
[ "$PROFILE_CODE" != "401" ] || fail "profile-service rejected the JWT (got 401)"
ok "JWT accepted by profile-service ($PROFILE_CODE)"

# Export for later phase checks
export TOKEN

# --- Phases 3-6 append their checks below this line ---

echo "All smoke checks passed."
