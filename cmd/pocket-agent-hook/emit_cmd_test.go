// SPDX-License-Identifier: GPL-3.0-or-later
package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/pocket-agent/pocket-agent/host/emit"
	"github.com/pocket-agent/pocket-agent/host/journal"
)

// flushJournal uçtan uca: journal'daki agent_event backend'e postlanır,
// offset ilerler; ikinci flush tekrar postlamaz.
func TestFlushJournal(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("POCKET_HOME", dir)
	posted := 0
	var lastTenant string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "POST" && strings.HasSuffix(r.URL.Path, "/events") {
			posted++
			lastTenant = r.Header.Get("X-Tenant")
			w.WriteHeader(202)
			return
		}
		w.WriteHeader(404)
	}))
	defer srv.Close()
	t.Setenv("POCKET_BACKEND", srv.URL)

	j, err := journal.Open(journalPath())
	if err != nil {
		t.Fatal(err)
	}
	s := emit.Build("h1", "claude", "session_started", "src-1", "sess", "hi", time.Now())
	p, _ := json.Marshal(s)
	if _, err := j.Append(journal.Entry{CommandID: s.EventID, Kind: "agent_event", Payload: string(p)}); err != nil {
		t.Fatal(err)
	}
	// İlgisiz kind atlanmalı.
	j.Append(journal.Entry{CommandID: "x", Kind: "other", Payload: "{}"})

	flushJournal(stateDir())
	if posted != 1 {
		t.Fatalf("1 event postlanmalıydı, %d", posted)
	}
	if lastTenant != "default" {
		t.Fatalf("varsayılan tenant 'default' olmalı: %q", lastTenant)
	}
	// Tekrar flush: yeni kayıt yok — offset korundu.
	flushJournal(stateDir())
	if posted != 1 {
		t.Fatalf("tekrar flush postlamamalı, %d", posted)
	}
	// Yeni kayıt → sadece o postlanır.
	s2 := emit.Build("h1", "claude", "session_ended", "src-2", "sess", "bye", time.Now())
	p2, _ := json.Marshal(s2)
	j2, _ := journal.Open(journalPath())
	j2.Append(journal.Entry{CommandID: s2.EventID, Kind: "agent_event", Payload: string(p2)})
	flushJournal(stateDir())
	if posted != 2 {
		t.Fatalf("yeni kayıt postlanmalı, toplam %d", posted)
	}
}

// Daemon yokken emitEvent doğrudan posta düşer — olay kaybolmaz.
func TestEmitEventDirectPostFallback(t *testing.T) {
	dir := t.TempDir()
	t.Setenv("POCKET_HOME", dir)
	posted := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		posted++
		w.WriteHeader(202)
	}))
	defer srv.Close()
	t.Setenv("POCKET_BACKEND", srv.URL)

	if err := emitEvent("claude", "task_complete", "id-1", "sess-1", "tamam"); err != nil {
		t.Fatal(err)
	}
	if posted != 1 {
		t.Fatalf("daemon yokken doğrudan post düşmeliydi, %d", posted)
	}
	// Dedupe: aynı (source,id) tekrar postlanmaz.
	if err := emitEvent("claude", "task_complete", "id-1", "sess-1", "tamam"); err != nil {
		t.Fatal(err)
	}
	if posted != 1 {
		t.Fatal("dupe event postlandı")
	}
	// Journal'da kayıt durur (logs komutu görebilir).
	if _, err := os.Stat(filepath.Join(dir, ".local/state/pocket-agent/journal.jsonl")); err != nil {
		t.Fatal("journal yazılmadı")
	}
}
