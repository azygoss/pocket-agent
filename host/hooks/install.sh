#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# P12: owned-block hook install (idempotent, preserves user blocks).
set -euo pipefail
begin="# pocket-agent begin"; end="# pocket-agent end"
for f in ~/.claude.json ~/.codex/config.toml ~/.config/opencode/config.json; do
  [ -f "$f" ] || continue
  grep -q "$begin" "$f" 2>/dev/null && continue
  printf '\n%s\n# managed by pocket-agent (remove block to uninstall)\n%s\n' "$begin" "$end" >> "$f"
  echo "patched $f"
done
