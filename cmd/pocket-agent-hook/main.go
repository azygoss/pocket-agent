// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent-hook: single Go CLI + daemon client (stdlib-only skeleton).
// Full Cobra/TOML/SQLite/systemd port lands incrementally; command surface frozen now.
package main

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/pocket-agent/pocket-agent/host/doctor"
)

const version = "0.1.0-p03"

func main() {
	if len(os.Args) < 2 {
		// pocket-agent [directory]: idempotent tmux open/attach (stub prints intent).
		fmt.Println("pocket-agent: no args => would open/attach tmux session in cwd (P03 stub)")
		return
	}
	switch os.Args[1] {
	case "version", "--version", "-V":
		fmt.Println("pocket-agent-hook " + version)
	case "status":
		asJSON := false
		for _, a := range os.Args {
			if a == "--json" {
				asJSON = true
			}
		}
		if asJSON {
			json.NewEncoder(os.Stdout).Encode(map[string]any{"version": version, "schema": 1, "ok": true})
		} else {
			fmt.Println("ok " + version)
		}
	case "doctor":
		checks := doctor.Run()
		for _, c := range checks {
			st := "ok"
			if !c.OK {
				st = "FAIL"
			}
			fmt.Printf("%-12s %s %s\n", c.Name, st, c.Info)
		}
		os.Exit(doctor.ExitCode(checks))
	case "probe":
		fmt.Println("probe: ssh/mosh/et/tmux checks via doctor (see: pocket-agent doctor)")
	case "host", "hooks", "pair", "unpair", "servers", "service", "set", "logs", "usage", "diff", "context", "cwd-list", "completion", "update":
		fmt.Printf("pocket-agent %s: P03 skeleton — full behavior in next slice (see plan.md P03/P04)\n", os.Args[1])
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n", os.Args[1])
		os.Exit(2)
	}
}
