// SPDX-License-Identifier: GPL-3.0-or-later
// help / completion / onboard: komut keşfi ve tek-komut kurulum.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	"github.com/pocket-agent/pocket-agent/host/config"
	"github.com/pocket-agent/pocket-agent/host/service"
)

const helpText = `pocket-agent — self-hosted Android terminal companion host aracı

Kullanım: pocket-agent <komut> [altkomut] [bayraklar]

Kurulum:
  onboard [--root dir] [--backend url]   Tek komutla kurulum: daemon + gateway +
                                         agent hook'ları + eşleştirme QR'ı
  service install|status|uninstall       Kullanıcı daemon'ı (systemd user unit)
  service install-gateway                Gateway user unit'i (root = cwd)

Eşleştirme:
  pair [--backend url] [--host h]        QR + XXXX-XXXX kodu üret (5dk, tek kullanım)
       [--port n] [--user u] [--png f]
  unpair <kod|deviceId>                  Eşleştirme anahtarını authorized_keys'ten kaldır
  host setup|list|revoke|enable-ssh      Host tarafı eşleştirme yardımcıları

Çalışma zamanı:
  gateway serve [--root dir] [--addr a]  Dosya/diff sunucusu (yalnız loopback:24543)
  servers                                Aktif tmux oturumlarını listele
  servers kill <ad>                      Oturum kapat (kill-server asla)
  hooks install|uninstall                Claude/Codex hook'larını config'e işle/söyle
  emit <src> <cat> <id> <mesaj>          Agent olayı üret (journal → backend)
  emit-hook <src> <cat> [json]           Agent hook'larının çağırdığı komut (stdin/arg JSON)
  daemon                                 Event flusher daemon'ı (systemd unit çalıştırır)
  logs [n]                               Journal'ın son n satırı

Tanı:
  doctor [--json]                        Ortam kontrolleri (sshd, port, disk, backend)
  probe                                  doctor'un kısa sürümü (script'ler için)
  status [--json]                        Sürüm + sağlık özeti
  host list [--json]                     Config + authorized_keys özeti
  service status [--json]                Unit durumları

Yapılandırma:
  set <key> <value>                      backend_url | host_id | tenant
                                           (tenant = uygulamadaki tenant token, varsayılan "default")
  completion bash|zsh|fish               Kabuk tamamlama script'i üret

Diğer:
  diff [staged|unstaged|working|last]    cwd'de git diff (gateway ile aynı motor)
  update                                 İmzalı manifest ile güncelle (imzasız reddedilir)
  version, -V                            Sürüm
  help                                   Bu metin

Ortam değişkenleri:
  POCKET_HOME                            Ev dizini override (test)
  POCKET_BACKEND                         Backend URL override
  POCKET_GATEWAY_TOKEN                   Gateway token override (test)
`

func printHelp() { fmt.Print(helpText) }

// Kabuk tamamlamaları: komut listesi tek kaynaktan üretilir.
var topCommands = []string{
	"onboard", "service", "pair", "unpair", "host", "gateway", "servers",
	"hooks", "emit", "emit-hook", "daemon", "logs", "doctor", "probe",
	"status", "set", "completion",
	"diff", "update", "version", "help", "usage", "context", "cwd-list",
}

var subCommands = map[string][]string{
	"service":    {"install", "install-gateway", "status", "uninstall"},
	"host":       {"setup", "list", "revoke", "enable-ssh"},
	"gateway":    {"serve"},
	"hooks":      {"install", "uninstall"},
	"servers":    {"kill"},
	"set":        {"backend_url", "host_id", "tenant"},
	"completion": {"bash", "zsh", "fish"},
	"diff":       {"staged", "unstaged", "untracked", "working", "last"},
}

func cmdCompletion(args []string) {
	shell := "bash"
	if len(args) > 0 {
		shell = args[0]
	}
	switch shell {
	case "bash":
		fmt.Print(bashCompletion())
	case "zsh":
		fmt.Print(zshCompletion())
	case "fish":
		fmt.Print(fishCompletion())
	default:
		fmt.Fprintln(os.Stderr, "usage: completion bash|zsh|fish")
		os.Exit(2)
	}
}

func bashCompletion() string {
	subs := ""
	for k, v := range subCommands {
		subs += fmt.Sprintf("    %s) COMPREPLY=( $(compgen -W \"%s\" -- \"$cur\") );;\n", k, strings.Join(v, " "))
	}
	return fmt.Sprintf(`# pocket-agent bash completion — source <(pocket-agent completion bash)
_pocket_agent() {
  local cur prev
  cur="${COMP_WORDS[COMP_CWORD]}"
  prev="${COMP_WORDS[COMP_CWORD-1]}"
  if [ "$COMP_CWORD" -eq 1 ]; then
    COMPREPLY=( $(compgen -W "%s" -- "$cur") )
    return 0
  fi
  case "$prev" in
%s    *) COMPREPLY=() ;;
  esac
}
complete -F _pocket_agent pocket-agent pocket-agent-hook
`, strings.Join(topCommands, " "), subs)
}

func zshCompletion() string {
	var subs strings.Builder
	for k, v := range subCommands {
		fmt.Fprintf(&subs, "      %s) compadd %s ;;\n", k, strings.Join(v, " "))
	}
	return fmt.Sprintf(`#compdef pocket-agent pocket-agent-hook
# pocket-agent zsh completion — pocket-agent completion zsh > "${fpath[1]}/_pocket-agent"
_pocket_agent() {
  local -a cmds
  cmds=(%s)
  if (( CURRENT == 2 )); then
    compadd $cmds
    return
  fi
  case "$words[2]" in
%s  esac
}
_pocket_agent "$@"
`, strings.Join(topCommands, " "), subs.String())
}

func fishCompletion() string {
	var b strings.Builder
	b.WriteString("# pocket-agent fish completion — pocket-agent completion fish > ~/.config/fish/completions/pocket-agent.fish\n")
	for _, c := range topCommands {
		fmt.Fprintf(&b, "complete -c pocket-agent -f -n '__fish_use_subcommand' -a %s\n", c)
	}
	for k, v := range subCommands {
		for _, s := range v {
			fmt.Fprintf(&b, "complete -c pocket-agent -f -n '__fish_seen_subcommand_from %s' -a %s\n", k, s)
		}
	}
	return b.String()
}

// onboard: tek komutla host tarafı kurulum — daemon + gateway unit + agent
// hook'ları + eşleştirme QR'ı. Her adım idempotent; tekrar koşusu converge eder.
func cmdOnboard(args []string) {
	root, _ := os.Getwd()
	backend := backendURL()
	noPair := false
	for i := 0; i < len(args); i++ {
		switch args[i] {
		case "--root":
			i++
			if i < len(args) {
				root = args[i]
			}
		case "--backend":
			i++
			if i < len(args) {
				backend = args[i]
			}
		case "--no-pair":
			noPair = true
		}
	}
	h := home()
	exe, _ := os.Executable()
	step := func(name string, fn func() error) bool {
		if err := fn(); err != nil {
			fmt.Fprintf(os.Stderr, "  ✗ %s: %v\n", name, err)
			return false
		}
		fmt.Printf("  ✓ %s\n", name)
		return true
	}

	fmt.Println("pocket-agent onboard")
	// 1) config: backend kaydedilir (pair dahil sonraki komutlar bunu okur).
	if backend != "" {
		p := config.DefaultPath()
		c, _ := config.Load(p)
		c.BackendURL = backend
		step("config → "+p, func() error { return config.Save(p, c) })
	}
	// 2) daemon unit
	step("daemon unit (systemd user)", func() error {
		_, err := service.Install(h, exe)
		return err
	})
	// 3) gateway unit
	step("gateway unit (root="+root+")", func() error {
		_, err := service.InstallGateway(h, exe, root)
		return err
	})
	// 4) agent hook'ları
	step("agent hooks (claude/codex)", func() error {
		return installHooks(h, exe)
	})
	// 4b) daemon'ı hemen başlat (systemd varsa) — event akışı bununla canlanır.
	step("daemon start (systemctl --user)", func() error {
		if _, err := exec.LookPath("systemctl"); err != nil {
			fmt.Println("    systemctl yok — daemon'ı elle: pocket-agent daemon")
			return nil
		}
		return exec.Command("systemctl", "--user", "enable", "--now", "pocket-agent.service").Run()
	})
	// 5) sshd + authorized_keys hazırlığı
	ak := filepath.Join(h, ".ssh", "authorized_keys")
	if _, err := os.Stat(ak); os.IsNotExist(err) {
		step("~/.ssh/authorized_keys", func() error {
			if err := os.MkdirAll(filepath.Dir(ak), 0o700); err != nil {
				return err
			}
			return os.WriteFile(ak, nil, 0o600)
		})
	}
	fmt.Println()
	if noPair {
		fmt.Println("Eşleştirme atlandı (--no-pair). Hazır olunca: pocket-agent pair")
		return
	}
	// 6) pair — backend'den bağımsız adımlar bitti; QR + kodu bas.
	cmdPair([]string{"--backend", backend})
}
