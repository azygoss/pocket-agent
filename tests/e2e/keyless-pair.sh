#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Keyless pairing e2e against the real backend binary: host registers,
# opens a code, a "phone" claims with its own public key, host approves;
# asserts the private key never leaves the phone and the device token works.
# Uses Postgres when PA_TEST_DATABASE_URL is set (also checks restart).
set -euo pipefail
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
trap 'kill ${srv:-} 2>/dev/null || true; rm -rf "$work"' EXIT
go build -o "$work/server" ./backend/cmd/server
go build -o "$work/pa" ./cmd/pocket-agent-hook
port=$((20000 + RANDOM % 20000))
start() {
    DATABASE_URL="${PA_TEST_DATABASE_URL:-}" POCKET_ADDR="127.0.0.1:$port" "$work/server" >>"$work/server.log" 2>&1 &
    srv=$!
    for _ in $(seq 50); do curl -sf "http://127.0.0.1:$port/v1/healthz" >/dev/null && return; sleep 0.1; done
    echo "server did not start"; cat "$work/server.log"; exit 1
}
start
B="http://127.0.0.1:$port"
export POCKET_HOME="$work/home" HOME="$work/home"
mkdir -p "$HOME"
ssh-keygen -q -t ed25519 -N "" -C phone -f "$work/phone" >/dev/null
"$work/pa" pair --backend "$B" --host 127.0.0.1 --user tester --yes >"$work/pair.out" 2>&1 &
pair=$!
for _ in $(seq 50); do grep -q "Kod:" "$work/pair.out" && break; sleep 0.1; done
code=$(sed -n 's/.*Kod: \([A-Z0-9-]*\).*/\1/p' "$work/pair.out")
[ -n "$code" ] || { cat "$work/pair.out"; exit 1; }
claim=$(curl -sf -X POST "$B/v1/pairing-sessions/$code/claim" -H 'Content-Type: application/json' \
    -d "{\"device_id\":\"device:e2e\",\"device_name\":\"E2E\",\"public_key\":\"$(cat "$work/phone.pub")\"}")
token=$(printf '%s' "$claim" | sed -n 's/.*"device_token":"\([^"]*\)".*/\1/p')
[ -n "$token" ] || { echo "no device token: $claim"; exit 1; }
wait $pair || { cat "$work/pair.out"; exit 1; }
grep -q "$(cut -d' ' -f2 "$work/phone.pub")" "$HOME/.ssh/authorized_keys" || { echo "pubkey not authorized"; exit 1; }
if grep -rq "PRIVATE KEY" "$work/server.log" "$work/pair.out" "$POCKET_HOME/.config"; then
    echo "private key material leaked"; exit 1
fi
curl -sf "$B/v1/whoami" -H "Authorization: Bearer $token" | grep -q '"device:e2e"'
[ "$(curl -s -o /dev/null -w '%{http_code}' "$B/v1/events" -H 'X-Tenant: default')" = 401 ]
if [ -n "${PA_TEST_DATABASE_URL:-}" ]; then
    kill $srv; wait $srv 2>/dev/null || true
    start
    curl -sf "$B/v1/whoami" -H "Authorization: Bearer $token" | grep -q '"device:e2e"' || { echo "token lost on restart"; exit 1; }
    echo "keyless-pair e2e OK (postgres, survived restart)"
else
    echo "keyless-pair e2e OK (memory)"
fi
