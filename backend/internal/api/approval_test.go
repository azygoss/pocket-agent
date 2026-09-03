// SPDX-License-Identifier: GPL-3.0-or-later
package api

import (
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func TestApprovalAndFCMHeaders(t *testing.T) {
	s := &Server{Store: store.New()}
	mux := s.routes()
	// approval action requires tenant
	req := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"decision":"approve"}`))
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)
	if w.Code != 401 {
		t.Fatalf("missing tenant must be 401, got %d", w.Code)
	}
	req2 := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"decision":"approve"}`))
	req2.Header.Set("X-Tenant", "t1")
	w2 := httptest.NewRecorder()
	mux.ServeHTTP(w2, req2)
	if w2.Code != 202 {
		t.Fatalf("tenant approval must be 202, got %d", w2.Code)
	}
}
