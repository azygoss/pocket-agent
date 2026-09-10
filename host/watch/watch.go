// SPDX-License-Identifier: GPL-3.0-or-later
// Package watch: /proc taramasıyla agent process'lerini izler (P12 tamamlayıcı).
// Config şemasına ihtiyaç duymaz — 12 agent'ın hepsi için çalışır; config'e
// wire edilemeyen agent'lar (opencode/kimi/grok/pi/omp/hermes/antigravity)
// buradan session_started/session_ended olayı üretir. Semantik: process'in
// ilk görülmesi = started, kaybolması = ended.
package watch

import (
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
)

// Agents: izlenen process adları (comm + argv[0] basename eşleşmesi).
var Agents = map[string]bool{
	"claude": true, "codex": true, "opencode": true, "cursor-agent": true,
	"cursor": true, "kimi": true, "grok": true, "pi": true, "omp": true,
	"hermes": true, "gemini": true, "antigravity": true, "qwen": true,
	"qwen-code": true, "devin": true,
}

type Proc struct {
	PID     int
	Agent   string
	Session string // "tmux:<ad>" veya "proc:<pid>" — ended olayı aynısını taşır
}

// TmuxPanes: pane_pid -> session_name. tmux yoksa/komut hata verirse boş map.
// Agent process'inin pane'i üzerinden tmux oturumu bulunur — uygulama
// "oturuma git" özelliğinde bu ada attach eder.
func TmuxPanes() map[int]string {
	out, err := exec.Command("tmux", "list-panes", "-a", "-F", "#{pane_pid} #{session_name}").Output()
	panes := map[int]string{}
	if err != nil {
		return panes
	}
	for _, line := range strings.Split(strings.TrimSpace(string(out)), "\n") {
		f := strings.Fields(line)
		if len(f) >= 2 {
			if pid, err := strconv.Atoi(f[0]); err == nil {
				panes[pid] = f[1]
			}
		}
	}
	return panes
}

// SessionFor: pid'in ppid zincirini tırmanıp tmux pane'ine denk gelen atayı
// bulur; bulursa "tmux:<oturum>" döner. Bulunamazsa "".
func SessionFor(procRoot string, pid int, panes map[int]string) string {
	for depth := 0; pid > 1 && depth < 16; depth++ {
		if s, ok := panes[pid]; ok {
			return "tmux:" + s
		}
		b, err := os.ReadFile(filepath.Join(procRoot, strconv.Itoa(pid), "status"))
		if err != nil {
			return ""
		}
		next := 0
		for _, l := range strings.Split(string(b), "\n") {
			if strings.HasPrefix(l, "PPid:") {
				next, _ = strconv.Atoi(strings.TrimSpace(strings.TrimPrefix(l, "PPid:")))
				break
			}
		}
		if next <= 1 {
			return ""
		}
		pid = next
	}
	return ""
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
	live map[int]Proc // pid -> proc (ended olayı session'ı buradan hatırlar)
}

func NewTracker() *Tracker { return &Tracker{live: map[int]Proc{}} }

// Diff: yeni taramayı önceki durumla karşılaştırır; (başlayan, biten)
// process'leri döner ve iç durumu günceller.
func (t *Tracker) Diff(cur map[int]Proc) (started, ended []Proc) {
	for pid, p := range cur {
		if _, ok := t.live[pid]; !ok {
			started = append(started, p)
		}
	}
	for pid, p := range t.live {
		if _, ok := cur[pid]; !ok {
			ended = append(ended, p)
		}
	}
	t.live = cur
	return started, ended
}
