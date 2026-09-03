// SPDX-License-Identifier: GPL-3.0-or-later
// Command pocket-agent-backend: stdlib HTTP + 1m TTL sweeper (P02/P13/P15).
package main

import (
	"log"
	"net/http"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/api"
	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func main() {
	st := store.New()
	srv := api.New(st)
	go func() {
		t := time.NewTicker(time.Minute)
		defer t.Stop()
		for now := range t.C {
			ev, pr := st.SweepTTL(now)
			if ev+pr > 0 {
				log.Printf("ttl sweep: events=%d pairings=%d", ev, pr)
			}
		}
	}()
	addr := ":8080"
	log.Printf("pocket-agent backend listening on %s", addr)
	if err := http.ListenAndServe(addr, srv.Handler()); err != nil {
		log.Fatal(err)
	}
}
