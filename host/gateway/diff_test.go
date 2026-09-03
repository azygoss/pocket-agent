// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"os"
	"os/exec"
	"testing"
)

func TestGitDiffKinds(t *testing.T) {
	if _, err := exec.LookPath("git"); err != nil {
		t.Skip("git missing")
	}
	dir := t.TempDir()
	run := func(args ...string) {
		cmd := exec.Command("git", args...)
		cmd.Dir = dir
		cmd.Env = append(os.Environ(), "GIT_CONFIG_NOSYSTEM=1", "HOME="+dir)
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("%v: %s", args, out)
		}
	}
	run("init")
	run("config", "user.email", "t@t")
	run("config", "user.name", "t")
	os.WriteFile(dir+"/a.txt", []byte("1\n"), 0o600)
	run("add", ".")
	run("commit", "-m", "init")
	os.WriteFile(dir+"/a.txt", []byte("2\n"), 0o600)
	if _, err := GitDiff(dir, "unstaged"); err != nil {
		t.Fatal(err)
	}
	if _, err := GitDiff(dir, "working"); err != nil {
		t.Fatal(err)
	}
}
