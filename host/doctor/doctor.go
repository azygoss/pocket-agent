// SPDX-License-Identifier: GPL-3.0-or-later
// Package doctor: isolated checks with bitmask exit codes.
// Mevcut kontrol sırası bitmask uyumluluğu için korunur; yeni kontroller
// sona eklenir.
package doctor

import (
	"fmt"
	"net"
	"net/url"
	"os"
	"os/exec"
	"syscall"
	"time"
)

type Check struct {
	Name string
	OK   bool
	Info string
}

// BackendURL'i config dışından çözmek için opsiyonel resolver — main.go
// config.Load'a bağlar; testler doğrudan ayarlayabilir.
var BackendURL = func() string { return "" }

func Run() []Check {
	checks := []Check{}
	bin := func(name string) Check {
		_, err := exec.LookPath(name)
		return Check{Name: name, OK: err == nil, Info: where(name)}
	}
	for _, b := range []string{"ssh", "mosh-server", "et", "tmux", "zellij", "herdr"} {
		checks = append(checks, bin(b))
	}
	// gateway port free or already serving?
	checks = append(checks, gatewayPort())
	// backend reachable? (env-driven, never fails closed here)
	checks = append(checks, backendProbe())
	// perms
	home, _ := os.UserHomeDir()
	fi, err := os.Stat(home + "/.config/pocket-agent/config.toml")
	ok := err == nil
	info := "missing (run service install)"
	if ok {
		ok = fi.Mode().Perm()&0o077 == 0
		info = fmt.Sprintf("mode %o", fi.Mode().Perm())
	}
	checks = append(checks, Check{Name: "perms", OK: ok, Info: info})

	// ── genişletilmiş kontroller (bitmask sonuna ekli) ─────────────────────
	checks = append(checks, sshdProbe())
	checks = append(checks, diskFree(home))
	checks = append(checks, systemdUser())
	return checks
}

// gatewayPort: 24543 ya boş (serve edilebilir) ya zaten gateway dinliyor.
// Yabancı bir süreç tutuyorsa uyarı.
func gatewayPort() Check {
	addr := "127.0.0.1:24543"
	conn, err := net.DialTimeout("tcp", addr, 500*time.Millisecond)
	if err != nil {
		return Check{Name: "gateway", OK: true, Info: addr + " boş (serve edilebilir)"}
	}
	conn.Close()
	return Check{Name: "gateway", OK: true, Info: addr + " dinliyor (gateway ayakta?)"}
}

// backendProbe: yapılandırılmış backend'e TCP dial (3s). URL yoksa bilgi
// amaçlı atlanır — doctor hiçbir zaman backend yokluğunda fail olmaz.
func backendProbe() Check {
	raw := BackendURL()
	if raw == "" {
		return Check{Name: "backend", OK: true, Info: "yapılandırılmadı (set backend_url)"}
	}
	u, err := url.Parse(raw)
	if err != nil || u.Host == "" {
		return Check{Name: "backend", OK: false, Info: "geçersiz URL: " + raw}
	}
	host := u.Host
	if _, _, err := net.SplitHostPort(host); err != nil {
		if u.Scheme == "http" {
			host += ":80"
		} else {
			host += ":443"
		}
	}
	t0 := time.Now()
	conn, err := net.DialTimeout("tcp", host, 3*time.Second)
	if err != nil {
		return Check{Name: "backend", OK: false, Info: "erişilemedi: " + host}
	}
	conn.Close()
	return Check{Name: "backend", OK: true, Info: fmt.Sprintf("%s (%dms)", host, time.Since(t0).Milliseconds())}
}

// sshdProbe: Android'in bağlanacağı sshd :22 dinliyor mu?
func sshdProbe() Check {
	conn, err := net.DialTimeout("tcp", "127.0.0.1:22", 1*time.Second)
	if err != nil {
		return Check{Name: "sshd", OK: false, Info: ":22 dinlemiyor — openssh-server kur/enable et"}
	}
	conn.Close()
	return Check{Name: "sshd", OK: true, Info: ":22 dinliyor"}
}

// diskFree: home dosya sisteminde boş alan (%5 altı veya <100MB fail).
func diskFree(home string) Check {
	var st syscall.Statfs_t
	if err := syscall.Statfs(home, &st); err != nil {
		return Check{Name: "disk", OK: true, Info: "ölçülemedi"}
	}
	free := st.Bavail * uint64(st.Bsize)
	total := st.Blocks * uint64(st.Bsize)
	pct := 0
	if total > 0 {
		pct = int(free * 100 / total)
	}
	ok := free > 100<<20 && pct > 5
	return Check{Name: "disk", OK: ok, Info: fmt.Sprintf("%.1fGiB boş (%%%d)", float64(free)/(1<<30), pct)}
}

// systemdUser: user unit'leri çalışıyor mu? (service install için önkoşul)
func systemdUser() Check {
	if _, err := exec.LookPath("systemctl"); err != nil {
		return Check{Name: "systemd", OK: false, Info: "systemctl yok — daemon'ı elle başlat"}
	}
	out, err := exec.Command("systemctl", "--user", "is-system-running").Output()
	_ = out
	if err != nil {
		return Check{Name: "systemd", OK: false, Info: "systemctl --user yanıt vermiyor (lingering?)"}
	}
	return Check{Name: "systemd", OK: true, Info: "user manager aktif"}
}

func where(name string) string {
	p, err := exec.LookPath(name)
	if err != nil {
		return "missing"
	}
	return p
}

// ExitCode bitmask: bit i set when check i fails.
func ExitCode(checks []Check) int {
	code := 0
	for i, c := range checks {
		if !c.OK {
			code |= 1 << i
		}
	}
	return code
}
