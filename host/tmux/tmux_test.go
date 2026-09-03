// SPDX-License-Identifier: GPL-3.0-or-later
package tmux

import (
	"fmt"
	"os"
	"testing"
)

func TestEnsureListKill(t *testing.T) {
	sock := fmt.Sprintf("pa-test-%d", os.Getpid())
	name := "sess1"
	if err := Ensure(sock, name, ""); err != nil {
		t.Fatalf("ensure: %v", err)
	}
	if err := Ensure(sock, name, ""); err != nil {
		t.Fatalf("re-ensure idempotent: %v", err)
	}
	names, err := List(sock)
	if err != nil {
		t.Fatal(err)
	}
	found := false
	for _, n := range names {
		if n == name {
			found = true
		}
	}
	if !found {
		t.Fatalf("session missing: %v", names)
	}
	if err := Kill(sock, name); err != nil {
		t.Fatal(err)
	}
	names, _ = List(sock)
	for _, n := range names {
		if n == name {
			t.Fatal("kill must remove")
		}
	}
}
