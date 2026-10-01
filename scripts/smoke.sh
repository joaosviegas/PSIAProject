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

# --- Phase 3: profile-service ---

# 7. Create/update profile
PUT_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$BASE/api/profiles/me" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"displayName":"Smoke User","bio":"testing","favoriteGenres":["Drama","Sci-Fi"],"favoriteMovieIds":[1,2],"dislikedMovieIds":[3]}')
[ "$PUT_CODE" = "200" ] || fail "PUT /api/profiles/me returned $PUT_CODE"
ok "PUT /api/profiles/me -> 200"

# 8. Read profile back
PROFILE=$(curl -s "$BASE/api/profiles/me" -H "Authorization: Bearer $TOKEN")
echo "$PROFILE" | grep -q '"Smoke User"' || fail "/api/profiles/me did not return displayName"
ok "GET /api/profiles/me -> profile matches"

# 9. Browse profiles (excludes self, so may be empty — just check 200)
BROWSE_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/profiles?genre=Drama" -H "Authorization: Bearer $TOKEN")
[ "$BROWSE_CODE" = "200" ] || fail "GET /api/profiles?genre=Drama returned $BROWSE_CODE"
ok "GET /api/profiles?genre=Drama -> 200"

# 10. Internal endpoint reachable inside Docker network
INTERNAL_CODE=$(docker compose exec -T profile-service curl -s -o /dev/null -w '%{http_code}' \
  "http://profile-service:8080/internal/profiles?updatedSince=2020-01-01T00:00:00Z")
[ "$INTERNAL_CODE" = "200" ] || fail "GET /internal/profiles?updatedSince returned $INTERNAL_CODE"
ok "GET /internal/profiles?updatedSince (internal) -> 200"

# --- Phases 4-6 append their checks below this line ---

# --- agent-service ---

# 11. Feed requires a token
FEED_NOAUTH=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/feed")
[ "$FEED_NOAUTH" = "401" ] || fail "GET /api/feed without token returned $FEED_NOAUTH"
ok "GET /api/feed without token -> 401"

# 12. Feed is either ready (200) or queued for the next agent run (202)
FEED_CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/feed" -H "Authorization: Bearer $TOKEN")
[ "$FEED_CODE" = "200" ] || [ "$FEED_CODE" = "202" ] || fail "GET /api/feed returned $FEED_CODE"
ok "GET /api/feed -> $FEED_CODE"

echo "All smoke checks passed."
