// SPDX-License-Identifier: GPL-3.0-or-later
package store

import (
	"context"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"
)

// backends runs a conformance test against Memory and, when
// PA_TEST_DATABASE_URL is set, against a fresh Postgres schema too.
func backends(t *testing.T, fn func(t *testing.T, s Backend)) {
	t.Run("memory", func(t *testing.T) { fn(t, New()) })
	url := os.Getenv("PA_TEST_DATABASE_URL")
	if url == "" {
		return
	}
	t.Run("postgres", func(t *testing.T) {
		pg := freshPG(t, url)
		fn(t, pg)
	})
}

func freshPG(t *testing.T, url string) *PG {
	t.Helper()
	ctx := context.Background()
	pg, err := OpenPostgres(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	// The schema is wiped per test: refuse anything but a test database.
	var db string
	if err := pg.pool.QueryRow(ctx, `SELECT current_database()`).Scan(&db); err != nil || !strings.Contains(db, "test") {
		t.Fatalf("PA_TEST_DATABASE_URL must point at a disposable *test* database (got %q)", db)
	}
	if _, err := pg.pool.Exec(ctx, `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`); err != nil {
		t.Fatal(err)
	}
	pg.Close()
	pg, err = OpenPostgres(ctx, url)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(pg.Close)
	return pg
}

func seedTenants(t *testing.T, s Backend, ids ...string) {
	for _, id := range ids {
		if err := s.CreateTenant(id); err != nil {
			t.Fatal(err)
		}
	}
}

func TestTenantIsolation(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1", "t2")
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
	})
}

func TestTTLSweep(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1")
		now := time.Now()
		_ = s.PutEvent("t1", Event{EventID: "event:old", TenantID: "t1", CreatedAt: now.Add(-25 * time.Hour), ExpiresAt: now.Add(-time.Hour)})
		_ = s.PutEvent("t1", Event{EventID: "event:new", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(48 * time.Hour)})
		ev, _ := s.SweepTTL(now)
		if ev != 1 {
			t.Fatalf("want 1 swept, got %d", ev)
		}
		got := s.GetEvents("t1", "")
		if len(got) != 1 {
			t.Fatal("one event must remain")
		}
		if got[0].ExpiresAt.Sub(got[0].CreatedAt) > 24*time.Hour+time.Second {
			t.Fatal("TTL must be capped at 24h")
		}
	})
}

func claim(dev string) Claim {
	return Claim{DeviceID: dev, Label: "Phone " + dev, PublicKey: "ssh-ed25519 AAAA" + dev, TokenHash: "sha256:" + dev + fmt.Sprint(time.Now().UnixNano())}
}

func TestPairingRace(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1")
		now := time.Now()
		if err := s.CreatePairing(Pairing{Code: "ABCD2345", TenantID: "t1", HostID: "host:x", ExpiresAt: now.Add(5 * time.Minute), Payload: `{"ssh_user":"u"}`}); err != nil {
			t.Fatal(err)
		}
		if err := s.CreatePairing(Pairing{Code: "ABCD2345", TenantID: "t1", HostID: "host:y", ExpiresAt: now.Add(5 * time.Minute)}); err != ErrConflict {
			t.Fatalf("live code must not be overwritten: %v", err)
		}
		c1 := claim("device:1")
		p, err := s.ClaimPairing("ABCD2345", c1, now)
		if err != nil || p.State != "CLAIMED" || p.Payload == "" {
			t.Fatalf("claim: %+v %v", p, err)
		}
		if _, ok := s.LookupToken(c1.TokenHash); ok {
			t.Fatal("claim token must not authenticate before host approval")
		}
		if len(s.ListDevices("t1")) != 0 {
			t.Fatal("claim must not enroll a device before approval")
		}
		// same device retry: idempotent, pending token replaced
		c1b := claim("device:1")
		if p, err := s.ClaimPairing("ABCD2345", c1b, now); err != nil || p.TokenHash != c1b.TokenHash {
			t.Fatal("same claim must be idempotent")
		}
		if _, err := s.ClaimPairing("ABCD2345", claim("device:2"), now); err != ErrConflict {
			t.Fatalf("different second claim must conflict: %v", err)
		}
		if err := s.FinishPairing("t2", "host:x", "ABCD2345", true, now); err != ErrNotFound {
			t.Fatalf("foreign tenant must not finish: %v", err)
		}
		if err := s.FinishPairing("t1", "host:other", "ABCD2345", true, now); err != ErrNotFound {
			t.Fatalf("another host of the tenant must not finish: %v", err)
		}
		if err := s.FinishPairing("t1", "host:x", "ABCD2345", true, now); err != nil {
			t.Fatal(err)
		}
		if _, ok := s.LookupToken(c1.TokenHash); ok {
			t.Fatal("superseded claim token must stay invalid")
		}
		if tok, ok := s.LookupToken(c1b.TokenHash); !ok || tok.TenantID != "t1" || tok.Kind != KindDevice || tok.SubjectID != "device:1" {
			t.Fatalf("approval must activate the device token: %+v", tok)
		}
		got, _ := s.GetPairing("ABCD2345")
		if got.State != "CONSUMED" || got.Payload != "" || got.DevicePublicKey != c1b.PublicKey {
			t.Fatalf("after finish: %+v", got)
		}
		if _, err := s.ClaimPairing("ABCD2345", claim("device:3"), now); err != ErrConflict {
			t.Fatalf("consumed code must not be claimable: %v", err)
		}
		if devs := s.ListDevices("t1"); len(devs) != 1 || devs[0].PublicKey != c1b.PublicKey {
			t.Fatalf("devices: %+v", devs)
		}
	})
}

func TestPairingRejectAndExpiry(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1")
		now := time.Now()
		_ = s.CreatePairing(Pairing{Code: "REJECT22", TenantID: "t1", HostID: "h", ExpiresAt: now.Add(time.Minute)})
		c := claim("device:r")
		if _, err := s.ClaimPairing("REJECT22", c, now); err != nil {
			t.Fatal(err)
		}
		if err := s.FinishPairing("t1", "h", "REJECT22", false, now); err != nil {
			t.Fatal(err)
		}
		if _, ok := s.LookupToken(c.TokenHash); ok {
			t.Fatal("rejected device must lose its token")
		}
		if len(s.ListDevices("t1")) != 0 {
			t.Fatal("rejected device must be removed")
		}
		_ = s.CreatePairing(Pairing{Code: "EXPIRE22", TenantID: "t1", HostID: "h", ExpiresAt: now.Add(-time.Second), Payload: `{"x":1}`})
		if _, err := s.ClaimPairing("EXPIRE22", claim("device:e"), now); err != ErrExpired {
			t.Fatalf("expired: %v", err)
		}
		if _, pr := s.SweepTTL(now); pr != 1 {
			t.Fatalf("sweep must expire 1 pairing, got %d", pr)
		}
		if p, _ := s.GetPairing("EXPIRE22"); p.State != "EXPIRED" || p.Payload != "" {
			t.Fatalf("expired pairing must drop payload: %+v", p)
		}
		if err := s.CreatePairing(Pairing{Code: "EXPIRE22", TenantID: "t1", HostID: "h", ExpiresAt: now.Add(time.Minute)}); err != nil {
			t.Fatalf("expired code may be reused: %v", err)
		}
		// A claimed code past its approval window can no longer be approved
		// and is swept to EXPIRED.
		_ = s.CreatePairing(Pairing{Code: "LATE2222", TenantID: "t1", HostID: "h", ExpiresAt: now.Add(time.Minute)})
		late := claim("device:l")
		late.ApproveBy = now.Add(time.Minute)
		if _, err := s.ClaimPairing("LATE2222", late, now); err != nil {
			t.Fatal(err)
		}
		if err := s.FinishPairing("t1", "h", "LATE2222", true, now.Add(2*time.Minute)); err != ErrExpired {
			t.Fatalf("late approval must fail: %v", err)
		}
		s.SweepTTL(now.Add(2 * time.Minute))
		if p, _ := s.GetPairing("LATE2222"); p.State != "EXPIRED" {
			t.Fatalf("sweep must expire the stale claim: %+v", p)
		}
		if _, ok := s.LookupToken(late.TokenHash); ok {
			t.Fatal("expired claim must not yield a token")
		}
		if _, err := s.ClaimPairing("NOPE2222", claim("d"), now); err != ErrNotFound {
			t.Fatalf("unknown: %v", err)
		}
	})
}

func TestTokensAndRevocation(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1", "t2")
		_ = s.AddHost("t1", "host:a", "laptop")
		_ = s.PutToken(Token{Hash: "sha256:h1", TenantID: "t1", Kind: KindHost, SubjectID: "host:a"})
		if tok, ok := s.LookupToken("sha256:h1"); !ok || tok.TenantID != "t1" {
			t.Fatal("lookup")
		}
		if _, ok := s.LookupToken("sha256:nope"); ok {
			t.Fatal("unknown hash must not resolve")
		}
		if err := s.RemoveHost("t2", "host:a"); err != ErrNotFound {
			t.Fatal("foreign tenant must not remove host")
		}
		if got := s.ListHosts("t2"); len(got) != 0 {
			t.Fatal("t2 must not see t1 host")
		}
		if err := s.RemoveHost("t1", "host:a"); err != nil {
			t.Fatal(err)
		}
		if _, ok := s.LookupToken("sha256:h1"); ok {
			t.Fatal("removing a host must revoke its tokens")
		}
	})
}

func TestDecideCAS(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1", "t2")
		now := time.Now()
		_ = s.PutEvent("t1", Event{EventID: "event:a", TenantID: "t1", Digest: "d", Revision: "1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
		if err := s.Decide("t2", "event:a", "d", "1", "dev:x"); err != ErrNotFound {
			t.Fatalf("foreign tenant: %v", err)
		}
		if err := s.Decide("t1", "event:a", "other", "1", "dev:a"); err != ErrMismatch {
			t.Fatalf("mismatch: %v", err)
		}
		if err := s.Decide("t1", "event:a", "d", "1", "dev:a"); err != nil {
			t.Fatal(err)
		}
		if err := s.Decide("t1", "event:a", "d", "1", "dev:a"); err != nil {
			t.Fatal("same device must be idempotent")
		}
		if err := s.Decide("t1", "event:a", "d", "1", "dev:b"); err != ErrConflict {
			t.Fatalf("loser: %v", err)
		}
	})
}

func TestUploads(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1", "t2")
		now := time.Now()
		if err := s.PutUpload("t1", Upload{ID: "u", TenantID: "t1", ShortCode: "s", Size: 10<<20 + 1, CreatedAt: now, ExpiresAt: now.Add(time.Hour)}); err != ErrTooLarge {
			t.Fatal("cap")
		}
		_ = s.PutUpload("t1", Upload{ID: "u1", TenantID: "t1", ShortCode: "s1", Size: 1, CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
		if err := s.PutUpload("t2", Upload{ID: "u2", TenantID: "t2", ShortCode: "s1", Size: 1, CreatedAt: now, ExpiresAt: now.Add(time.Hour)}); err != ErrConflict {
			t.Fatalf("short code collision: %v", err)
		}
		if err := s.DeleteUpload("t2", "u1"); err != ErrNotFound {
			t.Fatal("foreign delete")
		}
		if _, ok := s.GetUploadByShort("s1", now); !ok {
			t.Fatal("get")
		}
	})
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
// İmleç ekleme sırasını izler; liste eklenme sırasıyla döner.
func TestEventsAfterFollowsInsertionOrder(t *testing.T) {
	backends(t, func(t *testing.T, s Backend) {
		seedTenants(t, s, "t1")
		now := time.Now()
		put := func(id string) {
			if err := s.PutEvent("t1", Event{EventID: id, TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)}); err != nil {
				t.Fatal(err)
			}
		}
		put("event:f000")
		put("event:9000")
		first := s.GetEvents("t1", "")
		if len(first) != 2 || first[0].EventID != "event:f000" || first[1].EventID != "event:9000" {
			t.Fatalf("eklenme sırası bekleniyordu: %v", first)
		}
		cursor := first[len(first)-1].EventID
		put("event:0001")
		put("event:ffff")
		next := s.GetEvents("t1", cursor)
		if len(next) != 2 || next[0].EventID != "event:0001" || next[1].EventID != "event:ffff" {
			t.Fatalf("imleçten sonra eklenen iki olay bekleniyordu: %v", next)
		}
		put("event:f000")
		if got := s.GetEvents("t1", "event:ffff"); len(got) != 0 {
			t.Fatalf("tekrar gönderim yeni olay sayılmamalı: %v", got)
		}
		if got := s.GetEvents("t1", "event:gone"); len(got) != 4 {
			t.Fatalf("bilinmeyen imleçte tümü bekleniyordu, %d geldi", len(got))
		}
	})
}

// Persistence: a second connection (≈ backend restart) sees the same data.
func TestPostgresSurvivesRestart(t *testing.T) {
	url := os.Getenv("PA_TEST_DATABASE_URL")
	if url == "" {
		t.Skip("PA_TEST_DATABASE_URL not set")
	}
	pg := freshPG(t, url)
	now := time.Now()
	seedTenants(t, pg, "t1")
	_ = pg.PutToken(Token{Hash: "sha256:keep", TenantID: "t1", Kind: KindHost, SubjectID: "h"})
	_ = pg.PutEvent("t1", Event{EventID: "event:keep", TenantID: "t1", CreatedAt: now, ExpiresAt: now.Add(time.Hour)})
	pg.Close()
	pg2, err := OpenPostgres(context.Background(), url)
	if err != nil {
		t.Fatal(err)
	}
	defer pg2.Close()
	if _, ok := pg2.LookupToken("sha256:keep"); !ok {
		t.Fatal("token lost across restart")
	}
	if got := pg2.GetEvents("t1", ""); len(got) != 1 {
		t.Fatal("event lost across restart")
	}
}
