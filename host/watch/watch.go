// SPDX-License-Identifier: GPL-3.0-or-later
// Package watch: /proc taramasıyla agent process'lerini izler (P12 tamamlayıcı).
// Config şemasına ihtiyaç duymaz — 12 agent'ın hepsi için çalışır; config'e
// wire edilemeyen agent'lar (opencode/kimi/grok/pi/omp/hermes/antigravity)
// buradan session_started/session_ended olayı üretir. Semantik: process'in
// ilk görülmesi = started, kaybolması = ended.
package watch

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Agents: izlenen process adları (comm + argv[0] basename eşleşmesi).
var Agents = map[string]bool{
	"claude": true, "codex": true, "opencode": true, "cursor-agent": true,
	"cursor": true, "kimi": true, "grok": true, "pi": true, "omp": true,
	"hermes": true, "gemini": true, "antigravity": true, "qwen": true,
	"qwen-code": true,
}

type Proc struct {
	PID   int
	Agent string
}

// Scan: procRoot altındaki çalışan agent process'lerini döner
// (testte sahte /proc kökü verilir; prod'da "/proc").
func Scan(procRoot string) (map[int]string, error) {
	entries, err := os.ReadDir(procRoot)
	if err != nil {
		return nil, err
	}
	cur := map[int]string{}
	for _, e := range entries {
		pid, err := strconv.Atoi(e.Name())
		if err != nil {
			continue
		}
		if name := agentOf(filepath.Join(procRoot, e.Name())); name != "" {
			cur[pid] = name
		}
	}
	return cur, nil
}

// agentOf: /proc/<pid>/comm veya argv[0] basename agent adıysa onu döner.
func agentOf(pdir string) string {
	if b, err := os.ReadFile(filepath.Join(pdir, "comm")); err == nil {
		if a := strings.TrimSpace(string(b)); Agents[a] {
			return a
		}
	}
	if b, err := os.ReadFile(filepath.Join(pdir, "cmdline")); err == nil && len(b) > 0 {
		argv0 := strings.SplitN(string(b), "\x00", 2)[0]
		if a := filepath.Base(argv0); Agents[a] {
			return a
		}
	}
	return ""
}

// Tracker: daemon yaşamı boyunca geçişleri takip eder.
type Tracker struct {
	live map[int]string // pid -> agent
}

func NewTracker() *Tracker { return &Tracker{live: map[int]string{}} }

// Diff: yeni taramayı önceki durumla karşılaştırır; (başlayan, biten)
// process'leri döner ve iç durumu günceller.
func (t *Tracker) Diff(cur map[int]string) (started, ended []Proc) {
	for pid, agent := range cur {
		if _, ok := t.live[pid]; !ok {
			started = append(started, Proc{PID: pid, Agent: agent})
		}
	}
	for pid, agent := range t.live {
		if _, ok := cur[pid]; !ok {
			ended = append(ended, Proc{PID: pid, Agent: agent})
		}
	}
	t.live = cur
	return started, ended
}
