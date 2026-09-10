// SPDX-License-Identifier: GPL-3.0-or-later
// Package journal: append-only JSONL outbox (SQLite WAL lands in later slice;
// file journal preserves journal-first + retry + CommandId dedupe semantics).
package journal

import (
	"bufio"
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

type Entry struct {
	CommandID string `json:"command_id"`
	Kind      string `json:"kind"`
	Payload   string `json:"payload"`
}

type Journal struct {
	mu   sync.Mutex
	path string
	seen map[string]string // commandID -> receipt
}

func Open(path string) (*Journal, error) {
	j := &Journal{path: path, seen: map[string]string{}}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, err
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_RDONLY, 0o600)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 1<<20), 1<<20)
	for sc.Scan() {
		var e Entry
		if json.Unmarshal(sc.Bytes(), &e) == nil && e.CommandID != "" {
			j.seen[e.CommandID] = "receipt:" + e.CommandID
		}
	}
	return j, sc.Err()
}

// Append writes first, returns idempotent receipt. Same CommandId => same receipt.
func (j *Journal) Append(e Entry) (string, error) {
	j.mu.Lock()
	defer j.mu.Unlock()
	if r, ok := j.seen[e.CommandID]; ok {
		return r, nil
	}
	f, err := os.OpenFile(j.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
	if err != nil {
		return "", err
	}
	defer f.Close()
	if err := json.NewEncoder(f).Encode(e); err != nil {
		return "", err
	}
	r := "receipt:" + e.CommandID
	j.seen[e.CommandID] = r
	return r, nil
}
