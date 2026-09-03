// SPDX-License-Identifier: GPL-3.0-or-later
// Package pairing: Easy Pair QR + authorized_keys marker + TOFU pinning.
package pairing

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"
)

const (
	QRVersion   = 1
	PairTTL     = 5 * time.Minute
	MarkerPrefix = "# pocket-agent:"
)

// QRPayload is shown as QR (secret appears once, never logged).
type QRPayload struct {
	Version    int
	BackendURL string
	Code       string
	HostID     string
	SSHUser    string
	SSHHost    string
	SSHPort    int
	Secret     string
}

func (q QRPayload) Encode() string {
	return fmt.Sprintf("pa%d|%s|%s|%s|%s@%s:%d|%s",
		q.Version, q.BackendURL, q.Code, q.HostID, q.SSHUser, q.SSHHost, q.SSHPort, q.Secret)
}

func NewSecret() string {
	b := make([]byte, 18)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}

// AddAuthorizedKey appends a marker-tagged pubkey line (idempotent).
func AddAuthorizedKey(authKeysPath, deviceID, pubkey string) error {
	marker := MarkerPrefix + deviceID
	cur, _ := os.ReadFile(authKeysPath)
	for _, l := range strings.Split(string(cur), "\n") {
		if strings.Contains(l, marker) && strings.Contains(l, strings.TrimSpace(pubkey)) {
			return nil // already present
		}
	}
	f, err := os.OpenFile(authKeysPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = fmt.Fprintf(f, "%s %s\n", strings.TrimSpace(pubkey), marker)
	return err
}

// RevokeAuthorizedKey removes ONLY lines with this device marker.
func RevokeAuthorizedKey(authKeysPath, deviceID string) (removed int, err error) {
	marker := MarkerPrefix + deviceID
	cur, err := os.ReadFile(authKeysPath)
	if err != nil {
		return 0, err
	}
	var keep []string
	for _, l := range strings.Split(string(cur), "\n") {
		if l == "" {
			continue
		}
		if strings.Contains(l, marker) {
			removed++
			continue
		}
		keep = append(keep, l)
	}
	out := strings.Join(keep, "\n")
	if out != "" {
		out += "\n"
	}
	return removed, os.WriteFile(authKeysPath, []byte(out), 0o600)
}

var ErrHostKeyChanged = errors.New("host key changed: hard-stop, re-pair required")

// CheckTOFU pins fingerprint on first connect; mismatch => hard-stop (no fallback).
func CheckTOFU(knownHostsPath, hostPort, fingerprint string) error {
	pin := hostPort + " " + fingerprint
	cur, _ := os.ReadFile(knownHostsPath)
	if len(strings.TrimSpace(string(cur))) == 0 {
		return os.WriteFile(knownHostsPath, []byte(pin+"\n"), 0o600)
	}
	for _, l := range strings.Split(string(cur), "\n") {
		if strings.HasPrefix(l, hostPort+" ") {
			if strings.TrimSpace(l) == strings.TrimSpace(pin) {
				return nil
			}
			return ErrHostKeyChanged
		}
	}
	f, err := os.OpenFile(knownHostsPath, os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = f.WriteString(pin + "\n")
	return err
}

func FingerprintSHA256(pubkey []byte) string {
	h := sha256.Sum256(pubkey)
	return "SHA256:" + base64.RawStdEncoding.EncodeToString(h[:])
}
