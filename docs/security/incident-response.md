# Incident response (P18 skeleton)
1. Revoke: backend device delete + host authorized_keys marker remove.
2. Rotate: backend + host secrets, FCM key if scope.
3. Purge: TTL sweep + uploads short-code invalidate.
4. Notify: audit_log export, user notice.
Runbook tested via `go test ./backend/... ./host/...`.
