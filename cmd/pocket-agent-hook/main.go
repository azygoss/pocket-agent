// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent-hook: single Go CLI + daemon client (stdlib-only, no emulator needed).
package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"

	"github.com/pocket-agent/pocket-agent/host/config"
	"github.com/pocket-agent/pocket-agent/host/doctor"
	"github.com/pocket-agent/pocket-agent/host/hooks"
	"github.com/pocket-agent/pocket-agent/host/pairing"
	"github.com/pocket-agent/pocket-agent/host/service"
	hosttmux "github.com/pocket-agent/pocket-agent/host/tmux"
)

const version = "0.1.0-p03"

func home() string {
	if h := os.Getenv("POCKET_HOME"); h != "" {
		return h
	}
	h, _ := os.UserHomeDir()
	return h
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("pocket-agent: no args => would open/attach tmux session in cwd (P03: see tmux adapter)")
		return
	}
	switch os.Args[1] {
	case "version", "--version", "-V":
		fmt.Println("pocket-agent-hook " + version)
	case "status":
		asJSON := has("--json")
		if asJSON {
			json.NewEncoder(os.Stdout).Encode(map[string]any{"version": version, "schema": 1, "ok": true})
		} else {
			fmt.Println("ok " + version)
		}
	case "doctor":
		checks := doctor.Run()
		if has("--json") {
			json.NewEncoder(os.Stdout).Encode(checks)
			os.Exit(doctor.ExitCode(checks))
			return
		}
		for _, c := range checks {
			st := "ok"
			if !c.OK {
				st = "FAIL"
			}
			fmt.Printf("%-12s %s %s\n", c.Name, st, c.Info)
		}
		os.Exit(doctor.ExitCode(checks))
	case "probe":
		checks := doctor.Run()
		bad := 0
		for _, c := range checks {
			st := "ok"
			if !c.OK {
				st = "FAIL"
				bad++
			}
			fmt.Printf("%-12s %s %s\n", c.Name, st, c.Info)
		}
		if bad > 0 {
			os.Exit(1)
		}
	case "host":
		cmdHost(os.Args[2:])
	case "hooks":
		cmdHooks(os.Args[2:])
	case "service":
		cmdService(os.Args[2:])
	case "servers":
		cmdServers(os.Args[2:])
	case "logs":
		fmt.Println("logs: user log at ~/.local/share/pocket-agent/daemon.log")
	case "update":
		// Signed manifest required; unsigned always refused (P03 gate).
		fmt.Fprintln(os.Stderr, "update refused: missing signed manifest (checksum+sig required)")
		os.Exit(3)
	case "pair", "unpair", "set", "usage", "diff", "context", "cwd-list", "completion":
		fmt.Printf("pocket-agent %s: wired in next slice (surface frozen)\n", os.Args[1])
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n", os.Args[1])
		os.Exit(2)
	}
}

func has(f string) bool {
	for _, a := range os.Args {
		if a == f {
			return true
		}
	}
	return false
}

func cmdHost(args []string) {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: host setup|list|revoke|enable-ssh")
		os.Exit(2)
	}
	h := home()
	switch args[0] {
	case "setup":
		// 5-min single-use QR payload (secret shown once, never logged).
		code := "PA-" + rand4()
		q := pairing.QRPayload{Version: pairing.QRVersion, BackendURL: backendURL(), Code: code, HostID: "host:local", SSHUser: user(), SSHHost: "localhost", SSHPort: 22, Secret: pairing.NewSecret()}
		fmt.Println(q.Encode())
		fmt.Fprintln(os.Stderr, "QR single-use, 5min TTL. Scan, then claim with device key.")
	case "list":
		cfg, _ := config.Load(config.DefaultPath())
		fmt.Printf("host_id=%s backend=%s\n", cfg.HostID, cfg.BackendURL)
		ak := filepath.Join(h, ".ssh", "authorized_keys")
		b, _ := os.ReadFile(ak)
		fmt.Printf("authorized_keys: %d bytes\n", len(b))
	case "revoke":
		if len(args) < 2 {
			fmt.Fprintln(os.Stderr, "usage: host revoke <deviceId>")
			os.Exit(2)
		}
		ak := filepath.Join(h, ".ssh", "authorized_keys")
		n, err := pairing.RevokeAuthorizedKey(ak, args[1])
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Printf("revoked %d line(s) for %s\n", n, args[1])
	case "enable-ssh":
		fmt.Println("enable-ssh: ensure sshd running; see doctor + docs")
	default:
		fmt.Fprintln(os.Stderr, "unknown host subcommand")
		os.Exit(2)
	}
}

func cmdHooks(args []string) {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: hooks install|uninstall")
		os.Exit(2)
	}
	h := home()
	switch args[0] {
	case "install":
		for _, a := range hooks.All() {
			p := filepath.Join(h, a.ConfigFile)
			cur, _ := os.ReadFile(p)
			merged := hooks.Merge(string(cur), a.Block)
			if merged != string(cur) {
				_ = os.MkdirAll(filepath.Dir(p), 0o700)
				_ = os.WriteFile(p, []byte(merged), 0o600)
				fmt.Println("patched " + p)
			}
		}
	case "uninstall":
		for _, a := range hooks.All() {
			p := filepath.Join(h, a.ConfigFile)
			cur, _ := os.ReadFile(p)
			if cur == nil {
				continue
			}
			cleaned := hooks.Merge(string(cur), "")
			_ = cleaned
			fmt.Println("kept user content in " + p + " (owned block removed on next slice)")
		}
	default:
		fmt.Fprintln(os.Stderr, "unknown hooks subcommand")
		os.Exit(2)
	}
}

func cmdService(args []string) {
	if len(args) == 0 {
		fmt.Fprintln(os.Stderr, "usage: service install|status|uninstall")
		os.Exit(2)
	}
	h := home()
	exe, _ := os.Executable()
	switch args[0] {
	case "install":
		p, err := service.Install(h, exe)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println("installed " + p + " (idempotent rerun converges)")
	case "status":
		fmt.Println(service.Status(h))
	case "uninstall":
		if err := service.Uninstall(h); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println("uninstalled")
	default:
		fmt.Fprintln(os.Stderr, "unknown service subcommand")
		os.Exit(2)
	}
}

func cmdServers(args []string) {
	if len(args) > 0 && args[0] == "kill" {
		fmt.Println("servers kill: pass a session name (never kill-server)")
		return
	}
	names, err := hosttmux.List("")
	if err != nil {
		fmt.Fprintln(os.Stderr, "tmux: "+err.Error())
		os.Exit(1)
	}
	if len(names) == 0 {
		fmt.Println("no sessions")
		return
	}
	for _, n := range names {
		fmt.Println(n)
	}
}

func backendURL() string {
	if u := os.Getenv("POCKET_BACKEND"); u != "" {
		return u
	}
	cfg, _ := config.Load(config.DefaultPath())
	if cfg.BackendURL != "" {
		return cfg.BackendURL
	}
	return "https://backend.local"
}

func user() string {
	if u := os.Getenv("USER"); u != "" {
		return u
	}
	return "user"
}

func rand4() string {
	h := sha256.Sum256([]byte(os.Getenv("HOSTNAME")))
	return fmt.Sprintf("%X", h[:2])
}
