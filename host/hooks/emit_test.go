// SPDX-License-Identifier: GPL-3.0-or-later
package hooks

import (
	"testing"

	"github.com/pocket-agent/pocket-agent/host/journal"
)

// P12 emission: normalized event -> journal append (journal-first, retry later).
func TestEmitToJournal(t *testing.T) {
	b := New()
	j, _ := Open(t.TempDir() + "/j.jsonl")
	msg, err := b.Normalize("claude", "approval_required", "src-1", "approve deploy?")
	if err != nil {
		t.Fatal(err)
	}
	r1, err := j.Append(journal.Entry{CommandID: "cmd:src-1", Kind: "agent_event", Payload: msg})
	if err != nil || r1 == "" {
		t.Fatal("journal-first append")
	}
	if _, err := b.Normalize("claude", "approval_required", "src-1", "approve deploy?"); err != ErrDupe {
		t.Fatal("dupe event must not re-emit")
	}
}
