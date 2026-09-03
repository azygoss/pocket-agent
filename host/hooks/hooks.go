// SPDX-License-Identifier: GPL-3.0-or-later
// Package hooks: agent adapter normalization + dedupe (P12).
// ANSI scraping is forbidden: only hook/transcript/adapter output enters here.
package hooks

import (
	"errors"
	"strings"
	"sync"
)

var categories = map[string]bool{
	"approval_required": true, "task_complete": true, "session_started": true,
	"session_ended": true, "tool_running": true, "tool_finished": true, "error": true,
}

var sources = map[string]bool{
	"claude": true, "codex": true, "opencode": true, "cursor": true, "kimi": true,
	"grok": true, "pi": true, "omp": true, "hermes": true,
	"gemini": true, "antigravity": true, "qwen": true,
}

type Bus struct {
	mu   sync.Mutex
	seen map[string]bool
}

func New() *Bus { return &Bus{seen: map[string]bool{}} }

var ErrDupe = errors.New("duplicate source event")

// Normalize validates + dedupes by sourceEventID. Oversize message truncated.
func (b *Bus) Normalize(source, category, sourceEventID, message string) (string, error) {
	if !sources[source] {
		return "", errors.New("unknown source")
	}
	if !categories[category] {
		return "", errors.New("unknown category")
	}
	if strings.Contains(message, "\x1b[") {
		// ANSI escape present => caller tried to pipe terminal scrape; reject.
		return "", errors.New("ansi scrape forbidden")
	}
	b.mu.Lock()
	defer b.mu.Unlock()
	if b.seen[source+"|"+sourceEventID] {
		return "", ErrDupe
	}
	b.seen[source+"|"+sourceEventID] = true
	if len([]rune(message)) > 256 {
		message = string([]rune(message)[:256])
	}
	return message, nil
}
