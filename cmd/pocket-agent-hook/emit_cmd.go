// SPDX-License-Identifier: GPL-3.0-or-later
// emit / emit-hook / daemon: agent olaylarının host tarafı zinciri.
// Journal-first: emit önce ~/.local/state/pocket-agent/journal.jsonl'e yazar
// (idempotent CommandID), sonra daemon'a socket'ten "flush" bildirir; daemon
// yoksa doğrudan backend'e postlar. Daemon kuyruktaki birikmiş kayıtları
// offset takibiyle retry'li boşaltır.
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/pocket-agent/pocket-agent/host/daemon"
	"github.com/pocket-agent/pocket-agent/host/emit"
	"github.com/pocket-agent/pocket-agent/host/hooks"
	"github.com/pocket-agent/pocket-agent/host/journal"
)

func stateDir() string {
	return filepath.Join(home(), ".local", "state", "pocket-agent")
}

func journalPath() string { return filepath.Join(stateDir(), "journal.jsonl") }
func socketPath() string  { return filepath.Join(stateDir(), "daemon.sock") }

func hostID() string {
	cfg, _ := cfgLoad()
	if cfg.HostID != "" {
		return cfg.HostID
	}
	hn, _ := os.Hostname()
	return "host:" + hn
}

func tenant() string {
	if t := os.Getenv("POCKET_TENANT"); t != "" {
		return t
	}
	cfg, _ := cfgLoad()
	if cfg.Tenant != "" {
		return cfg.Tenant
	}
	return "default"
}

var emitBus = hooks.New()

// emitEvent: normalize -> journal append -> daemon'a bildir (veya doğrudan post).
// Hook bağlamında çağrılabilir: soft hata asla agent'ı kırmaz.
func emitEvent(source, category, sourceID, session, message string) error {
	msg, err := emitBus.Normalize(source, category, sourceID, message)
	if err != nil {
		if err == hooks.ErrDupe {
			return nil // aynı olay ikinci kez geldi — sessizce düşür
		}
		return err
	}
	s := emit.Build(hostID(), source, category, sourceID, session, msg, time.Now())
	payload, _ := json.Marshal(s)
	j, err := journal.Open(journalPath())
	if err != nil {
		return err
	}
	if _, err := j.Append(journal.Entry{CommandID: s.EventID, Kind: "agent_event", Payload: string(payload)}); err != nil {
		return err
	}
	if nudgeDaemon() != nil {
		// Daemon ayakta değilse doğrudan post — olay kaybolmaz.
		_ = emit.Post(nil, backendURL(), tenant(), hostID(), s)
	}
	return nil
}

// nudgeDaemon: daemon.sock'a tek satır "flush" — hata durumunda daemon yok sayılır.
func nudgeDaemon() error {
	c, err := net.DialTimeout("unix", socketPath(), 300*time.Millisecond)
	if err != nil {
		return err
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(500 * time.Millisecond))
	_, err = fmt.Fprint(c, "flush\n")
	return err
}

// cmdEmit: pocket-agent emit <source> <category> <sourceEventId> <mesaj...>
func cmdEmit(args []string) {
	if len(args) < 4 {
		fmt.Fprintln(os.Stderr, "usage: emit <source> <category> <event-id> <mesaj...>")
		os.Exit(2)
	}
	if err := emitEvent(args[0], args[1], args[2], "", strings.Join(args[3:], " ")); err != nil {
		fmt.Fprintln(os.Stderr, "emit:", err)
		os.Exit(1)
	}
}

// cmdEmitHook: agent hook'larının çağırdığı komut. stdin (veya son arg) JSON
// payload'ından session/id/mesaj çıkarır. Asla agent'ı kırmaz: bilinen tüm
// soft hatalarda 0 döner.
//
//	pocket-agent emit-hook <source> <category> [json-payload]
func cmdEmitHook(args []string) {
	if len(args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: emit-hook <source> <category> [json-payload]")
		os.Exit(2)
	}
	source, category := args[0], args[1]
	var raw []byte
	if len(args) > 2 && args[2] != "-" {
		raw = []byte(args[2]) // codex notify: JSON son argüman olarak gelir
	} else if st, _ := os.Stdin.Stat(); st != nil && st.Mode()&os.ModeCharDevice == 0 {
		buf := make([]byte, 256<<10)
		n, _ := os.Stdin.Read(buf) // hook stdin'i tek write+EOF'dur
		raw = buf[:n]
	}
	var m map[string]any
	_ = json.Unmarshal(raw, &m)
	pick := func(keys ...string) string {
		for _, k := range keys {
			if v, ok := m[k].(string); ok && v != "" {
				return v
			}
		}
		return ""
	}
	session := pick("session_id", "conversation_id", "turn-id", "turn_id", "id")
	id := pick("tool_use_id", "notification_id", "id", "turn-id", "turn_id")
	if id == "" {
		id = session + "|" + category + "|" + strconv.FormatInt(time.Now().UnixNano(), 36)
	}
	msg := pick("message", "last-assistant-message", "title", "hook_event_name")
	if msg == "" {
		msg = category
	}
	if err := emitEvent(source, category, id, session, msg); err != nil {
		fmt.Fprintln(os.Stderr, "emit-hook:", err) // log'a düşer; exit 0
	}
}

// cmdDaemon: systemd user unit'inin çalıştırdığı süreç. Unix socket (0600)
// üzerinden "ping"/"flush" kabul eder; journal'ı offset'li olarak backend'e
// boşaltır (15s tick + emit'ten gelen flush bildirimi).
func cmdDaemon(_ []string) {
	dir := stateDir()
	l, sp, err := daemon.Listen(dir)
	if err != nil {
		fmt.Fprintln(os.Stderr, "daemon:", err)
		os.Exit(1)
	}
	defer l.Close()
	fmt.Println("daemon: socket", sp)

	flushCh := make(chan struct{}, 1)
	go func() {
		for {
			c, err := l.Accept()
			if err != nil {
				return
			}
			go func(c net.Conn) {
				defer c.Close()
				_ = c.SetDeadline(time.Now().Add(2 * time.Second))
				line, _ := bufio.NewReader(c).ReadString('\n')
				fmt.Fprint(c, "pong\n")
				if strings.TrimSpace(line) == "flush" {
					select {
					case flushCh <- struct{}{}:
					default:
					}
				}
			}(c)
		}
	}()

	flush := func() { flushJournal(dir) }
	flush()
	for {
		select {
		case <-time.After(15 * time.Second):
			flush()
		case <-flushCh:
			flush()
		}
	}
}

// flushJournal: journal.jsonl'de offset'ten sonraki agent_event kayıtlarını
// backend'e postlar. Başarılı (veya kalıcı 4xx) kayıtta offset ilerler;
// ağ hatasında durur, sonraki tur tekrar dener.
func flushJournal(dir string) {
	offPath := filepath.Join(dir, "journal.offset")
	var off int64
	if b, err := os.ReadFile(offPath); err == nil {
		off, _ = strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64)
	}
	f, err := os.Open(journalPath())
	if err != nil {
		return
	}
	defer f.Close()
	if fi, err := f.Stat(); err == nil && fi.Size() < off {
		off = 0 // journal yeniden başlatılmış/kısaltılmış
	}
	if _, err := f.Seek(off, 0); err != nil {
		return
	}
	cfgB, cfgT, cfgH := backendURL(), tenant(), hostID()
	client := &http.Client{Timeout: 8 * time.Second}
	r := bufio.NewReader(f)
	save := func() {
		tmp := offPath + ".tmp"
		if os.WriteFile(tmp, []byte(strconv.FormatInt(off, 10)), 0o600) == nil {
			_ = os.Rename(tmp, offPath)
		}
	}
	for {
		line, err := r.ReadBytes('\n')
		if len(line) > 0 {
			var e journal.Entry
			if json.Unmarshal(line, &e) == nil && e.Kind == "agent_event" {
				var s emit.Summary
				if json.Unmarshal([]byte(e.Payload), &s) == nil {
					if perr := emit.Post(client, cfgB, cfgT, cfgH, s); perr != nil && !strings.HasPrefix(perr.Error(), "backend 4") {
						save()
						return // ağ/5xx hatası — offset korunur, retry
					}
				}
			}
			off += int64(len(line))
		}
		if err != nil {
			break
		}
	}
	save()
}
