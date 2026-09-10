// SPDX-License-Identifier: GPL-3.0-or-later
// Package hooks: agent adapter normalization + dedupe (P12).
// Install touches only our marked block; user content preserved.
package hooks

import "strings"

type Agent struct {
	Name       string
	ConfigFile string // relative to $HOME
	Block      string
}

func All() []Agent {
	hook := func(tool string) string {
		return "# pocket-agent begin (" + tool + ")\n# managed hook — pocket-agent\n# pocket-agent end (" + tool + ")"
	}
	return []Agent{
		{"claude", ".claude.json", hook("claude")},
		{"codex", ".codex/config.toml", hook("codex")},
		{"opencode", ".config/opencode/config.json", hook("opencode")},
		{"cursor", ".cursor/hooks.json", hook("cursor")},
		{"kimi", ".kimi/hooks.json", hook("kimi")},
		{"grok", ".grok/hooks.json", hook("grok")},
		{"pi", ".pi/hooks.json", hook("pi")},
		{"omp", ".omp/hooks.json", hook("omp")},
		{"hermes", ".hermes/hooks.json", hook("hermes")},
		{"gemini", ".gemini/hooks.json", hook("gemini")},
		{"antigravity", ".antigravity/hooks.json", hook("antigravity")},
		{"qwen", ".qwen/hooks.json", hook("qwen")},
	}
}

// Merge inserts or replaces only our block, preserving user content.
func Merge(existing, block string) string {
	begin := strings.SplitN(block, "\n", 2)[0]
	end := ""
	for _, l := range strings.Split(block, "\n") {
		if strings.Contains(l, "pocket-agent end") {
			end = l
		}
	}
	if existing == "" {
		return block + "\n"
	}
	lines := strings.Split(existing, "\n")
	var out []string
	inOurs := false
	found := false
	for _, l := range lines {
		if strings.Contains(l, "pocket-agent begin") {
			inOurs = true
			found = true
			continue
		}
		if inOurs {
			if l == end || strings.Contains(l, "pocket-agent end") {
				inOurs = false
			}
			continue
		}
		out = append(out, l)
	}
	_ = begin
	_ = found
	// Kalan içerik yalnız boş satırsa (dosya sadece bizim bloktu) bloğu
	// başa "\n" eklemeden yaz — tekrar koşuda çıktı birebir aynı kalsın.
	allEmpty := true
	for _, l := range out {
		if strings.TrimSpace(l) != "" {
			allEmpty = false
			break
		}
	}
	if allEmpty {
		return block + "\n"
	}
	return strings.TrimRight(strings.Join(out, "\n"), "\n") + "\n" + block + "\n"
}
