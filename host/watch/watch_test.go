// SPDX-License-Identifier: GPL-3.0-or-later
package watch

import (
	"os"
	"path/filepath"
	"strconv"
	"testing"
)

// fakeProc: sahte /proc ağacı — pid dizini + comm dosyası.
func fakeProc(t *testing.T, procs map[int]string) string {
	root := t.TempDir()
	for pid, comm := range procs {
		d := filepath.Join(root, strconv.Itoa(pid))
		if err := os.MkdirAll(d, 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(d, "comm"), []byte(comm+"\n"), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	return root
}

func TestScanFindsAgents(t *testing.T) {
	root := fakeProc(t, map[int]string{100: "claude", 200: "sshd", 300: "gemini"})
	cur, err := Scan(root)
	if err != nil {
		t.Fatal(err)
	}
	if len(cur) != 2 || cur[100] != "claude" || cur[300] != "gemini" {
		t.Fatalf("cur=%v", cur)
	}
}

func TestScanArgv0Match(t *testing.T) {
	root := t.TempDir()
	d := filepath.Join(root, "55")
	os.MkdirAll(d, 0o755)
	os.WriteFile(filepath.Join(d, "comm"), []byte("node\n"), 0o644)
	os.WriteFile(filepath.Join(d, "cmdline"), []byte("/usr/local/bin/qwen\x00--chat\x00"), 0o644)
	cur, _ := Scan(root)
	if cur[55] != "qwen" {
		t.Fatalf("argv0 eşleşmedi: %v", cur)
	}
}

func TestTrackerDiff(t *testing.T) {
	tr := NewTracker()
	started, ended := tr.Diff(map[int]Proc{1: {PID: 1, Agent: "claude", Session: "tmux:main"}})
	if len(started) != 1 || started[0].Agent != "claude" || len(ended) != 0 {
		t.Fatalf("%v %v", started, ended)
	}
	// Aynı taramada geçiş yok.
	started, ended = tr.Diff(map[int]Proc{1: {PID: 1, Agent: "claude", Session: "tmux:main"}})
	if len(started) != 0 || len(ended) != 0 {
		t.Fatal("stabil durumda geçiş olmamalı")
	}
	// Process kaybolur → ended, session hatırlanır.
	started, ended = tr.Diff(map[int]Proc{})
	if len(ended) != 1 || ended[0].PID != 1 || ended[0].Session != "tmux:main" {
		t.Fatalf("ended=%v", ended)
	}
}

func TestSessionFor(t *testing.T) {
	// Sahte /proc: 300(claude) -> 200(shell, pane) -> 1(init)
	root := fakeProc(t, map[int]string{200: "bash", 300: "claude"})
	os.WriteFile(filepath.Join(root, "300", "status"), []byte("Name: claude\nPPid:\t200\n"), 0o644)
	os.WriteFile(filepath.Join(root, "200", "status"), []byte("Name: bash\nPPid:\t1\n"), 0o644)
	panes := map[int]string{200: "work"}
	if s := SessionFor(root, 300, panes); s != "tmux:work" {
		t.Fatalf("session=%q", s)
	}
	// tmux'da olmayan process → ""
	if s := SessionFor(root, 200, map[int]string{}); s != "" {
		t.Fatalf("session=%q", s)
	}
}
