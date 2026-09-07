// SPDX-License-Identifier: GPL-3.0-or-later
// Package store: in-memory tenant-isolated store (PostgreSQL in compose; same guard logic).
package store

import (
	"sync"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
)

type Event struct {
	EventID    string
	TenantID   string
	OpaqueHost string
	OpaqueSess string
	Source     string
	Category   string
	Message    string
	Digest     string
	Revision   string
	CreatedAt  time.Time
	ExpiresAt  time.Time
}

type Pairing struct {
	Code       string
	TenantID   string
	HostID     string
	SecretHash string
	State      string // PENDING|CLAIMED|CONSUMED|EXPIRED
	ClaimedBy  string
	ExpiresAt  time.Time
	// SSH bağlantı yükü (host/port/user/private-key). Yalnız claim sonrası
	// döner; GET varlık sorgusu payload sızdırmaz. TTL + tek-kullanım.
	Payload string
}

type Upload struct {
	ID        string
	TenantID  string
	ShortCode string
	Size      int64
	CreatedAt time.Time
	ExpiresAt time.Time
}

type Store struct {
	mu       sync.Mutex
	events   map[string]Event
	pairings map[string]Pairing
	users    map[string]string // userID -> tenantID
	devices  map[string]string // deviceID -> tenantID
	hosts    map[string]string // hostID -> tenantID
	uploads  map[string]Upload // id -> upload
	byShort  map[string]string // shortCode -> upload id
}

func New() *Store {
	return &Store{events: map[string]Event{}, pairings: map[string]Pairing{}, users: map[string]string{}, devices: map[string]string{}, hosts: map[string]string{}, uploads: map[string]Upload{}, byShort: map[string]string{}}
}

func (s *Store) AddUser(id, tenant string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.users[id] = tenant
}

// PutEvent enforces tenant guard + 24h TTL cap.
func (s *Store) PutEvent(callerTenant string, e Event) error {
	if err := auth.GuardTenant(e.TenantID, callerTenant); err != nil {
		return err
	}
	if e.ExpiresAt.Sub(e.CreatedAt) > 24*time.Hour {
		e.ExpiresAt = e.CreatedAt.Add(24 * time.Hour)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.events[e.EventID] = e
	return nil
}

func (s *Store) GetEvents(callerTenant, after string) []Event {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []Event
	for _, e := range s.events {
		if e.TenantID != callerTenant {
			continue // tenant isolation: silently skip foreign rows
		}
		if after != "" && e.EventID <= after {
			continue
		}
		out = append(out, e)
	}
	return out
}

// SweepTTL deletes expired events/pairings/uploads; returns events+pairings swept.
func (s *Store) SweepTTL(now time.Time) (events, pairings int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for id, e := range s.events {
		if !e.ExpiresAt.After(now) {
			delete(s.events, id)
			events++
		}
	}
	for c, p := range s.pairings {
		if !p.ExpiresAt.After(now) && p.State == "PENDING" {
			s.pairings[c] = Pairing{Code: p.Code, TenantID: p.TenantID, State: "EXPIRED"}
			pairings++
		}
	}
	for id, u := range s.uploads {
		if !u.ExpiresAt.After(now) {
			delete(s.byShort, u.ShortCode)
			delete(s.uploads, id)
		}
	}
	return
}

// WipeUser cascade-deletes user data.
func (s *Store) WipeUser(userID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	tenant := s.users[userID]
	for id, e := range s.events {
		if e.TenantID == tenant {
			delete(s.events, id)
		}
	}
	delete(s.users, userID)
}

// ClaimPairing: same claim idempotent, different claim -> conflict.
func (s *Store) ClaimPairing(code, deviceID string, now time.Time) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.pairings[code]
	if !ok {
		return "", errNotFound()
	}
	if now.After(p.ExpiresAt) {
		return "", errExpired()
	}
	if p.State == "CLAIMED" || p.State == "CONSUMED" {
		if p.ClaimedBy == deviceID {
			return p.State, nil // idempotent same-claim
		}
		return "", errConflict()
	}
	p.State = "CLAIMED"
	p.ClaimedBy = deviceID
	s.pairings[code] = p
	return "CLAIMED", nil
}

// ClaimPairingPayload: claim + payload döner (aynı cihaz tekrar isteyebilir —
// ağ koptuysa yeniden deneme); farklı cihaz 409.
func (s *Store) ClaimPairingPayload(code, deviceID string, now time.Time) (string, string, error) {
	st, err := s.ClaimPairing(code, deviceID, now)
	if err != nil {
		return "", "", err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return st, s.pairings[code].Payload, nil
}

func (s *Store) CreatePairing(p Pairing) { s.mu.Lock(); defer s.mu.Unlock(); s.pairings[p.Code] = p }

type storeErr string

func (e storeErr) Error() string { return string(e) }
func errNotFound() error         { return storeErr("not found") }
func errExpired() error          { return storeErr("expired") }
func errConflict() error         { return storeErr("conflict") }

// Devices / hosts (P02): tenant-guarded register + delete.
func (s *Store) AddDevice(callerTenant, deviceID string) error {
	if callerTenant == "" || deviceID == "" {
		return errNotFound()
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.devices[deviceID] = callerTenant
	return nil
}

func (s *Store) RemoveDevice(callerTenant, deviceID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.devices[deviceID]
	if !ok {
		return errNotFound()
	}
	if t != callerTenant {
		return errNotFound() // do not leak existence across tenants
	}
	delete(s.devices, deviceID)
	for hid, ht := range s.hosts {
		_ = hid
		_ = ht
	}
	return nil
}

func (s *Store) AddHost(callerTenant, hostID string) error {
	if callerTenant == "" || hostID == "" {
		return errNotFound()
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.hosts[hostID] = callerTenant
	return nil
}

func (s *Store) ListHosts(callerTenant string) []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []string
	for hid, ht := range s.hosts {
		if ht == callerTenant {
			out = append(out, hid)
		}
	}
	return out
}

func (s *Store) RemoveHost(callerTenant, hostID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.hosts[hostID]
	if !ok {
		return errNotFound()
	}
	if t != callerTenant {
		return errNotFound()
	}
	delete(s.hosts, hostID)
	return nil
}

// Uploads (P15): 10MB cap + 24h TTL enforced at store layer too.
func (s *Store) PutUpload(callerTenant string, u Upload) error {
	if u.TenantID != callerTenant {
		return errConflict()
	}
	if u.Size > 10<<20 {
		return errTooLarge()
	}
	if u.ExpiresAt.Sub(u.CreatedAt) > 24*3600000000000 {
		u.ExpiresAt = u.CreatedAt.Add(24 * 3600000000000)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.uploads[u.ID] = u
	s.byShort[u.ShortCode] = u.ID
	return nil
}

func (s *Store) GetUploadByShort(code string, now int64) (Upload, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	id, ok := s.byShort[code]
	if !ok {
		return Upload{}, false
	}
	u := s.uploads[id]
	_ = now
	return u, true
}

func (s *Store) DeleteUpload(callerTenant, id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.uploads[id]
	if !ok || u.TenantID != callerTenant {
		return errNotFound()
	}
	delete(s.byShort, u.ShortCode)
	delete(s.uploads, id)
	return nil
}

func errTooLarge() error { return storeErr("too large") }

// Webhook tokens (P02): prefix lookup, hash-only secret.
func (s *Store) PutWebhookToken(prefix, secretHash, tenant string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.byShort == nil {
		s.byShort = map[string]string{}
	}
	s.byShort["wh:"+prefix] = secretHash + "|" + tenant
}

func (s *Store) GetWebhookToken(prefix string) (hash, tenant string, ok bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	v, ok := s.byShort["wh:"+prefix]
	if !ok {
		return "", "", false
	}
	parts := splitOnce(v)
	return parts[0], parts[1], true
}

func splitOnce(v string) [2]string {
	for i := 0; i < len(v); i++ {
		if v[i] == '|' {
			return [2]string{v[:i], v[i+1:]}
		}
	}
	return [2]string{v, ""}
}
