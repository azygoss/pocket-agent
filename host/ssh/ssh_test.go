// SPDX-License-Identifier: GPL-3.0-or-later
package ssh

import (
	"errors"
	"fmt"
	"net"
	"os"
	"os/user"
	"os/exec"
	"path/filepath"
	"testing"
	"time"
)

func freePort(t *testing.T) int {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port
}

func TestLiveLocalhost(t *testing.T) {
	if _, err := exec.LookPath("sshd"); err != nil {
		t.Skip("sshd missing")
	}
	if _, err := exec.LookPath("ssh-keygen"); err != nil {
		t.Skip("ssh-keygen missing")
	}
	dir := t.TempDir()
	run := func(args ...string) {
		cmd := exec.Command("ssh-keygen", args...)
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("ssh-keygen %v: %s", args, out)
		}
	}
	hostKey := filepath.Join(dir, "ssh_host_ed25519_key")
	run("-t", "ed25519", "-f", hostKey, "-N", "")
	userKey := filepath.Join(dir, "id_ed25519")
	run("-t", "ed25519", "-f", userKey, "-N", "")
	pub, _ := os.ReadFile(userKey + ".pub")
	authKeys := filepath.Join(dir, "authorized_keys")
	os.WriteFile(authKeys, pub, 0o600)
	port := freePort(t)
	// CI runner'ları root değildir ve root hesabı kilitlidir (shadow '!') —
	// pubkey denense bile sshd reddeder. Geçerli kullanıcıyla dial et;
	// bu makinede zaten root'uz, runner'da 'runner' olur.
	u, uerr := user.Current()
	if uerr != nil || u.Username == "" {
		t.Skip("current user unknown")
	}
	cfg := fmt.Sprintf(`Port %d
ListenAddress 127.0.0.1
HostKey %s
PidFile %s/sshd.pid
AuthorizedKeysFile %s
PasswordAuthentication no
PubkeyAuthentication yes
StrictModes no
UsePAM no
`, port, hostKey, dir, authKeys)
	cfgPath := filepath.Join(dir, "sshd_config")
	os.WriteFile(cfgPath, []byte(cfg), 0o600)
	sshd := exec.Command("/usr/sbin/sshd", "-f", cfgPath, "-E", filepath.Join(dir, "log"))
	if err := sshd.Start(); err != nil {
		t.Fatalf("sshd start: %v", err)
	}
	defer exec.Command("kill", fmt.Sprint(sshd.Process.Pid)).Run()
	time.Sleep(500 * time.Millisecond)

	keyPEM, _ := os.ReadFile(userKey)
	kh := filepath.Join(dir, "known_hosts_pa")
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	c, err := Dial(Options{User: u.Username, Addr: addr, PrivateKeyPEM: keyPEM, KnownHostsFile: kh, Timeout: 5 * time.Second})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	out, err := Run(c, "whoami; pwd")
	if err != nil || out == "" {
		t.Fatalf("run: %q %v", out, err)
	}
	c.Close()
	// second dial: TOFU pinned, must still pass
	c2, err := Dial(Options{User: u.Username, Addr: addr, PrivateKeyPEM: keyPEM, KnownHostsFile: kh, Timeout: 5 * time.Second})
	if err != nil {
		t.Fatalf("redial pinned: %v", err)
	}
	c2.Close()
	// wrong key => auth failed (no fallback)
	bad := append([]byte{}, keyPEM...)
	bad[len(bad)-10] ^= 0xff
	if _, err := Dial(Options{User: u.Username, Addr: addr, PrivateKeyPEM: bad, KnownHostsFile: filepath.Join(dir, "kh2"), Timeout: 5 * time.Second}); !errors.Is(err, ErrAuthFailed) {
		t.Fatalf("bad key must be ErrAuthFailed, got %v", err)
	}
}
