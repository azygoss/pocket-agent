// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestChatRecentAllowlist(t *testing.T) {
	home := t.TempDir()
	old := homeDir
	homeDir = func() (string, error) { return home, nil }
	defer func() { homeDir = old }()

	// allowlist içi: claude + codex transcript
	cl := filepath.Join(home, ".claude", "projects", "proj1")
	co := filepath.Join(home, ".codex", "sessions")
	os.MkdirAll(cl, 0o700)
	os.MkdirAll(co, 0o700)
	src, _ := os.ReadFile("../hooks/testdata/claude.jsonl")
	os.WriteFile(filepath.Join(cl, "s1.jsonl"), src, 0o600)
	os.WriteFile(filepath.Join(co, "s2.jsonl"), src, 0o600)
	os.WriteFile(filepath.Join(cl, "notjson.txt"), []byte("x"), 0o600) // dahil edilmemeli

	list := RecentTranscripts(50)
	if len(list) != 2 {
		t.Fatalf("transcripts: got %d", len(list))
	}

	// src=claude ile bloklar gelir; traversal reddedilir
	s := &Server{Token: "tok", Root: t.TempDir()}
	req := httptest.NewRequest("GET", "http://127.0.0.1/chat?src=claude&path=proj1/s1.jsonl", nil)
	req.Host = "127.0.0.1"
	req.Header.Set("Authorization", "Bearer tok")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 200 {
		t.Fatalf("src=claude: got %d", w.Code)
	}
	var resp struct{ Blocks []chatBlock }
	if err := json.Unmarshal(w.Body.Bytes(), &resp); err != nil || len(resp.Blocks) == 0 {
		t.Fatalf("blocks empty: err=%v", err)
	}

	// src=claude altında workspace dosyasına erişim yok (jail ayrı)
	bad := httptest.NewRequest("GET", "http://127.0.0.1/chat?src=claude&path=../../s2.jsonl", nil)
	bad.Host = "127.0.0.1"
	bad.Header.Set("Authorization", "Bearer tok")
	wb := httptest.NewRecorder()
	s.ServeHTTP(wb, bad)
	if wb.Code != 400 {
		t.Fatalf("traversal src=claude: got %d", wb.Code)
	}

	// jsonl olmayan dosya reddedilir
	nj := httptest.NewRequest("GET", "http://127.0.0.1/chat?src=claude&path=proj1/notjson.txt", nil)
	nj.Host = "127.0.0.1"
	nj.Header.Set("Authorization", "Bearer tok")
	wn := httptest.NewRecorder()
	s.ServeHTTP(wn, nj)
	if wn.Code != 400 {
		t.Fatalf("non-jsonl: got %d", wn.Code)
	}
}
