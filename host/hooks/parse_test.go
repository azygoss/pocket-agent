// SPDX-License-Identifier: GPL-3.0-or-later
package hooks

import "testing"

func TestParseClaude(t *testing.T) {
	got, err := ParseClaudeJSONL("testdata/claude.jsonl")
	if err != nil || len(got) != 3 {
		t.Fatalf("claude: %v %d", err, len(got))
	}
	if got[1].Text != "tool:Bash" {
		t.Fatalf("tool line: %+v", got[1])
	}
	if got[2].Category != "task_complete" {
		t.Fatalf("result: %+v", got[2])
	}
}

func TestParseCodex(t *testing.T) {
	got, err := ParseCodexJSONL("testdata/codex.jsonl")
	if err != nil || len(got) != 3 {
		t.Fatalf("codex: %v %d", err, len(got))
	}
	if got[2].Category != "approval_required" || got[2].Digest != "abc123" {
		t.Fatalf("approval: %+v", got[2])
	}
}
