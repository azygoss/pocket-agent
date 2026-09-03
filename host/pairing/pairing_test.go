// SPDX-License-Identifier: GPL-3.0-or-later
package pairing

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestAuthorizedKeysScopedRevoke(t *testing.T) {
	dir := t.TempDir()
	p := filepath.Join(dir, "authorized_keys")
	_ = os.WriteFile(p, []byte("ssh-ed25519 AAAA user@other\n"), 0o600)
	if err := AddAuthorizedKey(p, "device:1", "ssh-ed25519 BBBB"); err != nil {
		t.Fatal(err)
	}
	if err := AddAuthorizedKey(p, "device:1", "ssh-ed25519 BBBB"); err != nil {
		t.Fatal(err)
	}
	cur, _ := os.ReadFile(p)
	if strings.Count(string(cur), "BBBB") != 1 {
		t.Fatal("add must be idempotent")
	}
	n, err := RevokeAuthorizedKey(p, "device:1")
	if err != nil || n != 1 {
		t.Fatalf("revoke: %d %v", n, err)
	}
	cur, _ = os.ReadFile(p)
	if !strings.Contains(string(cur), "user@other") || strings.Contains(string(cur), "BBBB") {
		t.Fatal("revoke must remove only own marker line")
	}
}

func TestTOFUHadStop(t *testing.T) {
	p := filepath.Join(t.TempDir(), "known_hosts_pa")
	if err := CheckTOFU(p, "h:22", "SHA256:AAA"); err != nil {
		t.Fatal(err)
	}
	if err := CheckTOFU(p, "h:22", "SHA256:AAA"); err != nil {
		t.Fatal("same fp must pass")
	}
	if err := CheckTOFU(p, "h:22", "SHA256:BBB"); err != ErrHostKeyChanged {
		t.Fatalf("changed fp must hard-stop, got %v", err)
	}
}

func TestQREncode(t *testing.T) {
	q := QRPayload{Version: 1, BackendURL: "https://x", Code: "C", HostID: "host:1", SSHUser: "u", SSHHost: "h", SSHPort: 22, Secret: NewSecret()}
	if !strings.HasPrefix(q.Encode(), "pa1|") {
		t.Fatal("qr version prefix")
	}
}
