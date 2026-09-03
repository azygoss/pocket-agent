#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P01 gate: forbidden sensitive fields must never be expressible as AgentEventSummary.
set -euo pipefail
cd "$(dirname "$0")/../.."
FIX=protocol/fixtures/golden-v1.json
echo "[privacy-schema] checking $FIX ..."
python3 - <<'PY'
import json, sys
allowed = {"description","negotiate_request","negotiate_response_major_mismatch",
 "unknown_capability","event_summary_ok","event_summary_rejected_forbidden_fields",
 "event_id","opaque_host_id","opaque_session_id","source","category","title","message",
 "truncated","project_label","model_label","tool_label","context_percent",
 "request_digest","revision","created_at","expires_at",
 "client","major","minor","want","ssh","mosh","et","tmux","zellij","herdr","gateway","chat",
 "host","structured_enabled","note","have","future_feature_x"}
forbidden = {"transcript","code","diff","filePath","file_path","previewBody","preview_body",
 "password","privateKey","private_key","audio","scrollback","ssh_secret","mosh_key"}
data = json.load(open("protocol/fixtures/golden-v1.json"))
ok = data["event_summary_ok"]
bad = [k for k in ok if k not in allowed]
if bad:
    print(f"FAIL: event_summary_ok has unexpected fields: {bad}"); sys.exit(1)
rej = data["event_summary_rejected_forbidden_fields"]
hit = [k for k in rej if k in forbidden]
if not hit:
    print("FAIL: rejected fixture must contain forbidden fields for test"); sys.exit(1)
# message length cap
if len(ok["message"]) > 256:
    print("FAIL: message > 256 chars"); sys.exit(1)
print(f"[privacy-schema] OK (ok-fields={len(ok)}, rejected-sample={hit})")
PY
# buf lint if available (warn-only until toolchain pinned)
if command -v buf >/dev/null; then buf lint || echo "WARN: buf lint failed (fix before P01 done)"; fi
