#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Toolchain bootstrap (full body lands with P01 env work; P00 keeps it idempotent stub).
# Real installer: Ubuntu pkgs, JDK, Go, Android SDK/NDK/CMake, Buf, protoc — see plan.md §4.1.
set -euo pipefail
echo "[setup-dev-environment] P00 stub — full installer in P01/P05 work."
echo "Intended: apt-get, JDK17, Go 1.22+, /opt/android-sdk, NDK, CMake, buf."
