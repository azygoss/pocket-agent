// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent pair: Easy Pair (P04), keyless. Telefon kendi ed25519 anahtar
// çiftini üretir; özel anahtar cihazdan hiç çıkmaz. Akış:
//  1. host backend'e kayıtlı değilse kaydolur (host token, config 0600)
//  2. 5dk TTL, tek kullanımlık XXXX-XXXX kodu + yalnız bağlantı ipucu
//     (ssh host/port/user + host key parmak izleri) backend'e yazılır
//  3. telefon kodu kendi PUBLIC key'iyle claim eder
//  4. host claim'i görür, kullanıcıya cihaz + anahtar parmak izini gösterir,
//     onaylanırsa pubkey'i marker ile authorized_keys'e ekler ve tamamlar.
package main

import (
	"bufio"
	"bytes"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/pocket-agent/pocket-agent/host/config"
	"github.com/pocket-agent/pocket-agent/host/pairing"
	qrcode "github.com/skip2/go-qrcode"
	"golang.org/x/crypto/ssh"
)

const pairAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // 0/O/1/I/L yok

func pairCode() string {
	var sb strings.Builder
	b := make([]byte, 1)
	for sb.Len() < 9 {
		if sb.Len() == 4 {
			sb.WriteByte('-')
		}
		_, _ = rand.Read(b)
		// Rejection sampling: 248 = 8*31, modulo bias yok.
		if int(b[0]) >= 248 {
			continue
		}
		sb.WriteByte(pairAlphabet[int(b[0])%len(pairAlphabet)])
	}
	return sb.String()
}

// pairPayload: backend'de taşınan bağlantı ipucu. Anahtar materyali YOK;
// backend "private"/PEM içeren yükü 400 ile reddeder.
type pairPayload struct {
	SSHHost  string   `json:"ssh_host"`
	SSHPort  int      `json:"ssh_port"`
	SSHUser  string   `json:"ssh_user"`
	HostKeys []string `json:"host_keys,omitempty"` // SHA256:… parmak izleri
	HostName string   `json:"host_name,omitempty"`
}

var httpc = &http.Client{Timeout: 10 * time.Second}

func apiCall(method, backend, path, token string, in, out any) (int, error) {
	var body io.Reader
	if in != nil {
		b, _ := json.Marshal(in)
		body = bytes.NewReader(b)
	}
	req, err := http.NewRequest(method, strings.TrimRight(backend, "/")+path, body)
	if err != nil {
		return 0, err
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := httpc.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if out != nil && resp.StatusCode < 300 {
		_ = json.NewDecoder(io.LimitReader(resp.Body, 64<<10)).Decode(out)
	}
	return resp.StatusCode, nil
}

// ensureHostToken: config'te bu backend'e ait token yoksa host'u kaydeder.
func ensureHostToken(backend, enroll string, force bool) (string, error) {
	if t := os.Getenv("POCKET_HOST_TOKEN"); t != "" && !force {
		return t, nil
	}
	p := config.DefaultPath()
	c, _ := config.Load(p)
	if !force && c.HostToken != "" && strings.TrimRight(c.TokenBackend, "/") == strings.TrimRight(backend, "/") {
		return c.HostToken, nil
	}
	if enroll == "" {
		enroll = os.Getenv("POCKET_ENROLL_TOKEN")
	}
	hn, _ := os.Hostname()
	var out struct {
		TenantID  string `json:"tenant_id"`
		HostToken string `json:"host_token"`
	}
	code, err := apiCall("POST", backend, "/v1/hosts/register", "", map[string]string{"host_id": hostID(), "label": hn, "enroll_token": enroll}, &out)
	switch {
	case err != nil:
		return "", fmt.Errorf("backend erişilemedi: %w", err)
	case code == 403:
		return "", errors.New("backend kayıt için enroll token istiyor: --enroll-token <değer> veya POCKET_ENROLL_TOKEN")
	case code != 201 || out.HostToken == "":
		return "", fmt.Errorf("host kaydı başarısız: HTTP %d", code)
	}
	// The token only works on this backend, so events must follow it.
	c.HostToken, c.TokenBackend, c.BackendURL = out.HostToken, backend, backend
	// Pin the id the token was issued for: events are only accepted for it.
	if c.HostID == "" {
		c.HostID = hostID()
	}
	if err := config.Save(p, c); err != nil {
		return "", err
	}
	fmt.Printf("  ✓ host backend'e kaydedildi (token %s içinde, 0600)\n", p)
	return out.HostToken, nil
}

// glob: sshd host public keys (default /etc/ssh/ssh_host_*_key.pub; custom
// sshd configs pass --host-keys).
func hostKeyFingerprints(glob string) []string {
	var out []string
	if glob == "" {
		glob = "/etc/ssh/ssh_host_*_key.pub"
	}
	matches, _ := filepath.Glob(glob)
	for _, m := range matches {
		b, err := os.ReadFile(m)
		if err != nil {
			continue
		}
		if k, _, _, _, err := ssh.ParseAuthorizedKey(b); err == nil {
			out = append(out, ssh.FingerprintSHA256(k))
		}
	}
	return out
}

func stdinIsTTY() bool {
	fi, err := os.Stdin.Stat()
	return err == nil && fi.Mode()&os.ModeCharDevice != 0
}

type pairOpts struct {
	backend, host, user, png, enroll string
	port                             int
	yes                              bool
	ttl                              time.Duration
	hostKeys                         string
}

func cmdPair(args []string) {
	o := pairOpts{backend: backendURL(), port: 22, user: user(), ttl: 5 * time.Minute}
	for i := 0; i < len(args); i++ {
		next := func() string {
			i++
			if i >= len(args) {
				fatal(fmt.Errorf("%s bir değer bekliyor", args[i-1]))
			}
			return args[i]
		}
		switch args[i] {
		case "--backend":
			o.backend = next()
		case "--host":
			o.host = next()
		case "--port":
			fmt.Sscanf(next(), "%d", &o.port)
		case "--user":
			o.user = next()
		case "--png":
			o.png = next()
		case "--enroll-token":
			o.enroll = next()
		case "--host-keys":
			o.hostKeys = next()
		case "--yes", "-y":
			o.yes = true
		}
	}
	if o.host == "" {
		o.host = detectPublicIP()
	}
	if err := runPair(o, os.Stdin, os.Stdout); err != nil {
		fatal(err)
	}
}

type claimView struct {
	State      string `json:"state"`
	DeviceID   string `json:"device_id"`
	DeviceName string `json:"device_name"`
	PublicKey  string `json:"public_key"`
}

func runPair(o pairOpts, in io.Reader, out io.Writer) error {
	token, err := ensureHostToken(o.backend, o.enroll, false)
	if err != nil {
		return err
	}
	hn, _ := os.Hostname()
	payload, _ := json.Marshal(pairPayload{SSHHost: o.host, SSHPort: o.port, SSHUser: o.user, HostKeys: hostKeyFingerprints(o.hostKeys), HostName: hn})
	code := pairCode()
	create := func() (int, error) {
		return apiCall("POST", o.backend, "/v1/pairing-sessions", token, map[string]string{"code": code, "payload": string(payload)}, nil)
	}
	st, err := create()
	if err == nil && st == 401 && os.Getenv("POCKET_HOST_TOKEN") == "" {
		// Token iptal edilmiş/backend sıfırlanmış: bir kez yeniden kaydol.
		if token, err = ensureHostToken(o.backend, o.enroll, true); err != nil {
			return err
		}
		st, err = create()
	}
	if err != nil {
		return fmt.Errorf("backend erişilemedi (%w) — eşleştirme için backend gerekli", err)
	}
	if st != 201 {
		return fmt.Errorf("backend pairing create: HTTP %d", st)
	}

	qrText := fmt.Sprintf("pa2|%s|%s|%s", o.backend, code, o.user)
	fmt.Fprintln(out)
	printTerminalQR(out, qrText)
	fmt.Fprintf(out, "\n  Kod: %s\n", code)
	fmt.Fprintf(out, "  Backend: %s\n  SSH: %s@%s:%d\n", o.backend, o.user, o.host, o.port)
	fmt.Fprintln(out, "  5 dakika geçerli, tek kullanım. Telefonda: Bağlantılar → QR ile bağlan (veya kodu gir).")
	if o.png != "" {
		if err := writeQRPNG(qrText, o.png); err != nil {
			fmt.Fprintln(os.Stderr, "png:", err)
		} else {
			fmt.Fprintln(out, "  PNG:", o.png)
		}
	}
	fmt.Fprintln(out, "  Telefonun bağlanması bekleniyor…")

	var v claimView
	deadline := time.Now().Add(o.ttl)
	for {
		if time.Now().After(deadline) {
			return errors.New("kodun süresi doldu — yeniden: pocket-agent pair")
		}
		st, err := apiCall("GET", o.backend, "/v1/pairing-sessions/"+strings.ReplaceAll(code, "-", ""), token, nil, &v)
		if err == nil && st == 200 {
			if v.State == "CLAIMED" {
				break
			}
			if v.State == "EXPIRED" {
				return errors.New("kodun süresi doldu — yeniden: pocket-agent pair")
			}
		}
		time.Sleep(pollInterval)
	}
	k, _, _, _, err := ssh.ParseAuthorizedKey([]byte(v.PublicKey))
	if err != nil {
		_ = finishPair(o.backend, token, code, false)
		return fmt.Errorf("cihaz geçersiz anahtar gönderdi: %w", err)
	}
	name := v.DeviceName
	if name == "" {
		name = v.DeviceID
	}
	fmt.Fprintf(out, "\n  Cihaz: %s\n  Anahtar: %s %s\n", name, k.Type(), ssh.FingerprintSHA256(k))
	approve := o.yes
	if !approve {
		if f, ok := in.(*os.File); !ok || f != os.Stdin || stdinIsTTY() {
			fmt.Fprint(out, "  Bu cihaza SSH erişimi verilsin mi? [E/h] ")
			line, _ := bufio.NewReader(in).ReadString('\n')
			line = strings.ToLower(strings.TrimSpace(line))
			approve = line == "" || line == "e" || line == "evet" || line == "y" || line == "yes"
		} else {
			fmt.Fprintln(out, "  Etkileşimsiz oturum: onay için --yes gerekli; reddedildi.")
		}
	}
	if !approve {
		_ = finishPair(o.backend, token, code, false)
		return errors.New("eşleştirme reddedildi")
	}
	ak := filepath.Join(home(), ".ssh", "authorized_keys")
	if err := os.MkdirAll(filepath.Dir(ak), 0o700); err != nil {
		return err
	}
	marker := "pair-" + code
	if err := pairing.AddAuthorizedKey(ak, marker, strings.TrimSpace(string(ssh.MarshalAuthorizedKey(k)))); err != nil {
		_ = finishPair(o.backend, token, code, false)
		return fmt.Errorf("authorized_keys: %w", err)
	}
	if err := finishPair(o.backend, token, code, true); err != nil {
		// The request may have committed even though the response was lost:
		// keep the key if the backend already says CONSUMED.
		var st claimView
		if c, e := apiCall("GET", o.backend, "/v1/pairing-sessions/"+strings.ReplaceAll(code, "-", ""), token, nil, &st); e != nil || c != 200 || st.State != "CONSUMED" {
			_, _ = pairing.RevokeAuthorizedKey(ak, marker)
			return err
		}
	}
	fmt.Fprintf(out, "  ✓ %s eşleşti. Erişimi kaldırmak için: pocket-agent unpair %s\n", name, code)
	return nil
}

var pollInterval = 1500 * time.Millisecond

func finishPair(backend, token, code string, approved bool) error {
	st, err := apiCall("POST", backend, "/v1/pairing-sessions/"+strings.ReplaceAll(code, "-", "")+"/complete", token, map[string]bool{"approved": approved}, nil)
	if err != nil {
		return err
	}
	if st != 200 {
		return fmt.Errorf("pairing complete: HTTP %d", st)
	}
	return nil
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "pair:", err)
	os.Exit(1)
}

func printTerminalQR(out io.Writer, text string) {
	q, err := qrcode.New(text, qrcode.Medium)
	if err != nil {
		fmt.Fprintln(out, text)
		return
	}
	fmt.Fprintln(out, q.ToSmallString(false))
}

func writeQRPNG(text, path string) error {
	return qrcode.WriteFile(text, qrcode.Medium, 512, path)
}

func detectPublicIP() string {
	for _, u := range []string{"https://api.ipify.org", "https://ifconfig.me"} {
		resp, err := (&http.Client{Timeout: 5 * time.Second}).Get(u)
		if err != nil {
			continue
		}
		b, _ := io.ReadAll(http.MaxBytesReader(nil, resp.Body, 64))
		resp.Body.Close()
		if ip := strings.TrimSpace(string(b)); ip != "" && len(ip) < 46 {
			return ip
		}
	}
	return "127.0.0.1"
}
