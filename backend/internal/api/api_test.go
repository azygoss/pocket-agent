// SPDX-License-Identifier: GPL-3.0-or-later
package api

import (
	"strings"
	"testing"
	"net/http/httptest"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func TestPostEventWhitelist(t *testing.T) {
	s := &Server{Store: store.New()}
	ok := `{"event_id":"event:1","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"APPROVAL_REQUIRED","title":"t","message":"hi","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`
	req := httptest.NewRequest("POST", "/v1/hosts/h/events", strings.NewReader(ok))
	req.Header.Set("X-Tenant", "t1")
	w := httptest.NewRecorder()
	s.routes().ServeHTTP(w, req)
	if w.Code != 202 {
		t.Fatalf("want 202, got %d: %s", w.Code, w.Body.String())
	}
	bad := `{"event_id":"event:2","transcript":"SECRET","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"ERROR","message":"x","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`
	req2 := httptest.NewRequest("POST", "/v1/hosts/h/events", strings.NewReader(bad))
	req2.Header.Set("X-Tenant", "t1")
	w2 := httptest.NewRecorder()
	s.routes().ServeHTTP(w2, req2)
	if w2.Code != 400 {
		t.Fatalf("forbidden field must be 400, got %d", w2.Code)
	}
	if strings.Contains(w2.Body.String(), "SECRET") {
		t.Fatal("reject response must not echo secret")
	}
}
