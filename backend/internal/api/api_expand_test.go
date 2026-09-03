// SPDX-License-Identifier: GPL-3.0-or-later
package api

import (
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func TestPairingHTTP(t *testing.T) {
	s := &Server{Store: store.New()}
	mux := s.routes()
	// create
	req := httptest.NewRequest("POST", "/v1/pairing-sessions", strings.NewReader(`{"code":"C1","host_id":"host:1"}`))
	req.Header.Set("X-Tenant", "t1")
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)
	if w.Code != 201 {
		t.Fatalf("create %d", w.Code)
	}
	// claim device A
	req2 := httptest.NewRequest("POST", "/v1/pairing-sessions/C1/claim", strings.NewReader(`{"device_id":"device:A"}`))
	w2 := httptest.NewRecorder()
	mux.ServeHTTP(w2, req2)
	if w2.Code != 200 {
		t.Fatalf("claim A %d %s", w2.Code, w2.Body.String())
	}
	// different device => 409
	req3 := httptest.NewRequest("POST", "/v1/pairing-sessions/C1/claim", strings.NewReader(`{"device_id":"device:B"}`))
	w3 := httptest.NewRecorder()
	mux.ServeHTTP(w3, req3)
	if w3.Code != 409 {
		t.Fatalf("race must be 409, got %d", w3.Code)
	}
}

func TestUploadCapAndHosts(t *testing.T) {
	s := &Server{Store: store.New()}
	mux := s.routes()
	// too large
	big := `{"id":"u1","short_code":"s1","size":10485761}`
	req := httptest.NewRequest("POST", "/v1/uploads", strings.NewReader(big))
	req.Header.Set("X-Tenant", "t1")
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)
	if w.Code != 400 {
		t.Fatalf("10MB+1 must be 400, got %d", w.Code)
	}
	// hosts tenant isolation
	_ = s.Store.AddHost("t1", "host:1")
	req2 := httptest.NewRequest("GET", "/v1/hosts", nil)
	req2.Header.Set("X-Tenant", "t2")
	w2 := httptest.NewRecorder()
	mux.ServeHTTP(w2, req2)
	if !strings.Contains(w2.Body.String(), "null") && strings.Contains(w2.Body.String(), "host:1") {
		t.Fatal("t2 must not see t1 host")
	}
}
