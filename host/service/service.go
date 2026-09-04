// SPDX-License-Identifier: GPL-3.0-or-later
// Package service: user-level daemon install (systemd/launchd), idempotent.
package service

import (
	"fmt"
	"os"
	"path/filepath"
)

func unitDir(home string) string { return filepath.Join(home, ".config", "systemd", "user") }

// Install writes/overwrites the unit with identical content on rerun (converge).
func Install(home, execPath string) (string, error) {
	dir := unitDir(home)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	p := filepath.Join(dir, "pocket-agent.service")
	body := fmt.Sprintf(`[Unit]
Description=pocket-agent host daemon
After=network-online.target

[Service]
ExecStart=%s daemon
Restart=on-failure

[Install]
WantedBy=default.target
`, execPath)
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, []byte(body), 0o600); err != nil {
		return "", err
	}
	return p, os.Rename(tmp, p)
}

func Status(home string) string {
	p := filepath.Join(unitDir(home), "pocket-agent.service")
	if _, err := os.Stat(p); err == nil {
		return "installed:" + p
	}
	return "missing"
}

// InstallGateway: P11 dosya/diff sunucusu için ayrı user unit. Workspace root
// kurulum anındaki cwd'dir; değiştirmek için unit'i yeniden kur.
func InstallGateway(home, execPath, root string) (string, error) {
	dir := unitDir(home)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return "", err
	}
	p := filepath.Join(dir, "pocket-agent-gateway.service")
	body := fmt.Sprintf(`[Unit]
Description=pocket-agent gateway (workspace files/diff, loopback only)
After=network-online.target

[Service]
ExecStart=%s gateway serve --root %s
Restart=on-failure

[Install]
WantedBy=default.target
`, execPath, root)
	tmp := p + ".tmp"
	if err := os.WriteFile(tmp, []byte(body), 0o600); err != nil {
		return "", err
	}
	return p, os.Rename(tmp, p)
}

func GatewayStatus(home string) string {
	p := filepath.Join(unitDir(home), "pocket-agent-gateway.service")
	if _, err := os.Stat(p); err == nil {
		return "installed:" + p
	}
	return "missing"
}

func Uninstall(home string) error {
	p := filepath.Join(unitDir(home), "pocket-agent.service")
	if err := os.Remove(p); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}
