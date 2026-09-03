// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import "testing"

func TestTraversal(t *testing.T) {
	if _, err := Jail("/ws", "../../etc/passwd"); err == nil {
		t.Fatal("traversal must fail")
	}
	if _, err := Jail("/ws", "/etc/passwd"); err == nil {
		t.Fatal("abs must fail")
	}
	if _, err := Jail("/ws", "a/../b.md"); err == nil {
		t.Fatal("inner .. must also fail (strict)")
	}
	if p, err := Jail("/ws", "docs/a.md"); err != nil || p != "/ws/docs/a.md" {
		t.Fatalf("ok: %s %v", p, err)
	}
}

func TestSSRF(t *testing.T) {
	if err := LoopbackOnly("127.0.0.1"); err != nil {
		t.Fatal(err)
	}
	if err := LoopbackOnly("::1"); err != nil {
		t.Fatal(err)
	}
	if err := LoopbackOnly("169.254.169.254"); err == nil {
		t.Fatal("metadata IP must fail")
	}
	if err := LoopbackOnly("example.com"); err == nil {
		t.Fatal("public host must fail")
	}
}
