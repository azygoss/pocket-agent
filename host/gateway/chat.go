// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"encoding/json"
	"net/http"
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
	full, err := Jail(s.Root, rel)
	if err != nil {
		http.Error(w, "rejected", 400)
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
