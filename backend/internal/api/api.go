// SPDX-License-Identifier: GPL-3.0-or-later
// Package api: stdlib HTTP surface. Every tenant-scoped route requires a
// bearer token; the tenant comes from the token record, never from the
// client. Tokens are minted at host registration and device pairing and are
// stored hash-only.
package api

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"golang.org/x/crypto/ssh"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
	"github.com/pocket-agent/pocket-agent/backend/internal/store"
	pocketprotocol "github.com/pocket-agent/pocket-agent/protocol"
)

const pairingTTL = 5 * time.Minute

type Server struct {
	Store store.Backend
	// EnrollToken, when set, is required to register a new host (and thus
	// create a tenant). Empty means open registration.
	EnrollToken string
	// TrustProxy: take the client IP from X-Forwarded-For (behind Caddy).
	TrustProxy bool

	started  time.Time
	limiter  limiter
	nEvents  atomic.Int64
	nPairs   atomic.Int64
	nClaims  atomic.Int64
	nUploads atomic.Int64
	nApprove atomic.Int64
}

// New builds the handler with shared store.
func New(st store.Backend) *Server {
	return &Server{Store: st, started: time.Now()}
}

// Handler exposes routes for cmd/server.
func (s *Server) Handler() http.Handler { return s.routes() }

type principal struct {
	Tenant, Kind, Subject string
}

func bearer(r *http.Request) string {
	h := r.Header.Get("Authorization")
	if len(h) < 7 || !strings.EqualFold(h[:7], "bearer ") {
		return ""
	}
	return strings.TrimSpace(h[7:])
}

func (s *Server) principalOf(r *http.Request) (principal, bool) {
	tok := bearer(r)
	if tok == "" {
		return principal{}, false
	}
	t, ok := s.Store.LookupToken(auth.Hash(tok))
	if !ok {
		return principal{}, false
	}
	return principal{Tenant: t.TenantID, Kind: t.Kind, Subject: t.SubjectID}, true
}

// authed wraps a handler that needs a valid token of one of kinds.
func (s *Server) authed(h func(http.ResponseWriter, *http.Request, principal), kinds ...string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		p, ok := s.principalOf(r)
		if !ok {
			if !s.limiter.allow(s.clientIP(r), time.Now()) {
				http.Error(w, "too many requests", 429)
				return
			}
			w.Header().Set("WWW-Authenticate", `Bearer realm="pocket-agent"`)
			http.Error(w, "unauthorized", 401)
			return
		}
		for _, k := range kinds {
			if p.Kind == k {
				h(w, r, p)
				return
			}
		}
		http.Error(w, "forbidden for this token", 403)
	}
}

func (s *Server) routes() *http.ServeMux {
	m := http.NewServeMux()
	any := []string{store.KindHost, store.KindDevice}
	m.HandleFunc("GET /v1/healthz", func(w http.ResponseWriter, _ *http.Request) {
		w.Write([]byte("ok"))
	})
	m.HandleFunc("POST /v1/hosts/register", s.handleHostRegister)
	m.HandleFunc("POST /v1/hosts/{hostId}/events", s.authed(s.handlePostEvent, store.KindHost))
	m.HandleFunc("GET /v1/events", s.authed(s.handleGetEvents, any...))
	m.HandleFunc("GET /v1/whoami", s.authed(s.handleWhoami, any...))
	m.HandleFunc("POST /v1/pairing-sessions", s.authed(s.handlePairCreate, store.KindHost))
	m.HandleFunc("POST /v1/pairing-sessions/{code}/claim", s.handlePairClaim)
	m.HandleFunc("GET /v1/pairing-sessions/{code}", s.authed(s.handlePairGet, any...))
	m.HandleFunc("POST /v1/pairing-sessions/{code}/complete", s.authed(s.handlePairComplete, store.KindHost))
	m.HandleFunc("GET /v1/devices", s.authed(s.handleDeviceList, any...))
	m.HandleFunc("DELETE /v1/devices/{deviceId}", s.authed(s.handleDeviceDel, any...))
	m.HandleFunc("GET /v1/hosts", s.authed(s.handleHostList, any...))
	m.HandleFunc("DELETE /v1/hosts/{hostId}", s.authed(s.handleHostDel, any...))
	m.HandleFunc("GET /v1/usages", s.authed(s.handleUsages, any...))
	m.HandleFunc("POST /v1/uploads", s.authed(s.handleUploadCreate, any...))
	m.HandleFunc("GET /i/{shortCode}", s.handleUploadGet)
	m.HandleFunc("DELETE /v1/uploads/{uploadId}", s.authed(s.handleUploadDel, any...))
	m.HandleFunc("POST /v1/approvals/{approvalId}/actions", s.authed(s.handleApprovalAction, store.KindDevice))
	m.HandleFunc("POST /v1/webhooks/{token}", s.handleWebhook)
	// /v1/metrics: basit operasyon sayaçları — gözlemlenebilirlik, auth istemez
	// (yalnızca toplamlar; tenant'a özgü veri yok).
	m.HandleFunc("GET /v1/metrics", s.handleMetrics)
	return m
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func randomID(prefix string) string {
	b := make([]byte, 12)
	if _, err := rand.Read(b); err != nil {
		panic(err)
	}
	return prefix + hex.EncodeToString(b)
}

func (s *Server) handleMetrics(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, 200, map[string]any{
		"uptime_s":        int64(time.Since(s.started).Seconds()),
		"events_total":    s.nEvents.Load(),
		"pairings_total":  s.nPairs.Load(),
		"claims_total":    s.nClaims.Load(),
		"uploads_total":   s.nUploads.Load(),
		"approvals_total": s.nApprove.Load(),
		"schema":          2,
	})
}

// Host registration creates a tenant and returns its first host token (once).
func (s *Server) handleHostRegister(w http.ResponseWriter, r *http.Request) {
	if !s.limiter.allow(s.clientIP(r), time.Now()) {
		http.Error(w, "too many requests", 429)
		return
	}
	var v struct {
		HostID      string `json:"host_id"`
		Label       string `json:"label"`
		EnrollToken string `json:"enroll_token"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 8<<10)).Decode(&v); err != nil || v.HostID == "" || len(v.HostID) > 128 || len(v.Label) > 128 {
		http.Error(w, "host_id required", 400)
		return
	}
	if s.EnrollToken != "" && subtle.ConstantTimeCompare([]byte(v.EnrollToken), []byte(s.EnrollToken)) != 1 {
		http.Error(w, "enrollment token required", 403)
		return
	}
	tenant := randomID("tenant:")
	tok, hash, err := auth.Mint("pah")
	if err == nil {
		err = s.Store.CreateTenant(tenant)
	}
	if err == nil {
		err = s.Store.AddHost(tenant, v.HostID, v.Label)
	}
	if err == nil {
		err = s.Store.PutToken(store.Token{Hash: hash, TenantID: tenant, Kind: store.KindHost, SubjectID: v.HostID})
	}
	if err != nil {
		http.Error(w, "storage error", 500)
		return
	}
	writeJSON(w, 201, map[string]string{"tenant_id": tenant, "host_token": tok})
}

func (s *Server) handleWhoami(w http.ResponseWriter, _ *http.Request, p principal) {
	writeJSON(w, 200, map[string]string{"tenant_id": p.Tenant, "kind": p.Kind, "subject": p.Subject})
}

func (s *Server) handlePostEvent(w http.ResponseWriter, r *http.Request, p principal) {
	s.nEvents.Add(1)
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
	if err := s.Store.PutEvent(p.Tenant, store.Event{
		EventID: v.EventID, TenantID: p.Tenant, CreatedAt: created, ExpiresAt: expires,
		OpaqueHost: v.OpaqueH, OpaqueSess: v.OpaqueS, Source: v.Source, Category: v.Cat,
		Message: v.Message, Digest: v.Digest, Revision: v.Rev,
	}); err != nil {
		http.Error(w, "storage error", 500)
		return
	}
	w.WriteHeader(202)
}

func (s *Server) handleGetEvents(w http.ResponseWriter, r *http.Request, p principal) {
	evs := s.Store.GetEvents(p.Tenant, r.URL.Query().Get("after"))
	out := []store.Event{}
	for _, e := range evs {
		if e.TenantID == p.Tenant && strings.HasPrefix(e.EventID, "event:") {
			out = append(out, e)
		}
	}
	writeJSON(w, 200, out)
}

// NormalizeCode accepts "abcd-efgh", "ABCD EFGH", ... → "ABCDEFGH".
func NormalizeCode(c string) string {
	c = strings.ToUpper(c)
	return strings.Map(func(r rune) rune {
		if r == '-' || r == ' ' {
			return -1
		}
		return r
	}, c)
}

// payloadSafe: the pairing payload is a connection hint only. Anything that
// looks like key material is refused so it can never be persisted.
func payloadSafe(payload string) bool {
	if payload == "" {
		return true
	}
	var m map[string]any
	if json.Unmarshal([]byte(payload), &m) != nil {
		return false
	}
	low := strings.ToLower(payload)
	return !strings.Contains(low, "private") && !strings.Contains(low, "-----begin")
}

// Pairing (P04): host opens a 5-min single-use code; the device claims it
// with its own public key; the host authorizes that key and completes.
func (s *Server) handlePairCreate(w http.ResponseWriter, r *http.Request, p principal) {
	s.nPairs.Add(1)
	var v struct {
		Code    string `json:"code"`
		Payload string `json:"payload"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16<<10)).Decode(&v); err != nil {
		http.Error(w, "bad request", 400)
		return
	}
	code := NormalizeCode(v.Code)
	if len(code) < 8 || len(code) > 16 {
		http.Error(w, "code must be 8-16 characters", 400)
		return
	}
	if !payloadSafe(v.Payload) {
		http.Error(w, "payload must be a JSON connection hint without key material", 400)
		return
	}
	if err := s.Store.CreatePairing(store.Pairing{Code: code, TenantID: p.Tenant, HostID: p.Subject, State: "PENDING", ExpiresAt: time.Now().Add(pairingTTL), Payload: v.Payload}); err != nil {
		if errors.Is(err, store.ErrConflict) {
			http.Error(w, "code in use", 409)
			return
		}
		http.Error(w, "storage error", 500)
		return
	}
	writeJSON(w, 201, map[string]any{"code": code, "expires_in": int(pairingTTL.Seconds())})
}

func (s *Server) handlePairClaim(w http.ResponseWriter, r *http.Request) {
	s.nClaims.Add(1)
	// Unauthenticated by design (the code is the secret): rate-limited per IP
	// so the 31^8 code space cannot be walked.
	if !s.limiter.allow(s.clientIP(r), time.Now()) {
		http.Error(w, "too many requests", 429)
		return
	}
	var v struct {
		DeviceID   string `json:"device_id"`
		DeviceName string `json:"device_name"`
		PublicKey  string `json:"public_key"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 16<<10)).Decode(&v); err != nil || v.DeviceID == "" || len(v.DeviceID) > 128 || len(v.DeviceName) > 128 {
		http.Error(w, "device_id required", 400)
		return
	}
	pub, err := normalizePublicKey(v.PublicKey)
	if err != nil {
		http.Error(w, "public_key must be an OpenSSH ed25519/ecdsa/rsa public key", 400)
		return
	}
	tok, hash, err := auth.Mint("pad")
	if err != nil {
		http.Error(w, "internal error", 500)
		return
	}
	pr, err := s.Store.ClaimPairing(NormalizeCode(r.PathValue("code")), store.Claim{DeviceID: v.DeviceID, Label: v.DeviceName, PublicKey: pub, TokenHash: hash}, time.Now())
	switch {
	case errors.Is(err, store.ErrConflict):
		http.Error(w, "already claimed by another device", 409)
		return
	case err != nil:
		s.limiter.penalize(s.clientIP(r), time.Now())
		http.Error(w, "not found/expired", 404)
		return
	}
	writeJSON(w, 200, map[string]any{
		"state": pr.State, "payload": pr.Payload, "device_token": tok,
		"tenant_id": pr.TenantID, "host_id": pr.HostID,
		"expires_in": int(time.Until(pr.ExpiresAt).Seconds()),
	})
}

func normalizePublicKey(s string) (string, error) {
	if len(s) > 8<<10 {
		return "", errors.New("too long")
	}
	k, _, _, _, err := ssh.ParseAuthorizedKey([]byte(s))
	if err != nil {
		return "", err
	}
	switch k.Type() {
	case ssh.KeyAlgoED25519, ssh.KeyAlgoECDSA256, ssh.KeyAlgoECDSA384, ssh.KeyAlgoECDSA521, ssh.KeyAlgoRSA:
	default:
		return "", errors.New("unsupported key type")
	}
	return strings.TrimSpace(string(ssh.MarshalAuthorizedKey(k))), nil
}

func (s *Server) handlePairGet(w http.ResponseWriter, r *http.Request, p principal) {
	pr, ok := s.Store.GetPairing(NormalizeCode(r.PathValue("code")))
	state := pr.State
	if ok && state == "PENDING" && time.Now().After(pr.ExpiresAt) {
		state = "EXPIRED"
	}
	switch {
	case ok && p.Kind == store.KindHost && pr.TenantID == p.Tenant:
		writeJSON(w, 200, map[string]any{
			"code": pr.Code, "state": state, "device_id": pr.ClaimedBy,
			"device_name": pr.DeviceLabel, "public_key": pr.DevicePublicKey,
			"expires_in": int(time.Until(pr.ExpiresAt).Seconds()),
		})
	case ok && p.Kind == store.KindDevice && pr.TenantID == p.Tenant && pr.ClaimedBy == p.Subject:
		writeJSON(w, 200, map[string]any{"code": pr.Code, "state": state})
	default:
		http.Error(w, "not found", 404) // no cross-tenant existence oracle
	}
}

func (s *Server) handlePairComplete(w http.ResponseWriter, r *http.Request, p principal) {
	var v struct {
		Approved bool `json:"approved"`
	}
	if err := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4<<10)).Decode(&v); err != nil {
		http.Error(w, "bad request", 400)
		return
	}
	err := s.Store.FinishPairing(p.Tenant, NormalizeCode(r.PathValue("code")), v.Approved)
	switch {
	case errors.Is(err, store.ErrNotFound):
		http.Error(w, "not found", 404)
	case errors.Is(err, store.ErrConflict):
		http.Error(w, "pairing is not awaiting approval", 409)
	case err != nil:
		http.Error(w, "storage error", 500)
	default:
		state := "REJECTED"
		if v.Approved {
			state = "CONSUMED"
		}
		writeJSON(w, 200, map[string]string{"state": state})
	}
}

func (s *Server) handleDeviceList(w http.ResponseWriter, _ *http.Request, p principal) {
	writeJSON(w, 200, s.Store.ListDevices(p.Tenant))
}

func (s *Server) handleDeviceDel(w http.ResponseWriter, r *http.Request, p principal) {
	if err := s.Store.RemoveDevice(p.Tenant, r.PathValue("deviceId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

func (s *Server) handleHostList(w http.ResponseWriter, _ *http.Request, p principal) {
	writeJSON(w, 200, s.Store.ListHosts(p.Tenant))
}

func (s *Server) handleHostDel(w http.ResponseWriter, r *http.Request, p principal) {
	if err := s.Store.RemoveHost(p.Tenant, r.PathValue("hostId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

func (s *Server) handleUsages(w http.ResponseWriter, _ *http.Request, _ principal) {
	// Usage snapshots are derived, never raw transcripts (P13).
	writeJSON(w, 200, []string{})
}

// Uploads (P15): 10MB cap, 24h short URL.
func (s *Server) handleUploadCreate(w http.ResponseWriter, r *http.Request, p principal) {
	s.nUploads.Add(1)
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
	if err := s.Store.PutUpload(p.Tenant, store.Upload{ID: v.ID, TenantID: p.Tenant, ShortCode: v.ShortCode, Size: v.Size, CreatedAt: now, ExpiresAt: now.Add(24 * time.Hour)}); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	writeJSON(w, 201, map[string]string{"short": v.ShortCode})
}

func (s *Server) handleUploadGet(w http.ResponseWriter, r *http.Request) {
	u, ok := s.Store.GetUploadByShort(r.PathValue("shortCode"), time.Now())
	if !ok {
		http.Error(w, "gone", 410)
		return
	}
	writeJSON(w, 200, map[string]string{"id": u.ID})
}

func (s *Server) handleUploadDel(w http.ResponseWriter, r *http.Request, p principal) {
	if err := s.Store.DeleteUpload(p.Tenant, r.PathValue("uploadId")); err != nil {
		http.Error(w, "not found", 404)
		return
	}
	w.WriteHeader(204)
}

// Approvals (P13): CAS first-wins; digest/revision mismatch rejected. The
// deciding device is the token's subject, not a body field.
func (s *Server) handleApprovalAction(w http.ResponseWriter, r *http.Request, p principal) {
	s.nApprove.Add(1)
	var v struct {
		Digest   string `json:"digest"`
		Revision string `json:"revision"`
	}
	_ = json.NewDecoder(http.MaxBytesReader(w, r.Body, 64<<10)).Decode(&v)
	if v.Digest == "" || v.Revision == "" {
		http.Error(w, "digest/revision required", 400)
		return
	}
	err := s.Store.Decide(p.Tenant, r.PathValue("approvalId"), v.Digest, v.Revision, p.Subject)
	switch {
	case errors.Is(err, store.ErrNotFound):
		http.Error(w, "not found", 404)
	case err != nil:
		http.Error(w, "conflict: "+err.Error(), 409)
	default:
		w.WriteHeader(202)
	}
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

func (s *Server) clientIP(r *http.Request) string {
	if s.TrustProxy {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			return strings.TrimSpace(strings.Split(xff, ",")[0])
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// limiter: fixed one-minute window per client IP for unauthenticated
// surfaces (claim, register, bad tokens). Failed claims cost extra.
type limiter struct {
	mu      sync.Mutex
	window  time.Time
	counts  map[string]int
	Limit   int
	Penalty int
}

const (
	defaultLimit   = 30
	defaultPenalty = 3
)

func (l *limiter) bump(ip string, now time.Time, n int) int {
	l.mu.Lock()
	defer l.mu.Unlock()
	if l.counts == nil || now.Sub(l.window) >= time.Minute {
		l.counts, l.window = map[string]int{}, now
	}
	l.counts[ip] += n
	return l.counts[ip]
}

func (l *limiter) allow(ip string, now time.Time) bool {
	limit := l.Limit
	if limit == 0 {
		limit = defaultLimit
	}
	return l.bump(ip, now, 1) <= limit
}

func (l *limiter) penalize(ip string, now time.Time) {
	p := l.Penalty
	if p == 0 {
		p = defaultPenalty
	}
	l.bump(ip, now, p)
}
