#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Current-shell env (P00 stub; real ANDROID_HOME/Go paths set in later steps).
export POCKET_AGENT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export PATH="$POCKET_AGENT_ROOT/scripts:$PATH"
