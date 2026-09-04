// SPDX-License-Identifier: GPL-3.0-or-later
package service

import (
	"os"
	"path/filepath"
	"strings"
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

func TestGatewayUnit(t *testing.T) {
	home := t.TempDir()
	p, err := InstallGateway(home, "/usr/local/bin/pocket-agent-hook", "/srv/ws")
	if err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(p)
	s := string(b)
	if !containsAll(s, "gateway serve --root /srv/ws", "WantedBy=default.target") {
		t.Fatalf("unit body: %s", s)
	}
	if GatewayStatus(home) == "missing" {
		t.Fatal("gateway status")
	}
	// idempotent
	p2, err := InstallGateway(home, "/usr/local/bin/pocket-agent-hook", "/srv/ws")
	if err != nil || p != p2 {
		t.Fatal("reinstall must converge")
	}
}

func containsAll(s string, subs ...string) bool {
	for _, sub := range subs {
		if !strings.Contains(s, sub) {
			return false
		}
	}
	return true
}
