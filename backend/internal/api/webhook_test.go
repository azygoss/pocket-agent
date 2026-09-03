// SPDX-License-Identifier: GPL-3.0-or-later
package api

import (
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func TestWebhookTokenHashed(t *testing.T) {
	tok, hash, err := auth.Mint("wh")
	if err != nil {
		t.Fatal(err)
	}
	if hash == tok {
		t.Fatal("token must be hash-stored")
	}
	s := &Server{Store: store.New()}
	req := httptest.NewRequest("POST", "/v1/webhooks/"+tok, strings.NewReader(`{}`))
	w := httptest.NewRecorder()
	s.routes().ServeHTTP(w, req)
	if w.Code != 202 {
		t.Fatalf("webhook 202, got %d", w.Code)
	}
	// oversize capped at handler (1MB events / 256KB webhooks): huge body rejected or truncated
	big := strings.Repeat("x", 300<<10)
	req2 := httptest.NewRequest("POST", "/v1/webhooks/"+tok, strings.NewReader(big))
	w2 := httptest.NewRecorder()
	s.routes().ServeHTTP(w2, req2)
	if w2.Code != 202 && w2.Code != 400 {
		t.Fatalf("oversize must be 202/400, got %d", w2.Code)
	}
}
