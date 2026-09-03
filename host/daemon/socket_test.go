// SPDX-License-Identifier: GPL-3.0-or-later
package daemon

import (
	"net"
	"os"
	"testing"
)

func TestSocket0600(t *testing.T) {
	dir := t.TempDir()
	l, path, err := Listen(dir)
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	fi, _ := os.Stat(path)
	if fi.Mode().Perm() != 0o600 {
		t.Fatalf("socket must be 0600, got %o", fi.Mode().Perm())
	}
	go func() {
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		buf := make([]byte, 64)
		n, _ := c.Read(buf)
		c.Write([]byte("pong:" + string(buf[:n])))
	}()
	got, err := Ping(path)
	if err != nil || got != "pong:ping\n" {
		t.Fatalf("ping: %q %v", got, err)
	}
	_ = net.Dial
}
