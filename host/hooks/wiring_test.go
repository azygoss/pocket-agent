// SPDX-License-Identifier: GPL-3.0-or-later
package hooks

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestInstallClaudeWiresRealHooks(t *testing.T) {
	p := filepath.Join(t.TempDir(), ".claude", "settings.json")
	// Mevcut kullanıcı ayarları korunmalı.
	os.MkdirAll(filepath.Dir(p), 0o700)
	os.WriteFile(p, []byte(`{"model":"sonnet","hooks":{"SessionStart":[{"matcher":"x","hooks":[{"type":"command","command":"echo hi"}]}]}}`), 0o600)

	changed, err := InstallClaude(p, "/usr/bin/pocket-agent")
	if err != nil || !changed {
		t.Fatalf("install: %v changed=%v", err, changed)
	}
	var cfg map[string]any
	b, _ := os.ReadFile(p)
	if json.Unmarshal(b, &cfg) != nil {
		t.Fatal("settings.json parse edilemiyor — JSON bozuldu")
	}
	if cfg["model"] != "sonnet" {
		t.Fatal("kullanıcı anahtarı kayboldu")
	}
	hooksObj := cfg["hooks"].(map[string]any)
	// Kullanıcının SessionStart hook'u + bizimki birlikte olmalı.
	arr := hooksObj["SessionStart"].([]any)
	if len(arr) != 2 {
		t.Fatalf("SessionStart hook sayısı: %d", len(arr))
	}
	for _, ev := range []string{"SessionStart", "SessionEnd", "Stop", "Notification"} {
		if _, ok := hooksObj[ev]; !ok {
			t.Fatalf("eksik hook event: %s", ev)
		}
	}
	// Idempotent: ikinci kurulum değişiklik yapmaz.
	changed, err = InstallClaude(p, "/usr/bin/pocket-agent")
	if err != nil || changed {
		t.Fatalf("tekrar install: %v changed=%v", err, changed)
	}
	// Uninstall: sadece bizimkiler sökülür, kullanıcı hook'u kalır.
	ok, err := UninstallClaude(p)
	if err != nil || !ok {
		t.Fatalf("uninstall: %v ok=%v", err, ok)
	}
	b, _ = os.ReadFile(p)
	json.Unmarshal(b, &cfg)
	hooksObj = cfg["hooks"].(map[string]any)
	if len(hooksObj["SessionStart"].([]any)) != 1 {
		t.Fatal("kullanıcı hook'u da silindi")
	}
	if _, ok := hooksObj["Stop"]; ok {
		t.Fatal("bizim Stop hook'u kalmadı")
	}
}

func TestInstallClaudeFreshFile(t *testing.T) {
	p := filepath.Join(t.TempDir(), "settings.json")
	changed, err := InstallClaude(p, "/x/pa")
	if err != nil || !changed {
		t.Fatalf("%v %v", err, changed)
	}
	b, _ := os.ReadFile(p)
	if !strings.Contains(string(b), "emit-hook claude session_started") {
		t.Fatal("session_started komutu yok")
	}
}

func TestInstallCodexNotify(t *testing.T) {
	p := filepath.Join(t.TempDir(), ".codex", "config.toml")
	os.MkdirAll(filepath.Dir(p), 0o700)
	os.WriteFile(p, []byte("model = \"o4\"\n"), 0o600)
	changed, err := InstallCodex(p, "/usr/bin/pa")
	if err != nil || !changed {
		t.Fatalf("%v %v", err, changed)
	}
	b, _ := os.ReadFile(p)
	if !strings.Contains(string(b), "model = \"o4\"") {
		t.Fatal("kullanıcı içeriği kayboldu")
	}
	if !strings.Contains(string(b), `notify = ["/usr/bin/pa", "emit-hook", "codex", "task_complete"]`) {
		t.Fatalf("notify satırı yok:\n%s", b)
	}
	if changed, _ := InstallCodex(p, "/usr/bin/pa"); changed {
		t.Fatal("idempotent değil")
	}
	if ok, _ := UninstallCodex(p); !ok {
		t.Fatal("uninstall değişiklik yapmadı")
	}
	b, _ = os.ReadFile(p)
	if strings.Contains(string(b), "pocket-agent") {
		t.Fatal("marker kaldı")
	}
	if !strings.Contains(string(b), "model") {
		t.Fatal("kullanıcı içeriği silindi")
	}
}

func TestStripMarkersRepairsJSON(t *testing.T) {
	// Eski sürümün JSON'a yazdığı marker bloğu — parse bozuyor.
	corrupt := `{"a":1}
# pocket-agent begin (opencode)
# managed hook — pocket-agent
# pocket-agent end (opencode)
`
	out := StripMarkers(corrupt)
	var m map[string]any
	if json.Unmarshal([]byte(out), &m) != nil || m["a"] != 1.0 {
		t.Fatalf("onarım JSON'u bozuk bıraktı: %q", out)
	}
}
