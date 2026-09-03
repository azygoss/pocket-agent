# Revoke (P18)
1. Backend: `DELETE /v1/devices/{id}` + `DELETE /v1/hosts/{hostId}` (tenant izole, 404 yabancıya bilgi sızdırmaz).
2. Host: `pocket-agent host revoke <deviceId>` (yalnız marker satırı).
3. Upload: `DELETE /v1/uploads/{id}` (short code invalidate).
