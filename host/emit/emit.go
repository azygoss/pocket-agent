// SPDX-License-Identifier: GPL-3.0-or-later
// Package emit: journal'daki agent_event kayıtlarını backend'e postlar.
// P12/P13 zincirinin eksik halkası: hook -> emit -> journal -> daemon -> POST
// /v1/hosts/{hostId}/events (X-Tenant). Yalnız özet alanları gider — protocol
// ValidateEventSummary'nin whitelist'iyle birebir aynı şema.
package emit

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Summary: backend AgentEventSummary wire şeması (snake_case).
type Summary struct {
	EventID   string `json:"event_id"`
	OpaqueH   string `json:"opaque_host_id"`
	OpaqueS   string `json:"opaque_session_id"`
	Source    string `json:"source"`
	Category  string `json:"category"`
	Message   string `json:"message"`
	Digest    string `json:"request_digest"`
	Revision  string `json:"revision"`
	CreatedAt string `json:"created_at"`
	ExpiresAt string `json:"expires_at"`
}

func shortHash(parts ...string) string {
	h := sha256.New()
	for _, p := range parts {
		h.Write([]byte(p))
		h.Write([]byte{0})
	}
	return hex.EncodeToString(h.Sum(nil))[:16]
}

// Build: normalize edilmiş bir olayı wire özetine çevirir. Aynı
// (source, sourceEventID) aynı event_id'yi üretir — backend'de idempotent.
// Host/session kimlikleri hash'lenir (opaque) — plaintext hostname gitmez.
// opaqueSess: tmux:/proc: şemalı session kimlikleri RAW gider — uygulama
// "oturuma git" için tmux adına ihtiyaç duyar (telefon zaten SSH sahibi).
// Hook'lardan gelen keyfî session_id'ler hash'lenir.
func opaqueSess(session string) string {
	if strings.HasPrefix(session, "tmux:") || strings.HasPrefix(session, "proc:") {
		return session
	}
	return "s:" + shortHash(session)
}

func Build(hostID, source, category, sourceEventID, session, message string, now time.Time) Summary {
	created := now.UTC().Truncate(time.Second)
	return Summary{
		EventID:   "event:" + shortHash(source, sourceEventID),
		OpaqueH:   "h:" + shortHash(hostID),
		OpaqueS:   opaqueSess(session),
		Source:    source,
		Category:  strings.ToUpper(category),
		Message:   message,
		Digest:    shortHash(source, sourceEventID, message),
		Revision:  "1",
		CreatedAt: created.Format(time.RFC3339),
		ExpiresAt: created.Add(24 * time.Hour).Format(time.RFC3339),
	}
}

// Post: tek özeti backend'e gönderir. 202 => nil; 4xx => kalıcı hata
// (flusher offset'i ilerletir, zehirli kayıt kuyruğu tıkamaz).
func Post(client *http.Client, backend, tenant, hostID string, s Summary) error {
	body, _ := json.Marshal(s)
	u := strings.TrimRight(backend, "/") + "/v1/hosts/" + url.PathEscape(hostID) + "/events"
	req, err := http.NewRequest("POST", u, bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("X-Tenant", tenant)
	req.Header.Set("Content-Type", "application/json")
	if client == nil {
		client = &http.Client{Timeout: 8 * time.Second}
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode == 202 {
		return nil
	}
	return fmt.Errorf("backend %s", resp.Status)
}
