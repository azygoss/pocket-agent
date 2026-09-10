// SPDX-License-Identifier: GPL-3.0-or-later
// Package config: ~/.config/pocket-agent/config.toml (minimal TOML subset, stdlib only).
package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type Config struct {
	BackendURL  string `toml:"backend_url"`
	HostID      string `toml:"host_id"`
	GatewayPort int    `toml:"gateway_port"`
	// Tenant: backend'deki X-Tenant değeri — uygulamadaki "tenant token" ile
	// aynı olmalı (uygulama onboarding'i varsayılanı "default" yapar).
	Tenant string `toml:"tenant"`
}

func DefaultPath() string {
	home := os.Getenv("POCKET_HOME")
	if home == "" {
		home, _ = os.UserHomeDir()
	}
	return filepath.Join(home, ".config", "pocket-agent", "config.toml")
}

func Load(path string) (Config, error) {
	c := Config{GatewayPort: 24543}
	b, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return c, nil // idempotent: missing file => defaults
		}
		return c, err
	}
	for _, line := range strings.Split(string(b), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		kv := strings.SplitN(line, "=", 2)
		if len(kv) != 2 {
			continue
		}
		k := strings.TrimSpace(kv[0])
		v := strings.Trim(strings.TrimSpace(kv[1]), `"`)
		switch k {
		case "backend_url":
			c.BackendURL = v
		case "host_id":
			c.HostID = v
		case "tenant":
			c.Tenant = v
		case "gateway_port":
			var p int
			fmt.Sscanf(v, "%d", &p)
			if p != 0 {
				c.GatewayPort = p
			}
		}
	}
	return c, nil
}

// Save is idempotent: second write of same content is a no-op converge.
func Save(path string, c Config) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return err
	}
	out := fmt.Sprintf("# pocket-agent config (managed)\nbackend_url = %q\nhost_id = %q\ngateway_port = %d\ntenant = %q\n",
		c.BackendURL, c.HostID, c.GatewayPort, c.Tenant)
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, []byte(out), 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}
