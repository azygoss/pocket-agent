// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
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
