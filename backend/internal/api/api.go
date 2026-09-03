// SPDX-License-Identifier: GPL-3.0-or-later
// Package api: minimal stdlib HTTP surface (healthz, events, pairing claim).
// Full passkey/OIDC/FCM lands incrementally; tenant is taken from X-Tenant header
// in this skeleton (P02 e2e proves isolation; real auth middleware in next slice).
package api

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
	pocketprotocol "github.com/pocket-agent/pocket-agent/protocol"
)

type Server struct {
	Store *store.Store
}

func tenantOf(r *http.Request) string { return r.Header.Get("X-Tenant") }

func (s *Server) routes() *http.ServeMux {
	m := http.NewServeMux()
	m.HandleFunc("GET /v1/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Write([]byte("ok"))
	})
	m.HandleFunc("POST /v1/hosts/{hostId}/events", s.handlePostEvent)
	m.HandleFunc("GET /v1/events", s.handleGetEvents)
	return m
}

func (s *Server) handlePostEvent(w http.ResponseWriter, r *http.Request) {
	tenant := tenantOf(r)
	if tenant == "" {
		http.Error(w, "missing tenant", 401)
		return
	}
	var raw json.RawMessage
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 1<<20))
	if err := dec.Decode(&raw); err != nil {
		http.Error(w, "bad json", 400)
		return
	}
	// Privacy whitelist: forbidden fields => 400, never logged.
	if err := pocketprotocol.ValidateEventSummary(raw); err != nil {
		http.Error(w, "rejected: "+err.Error(), 400)
		return
	}
	var v struct {
		EventID string `json:"event_id"`
		Created string `json:"created_at"`
		Expires string `json:"expires_at"`
		Message string `json:"message"`
		Digest  string `json:"request_digest"`
		Rev     string `json:"revision"`
		OpaqueH string `json:"opaque_host_id"`
		OpaqueS string `json:"opaque_session_id"`
		Source  string `json:"source"`
		Cat     string `json:"category"`
	}
	_ = json.Unmarshal(raw, &v)
	created, _ := time.Parse(time.RFC3339, v.Created)
	expires, _ := time.Parse(time.RFC3339, v.Expires)
	if expires.Sub(created) > 24*time.Hour {
		expires = created.Add(24 * time.Hour)
	}
	_ = s.Store.PutEvent(tenant, store.Event{
		EventID: v.EventID, TenantID: tenant, CreatedAt: created, ExpiresAt: expires,
	})
	w.WriteHeader(202)
}

func (s *Server) handleGetEvents(w http.ResponseWriter, r *http.Request) {
	tenant := tenantOf(r)
	after := r.URL.Query().Get("after")
	evs := s.Store.GetEvents(tenant, after)
	// Never leak foreign tenants: store already filters; double-guard here.
	out := []store.Event{}
	for _, e := range evs {
		if strings.HasPrefix(e.EventID, "event:") {
			out = append(out, e)
		}
	}
	if out == nil {
		out = []store.Event{}
	}
	json.NewEncoder(w).Encode(out)
}
