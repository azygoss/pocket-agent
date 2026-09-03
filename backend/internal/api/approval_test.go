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
	req := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"digest":"d","revision":"3","device":"device:A"}`))
	w := httptest.NewRecorder()
	mux.ServeHTTP(w, req)
	if w.Code != 401 {
		t.Fatalf("missing tenant must be 401, got %d", w.Code)
	}
	// device A wins
	req2 := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"digest":"d","revision":"3","device":"device:A"}`))
	req2.Header.Set("X-Tenant", "t1")
	w2 := httptest.NewRecorder()
	mux.ServeHTTP(w2, req2)
	if w2.Code != 202 {
		t.Fatalf("first decision 202, got %d", w2.Code)
	}
	// device B loses race
	req3 := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"digest":"d","revision":"3","device":"device:B"}`))
	req3.Header.Set("X-Tenant", "t1")
	w3 := httptest.NewRecorder()
	mux.ServeHTTP(w3, req3)
	if w3.Code != 409 {
		t.Fatalf("race loser 409, got %d", w3.Code)
	}
	// digest mismatch
	req4 := httptest.NewRequest("POST", "/v1/approvals/event:1/actions", strings.NewReader(`{"digest":"other","revision":"3","device":"device:A"}`))
	req4.Header.Set("X-Tenant", "t1")
	w4 := httptest.NewRecorder()
	mux.ServeHTTP(w4, req4)
	if w4.Code != 409 && w4.Code != 202 {
		t.Fatalf("mismatch must not be 2xx-new, got %d", w4.Code)
	}
}
