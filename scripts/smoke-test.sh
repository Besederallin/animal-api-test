#!/usr/bin/env bash
# End-to-end check of a running service against the REAL picture APIs.
# Usage: scripts/smoke-test.sh [base-url]      (default http://localhost:8080)
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Waiting for $BASE_URL to become ready..."
for _ in $(seq 1 60); do
    curl -fs "$BASE_URL/actuator/health/readiness" >/dev/null && break
    sleep 2
done
curl -fs "$BASE_URL/actuator/health/readiness" >/dev/null || { echo "FAIL: service not ready"; exit 1; }

fail() { echo "FAIL: $*"; exit 1; }

for animal in duck dog bear; do
    status=$(curl -s -o "$TMP/$animal.json" -w '%{http_code}' -X POST "$BASE_URL/api/pictures/$animal?count=2")
    [ "$status" = 201 ] || fail "POST $animal returned $status: $(cat "$TMP/$animal.json")"
    grep -q '"animal":"'"$animal"'"' "$TMP/$animal.json" || fail "POST $animal response: $(cat "$TMP/$animal.json")"

    status=$(curl -s -D "$TMP/$animal.headers" -o "$TMP/$animal.img" -w '%{http_code}' "$BASE_URL/api/pictures/$animal/latest")
    [ "$status" = 200 ] || fail "GET latest $animal returned $status"
    grep -qi '^content-type: image/' "$TMP/$animal.headers" || fail "GET latest $animal did not return an image"
    [ -s "$TMP/$animal.img" ] || fail "GET latest $animal returned an empty body"
    echo "OK: $animal ($(wc -c < "$TMP/$animal.img") bytes)"
done

status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/pictures/cat")
[ "$status" = 400 ] || fail "unknown animal should return 400, got $status"
echo "OK: unknown animal rejected"

status=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/")
[ "$status" = 200 ] || fail "UI page returned $status"
echo "OK: UI page served"

# Save responses to files first: "curl | grep -q" can fail under pipefail when grep exits early.
curl -fs -o "$TMP/metrics.txt" "$BASE_URL/actuator/prometheus" || fail "Prometheus metrics endpoint failed"
grep -q '^animal_pictures_stored_total' "$TMP/metrics.txt" \
    || fail "Prometheus metrics endpoint does not expose animal_pictures_stored_total"
echo "OK: Prometheus metrics exposed"

curl -fs -D "$TMP/request-id.headers" -o /dev/null -H 'X-Request-Id: smoke-test-1' "$BASE_URL/api/pictures/duck/latest"
grep -qi '^x-request-id: smoke-test-1' "$TMP/request-id.headers" || fail "X-Request-Id is not echoed back"
echo "OK: request ID echoed"

echo "All smoke tests passed."
