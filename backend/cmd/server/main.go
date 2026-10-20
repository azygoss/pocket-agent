// SPDX-License-Identifier: GPL-3.0-or-later
package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/api"
	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

func main() {
	var st store.Backend
	if url := os.Getenv("DATABASE_URL"); url != "" {
		pg, err := store.OpenPostgres(context.Background(), url)
		if err != nil {
			log.Fatalf("postgres: %v", err)
		}
		defer pg.Close()
		st = pg
		log.Printf("store: postgres (migrations applied)")
	} else {
		st = store.New()
		log.Printf("store: in-memory — DATABASE_URL unset, data is lost on restart")
	}
	srv := api.New(st)
	srv.EnrollToken = os.Getenv("POCKET_ENROLL_TOKEN")
	srv.TrustProxy = os.Getenv("POCKET_TRUST_PROXY") == "1"
	if srv.EnrollToken == "" {
		log.Printf("host registration is open — set POCKET_ENROLL_TOKEN to restrict it")
	}
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
	addr := os.Getenv("POCKET_ADDR")
	if addr == "" {
		addr = ":8080"
	}
	hs := &http.Server{Addr: addr, Handler: srv.Handler(), ReadHeaderTimeout: 10 * time.Second}
	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
		<-sig
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = hs.Shutdown(ctx)
	}()
	log.Printf("pocket-agent backend listening on %s", addr)
	if err := hs.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
