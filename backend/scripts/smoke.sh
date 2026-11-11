#!/usr/bin/env bash
# End-to-end check of the API against a running server using FixtureExtractor.
# Usage, from the repository root: backend/scripts/smoke.sh [base-url]
# Needs only curl, grep and sed. Each run uses fresh idempotency keys, so it can be repeated.
set -u

BASE="${1:-http://localhost:8080}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# Windows curl cannot open Git Bash paths such as /c/Users inside -F image=@...
native() { if command -v cygpath > /dev/null; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
NOTES="$(native "$ROOT/kit/delivery-notes")"
RUN="$(date +%s)-$RANDOM"
PASS=0
FAIL=0

ok() { PASS=$((PASS + 1)); echo "  ok   $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  FAIL $1"; }
check() { if [ "$2" = "$3" ]; then ok "$1 = $3"; else bad "$1: expected '$3', got '$2'"; fi; }

receipt_id() { printf '%s' "$1" | grep -o '^{"id":"RCP-[0-9]*"' | sed 's/.*"\(RCP-[0-9]*\)"/\1/'; }
receipt_status() { printf '%s' "$1" | grep -o '^{"id":"[^"]*","status":"[A-Z_]*"' | sed 's/.*"\([A-Z_]*\)"$/\1/'; }
receipt_po() { printf '%s' "$1" | sed -E -n 's/^[{]"id":"[^"]*","status":"[^"]*","poNumber":(null|"[^"]*").*/\1/p' | tr -d '"'; }
line_json() { printf '%s' "$1" | grep -o "{\"id\":\"RL-[0-9]*\",\"poLineId\":[^}]*\"description\":\"$2\"[^}]*}"; }
line_field() { line_json "$1" "$2" | sed -n "s/.*\"$3\":\([^,}]*\).*/\1/p" | tr -d '"'; }
line_id() { line_json "$1" "$2" | sed -n 's/^{"id":"\(RL-[0-9]*\)".*/\1/p'; }
line_count() { printf '%s' "$1" | grep -o '"discrepancy":' | wc -l | tr -d ' '; }

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
get() { curl -s "$BASE$1"; }
send() { curl -s -X "$1" -H 'Content-Type: application/json' -d "$3" "$BASE$2"; }

upload() { # key fixture file
    curl -s -X POST -H "Idempotency-Key: $1" -H "X-Fixture: $2" -F "image=@$3;type=image/png" "$BASE/receipts"
}

wait_done() { # id -> final json
    local body
    for _ in $(seq 1 100); do
        body="$(get "/receipts/$1")"
        if [ "$(receipt_status "$body")" != "CAPTURED" ]; then
            printf '%s' "$body"
            return
        fi
        sleep 0.2
    done
    printf '%s' "$body"
}

capture() { # fixture file -> final json
    local created
    created="$(upload "smoke-$RUN-$1" "$1" "$2")"
    wait_done "$(receipt_id "$created")"
}

echo "== A1 purchase orders"
POS="$(get '/purchase-orders?status=OPEN')"
check "open PO count" "$(printf '%s' "$POS" | grep -o '"poNumber"' | wc -l | tr -d ' ')" 4
check "A1 status" "$(code "$BASE/purchase-orders?status=OPEN")" 200

echo "== A4 capture and idempotency"
KEY="smoke-$RUN-idem"
check "new key" "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Idempotency-Key: $KEY" -H 'X-Fixture: note-1' -F "image=@$NOTES/note-1-clean.png;type=image/png" "$BASE/receipts")" 202
FIRST="$(upload "$KEY" note-1 "$NOTES/note-1-clean.png")"
check "repeat key" "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Idempotency-Key: $KEY" -H 'X-Fixture: note-1' -F "image=@$NOTES/note-1-clean.png;type=image/png" "$BASE/receipts")" 200
ID_FIRST="$(receipt_id "$FIRST")"
SECOND="$(upload "$KEY" note-1 "$NOTES/note-1-clean.png")"
check "same receipt on repeat" "$(receipt_id "$SECOND")" "$ID_FIRST"

echo "== A4 concurrent upload with one key"
CKEY="smoke-$RUN-race"
TMP="$(native "$(mktemp -d)")"
BEFORE="$(get /receipts | grep -o '"clerk"' | wc -l | tr -d ' ')"
for i in $(seq 1 10); do
    curl -s -o "$TMP/$i.json" -w '%{http_code}\n' -X POST -H "Idempotency-Key: $CKEY" -H 'X-Fixture: note-1' \
        -F "image=@$NOTES/note-1-clean.png;type=image/png" "$BASE/receipts" > "$TMP/$i.code" &
done
wait
AFTER="$(get /receipts | grep -o '"clerk"' | wc -l | tr -d ' ')"
check "one receipt added" "$((AFTER - BEFORE))" 1
check "one 202" "$(cat "$TMP"/*.code | grep -c 202)" 1
check "nine 200s" "$(cat "$TMP"/*.code | grep -c 200)" 9
check "one id across responses" "$(for f in "$TMP"/*.json; do receipt_id "$(cat "$f")"; echo; done | sort -u | grep -c RCP)" 1
rm -rf "$TMP"

echo "== A4 errors"
check "missing key" "$(code -X POST -F "image=@$NOTES/note-1-clean.png" "$BASE/receipts")" 400
check "missing image" "$(code -X POST -H "Idempotency-Key: smoke-$RUN-x" -F 'other=1' "$BASE/receipts")" 400
BIG="$(native "$(mktemp)")"
head -c 11000000 /dev/zero > "$BIG"
check "image too large" "$(code -X POST -H "Idempotency-Key: smoke-$RUN-big" -F "image=@$BIG;type=image/png" "$BASE/receipts")" 413
rm -f "$BIG"

echo "== Note 1, clean"
N1="$(capture note-1 "$NOTES/note-1-clean.png")"
ID1="$(receipt_id "$N1")"
check "status" "$(receipt_status "$N1")" EXTRACTED
check "po" "$(receipt_po "$N1")" PO-4501
check "lines" "$(line_count "$N1")" 3
check "all MATCH" "$(printf '%s' "$N1" | grep -o '"discrepancy":"MATCH"' | wc -l | tr -d ' ')" 3
check "A3 status" "$(code "$BASE/receipts/$ID1")" 200
check "A9 status" "$(code "$BASE/receipts/$ID1/image")" 200
check "A9 content type" "$(curl -s -o /dev/null -w '%{content_type}' "$BASE/receipts/$ID1/image")" image/png
IMG="$(native "$(mktemp)")"
curl -s -o "$IMG" "$BASE/receipts/$ID1/image"
check "A9 bytes unchanged" "$(cmp -s "$IMG" "$NOTES/note-1-clean.png" && echo same || echo different)" same
rm -f "$IMG"
C1="$(send POST "/receipts/$ID1/confirm" '')"
check "A6 confirm" "$(receipt_status "$C1")" CONFIRMED
check "A6 repeat" "$(code -X POST "$BASE/receipts/$ID1/confirm")" 200
check "A7 after confirm" "$(code -X POST -H 'Content-Type: application/json' -d '{"reason":"x"}' "$BASE/receipts/$ID1/dispute")" 409
check "A2 filter finds it" "$(get '/receipts?status=CONFIRMED' | grep -c "\"id\":\"$ID1\"")" 1

echo "== Note 2, short"
N2="$(capture note-2 "$NOTES/note-2-short.png")"
ID2="$(receipt_id "$N2")"
check "status" "$(receipt_status "$N2")" EXTRACTED
check "Salmon" "$(line_field "$N2" 'Salmon fillet' discrepancy)" SHORT
check "Salmon delta" "$(line_field "$N2" 'Salmon fillet' delta)" -8
check "Scallops" "$(line_field "$N2" Scallops discrepancy)" UNKNOWN_ITEM
check "Prawns" "$(line_field "$N2" 'Tiger Prawns' discrepancy)" NOT_DELIVERED
LINE="$(line_id "$N2" 'Salmon fillet')"
P2="$(send PATCH "/receipts/$ID2/lines/$LINE" '{"receivedQty":70}')"
check "A5 recomputes delta" "$(line_field "$P2" 'Salmon fillet' delta)" -10
check "A5 marks clerk" "$(line_field "$P2" 'Salmon fillet' correctedByClerk)" true
check "A5 keeps aiQty" "$(line_field "$P2" 'Salmon fillet' aiQty)" 72
check "A7 blank reason" "$(code -X POST -H 'Content-Type: application/json' -d '{"reason":" "}' "$BASE/receipts/$ID2/dispute")" 400
D2="$(send POST "/receipts/$ID2/dispute" '{"reason":"Salmon short, scallops not ordered"}')"
check "A7 dispute" "$(receipt_status "$D2")" DISPUTED
check "A7 repeat" "$(code -X POST -H 'Content-Type: application/json' -d '{"reason":"again"}' "$BASE/receipts/$ID2/dispute")" 200
check "A6 after dispute" "$(code -X POST "$BASE/receipts/$ID2/confirm")" 409
check "A5 on closed receipt" "$(code -X PATCH -H 'Content-Type: application/json' -d '{"receivedQty":1}' "$BASE/receipts/$ID2/lines/$LINE")" 409

echo "== Note 3, messy"
N3="$(capture note-3 "$NOTES/note-3-messy.png")"
ID3="$(receipt_id "$N3")"
check "status" "$(receipt_status "$N3")" NEEDS_MANUAL
check "po is null" "$(receipt_po "$N3")" null
check "A6 with no PO" "$(code -X POST "$BASE/receipts/$ID3/confirm")" 409
check "A8 unknown PO" "$(code -X PATCH -H 'Content-Type: application/json' -d '{"poNumber":"PO-0000"}' "$BASE/receipts/$ID3")" 404
A3="$(send PATCH "/receipts/$ID3" '{"poNumber":"PO-4503"}')"
check "A8 status" "$(receipt_status "$A3")" EXTRACTED
check "A8 po" "$(receipt_po "$A3")" PO-4503
check "Milk" "$(line_field "$A3" 'Milk 1L' discrepancy)" OVER
check "Butter" "$(line_field "$A3" 'Butter 500g' discrepancy)" UNIT_MISMATCH
check "Cheddar" "$(line_field "$A3" Cheddar discrepancy)" SHORT
check "lines" "$(line_count "$A3")" 3
C3="$(send POST "/receipts/$ID3/confirm" '')"
check "confirm after assigning" "$(receipt_status "$C3")" CONFIRMED

echo "== Garbage"
G="$(capture garbage "$NOTES/note-1-clean.png")"
IDG="$(receipt_id "$G")"
check "status" "$(receipt_status "$G")" NEEDS_MANUAL
check "po is null" "$(receipt_po "$G")" null
check "A3 is not a 500" "$(code "$BASE/receipts/$IDG")" 200

echo "== 404s"
check "unknown receipt" "$(code "$BASE/receipts/RCP-999999")" 404
check "A2 status" "$(code "$BASE/receipts")" 200

echo
echo "passed: $PASS  failed: $FAIL"
[ "$FAIL" -eq 0 ]
