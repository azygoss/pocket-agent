// SPDX-License-Identifier: GPL-3.0-or-later
// Gerçek hook wiring'i: claude ve codex config'lerine çalışan komutlar işlenir.
// Önceki sürümlerin marker yorumlarını JSON config'lere yazması bozukluk
// yaratıyordu — StripMarkers kurulum/kaldırma sırasında onarım yapar.
package hooks

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// settings.json "hooks" objesi şemasını paylaşan agent'lar (claude, gemini,
// qwen — hepsi aynı matcher/hooks/command yapısını kullanır).
// Olay → kategori eşlemesi (yüksek-sinyal, düşük-gürültü):
var jsonHookEvents = map[string]string{
	"SessionStart": "session_started",
	"SessionEnd":   "session_ended",
	"Stop":         "task_complete",
	"Notification": "approval_required",
}

// InstallJSONHooks: settings.json'daki mevcut anahtarları koruyarak hooks
// objesine komutlarımızı ekler. Idempotent — bizim komut varsa dokunmaz.
func InstallJSONHooks(path, exe, source string) (bool, error) {
	var cfg map[string]any
	if b, err := os.ReadFile(path); err == nil {
		if json.Unmarshal(b, &cfg) != nil {
			cfg = nil
		}
	}
	if cfg == nil {
		cfg = map[string]any{}
	}
	hooksObj, _ := cfg["hooks"].(map[string]any)
	if hooksObj == nil {
		hooksObj = map[string]any{}
	}
	changed := false
	for event, cat := range jsonHookEvents {
		cmd := exe + " emit-hook " + source + " " + cat
		arr, _ := hooksObj[event].([]any)
		present := false
		for _, e := range arr {
			if m, ok := e.(map[string]any); ok {
				if hs, ok := m["hooks"].([]any); ok {
					for _, hh := range hs {
						if hm, ok := hh.(map[string]any); ok &&
							strings.Contains(fmt.Sprint(hm["command"]), "emit-hook "+source) {
							present = true
						}
					}
				}
			}
		}
		if !present {
			hooksObj[event] = append(arr, map[string]any{
				"matcher": "",
				"hooks":   []any{map[string]any{"type": "command", "command": cmd}},
			})
			changed = true
		}
	}
	if !changed {
		return false, nil
	}
	cfg["hooks"] = hooksObj
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return false, err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return false, err
	}
	return true, os.WriteFile(path, append(out, '\n'), 0o600)
}

func InstallClaude(path, exe string) (bool, error) {
	return InstallJSONHooks(path, exe, "claude")
}
func InstallGemini(path, exe string) (bool, error) {
	return InstallJSONHooks(path, exe, "gemini")
}
func InstallQwen(path, exe string) (bool, error) {
	return InstallJSONHooks(path, exe, "qwen")
}

// Cursor: ~/.cursor/hooks.json — düz komut dizisi şeması:
// {"version":1,"hooks":{"sessionStart":[{"command":"..."}], ...}}.
var cursorEvents = map[string]string{
	"sessionStart": "session_started",
	"sessionEnd":   "session_ended",
	"stop":         "task_complete",
}

func InstallCursor(path, exe string) (bool, error) {
	var cfg map[string]any
	if b, err := os.ReadFile(path); err == nil {
		if json.Unmarshal(b, &cfg) != nil {
			cfg = nil
		}
	}
	if cfg == nil {
		cfg = map[string]any{"version": 1}
	}
	hooksObj, _ := cfg["hooks"].(map[string]any)
	if hooksObj == nil {
		hooksObj = map[string]any{}
	}
	changed := false
	for event, cat := range cursorEvents {
		cmd := exe + " emit-hook cursor " + cat
		arr, _ := hooksObj[event].([]any)
		present := false
		for _, e := range arr {
			if m, ok := e.(map[string]any); ok &&
				strings.Contains(fmt.Sprint(m["command"]), "emit-hook cursor") {
				present = true
			}
		}
		if !present {
			hooksObj[event] = append(arr, map[string]any{"command": cmd})
			changed = true
		}
	}
	if !changed {
		return false, nil
	}
	cfg["hooks"] = hooksObj
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return false, err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return false, err
	}
	return true, os.WriteFile(path, append(out, '\n'), 0o600)
}

func UninstallCursor(path string) (bool, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return false, nil
	}
	var cfg map[string]any
	if json.Unmarshal(b, &cfg) != nil {
		return false, nil
	}
	hooksObj, _ := cfg["hooks"].(map[string]any)
	if hooksObj == nil {
		return false, nil
	}
	changed := false
	for event, v := range hooksObj {
		arr, _ := v.([]any)
		var keep []any
		for _, e := range arr {
			if m, ok := e.(map[string]any); ok &&
				strings.Contains(fmt.Sprint(m["command"]), "emit-hook cursor") {
				changed = true
				continue
			}
			keep = append(keep, e)
		}
		if len(keep) == 0 {
			delete(hooksObj, event)
		} else {
			hooksObj[event] = keep
		}
	}
	if !changed {
		return false, nil
	}
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return false, err
	}
	return true, os.WriteFile(path, append(out, '\n'), 0o600)
}

// UninstallJSONHooks: bu source'a ait emit-hook komutlarını hooks objesinden
// söker; kullanıcının diğer hook'larına dokunmaz.
func UninstallJSONHooks(path, source string) (bool, error) {
	b, err := os.ReadFile(path)
	if err != nil {
		return false, nil
	}
	var cfg map[string]any
	if json.Unmarshal(b, &cfg) != nil {
		return false, nil
	}
	hooksObj, _ := cfg["hooks"].(map[string]any)
	if hooksObj == nil {
		return false, nil
	}
	changed := false
	for event, v := range hooksObj {
		arr, _ := v.([]any)
		var keep []any
		for _, e := range arr {
			if m, ok := e.(map[string]any); ok {
				ours := false
				if hs, ok := m["hooks"].([]any); ok {
					for _, hh := range hs {
						if hm, ok := hh.(map[string]any); ok &&
							strings.Contains(fmt.Sprint(hm["command"]), "emit-hook "+source) {
							ours = true
						}
					}
				}
				if !ours {
					keep = append(keep, e)
				}
				changed = changed || ours
			} else {
				keep = append(keep, e)
			}
		}
		if len(keep) == 0 {
			delete(hooksObj, event)
		} else {
			hooksObj[event] = keep
		}
	}
	if !changed {
		return false, nil
	}
	if len(hooksObj) == 0 {
		delete(cfg, "hooks")
	}
	out, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return false, err
	}
	return true, os.WriteFile(path, append(out, '\n'), 0o600)
}

func UninstallClaude(path string) (bool, error) { return UninstallJSONHooks(path, "claude") }
func UninstallGemini(path string) (bool, error) { return UninstallJSONHooks(path, "gemini") }
func UninstallQwen(path string) (bool, error)   { return UninstallJSONHooks(path, "qwen") }

// Codex: ~/.codex/config.toml — `notify` tek komut dizisi alır, tur sonunda
// JSON argümanla çağrılır. Marker blok içinde tutulur (TOML '#' yorumu OK).
func InstallCodex(path, exe string) (bool, error) {
	cur, _ := os.ReadFile(path)
	block := fmt.Sprintf("# pocket-agent begin (codex)\n# managed hook — pocket-agent\nnotify = [%q, %q, %q, %q]\n# pocket-agent end (codex)",
		exe, "emit-hook", "codex", "task_complete")
	merged := Merge(string(cur), block)
	if merged == string(cur) {
		return false, nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return false, err
	}
	return true, os.WriteFile(path, []byte(merged), 0o600)
}

// UninstallCodex: marker bloğumuzu (içindeki notify dahil) söker.
func UninstallCodex(path string) (bool, error) {
	cur, _ := os.ReadFile(path)
	cleaned := StripMarkers(string(cur))
	if cleaned == string(cur) {
		return false, nil
	}
	return true, os.WriteFile(path, []byte(cleaned), 0o600)
}

// StripMarkers: "pocket-agent begin".."pocket-agent end" satırlarını (uçlar
// dahil) dosyadan söker. JSON config'lere yanlışlıkla yazılmış marker'ları
// onarmak için de kullanılır.
func StripMarkers(content string) string {
	lines := strings.Split(content, "\n")
	var out []string
	inOurs := false
	for _, l := range lines {
		if strings.Contains(l, "pocket-agent begin") {
			inOurs = true
			continue
		}
		if inOurs {
			if strings.Contains(l, "pocket-agent end") {
				inOurs = false
			}
			continue
		}
		out = append(out, l)
	}
	// baş/son fazladan boş satır bırakma
	return strings.TrimLeft(strings.Join(out, "\n"), "\n")
}
