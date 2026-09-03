// SPDX-License-Identifier: GPL-3.0-or-later
// Package daemon: 0600 Unix socket, same-UID only (P03).
package daemon

import (
	"fmt"
	"net"
	"os"
	"path/filepath"
)

// Listen creates a 0600 socket under dir (symlink-safe: dir 0700, O_EXCL-ish via remove+bind).
func Listen(dir string) (net.Listener, string, error) {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, "", err
	}
	path := filepath.Join(dir, "daemon.sock")
	_ = os.Remove(path)
	l, err := net.Listen("unix", path)
	if err != nil {
		return nil, "", err
	}
	if err := os.Chmod(path, 0o600); err != nil {
		l.Close()
		return nil, "", err
	}
	return l, path, nil
}

// Ping dials and exchanges one line.
func Ping(path string) (string, error) {
	c, err := net.Dial("unix", path)
	if err != nil {
		return "", err
	}
	defer c.Close()
	if _, err := fmt.Fprint(c, "ping\n"); err != nil {
		return "", err
	}
	buf := make([]byte, 64)
	n, err := c.Read(buf)
	if err != nil {
		return "", err
	}
	return string(buf[:n]), nil
}
