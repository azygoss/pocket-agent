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

// Regresyon: event kimlikleri hash (sıralı değil) ve map sırası rastgele.
// İstemci son gelen kimliği imleç yapınca, kimliği imleçten "küçük" yeni
// olaylar hiç dönmüyordu (onaylar uygulama yeniden açılana dek görünmüyordu).
// İmleç artık ekleme sırasını izler; liste eklenme sırasıyla döner.
func TestEventsAfterFollowsInsertionOrder(t *testing.T) {
	s := New()
	now := time.Now()
	put := func(id string) {
		_ = s.PutEvent("t1", Event{EventID: id, TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	}
	put("event:f000")
	put("event:9000")
	first := s.GetEvents("t1", "")
	if len(first) != 2 || first[0].EventID != "event:f000" || first[1].EventID != "event:9000" {
		t.Fatalf("eklenme sırası bekleniyordu: %v", first)
	}
	cursor := first[len(first)-1].EventID
	put("event:0001") // imleçten "küçük" kimlik
	put("event:ffff")
	next := s.GetEvents("t1", cursor)
	if len(next) != 2 || next[0].EventID != "event:0001" || next[1].EventID != "event:ffff" {
		t.Fatalf("imleçten sonra eklenen iki olay bekleniyordu: %v", next)
	}
	// Aynı olayın tekrar gönderilmesi (idempotent) sırasını değiştirmez.
	put("event:f000")
	if got := s.GetEvents("t1", "event:ffff"); len(got) != 0 {
		t.Fatalf("tekrar gönderim yeni olay sayılmamalı: %v", got)
	}
	// Bilinmeyen/süresi dolmuş imleç: hepsi döner (istemci kimlikle ayıklar).
	if got := s.GetEvents("t1", "event:gone"); len(got) != 4 {
		t.Fatalf("bilinmeyen imleçte tümü bekleniyordu, %d geldi", len(got))
	}
}
