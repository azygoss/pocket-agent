// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestServerJailAndAuth(t *testing.T) {
	root := t.TempDir()
	_ = os.WriteFile(filepath.Join(root, "ok.md"), []byte("hello"), 0o600)
	s := &Server{Token: "t", Root: root}

	// no token => 401
	req := httptest.NewRequest("GET", "/file/ok.md", nil)
	req.Host = "127.0.0.1:24543"
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 401 {
		t.Fatalf("want 401, got %d", w.Code)
	}
	// traversal => 400 (auth ok)
	req2 := httptest.NewRequest("GET", "/file/../../etc/passwd", nil)
	req2.Host = "127.0.0.1:24543"
	req2.Header.Set("Authorization", "Bearer t")
	w2 := httptest.NewRecorder()
	s.ServeHTTP(w2, req2)
	if w2.Code != 400 {
		t.Fatalf("want 400 traversal, got %d", w2.Code)
	}
	// ok file
	req3 := httptest.NewRequest("GET", "/file/ok.md", nil)
	req3.Host = "127.0.0.1:24543"
	req3.Header.Set("Authorization", "Bearer t")
	w3 := httptest.NewRecorder()
	s.ServeHTTP(w3, req3)
	if w3.Code != 200 || w3.Body.String() != "hello" {
		t.Fatalf("want 200 hello, got %d %q", w3.Code, w3.Body.String())
	}
}

func TestServerLsEndpoint(t *testing.T) {
	root := t.TempDir()
	_ = os.Mkdir(filepath.Join(root, "adir"), 0o700)
	_ = os.WriteFile(filepath.Join(root, "z.md"), []byte("z"), 0o600)
	_ = os.WriteFile(filepath.Join(root, "a.md"), []byte("a"), 0o600)
	s := &Server{Token: "t", Root: root}

	req := httptest.NewRequest("GET", "/ls/", nil)
	req.Host = "127.0.0.1:24543"
	req.Header.Set("Authorization", "Bearer t")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 200 {
		t.Fatalf("want 200, got %d", w.Code)
	}
	body := w.Body.String()
	// dizin önce sıralanır
	if idx := strings.Index(body, "adir"); idx < 0 {
		t.Fatalf("adir missing: %s", body)
	}
	if strings.Index(body, "adir") > strings.Index(body, "a.md") {
		t.Fatalf("dirs must come first: %s", body)
	}

	// traversal reddedilir
	req2 := httptest.NewRequest("GET", "/ls/../../etc", nil)
	req2.Host = "127.0.0.1:24543"
	req2.Header.Set("Authorization", "Bearer t")
	w2 := httptest.NewRecorder()
	s.ServeHTTP(w2, req2)
	if w2.Code != 400 {
		t.Fatalf("want 400, got %d", w2.Code)
	}
}

func TestServerDiffEndpoint(t *testing.T) {
	// git repo değilse 500 (kind zehirlenmesi olmaz — sabit arg seti)
	s := &Server{Token: "t", Root: t.TempDir()}
	req := httptest.NewRequest("GET", "/diff?kind=staged", nil)
	req.Host = "127.0.0.1:24543"
	req.Header.Set("Authorization", "Bearer t")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 500 {
		t.Fatalf("non-repo want 500, got %d", w.Code)
	}
}

func TestServerPreviewLoopbackOnly(t *testing.T) {
	s := &Server{Token: "t", Root: t.TempDir()}
	// SSRF: loopback olmayan hedef reddedilir
	req := httptest.NewRequest("GET", "/preview?h=169.254.169.254&p=80&path=/", nil)
	req.Host = "127.0.0.1:24543"
	req.Header.Set("Authorization", "Bearer t")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 400 {
		t.Fatalf("SSRF must be rejected, got %d", w.Code)
	}
	// loopback ama kapalı port → fetch hatası 400
	req2 := httptest.NewRequest("GET", "/preview?h=127.0.0.1&p=1&path=/", nil)
	req2.Host = "127.0.0.1:24543"
	req2.Header.Set("Authorization", "Bearer t")
	w2 := httptest.NewRecorder()
	s.ServeHTTP(w2, req2)
	if w2.Code != 400 {
		t.Fatalf("closed port want 400, got %d", w2.Code)
	}
}

func TestServerChatEndpoint(t *testing.T) {
	root := t.TempDir()
	// Claude-format transcript fixture kopyala
	src, _ := os.ReadFile("../hooks/testdata/claude.jsonl")
	os.WriteFile(root+"/session.jsonl", src, 0o600)
	s := &Server{Token: "tok", Root: root}

	req := httptest.NewRequest("GET", "http://127.0.0.1:24543/chat?path=session.jsonl", nil)
	req.Host = "127.0.0.1:24543"
	req.Header.Set("Authorization", "Bearer tok")
	w := httptest.NewRecorder()
	s.ServeHTTP(w, req)
	if w.Code != 200 {
		t.Fatalf("chat: got %d body=%s", w.Code, w.Body.String())
	}
	var resp struct {
		Blocks []struct {
			Role string `json:"role"`
			Text string `json:"text"`
		} `json:"blocks"`
	}
	json.Unmarshal(w.Body.Bytes(), &resp)
	if len(resp.Blocks) < 2 {
		t.Fatalf("blocks: got %d", len(resp.Blocks))
	}
	if resp.Blocks[0].Role != "message" {
		t.Fatalf("first role: got %q", resp.Blocks[0].Role)
	}

	// traversal reddedilir
	req2 := httptest.NewRequest("GET", "http://127.0.0.1:24543/chat?path=../escape.jsonl", nil)
	req2.Host = "127.0.0.1:24543"
	req2.Header.Set("Authorization", "Bearer tok")
	w2 := httptest.NewRecorder()
	s.ServeHTTP(w2, req2)
	if w2.Code != 400 {
		t.Fatalf("traversal: got %d", w2.Code)
	}
}
