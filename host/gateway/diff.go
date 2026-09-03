// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"os/exec"
	"strings"
)

// DiffKind mirrors P11: working-tree, staged, unstaged, untracked + past commits.
func GitDiff(dir, kind string) (string, error) {
	var args []string
	switch kind {
	case "staged":
		args = []string{"diff", "--cached", "--", "."}
	case "unstaged":
		args = []string{"diff", "--", "."}
	case "untracked":
		args = []string{"ls-files", "--others", "--exclude-standard"}
	case "working":
		args = []string{"diff", "HEAD", "--", "."}
	default:
		args = []string{"diff", "HEAD~1", "HEAD", "--", "."}
	}
	cmd := exec.Command("git", args...)
	cmd.Dir = dir
	out, err := cmd.Output()
	if err != nil {
		return "", err
	}
	// Binary guard: never render binary as text.
	if strings.Contains(string(out), "Binary files") {
		return "", ErrBinary
	}
	if len(out) > 1<<20 {
		out = out[:1<<20]
	}
	return string(out), nil
}
