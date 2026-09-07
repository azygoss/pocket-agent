// SPDX-License-Identifier: GPL-3.0-or-later
// pocket-agent pair: Easy Pair (P04). Tek seferlik ed25519 anahtarı üretir,
// pubkey'i marker ile authorized_keys'e ekler, payload'u 5dk TTL + tek-kullanım
// olarak backend'e yazar, terminalde QR + XXXX-XXXX kodu gösterir.
package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/pocket-agent/pocket-agent/host/pairing"
	"golang.org/x/crypto/ssh"
	qrcode "github.com/skip2/go-qrcode"
	"io"
)

const pairAlphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // 0/O/1/I/L yok

func pairCode() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	var sb strings.Builder
	for i, c := range b {
		if i == 4 {
			sb.WriteByte('-')
		}
		sb.WriteByte(pairAlphabet[int(c)%len(pairAlphabet)])
	}
	return sb.String()
}

// pairPayload: backend pairing session'ında taşınan SSH bağlantı bilgisi.
type pairPayload struct {
	SSHHost    string `json:"ssh_host"`
	SSHPort    int    `json:"ssh_port"`
	SSHUser    string `json:"ssh_user"`
	PrivateKey string `json:"private_key"` // OpenSSH PEM, tek-kullanım pairing anahtarı
}

func cmdPair(args []string) {
	backend := backendURL()
	host := ""
	port := 22
	usr := user()
	png := ""
	for i := 0; i < len(args); i++ {
		switch args[i] {
		case "--backend":
			i++; backend = args[i]
		case "--host":
			i++; host = args[i]
		case "--port":
			i++; fmt.Sscanf(args[i], "%d", &port)
		case "--user":
			i++; usr = args[i]
		case "--png":
			i++; png = args[i]
		}
	}
	if host == "" {
		// Dış adresi tahmin et (ifconfig.me); başarısızsa hostname -I
		host = detectPublicIP()
	}

	// 1) tek-seferlik anahtar
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		fatal(err)
	}
	sshPub, _ := ssh.NewPublicKey(pub)
	// OpenSSH native format: hem OpenSSH client hem SSHJ sorunsuz yükler
	// (PKCS8 "PRIVATE KEY" ed25519 için OpenSSH tarafında desteklenmiyor).
	privBlock, _ := ssh.MarshalPrivateKey(priv, "pocket-agent-pair")
	privPEM := pem.EncodeToMemory(privBlock)

	code := pairCode()
	// 2) authorized_keys'e marker'lı ekle (idempotent)
	ak := filepath.Join(home(), ".ssh", "authorized_keys")
	os.MkdirAll(filepath.Dir(ak), 0o700)
	if err := pairing.AddAuthorizedKey(ak, "pair-"+code, strings.TrimSpace(string(ssh.MarshalAuthorizedKey(sshPub)))); err != nil {
		fatal(fmt.Errorf("authorized_keys: %w", err))
	}

	// 3) backend pairing session (5dk TTL, tek-kullanım)
	payload, _ := json.Marshal(pairPayload{SSHHost: host, SSHPort: port, SSHUser: usr, PrivateKey: string(privPEM)})
	body, _ := json.Marshal(map[string]string{"code": code, "host_id": "host:local", "payload": string(payload)})
	req, _ := http.NewRequest("POST", strings.TrimRight(backend, "/")+"/v1/pairing-sessions", bytes.NewReader(body))
	req.Header.Set("X-Tenant", "pairing")
	req.Header.Set("Content-Type", "application/json")
	resp, err := (&http.Client{Timeout: 10 * time.Second}).Do(req)
	if err != nil {
		fmt.Fprintf(os.Stderr, "backend erişilemedi (%s) — QR/kod yalnız yerelde anlamlı değil, backend gerekli\n", err)
		os.Exit(1)
	}
	resp.Body.Close()
	if resp.StatusCode != 201 {
		fmt.Fprintf(os.Stderr, "backend pairing create: %s\n", resp.Status)
		os.Exit(1)
	}

	// 4) QR + kısa kod
	qrText := fmt.Sprintf("pa1|%s|%s|%s", backend, code, usr)
	fmt.Println()
	printTerminalQR(qrText)
	fmt.Printf("\n  Kod: %s\n", code)
	fmt.Printf("  Backend: %s\n  SSH: %s@%s:%d\n", backend, usr, host, port)
	fmt.Println("  5 dakika geçerli, tek kullanım. Telefonda: Bağlantılar → QR ile bağlan (veya kodu gir).")
	if png != "" {
		if err := writeQRPNG(qrText, png); err != nil {
			fmt.Fprintln(os.Stderr, "png:", err)
		} else {
			fmt.Println("  PNG:", png)
		}
	}
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "pair:", err)
	os.Exit(1)
}

func printTerminalQR(text string) {
	q, err := qrcode.New(text, qrcode.Medium)
	if err != nil {
		fmt.Println(text)
		return
	}
	fmt.Println(q.ToSmallString(false))
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
