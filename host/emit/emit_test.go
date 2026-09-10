// SPDX-License-Identifier: GPL-3.0-or-later
package emit

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestBuildDeterministicAndOpaque(t *testing.T) {
	now := time.Date(2026, 9, 10, 12, 0, 0, 0, time.UTC)
	a := Build("host:x", "claude", "session_started", "src-1", "sess-9", "merhaba", now)
	b := Build("host:x", "claude", "session_started", "src-1", "sess-9", "merhaba", now)
	if a != b {
		t.Fatal("aynı girdi aynı özeti üretmeli (idempotent event_id)")
	}
	if a.EventID[:6] != "event:" || a.OpaqueH[:2] != "h:" || a.OpaqueS[:2] != "s:" {
		t.Fatalf("prefixler: %q %q %q", a.EventID, a.OpaqueH, a.OpaqueS)
	}
	if a.Category != "SESSION_STARTED" {
		t.Fatalf("kategori wire'da büyük harf olmalı: %q", a.Category)
	}
	if a.Digest == "" || a.Revision == "" {
		t.Fatal("digest/revision zorunlu (backend whitelist)")
	}
}

func TestPostToBackend(t *testing.T) {
	var gotPath, gotTenant string
	var gotBody map[string]any
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath = r.URL.Path
		gotTenant = r.Header.Get("X-Tenant")
		_ = json.NewDecoder(r.Body).Decode(&gotBody)
		w.WriteHeader(202)
	}))
	defer srv.Close()
	s := Build("host:x", "codex", "task_complete", "t1", "s1", "done", time.Now())
	if err := Post(nil, srv.URL+"/", "acme", "host:x", s); err != nil {
		t.Fatal(err)
	}
	if gotPath != "/v1/hosts/host:x/events" || gotTenant != "acme" {
		t.Fatalf("path/tenant: %s %s", gotPath, gotTenant)
	}
	if gotBody["event_id"] != s.EventID {
		t.Fatalf("event_id body'de yok: %v", gotBody)
	}
}

func TestPostErrorStatuses(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(400)
	}))
	defer srv.Close()
	s := Build("h", "claude", "error", "e", "s", "m", time.Now())
	if err := Post(nil, srv.URL, "t", "h", s); err == nil {
		t.Fatal("400 hata dönmeli")
	}
}
