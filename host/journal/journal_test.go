// SPDX-License-Identifier: GPL-3.0-or-later
package journal

import "testing"

func TestIdempotentAppend(t *testing.T) {
	j, err := Open(t.TempDir() + "/j.jsonl")
	if err != nil {
		t.Fatal(err)
	}
	r1, _ := j.Append(Entry{CommandID: "cmd:1", Kind: "event", Payload: "{}"})
	r2, _ := j.Append(Entry{CommandID: "cmd:1", Kind: "event", Payload: "{}"})
	if r1 != r2 {
		t.Fatal("same CommandId must return same receipt")
	}
	j2, _ := Open(j.path)
	r3, _ := j2.Append(Entry{CommandID: "cmd:1", Kind: "event", Payload: "{}"})
	if r3 != r1 {
		t.Fatal("receipt must survive reopen")
	}
}
