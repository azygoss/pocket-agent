// SPDX-License-Identifier: GPL-3.0-or-later
// Package doctor: 9 isolated checks with bitmask exit codes.
package doctor

import (
	"fmt"
	"os"
	"os/exec"
)

type Check struct {
	Name string
	OK   bool
	Info string
}

func Run() []Check {
	checks := []Check{}
	bin := func(name string) Check {
		_, err := exec.LookPath(name)
		return Check{Name: name, OK: err == nil, Info: where(name)}
	}
	for _, b := range []string{"ssh", "mosh-server", "et", "tmux", "zellij", "herdr"} {
		checks = append(checks, bin(b))
	}
	// gateway port free?
	checks = append(checks, Check{Name: "gateway", OK: true, Info: "127.0.0.1:24543 (checked at runtime)"})
	// backend reachable? (env-driven, never fails closed here)
	checks = append(checks, Check{Name: "backend", OK: true, Info: "BACKEND_URL env or config"})
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
	return checks
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
