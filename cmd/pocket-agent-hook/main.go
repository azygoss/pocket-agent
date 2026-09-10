// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent-hook: single Go CLI + daemon client (stdlib-only, no emulator needed).
package main

import (
	"bufio"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/pocket-agent/pocket-agent/host/config"
	"github.com/pocket-agent/pocket-agent/host/doctor"
	"github.com/pocket-agent/pocket-agent/host/gateway"
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
	doctor.BackendURL = backendURL
	if len(os.Args) < 2 {
		fmt.Println("pocket-agent — self-hosted Android terminal companion")
		fmt.Println("usage: pocket-agent <command>  (tüm komutlar: pocket-agent help)")
		return
	}
	switch os.Args[1] {
	case "help", "--help", "-h":
		printHelp()
	case "onboard":
		cmdOnboard(os.Args[2:])
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
	case "gateway":
		cmdGateway(os.Args[2:])
	case "hooks":
		cmdHooks(os.Args[2:])
	case "emit":
		cmdEmit(os.Args[2:])
	case "emit-hook":
		cmdEmitHook(os.Args[2:])
	case "daemon":
		cmdDaemon(os.Args[2:])
	case "service":
		cmdService(os.Args[2:])
	case "servers":
		cmdServers(os.Args[2:])
	case "logs":
		cmdLogs(os.Args[2:])
	case "update":
		// Signed manifest required; unsigned always refused (P03 gate).
		fmt.Fprintln(os.Stderr, "update refused: missing signed manifest (checksum+sig required)")
		os.Exit(3)
	case "set":
		cmdSet(os.Args[2:])
	case "usage":
		fmt.Println("{\"note\":\"usage snapshots via backend GET /v1/usages (24h)\"}")
	case "diff":
		cmdDiff(os.Args[2:])
	case "context":
		fmt.Println("{\"cwd\":\"\",\"appetizer\":false}")
	case "cwd-list":
		fmt.Println("cwd-list: tmux pane cwd via servers (see servers)")
	case "completion":
		cmdCompletion(os.Args[2:])
	case "pair":
		cmdPair(os.Args[2:])
	case "unpair":
		if len(os.Args) < 3 {
			fmt.Fprintln(os.Stderr, "usage: pocket-agent unpair <pair-KODU|deviceId>")
			os.Exit(2)
		}
		ak := filepath.Join(home(), ".ssh", "authorized_keys")
		id := os.Args[2]
		if !strings.HasPrefix(id, "pair-") && len(id) == 9 {
			id = "pair-" + id
		}
		n, err := pairing.RevokeAuthorizedKey(ak, id)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Printf("unpair %s: %d anahtar kaldırıldı\n", id, n)
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q — tüm komutlar: pocket-agent help\n", os.Args[1])
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
		ak := filepath.Join(h, ".ssh", "authorized_keys")
		b, _ := os.ReadFile(ak)
		if has("--json") {
			json.NewEncoder(os.Stdout).Encode(map[string]any{
				"host_id":               cfg.HostID,
				"backend":               cfg.BackendURL,
				"authorized_keys_bytes": len(b),
				"schema":                1,
			})
			return
		}
		fmt.Printf("host_id=%s backend=%s\n", cfg.HostID, cfg.BackendURL)
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
	exe, _ := os.Executable()
	claudeCfg := filepath.Join(h, ".claude", "settings.json")
	codexCfg := filepath.Join(h, ".codex", "config.toml")
	switch args[0] {
	case "install":
		if err := installHooks(h, exe); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	case "uninstall":
		if ok, _ := hooks.UninstallClaude(claudeCfg); ok {
			fmt.Println("temizlendi " + claudeCfg)
		}
		if ok, _ := hooks.UninstallCodex(codexCfg); ok {
			fmt.Println("temizlendi " + codexCfg)
		}
		// Diğer config'lerdeki (JSON dahil) eski marker'ları da söker.
		for _, p := range markerPaths(h) {
			if b, err := os.ReadFile(p); err == nil {
				if cleaned := hooks.StripMarkers(string(b)); cleaned != string(b) {
					_ = os.WriteFile(p, []byte(cleaned), 0o600)
					fmt.Println("marker temizlendi " + p)
				}
			}
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
	case "install-gateway":
		cwd, _ := os.Getwd()
		p, err := service.InstallGateway(h, exe, cwd)
		if err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
		fmt.Println("installed " + p + " (root=" + cwd + ")")
	case "status":
		if has("--json") {
			json.NewEncoder(os.Stdout).Encode(map[string]any{
				"daemon":  service.Status(h),
				"gateway": service.GatewayStatus(h),
				"schema":  1,
			})
			return
		}
		fmt.Println(service.Status(h))
		fmt.Println("gateway: " + service.GatewayStatus(h))
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

func cfgLoad() (config.Config, error) { return config.Load(config.DefaultPath()) }

func backendURL() string {
	if u := os.Getenv("POCKET_BACKEND"); u != "" {
		return u
	}
	cfg, _ := cfgLoad()
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

func gatewayDiff(dir, kind string) (string, error) { return gateway.GitDiff(dir, kind) }

func cmdSet(args []string) {
	if len(args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: set <key> <value>  (keys: backend_url, host_id, tenant)")
		os.Exit(2)
	}
	p := config.DefaultPath()
	c, _ := config.Load(p)
	switch args[0] {
	case "backend_url":
		c.BackendURL = args[1]
	case "host_id":
		c.HostID = args[1]
	case "tenant":
		c.Tenant = args[1]
	default:
		fmt.Fprintln(os.Stderr, "unknown key")
		os.Exit(2)
	}
	if err := config.Save(p, c); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	fmt.Println("saved " + p)
}

// markerPaths: eski sürümlerin yorum-marker yazmış olabileceği tüm config
// yolları (onarım için; .claude.json dahil — hook'lar artık settings.json'da).
func markerPaths(h string) []string {
	paths := []string{filepath.Join(h, ".claude.json")}
	for _, a := range hooks.All() {
		paths = append(paths, filepath.Join(h, a.ConfigFile))
	}
	return paths
}

// installHooks: gerçek wiring — claude settings.json hooks objesi, codex
// notify satırı. Diğer agent'ların config formatı henüz wire edilmediğinden
// marker YAZILMAZ (yorum satırları JSON config'leri bozuyordu); eski
// kurulumlardan kalan marker'lar onarım için temizlenir.
func installHooks(h, exe string) error {
	claudeCfg := filepath.Join(h, ".claude", "settings.json")
	if ok, err := hooks.InstallClaude(claudeCfg, exe); err != nil {
		return err
	} else if ok {
		fmt.Println("wired " + claudeCfg + " (session/notification/stop)")
	}
	codexCfg := filepath.Join(h, ".codex", "config.toml")
	if ok, err := hooks.InstallCodex(codexCfg, exe); err != nil {
		return err
	} else if ok {
		fmt.Println("wired " + codexCfg + " (notify)")
	}
	for _, p := range markerPaths(h) {
		if p == claudeCfg || p == codexCfg {
			continue
		}
		if b, err := os.ReadFile(p); err == nil {
			if cleaned := hooks.StripMarkers(string(b)); cleaned != string(b) {
				if err := os.WriteFile(p, []byte(cleaned), 0o600); err != nil {
					return err
				}
				fmt.Println("repaired " + p + " (marker comments removed)")
			}
		}
	}
	return nil
}

func cmdLogs(args []string) {
	n := 50
	if len(args) > 0 {
		fmt.Sscanf(args[0], "%d", &n)
	}
	p := filepath.Join(home(), ".local", "state", "pocket-agent", "journal.jsonl")
	f, err := os.Open(p)
	if err != nil {
		fmt.Println("journal yok:", p)
		return
	}
	defer f.Close()
	var lines []string
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 1<<20), 1<<20)
	for sc.Scan() {
		lines = append(lines, sc.Text())
	}
	start := len(lines) - n
	if start < 0 {
		start = 0
	}
	for _, l := range lines[start:] {
		fmt.Println(l)
	}
}

func cmdDiff(args []string) {
	kind := "working"
	if len(args) > 0 {
		kind = args[0]
	}
	out, err := gatewayDiff(".", kind)
	if err != nil {
		fmt.Fprintln(os.Stderr, "diff: "+err.Error())
		os.Exit(1)
	}
	fmt.Print(out)
}
