// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"net/http/httptest"
	"os"
	"path/filepath"
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
