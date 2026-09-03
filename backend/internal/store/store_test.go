// SPDX-License-Identifier: GPL-3.0-or-later
package store

import (
	"testing"
	"time"
)

func TestTenantIsolation(t *testing.T) {
	s := New()
	now := time.Now()
	_ = s.PutEvent("t1", Event{EventID: "event:1", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	if err := s.PutEvent("t2", Event{EventID: "event:2", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)}); err == nil {
		t.Fatal("cross-tenant write must fail")
	}
	if got := s.GetEvents("t2", ""); len(got) != 0 {
		t.Fatalf("tenant t2 must see 0 events, saw %d", len(got))
	}
	if got := s.GetEvents("t1", ""); len(got) != 1 {
		t.Fatalf("tenant t1 must see 1, saw %d", len(got))
	}
}

func TestTTLSweep(t *testing.T) {
	s := New()
	now := time.Now()
	_ = s.PutEvent("t1", Event{EventID: "event:old", TenantID: "t1", CreatedAt: now.Add(-25 * time.Hour), ExpiresAt: now.Add(-time.Hour)})
	_ = s.PutEvent("t1", Event{EventID: "event:new", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	ev, _ := s.SweepTTL(now)
	if ev != 1 {
		t.Fatalf("want 1 swept, got %d", ev)
	}
	if got := s.GetEvents("t1", ""); len(got) != 1 {
		t.Fatal("one event must remain")
	}
}

func TestPairingRace(t *testing.T) {
	s := New()
	now := time.Now()
	s.CreatePairing(Pairing{Code: "ABC123", TenantID: "t1", HostID: "host:x", State: "PENDING", ExpiresAt: now.Add(5 * time.Minute)})
	if _, err := s.ClaimPairing("ABC123", "device:1", now); err != nil {
		t.Fatal(err)
	}
	// same claim idempotent
	if _, err := s.ClaimPairing("ABC123", "device:1", now); err != nil {
		t.Fatal("same claim must be idempotent")
	}
	// different claim -> conflict
	if _, err := s.ClaimPairing("ABC123", "device:2", now); err == nil {
		t.Fatal("different second claim must be rejected")
	}
}

func TestWipeUser(t *testing.T) {
	s := New()
	now := time.Now()
	s.AddUser("u1", "t1")
	_ = s.PutEvent("t1", Event{EventID: "event:1", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	s.WipeUser("u1")
	if got := s.GetEvents("t1", ""); len(got) != 0 {
		t.Fatal("wipe must remove events")
	}
}
