// SPDX-License-Identifier: GPL-3.0-or-later
package hooks

import "testing"

func TestDedupeAndAnsiBan(t *testing.T) {
	b := New()
	if _, err := b.Normalize("codex", "task_complete", "e1", "ok"); err != nil {
		t.Fatal(err)
	}
	if _, err := b.Normalize("codex", "task_complete", "e1", "ok"); err != ErrDupe {
		t.Fatal("dupe must be rejected")
	}
	if _, err := b.Normalize("codex", "task_complete", "e2", "\x1b[31mred"); err == nil {
		t.Fatal("ansi scrape must be rejected")
	}
}
