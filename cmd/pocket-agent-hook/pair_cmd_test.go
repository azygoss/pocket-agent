// SPDX-License-Identifier: GPL-3.0-or-later
package main

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/pocket-agent/pocket-agent/host/config"
	"golang.org/x/crypto/ssh"
)

// fakeBackend: pairing uçlarının asgari taklidi; gelen her gövdeyi kaydeder
// ki özel anahtarın backend'e hiç gitmediği doğrulanabilsin.
type fakeBackend struct {
	mu       sync.Mutex
	bodies   []string
	payload  string
	state    string
	pub      string
	approved *bool
}

func (f *fakeBackend) handler(t *testing.T) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		f.mu.Lock()
		defer f.mu.Unlock()
		f.bodies = append(f.bodies, string(b))
		auth := r.Header.Get("Authorization")
		switch {
		case r.URL.Path == "/v1/hosts/register":
			w.WriteHeader(201)
			w.Write([]byte(`{"tenant_id":"tenant:x","host_token":"pah_test"}`))
		case auth != "Bearer pah_test":
			w.WriteHeader(401)
		case r.Method == "POST" && r.URL.Path == "/v1/pairing-sessions":
			var v struct{ Code, Payload string }
			_ = json.Unmarshal(b, &v)
			f.payload, f.state = v.Payload, "PENDING"
			w.WriteHeader(201)
			// Telefon kısa süre sonra claim eder.
			go func() {
				time.Sleep(20 * time.Millisecond)
				f.mu.Lock()
				f.state = "CLAIMED"
				f.mu.Unlock()
			}()
		case r.Method == "GET" && strings.HasPrefix(r.URL.Path, "/v1/pairing-sessions/"):
			json.NewEncoder(w).Encode(map[string]string{"state": f.state, "device_id": "device:p", "device_name": "Pixel", "public_key": f.pub})
		case strings.HasSuffix(r.URL.Path, "/complete"):
			var v struct{ Approved bool }
			_ = json.Unmarshal(b, &v)
			f.approved = &v.Approved
			w.WriteHeader(200)
		default:
			w.WriteHeader(404)
		}
	})
}

func devicePub(t *testing.T) string {
	pub, _, _ := ed25519.GenerateKey(rand.Reader)
	k, _ := ssh.NewPublicKey(pub)
	return strings.TrimSpace(string(ssh.MarshalAuthorizedKey(k)))
}

func runFake(t *testing.T, answer string, yes bool) (*fakeBackend, string, error) {
	dir := t.TempDir()
	t.Setenv("POCKET_HOME", dir)
	t.Setenv("HOME", dir)
	t.Setenv("POCKET_HOST_TOKEN", "")
	pollInterval = 5 * time.Millisecond
	f := &fakeBackend{pub: devicePub(t)}
	srv := httptest.NewServer(f.handler(t))
	t.Cleanup(srv.Close)
	var out strings.Builder
	err := runPair(pairOpts{backend: srv.URL, host: "10.0.0.2", port: 22, user: "berk", yes: yes, ttl: 5 * time.Second}, strings.NewReader(answer), &out)
	return f, out.String(), err
}

func TestPairKeylessApproved(t *testing.T) {
	f, out, err := runFake(t, "e\n", false)
	if err != nil {
		t.Fatalf("%v\n%s", err, out)
	}
	if f.approved == nil || !*f.approved {
		t.Fatal("pairing must be completed as approved")
	}
	for _, b := range f.bodies {
		if strings.Contains(b, "PRIVATE KEY") || strings.Contains(strings.ToLower(b), "private_key") {
			t.Fatalf("private key must never reach the backend: %s", b)
		}
	}
	if !strings.Contains(f.payload, `"ssh_user":"berk"`) {
		t.Fatalf("payload: %s", f.payload)
	}
	ak, _ := os.ReadFile(filepath.Join(os.Getenv("HOME"), ".ssh", "authorized_keys"))
	if !strings.Contains(string(ak), strings.Fields(f.pub)[1]) {
		t.Fatalf("device public key must be authorized: %s", ak)
	}
	c, _ := config.Load(config.DefaultPath())
	if c.HostToken != "pah_test" {
		t.Fatalf("host token must be persisted: %+v", c)
	}
	fi, _ := os.Stat(config.DefaultPath())
	if fi.Mode().Perm() != 0o600 {
		t.Fatalf("config must be 0600, got %v", fi.Mode().Perm())
	}
	if !strings.Contains(out, "Pixel") || !strings.Contains(out, "SHA256:") {
		t.Fatalf("operator must see device and key fingerprint: %s", out)
	}
}

func TestPairKeylessRejected(t *testing.T) {
	f, _, err := runFake(t, "h\n", false)
	if err == nil {
		t.Fatal("rejection must return an error")
	}
	if f.approved == nil || *f.approved {
		t.Fatal("pairing must be completed as rejected")
	}
	ak, _ := os.ReadFile(filepath.Join(os.Getenv("HOME"), ".ssh", "authorized_keys"))
	if strings.Contains(string(ak), strings.Fields(f.pub)[1]) {
		t.Fatal("rejected key must not be authorized")
	}
}

func TestPairCodeAlphabet(t *testing.T) {
	for i := 0; i < 200; i++ {
		c := pairCode()
		if len(c) != 9 || c[4] != '-' {
			t.Fatalf("format: %q", c)
		}
		for _, r := range strings.ReplaceAll(c, "-", "") {
			if !strings.ContainsRune(pairAlphabet, r) {
				t.Fatalf("ambiguous char in %q", c)
			}
		}
	}
}
