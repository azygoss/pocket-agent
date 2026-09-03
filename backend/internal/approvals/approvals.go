// SPDX-License-Identifier: GPL-3.0-or-later
// Package approvals: signed decision + atomic CAS (P13). First writer wins.
package approvals

import (
	"errors"
	"sync"
)

type State string

const (
	Pending  State = "PENDING"
	Resolved State = "RESOLVED"
	Expired  State = "EXPIRED"
)

type Record struct {
	EventID string
	Digest  string
	Revision string
	State   State
	Winner  string
}

type Table struct {
	mu sync.Mutex
	m  map[string]*Record
}

func New() *Table { return &Table{m: map[string]*Record{}} }

var ErrConflict = errors.New("already resolved")

// Decide CAS: only PENDING transitions; same winner idempotent, other -> conflict.
func (t *Table) Decide(eventID, digest, rev, device string) (State, error) {
	t.mu.Lock()
	defer t.mu.Unlock()
	r, ok := t.m[eventID]
	if !ok {
		r = &Record{EventID: eventID, Digest: digest, Revision: rev, State: Pending}
		t.m[eventID] = r
	}
	if r.State != Pending {
		if r.Winner == device {
			return r.State, nil
		}
		return r.State, ErrConflict
	}
	if r.Digest != digest || r.Revision != rev {
		return r.State, errors.New("digest/revision mismatch")
	}
	r.State = Resolved
	r.Winner = device
	return r.State, nil
}
