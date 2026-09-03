// SPDX-License-Identifier: GPL-3.0-or-later
package hooks

import (
	"bufio"
	"encoding/json"
	"os"
	"strings"
)

// Parsed is a normalized hook event from transcript/adapter output (never ANSI scrape).
type Parsed struct {
	Source   string
	Category string
	Text     string
	Digest   string
}

// ParseClaudeJSONL reads assistant/tool_use/result lines.
func ParseClaudeJSONL(path string) ([]Parsed, error) {
	return parseJSONL(path, "claude", func(m map[string]any) (Parsed, bool) {
		t, _ := m["type"].(string)
		switch t {
		case "assistant":
			if msg, ok := m["message"].(map[string]any); ok {
				if content, ok := msg["content"].([]any); ok && len(content) > 0 {
					if c0, ok := content[0].(map[string]any); ok {
						if txt, ok := c0["text"].(string); ok {
							return Parsed{Source: "claude", Category: "tool_running", Text: txt}, true
						}
					}
				}
			}
		case "tool_use":
			name, _ := m["name"].(string)
			return Parsed{Source: "claude", Category: "tool_running", Text: "tool:" + name}, true
		case "result":
			st, _ := m["status"].(string)
			cat := "task_complete"
			if st != "success" {
				cat = "error"
			}
			return Parsed{Source: "claude", Category: cat, Text: "result:" + st}, true
		}
		return Parsed{}, false
	})
}

// ParseCodexJSONL reads role-based lines incl. approval_request.
func ParseCodexJSONL(path string) ([]Parsed, error) {
	return parseJSONL(path, "codex", func(m map[string]any) (Parsed, bool) {
		r, _ := m["role"].(string)
		switch r {
		case "assistant":
			if c, ok := m["content"].(string); ok {
				return Parsed{Source: "codex", Category: "tool_running", Text: c}, true
			}
		case "tool":
			n, _ := m["name"].(string)
			return Parsed{Source: "codex", Category: "tool_finished", Text: "tool:" + n}, true
		case "approval_request":
			cmd, _ := m["cmd"].(string)
			d, _ := m["digest"].(string)
			return Parsed{Source: "codex", Category: "approval_required", Text: cmd, Digest: d}, true
		}
		return Parsed{}, false
	})
}

func parseJSONL(path, _ string, fn func(map[string]any) (Parsed, bool)) ([]Parsed, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var out []Parsed
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 1<<20), 1<<20)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" {
			continue
		}
		var m map[string]any
		if json.Unmarshal([]byte(line), &m) != nil {
			continue // skip malformed, never crash
		}
		if p, ok := fn(m); ok {
			out = append(out, p)
		}
	}
	return out, sc.Err()
}
