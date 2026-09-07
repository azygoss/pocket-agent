// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"encoding/json"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/pocket-agent/pocket-agent/host/hooks"
)

// P14: /chat?path=<rel> — jail içi agent transcript'ini (JSONL) blok akışına
// çevirir. Tam içerik yalnız SSH tünelinde akar; backend hiç görmez.
// Format algılama: Claude ve Codex parser'ları sırayla denenir, çoğu blok
// üreten kazanır.

type chatBlock struct {
	Role     string `json:"role"` // message | tool | result | thinking
	Category string `json:"category"`
	Text     string `json:"text"`
}

func chatRole(category, text string) string {
	switch {
	case strings.HasPrefix(text, "tool:"):
		return "tool"
	case category == "task_complete":
		return "result"
	case category == "error":
		return "error"
	default:
		return "message"
	}
}

// ChatBlocks parses a transcript file into chat blocks. source boşsa hem
// Claude hem Codex denenir.
func ChatBlocks(fullPath string) ([]chatBlock, error) {
	claude, errC := hooks.ParseClaudeJSONL(fullPath)
	codex, errK := hooks.ParseCodexJSONL(fullPath)
	if errC != nil && errK != nil {
		return nil, errC
	}
	parsed := claude
	if len(codex) > len(claude) {
		parsed = codex
	}
	out := make([]chatBlock, 0, len(parsed))
	for _, p := range parsed {
		out = append(out, chatBlock{Role: chatRole(p.Category, p.Text), Category: p.Category, Text: p.Text})
	}
	return out, nil
}

func (s *Server) serveChat(w http.ResponseWriter, r *http.Request) {
	rel := strings.TrimPrefix(r.URL.Query().Get("path"), "/")
	if rel == "" || strings.Contains(rel, "..") {
		http.Error(w, "bad path", 400)
		return
	}
	// src=claude|codex → amaç-özel allowlist kökü (agent transcript dizini);
	// boş → workspace jail (varsayılan davranış).
	root := s.Root
	switch r.URL.Query().Get("src") {
	case "claude":
		root = filepath.Join(mustHome(), ".claude", "projects")
	case "codex":
		root = filepath.Join(mustHome(), ".codex", "sessions")
	}
	full, err := Jail(root, rel)
	if err != nil {
		http.Error(w, "rejected", 400)
		return
	}
	if !strings.HasSuffix(rel, ".jsonl") {
		http.Error(w, "only .jsonl transcripts", 400)
		return
	}
	blocks, err := ChatBlocks(full)
	if err != nil {
		http.Error(w, "unreadable transcript", 400)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"blocks": blocks})
}

var homeDir = os.UserHomeDir // testlerde değiştirilebilir

func mustHome() string {
	h, err := homeDir()
	if err != nil {
		return "/nonexistent"
	}
	return h
}

type transcriptEntry struct {
	Src   string `json:"src"`
	Rel   string `json:"rel"`
	Size  int64  `json:"size"`
	Mtime int64  `json:"mtime"`
}

// RecentTranscripts: bilinen agent dizinlerindeki son .jsonl transcript'leri
// listeler (içerik değil; yalnız kimlik/zaman/boyut). Amaç-özel allowlist —
// workspace jail'ini genişletmez, başka dizin okunamaz.
func RecentTranscripts(limit int) []transcriptEntry {
	type rootSpec struct{ src, dir string }
	roots := []rootSpec{
		{"claude", filepath.Join(mustHome(), ".claude", "projects")},
		{"codex", filepath.Join(mustHome(), ".codex", "sessions")},
	}
	var out []transcriptEntry
	for _, rs := range roots {
		base := rs.dir
		filepath.WalkDir(base, func(p string, d os.DirEntry, err error) error {
			if err != nil || d.IsDir() || !strings.HasSuffix(p, ".jsonl") {
				return nil
			}
			info, err := d.Info()
			if err != nil {
				return nil
			}
			rel, err := filepath.Rel(base, p)
			if err != nil || strings.HasPrefix(rel, "..") {
				return nil
			}
			out = append(out, transcriptEntry{Src: rs.src, Rel: rel, Size: info.Size(), Mtime: info.ModTime().Unix()})
			return nil
		})
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Mtime > out[j].Mtime })
	if len(out) > limit {
		out = out[:limit]
	}
	return out
}

func (s *Server) serveChatRecent(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]any{"transcripts": RecentTranscripts(50)})
}
