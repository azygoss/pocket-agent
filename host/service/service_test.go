// SPDX-License-Identifier: GPL-3.0-or-later
package service

import (
	"os"
	"path/filepath"
	"testing"
)

func TestIdempotent(t *testing.T) {
	home := t.TempDir()
	p, err := Install(home, "/usr/local/bin/pocket-agent-hook")
	if err != nil {
		t.Fatal(err)
	}
	b1, _ := os.ReadFile(p)
	p2, err := Install(home, "/usr/local/bin/pocket-agent-hook")
	if err != nil || p != p2 {
		t.Fatal("reinstall must converge")
	}
	b2, _ := os.ReadFile(p)
	if string(b1) != string(b2) {
		t.Fatal("content must be identical")
	}
	if Status(home) == "missing" {
		t.Fatal("status")
	}
	if err := Uninstall(home); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(home, ".config", "systemd", "user", "pocket-agent.service")); err == nil {
		t.Fatal("uninstall must remove")
	}
}
