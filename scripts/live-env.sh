#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# live-env.sh — env-gated canlı test ortamını tek komutla yönetir.
#   up     : backend :8080 + gateway :24543 + preview :8899 ayağa kaldır
#            (port zaten sağlıklı yanıt veriyorsa mevcut süreç sahiplenilir)
#   down   : yalnızca bu script'in başlattığı süreçleri durdur
#   env    : PA_LIVE_* export satırlarını bas (eval ile kullan)
#   status : durum özeti
set -euo pipefail
cd "$(dirname "$0")/.."

RUN=/tmp/pa-live
GW_TOKEN=tok123
WS=/tmp/pa-workspace
PREV=/tmp/pa-preview
KEY=/tmp/pa-dev-key
LIVE_USER=pa-dev

mkdir -p "$RUN" "$WS" "$PREV"

# Port sağlıklı mı? (bash /dev/tcp — curl/nc gerektirmez)
port_up() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && exec 3>&- 3<&-; }

alive() { [ -f "$RUN/$1.pid" ] && kill -0 "$(cat "$RUN/$1.pid")" 2>/dev/null; }
stop()  { if alive "$1"; then kill "$(cat "$RUN/$1.pid")" 2>/dev/null || true; fi; rm -f "$RUN/$1.pid"; }

spawn() { # name, port, cmd...
    local name=$1 port=$2; shift 2
    if port_up "$port"; then
        echo "  · $name zaten ayakta (:$port) — sahiplenildi"
        return
    fi
    "$@" >"$RUN/$name.log" 2>&1 &
    echo $! >"$RUN/$name.pid"
    for _ in $(seq 1 30); do port_up "$port" && break; sleep 0.2; done
    if port_up "$port"; then echo "  ✓ $name başlatıldı (:$port)"
    else echo "  ✗ $name başlatılamadı — $RUN/$name.log"; return 1; fi
}

cmd_up() {
    if ! id "$LIVE_USER" >/dev/null 2>&1; then
        echo "hata: $LIVE_USER kullanıcısı yok — HANDOFF §2'ye göre oluştur" >&2
        exit 1
    fi
    [ -f "$KEY" ] || { echo "hata: $KEY yok" >&2; exit 1; }
    go build -o /tmp/pa-backend ./backend/cmd/server
    go build -o /tmp/pa-hook ./cmd/pocket-agent-hook
    echo "PA_PREVIEW_OK pa-preview-marker" >"$PREV/index.html"
    spawn backend 8080 /tmp/pa-backend
    spawn gateway 24543 env "POCKET_GATEWAY_TOKEN=$GW_TOKEN" /tmp/pa-hook gateway serve --root "$WS"
    spawn preview 8899 python3 -m http.server 8899 --bind 127.0.0.1 --directory "$PREV"
    echo "env için: eval \$(scripts/live-env.sh env)"
}

cmd_down() {
    stop backend; stop gateway; stop preview
    echo "live-env durduruldu (yalnız bu script'in süreçleri)"
}

cmd_env() {
    cat <<EOF
export PA_LIVE_SSH=1
export PA_LIVE_USER=$LIVE_USER
export PA_LIVE_PEM=$KEY
export PA_LIVE_BACKEND=http://127.0.0.1:8080
export PA_LIVE_GW=1
export PA_LIVE_GW_TOKEN=$GW_TOKEN
export POCKET_HOME=/home/$LIVE_USER
EOF
}

cmd_status() {
    for s in "backend 8080" "gateway 24543" "preview 8899"; do
        set -- $s
        if port_up "$2"; then echo "  ✓ $1 (:$2)"; else echo "  ✗ $1 (:$2)"; fi
    done
    echo "env için: eval \$(scripts/live-env.sh env)"
}

case "${1:-up}" in
    up) cmd_up ;; down) cmd_down ;; env) cmd_env ;; status) cmd_status ;;
    *) echo "usage: live-env.sh up|down|env|status" >&2; exit 2 ;;
esac
