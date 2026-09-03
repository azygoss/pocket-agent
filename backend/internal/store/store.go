// SPDX-License-Identifier: GPL-3.0-or-later
// Package store: in-memory tenant-isolated store (PostgreSQL in compose; same guard logic).
package store

import (
	"sync"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
)

type Event struct {
	EventID     string
	TenantID    string
	OpaqueHost  string
	OpaqueSess  string
	Source      string
	Category    string
	Message     string
	Digest      string
	Revision    string
	CreatedAt   time.Time
	ExpiresAt   time.Time
}

type Pairing struct {
	Code       string
	TenantID   string
	HostID     string
	SecretHash string
	State      string // PENDING|CLAIMED|CONSUMED|EXPIRED
	ClaimedBy  string
	ExpiresAt  time.Time
}

type Store struct {
	mu       sync.Mutex
	events   map[string]Event
	pairings map[string]Pairing
	users    map[string]string // userID -> tenantID
}

func New() *Store {
	return &Store{events: map[string]Event{}, pairings: map[string]Pairing{}, users: map[string]string{}}
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

// SweepTTL deletes expired events/pairings; returns counts.
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

func (s *Store) CreatePairing(p Pairing) { s.mu.Lock(); defer s.mu.Unlock(); s.pairings[p.Code] = p }

type storeErr string

func (e storeErr) Error() string { return string(e) }
func errNotFound() error         { return storeErr("not found") }
func errExpired() error          { return storeErr("expired") }
func errConflict() error         { return storeErr("conflict") }
