// SPDX-License-Identifier: GPL-3.0-or-later
package main

import (
	"log"
	"net/http"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func main() {
	_ = store.New()
	_ = time.Now()
	mux := http.NewServeMux()
	mux.HandleFunc("GET /v1/healthz", func(w http.ResponseWriter, _ *http.Request) { w.Write([]byte("ok")) })
	log.Println("pocket-agent backend skeleton listening on :8080 (full API in P02 slices)")
	_ = http.ListenAndServe(":8080", mux)
}
