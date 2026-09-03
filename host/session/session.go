// SPDX-License-Identifier: GPL-3.0-or-later
// Package session: tmux/zellij/herdr adapters behind capability interface (P09).
// Herdr is integration-only (never reimplemented); hidden when binary missing.
package session

import "os/exec"

type Provider string

const (
	Shell  Provider = "SHELL"
	Tmux   Provider = "TMUX"
	Zellij Provider = "ZELLIJ"
	Herdr  Provider = "HERDR"
)

type Adapter struct {
	Provider  Provider
	Available bool
	Bin       string
}

func Detect() []Adapter {
	out := []Adapter{{Provider: Shell, Available: true}}
	for _, p := range []struct {
		prov Provider
		bin  string
	}{{Tmux, "tmux"}, {Zellij, "zellij"}, {Herdr, "herdr"}} {
		_, err := exec.LookPath(p.bin)
		out = append(out, Adapter{Provider: p.prov, Available: err == nil, Bin: p.bin})
	}
	return out
}

func Visible(in []Adapter) []Adapter {
	var v []Adapter
	for _, a := range in {
		if a.Available {
			v = append(v, a)
		}
	}
	return v
}
