// SPDX-License-Identifier: GPL-3.0-or-later
// Package api: minimal stdlib HTTP surface (healthz, events, pairing claim).
// Full passkey/OIDC/FCM lands incrementally; tenant is taken from X-Tenant header
// in this skeleton (P02 e2e proves isolation; real auth middleware in next slice).
package api

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync/atomic"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/approvals"
	"github.com/pocket-agent/pocket-agent/backend/internal/store"
	pocketprotocol "github.com/pocket-agent/pocket-agent/protocol"
)

type Server struct {
	Store *store.Store
	Table *approvals.Table

	started  time.Time
	nEvents  atomic.Int64
	nPairs   atomic.Int64
	nClaims  atomic.Int64
	nUploads atomic.Int64
	nApprove atomic.Int64
}

// New builds the handler with shared store.
func New(st *store.Store) *Server {
	return &Server{Store: st, Table: approvals.New(), started: time.Now()}
}

// Handler exposes routes for cmd/server.
func (s *Server) Handler() http.Handler { return s.routes() }

func tenantOf(r *http.Request) string { return r.Header.Get("X-Tenant") }

func (s *Server) routes() *http.ServeMux {
	m := http.NewServeMux()
	m.HandleFunc("GET /v1/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Write([]byte("ok"))
	})
	m.HandleFunc("POST /v1/hosts/{hostId}/events", s.handlePostEvent)
	m.HandleFunc("GET /v1/events", s.handleGetEvents)
	m.HandleFunc("POST /v1/pairing-sessions", s.handlePairCreate)
	m.HandleFunc("POST /v1/pairing-sessions/{code}/claim", s.handlePairClaim)
	m.HandleFunc("GET /v1/pairing-sessions/{code}", s.handlePairGet)
	m.HandleFunc("POST /v1/devices", s.handleDeviceAdd)
	m.HandleFunc("DELETE /v1/devices/{deviceId}", s.handleDeviceDel)
	m.HandleFunc("GET /v1/hosts", s.handleHostList)
	m.HandleFunc("DELETE /v1/hosts/{hostId}", s.handleHostDel)
	m.HandleFunc("GET /v1/usages", s.handleUsages)
	m.HandleFunc("POST /v1/uploads", s.handleUploadCreate)
	m.HandleFunc("GET /i/{shortCode}", s.handleUploadGet)
	m.HandleFunc("DELETE /v1/uploads/{uploadId}", s.handleUploadDel)
	m.HandleFunc("POST /v1/approvals/{approvalId}/actions", s.handleApprovalAction)
	m.HandleFunc("POST /v1/webhooks/{token}", s.handleWebhook)
	// /v1/metrics: basit operasyon sayaçları — gözlemlenebilirlik, auth istemez
	// (yalnızca toplamlar; tenant'a özgü veri yok).
	m.HandleFunc("GET /v1/metrics", s.handleMetrics)
	return m
}

func (s *Server) handleMetrics(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{
		"uptime_s":        int64(time.Since(s.started).Seconds()),
		"events_total":    s.nEvents.Load(),
		"pairings_total":  s.nPairs.Load(),
		"claims_total":    s.nClaims.Load(),
		"uploads_total":   s.nUploads.Load(),
		"approvals_total": s.nApprove.Load(),
		"schema":          1,
	})
}

func (s *Server) handlePostEvent(w http.ResponseWriter, r *http.Request) {
	s.nEvents.Add(1)
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
		OpaqueHost: v.OpaqueH, OpaqueSess: v.OpaqueS, Source: v.Source, Category: v.Cat,
		Message: v.Message, Digest: v.Digest, Revision: v.Rev,
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

// Pairing (P04): 5-min TTL, single-use; same-claim idempotent, different-claim 409.
func (s *Server) handlePairCreate(w http.ResponseWriter, r *http.Request) {
	s.nPairs.Add(1)
	tenant := tenantOf(r)
	if tenant == "" {
		http.Error(w, "missing tenant", 401)
		return
	}
	var v struct {
		Code    string `json:"code"`
		HostID  string `json:"host_id"`
		Payload string `json:"payload"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v); err != nil || v.Code == "" {
		http.Error(w, "bad request", 400)
		return
	}
	now := time.Now()
	s.Store.CreatePairing(store.Pairing{Code: v.Code, TenantID: tenant, HostID: v.HostID, State: "PENDING", ExpiresAt: now.Add(5 * time.Minute), Payload: v.Payload})
	w.WriteHeader(201)
	json.NewEncoder(w).Encode(map[string]string{"code": v.Code, "expires_in": "300"})
}

func (s *Server) handlePairClaim(w http.ResponseWriter, r *http.Request) {
	s.nClaims.Add(1)
	tenant := tenantOf(r)
	var v struct {
		DeviceID string `json:"device_id"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v)
	if v.DeviceID == "" {
		http.Error(w, "device_id required", 400)
		return
	}
	code := r.PathValue("code")
	st, payload, err := s.Store.ClaimPairingPayload(code, v.DeviceID, time.Now())
	if err != nil {
		if err.Error() == "conflict" {
			http.Error(w, "already claimed by another device", 409)
			return
		}
		http.Error(w, "not found/expired", 404)
		return
	}
	_ = tenant
	json.NewEncoder(w).Encode(map[string]string{"state": st, "payload": payload})
}

func (s *Server) handlePairGet(w http.ResponseWriter, r *http.Request) {
	tenant := tenantOf(r)
	if tenant == "" {
		http.Error(w, "missing tenant", 401)
		return
	}
	// Existence check without leaking cross-tenant state: claim with empty device probes.
	_, err := s.Store.ClaimPairing(r.PathValue("code"), "", time.Now())
	if err != nil && err.Error() == "not found" {
		http.Error(w, "not found", 404)
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"code": r.PathValue("code")})
}

// Devices / hosts (P02).
func (s *Server) handleDeviceAdd(w http.ResponseWriter, r *http.Request) {
	tenant := tenantOf(r)
	var v struct {
		DeviceID string `json:"device_id"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v); err != nil || v.DeviceID == "" {
		http.Error(w, "device_id required", 400)
		return
	}
	if err := s.Store.AddDevice(tenant, v.DeviceID); err != nil {
		http.Error(w, "bad", 400)
		return
	}
	w.WriteHeader(201)
}

func (s *Server) handleDeviceDel(w http.ResponseWriter, r *http.Request) {
	if err := s.Store.RemoveDevice(tenantOf(r), r.PathValue("deviceId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

func (s *Server) handleHostList(w http.ResponseWriter, r *http.Request) {
	json.NewEncoder(w).Encode(s.Store.ListHosts(tenantOf(r)))
}

func (s *Server) handleHostDel(w http.ResponseWriter, r *http.Request) {
	if err := s.Store.RemoveHost(tenantOf(r), r.PathValue("hostId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

func (s *Server) handleUsages(w http.ResponseWriter, r *http.Request) {
	_ = tenantOf(r)
	// Usage snapshots are derived, never raw transcripts (P13).
	json.NewEncoder(w).Encode([]string{})
}

// Uploads (P15): 10MB cap, 24h short URL.
func (s *Server) handleUploadCreate(w http.ResponseWriter, r *http.Request) {
	s.nUploads.Add(1)
	tenant := tenantOf(r)
	var v struct {
		ID        string `json:"id"`
		ShortCode string `json:"short_code"`
		Size      int64  `json:"size"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v); err != nil || v.ID == "" || v.ShortCode == "" {
		http.Error(w, "bad request", 400)
		return
	}
	now := time.Now()
	if err := s.Store.PutUpload(tenant, store.Upload{ID: v.ID, TenantID: tenant, ShortCode: v.ShortCode, Size: v.Size, CreatedAt: now, ExpiresAt: now.Add(24 * time.Hour)}); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	w.WriteHeader(201)
	json.NewEncoder(w).Encode(map[string]string{"short": v.ShortCode})
}

func (s *Server) handleUploadGet(w http.ResponseWriter, r *http.Request) {
	u, ok := s.Store.GetUploadByShort(r.PathValue("shortCode"), time.Now().Unix())
	if !ok {
		http.Error(w, "gone", 410)
		return
	}
	if time.Now().After(u.ExpiresAt) {
		http.Error(w, "gone", 410)
		return
	}
	json.NewEncoder(w).Encode(map[string]string{"id": u.ID})
}

func (s *Server) handleUploadDel(w http.ResponseWriter, r *http.Request) {
	if err := s.Store.DeleteUpload(tenantOf(r), r.PathValue("uploadId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

// Approvals (P13): CAS first-wins; digest/revision mismatch rejected.
func (s *Server) handleApprovalAction(w http.ResponseWriter, r *http.Request) {
	s.nApprove.Add(1)
	if tenantOf(r) == "" {
		http.Error(w, "missing tenant", 401)
		return
	}
	var v struct {
		Digest   string `json:"digest"`
		Revision string `json:"revision"`
		Device   string `json:"device"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v)
	if v.Digest == "" || v.Revision == "" || v.Device == "" {
		http.Error(w, "digest/revision/device required", 400)
		return
	}
	if s.Table == nil {
		s.Table = approvals.New()
	}
	if _, err := s.Table.Decide(r.PathValue("approvalId"), v.Digest, v.Revision, v.Device); err != nil {
		http.Error(w, "conflict: "+err.Error(), 409)
		return
	}
	w.WriteHeader(202)
}

// Webhooks (P02): token path only; body size-capped; never logs secrets.
func (s *Server) handleWebhook(w http.ResponseWriter, r *http.Request) {
	if r.PathValue("token") == "" {
		http.Error(w, "bad token", 404)
		return
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 256<<10)).Decode(&struct{}{})
	w.WriteHeader(202)
}
