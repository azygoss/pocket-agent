// SPDX-License-Identifier: GPL-3.0-or-later
package config

import (
	"os"
	"path/filepath"
	"testing"
)

func TestIdempotentSave(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, "config.toml")
	c := Config{BackendURL: "https://x", HostID: "host:a", GatewayPort: 24543}
	if err := Save(p, c); err != nil {
		t.Fatal(err)
	}
	b1, _ := os.ReadFile(p)
	if err := Save(p, c); err != nil {
		t.Fatal(err)
	}
	b2, _ := os.ReadFile(p)
	if string(b1) != string(b2) {
		t.Fatal("second save must converge")
	}
	got, err := Load(p)
	if err != nil || got.HostID != "host:a" {
		t.Fatalf("load: %+v %v", got, err)
	}
	if _, err := Load(filepath.Join(dir, "missing.toml")); err != nil {
		t.Fatal("missing file must return defaults")
	}
}
