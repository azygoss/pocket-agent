#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# iOS Simulator end-to-end: real backend (Postgres if PA_TEST_DATABASE_URL),
# an unprivileged sshd on 127.0.0.1:2222 with SFTP, a tmux session, and the
# host CLI running keyless `pair --yes`. The UI test pairs with the code,
# trusts the host key, attaches tmux, types a command, browses SFTP and
# approves an agent event. Requires macOS with Xcode, Go and tmux.
set -euo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
repo=$(cd "$here/../.." && pwd)
W=/tmp/pa-e2e
DEST=${DEST:-platform=iOS Simulator,name=iPhone 17}
PORT=${PA_E2E_PORT:-18080}
SSH_PORT=${PA_E2E_SSH_PORT:-2222}
TMUX=$(command -v tmux)

cleanup() {
    set +e
    [ -f "$W/sshd.pid" ] && kill "$(cat "$W/sshd.pid")" 2>/dev/null
    [ -n "${srv:-}" ] && kill "$srv" 2>/dev/null
    [ -n "${pairer:-}" ] && kill "$pairer" 2>/dev/null
    "$TMUX" kill-session -t pa-e2e 2>/dev/null
}
trap cleanup EXIT
rm -rf "$W" && mkdir -p "$W/bin" "$W/home/.ssh" "$W/sshd"
chmod 700 "$W/home/.ssh"

(cd "$repo" && go build -o "$W/bin/server" ./backend/cmd/server && go build -o "$W/bin/pa" ./cmd/pocket-agent-hook)

DATABASE_URL="${PA_TEST_DATABASE_URL:-}" POCKET_ADDR="127.0.0.1:$PORT" "$W/bin/server" >"$W/server.log" 2>&1 &
srv=$!
B="http://127.0.0.1:$PORT"
for _ in $(seq 50); do curl -sf "$B/v1/healthz" >/dev/null && break; sleep 0.1; done
echo "$B" >"$W/backend"

ssh-keygen -q -t ed25519 -N "" -f "$W/sshd/host_ed25519" -C e2e-host
cat >"$W/sshd/sshd_config" <<CFG
Port $SSH_PORT
ListenAddress 127.0.0.1
HostKey $W/sshd/host_ed25519
PidFile $W/sshd.pid
AuthorizedKeysFile $W/home/.ssh/authorized_keys
StrictModes no
PasswordAuthentication no
KbdInteractiveAuthentication no
UsePAM no
Subsystem sftp /usr/libexec/sftp-server
CFG
/usr/sbin/sshd -f "$W/sshd/sshd_config" -E "$W/sshd.log"
"$TMUX" new-session -d -s pa-e2e -x 120 -y 40
"$TMUX" send-keys -t pa-e2e "echo 'pocket-agent e2e: tmux oturumu hazır'" Enter
printf 'hello from sftp\n' >"$W/readme.txt"

# Host side: wait for the UI test to ask, then run keyless pair --yes.
# No announced host keys (--host-keys matches nothing) → app shows TOFU.
(
    while [ ! -f "$W/start-pair" ]; do sleep 0.3; done
    HOME="$W/home" POCKET_HOME="$W/home" "$W/bin/pa" pair --backend "$B" --host 127.0.0.1 \
        --port "$SSH_PORT" --user "$(whoami)" --yes --host-keys "$W/none-*" >"$W/pair.log" 2>&1 &
    p=$!
    for _ in $(seq 100); do grep -q "Kod:" "$W/pair.log" 2>/dev/null && break; sleep 0.1; done
    sed -n 's/.*Kod: \([A-Z0-9-]*\).*/\1/p' "$W/pair.log" | head -1 >"$W/code"
    wait $p && echo ok >"$W/paired"
    cfg="$W/home/.config/pocket-agent/config.toml"
    tok=$(sed -n 's/^host_token *= *"\(.*\)"/\1/p' "$cfg")
    hid=$(curl -sf "$B/v1/whoami" -H "Authorization: Bearer $tok" | sed -n 's/.*"subject":"\([^"]*\)".*/\1/p')
    now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    exp=$(date -u -v+1d +%Y-%m-%dT%H:%M:%SZ)
    curl -sf -X POST "$B/v1/hosts/${hid:-h}/events" -H "Authorization: Bearer $tok" -H 'Content-Type: application/json' \
        -d "{\"event_id\":\"event:ios-e2e\",\"opaque_host_id\":\"h\",\"opaque_session_id\":\"s\",\"source\":\"claude\",\"category\":\"APPROVAL_REQUIRED\",\"message\":\"Bash: npm run deploy\",\"request_digest\":\"dg-1\",\"revision\":\"1\",\"created_at\":\"$now\",\"expires_at\":\"$exp\"}" >/dev/null \
        && echo ok >"$W/event-posted"
) &
pairer=$!

cd "$here"
xcodebuild ${XCB_ACTION:-test} -project PocketAgent.xcodeproj -scheme PocketAgent -destination "$DEST" \
    -derivedDataPath build/dd -resultBundlePath "$W/e2e.xcresult" \
    -only-testing:PocketAgentUITests/PocketAgentUITests/testEndToEnd 2>&1 | tee "$W/xcodebuild.log" | grep -E "Test Case|error:|passed|failed" || true

# Host-side assertions.
fail=0
[ -f "$W/paired" ] || { echo "✗ host pair did not complete"; fail=1; }
[ "$(cat "$W/typed.txt" 2>/dev/null)" = "ios-e2e-ok" ] || { echo "✗ command typed in iOS terminal did not run"; fail=1; }
grep -q "pair-" "$W/home/.ssh/authorized_keys" || { echo "✗ device key not in authorized_keys"; fail=1; }
if grep -rqs "PRIVATE KEY" "$W/server.log" "$W/pair.log" "$W/home/.config"; then echo "✗ private key material leaked"; fail=1; fi
grep -qE "\*\* TEST (EXECUTE )?SUCCEEDED \*\*" "$W/xcodebuild.log" || { echo "✗ UI test failed (see $W/e2e.xcresult)"; fail=1; }
[ $fail = 0 ] && echo "✓ iOS e2e passed (result bundle: $W/e2e.xcresult)"
exit $fail
