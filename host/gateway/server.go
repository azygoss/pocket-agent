// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"crypto/subtle"
	"net/http"
	"os"
	"strings"
)

// Server binds ONLY 127.0.0.1:24543. Token from 0600 file; Android reaches it
// only via SSH local-forward. Direct LAN/WAN exposure is refused by bind addr.
type Server struct {
	Token string
	Root  string // workspace root jail
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Host != "" && !strings.HasPrefix(r.Host, "127.0.0.1") && !strings.HasPrefix(r.Host, "localhost") {
		http.Error(w, "gateway is loopback-only", 403)
		return
	}
	got := strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
	if subtle.ConstantTimeCompare([]byte(got), []byte(s.Token)) != 1 {
		http.Error(w, "unauthorized", 401)
		return
	}
	rel := strings.TrimPrefix(r.URL.Path, "/file/")
	if rel == r.URL.Path {
		http.Error(w, "bad path", 400)
		return
	}
	full, err := Jail(s.Root, rel)
	if err != nil {
		http.Error(w, "rejected", 400)
		return
	}
	if err := TextOnly(full); err != nil {
		http.Error(w, "binary listed, not rendered", 415)
		return
	}
	b, err := os.ReadFile(full)
	if err != nil {
		http.Error(w, "not found", 404)
		return
	}
	if len(b) > 1<<20 {
		b = b[:1<<20]
	}
	w.Write(b)
}
