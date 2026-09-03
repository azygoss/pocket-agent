// SPDX-License-Identifier: GPL-3.0-or-later
// Package tmux: real multiplexer control via tmux CLI (P09).
// Idempotent new-session; kill is scoped to our socket/name.
package tmux

import (
	"os/exec"
	"strings"
)

// List returns session names on the given socket (default server when socket=="").
func List(socket string) ([]string, error) {
	args := []string{}
	if socket != "" {
		args = append(args, "-L", socket)
	}
	args = append(args, "list-sessions", "-F", "#{session_name}")
	out, err := exec.Command("tmux", args...).Output()
	if err != nil {
		if strings.Contains(string(out), "no server running") || strings.Contains(err.Error(), "exit status 1") {
			return []string{}, nil
		}
		return nil, err
	}
	var names []string
	for _, l := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		if l != "" {
			names = append(names, l)
		}
	}
	return names, nil
}

// Ensure creates name if absent (idempotent), detached.
func Ensure(socket, name, dir string) error {
	names, err := List(socket)
	if err != nil {
		return err
	}
	for _, n := range names {
		if n == name {
			return nil
		}
	}
	args := []string{}
	if socket != "" {
		args = append(args, "-L", socket)
	}
	args = append(args, "new-session", "-d", "-s", name)
	cmd := exec.Command("tmux", args...)
	if dir != "" {
		cmd.Dir = dir
	}
	return cmd.Run()
}

// Kill removes one session (scoped, never kill-server).
func Kill(socket, name string) error {
	args := []string{}
	if socket != "" {
		args = append(args, "-L", socket)
	}
	args = append(args, "kill-session", "-t", name)
	return exec.Command("tmux", args...).Run()
}
