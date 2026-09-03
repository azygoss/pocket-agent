// SPDX-License-Identifier: GPL-3.0-or-later
// Package inbox: session-grouped inbox + 24h TTL (P13).
package inbox

import (
	"sync"
	"time"
)

type Item struct {
	SessionID string
	EventID   string
	CreatedAt time.Time
	Read      bool
}

type Box struct {
	mu    sync.Mutex
	items map[string]Item // eventID -> item
}

func New() *Box { return &Box{items: map[string]Item{}} }

// Add merges per session: one active row per session (latest wins).
func (b *Box) Add(sessionID, eventID string, now time.Time) {
	b.mu.Lock()
	defer b.mu.Unlock()
	for id, it := range b.items {
		if it.SessionID == sessionID && !it.Read {
			delete(b.items, id)
		}
	}
	b.items[eventID] = Item{SessionID: sessionID, EventID: eventID, CreatedAt: now}
}

func (b *Box) Sweep(now time.Time) int {
	b.mu.Lock()
	defer b.mu.Unlock()
	n := 0
	for id, it := range b.items {
		if now.Sub(it.CreatedAt) > 24*time.Hour {
			delete(b.items, id)
			n++
		}
	}
	return n
}
