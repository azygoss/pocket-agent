// SPDX-License-Identifier: GPL-3.0-or-later
// Package ssh: real SSH transport (key/password, TOFU-pinned host keys).
// Auth/host-key failures are terminal: callers must NOT fall back (P06/P08).
package ssh

import (
	"bytes"
	"errors"
	"fmt"
	"net"
	"os"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"
)

var (
	ErrAuthFailed     = errors.New("ssh auth failed: check user/key/password (no fallback)")
	ErrHostKeyChanged = errors.New("ssh host key changed: hard-stop, re-pair required")
)

type Options struct {
	User           string
	Addr           string // host:port
	PrivateKeyPEM  []byte // optional
	Password       string // optional, RAM-only
	KnownHostsFile string // TOFU pin file (separate from ~/.ssh/known_hosts)
	Timeout        time.Duration
}

// Dial connects with TOFU pinning: first connect pins, mismatch hard-stops.
func Dial(o Options) (*ssh.Client, error) {
	var auths []ssh.AuthMethod
	if len(o.PrivateKeyPEM) > 0 {
		signer, err := ssh.ParsePrivateKey(o.PrivateKeyPEM)
		if err != nil {
			return nil, fmt.Errorf("%w: %v", ErrAuthFailed, err)
		}
		auths = append(auths, ssh.PublicKeys(signer))
	}
	if o.Password != "" {
		auths = append(auths, ssh.Password(o.Password))
	}
	if len(auths) == 0 {
		return nil, ErrAuthFailed
	}
	to := o.Timeout
	if to == 0 {
		to = 10 * time.Second
	}
	host, _, err := net.SplitHostPort(o.Addr)
	if err != nil {
		host = o.Addr
	}
	cb, err := tofuCallback(o.KnownHostsFile, host)
	if err != nil {
		return nil, err
	}
	cfg := &ssh.ClientConfig{
		User:            o.User,
		Auth:            auths,
		HostKeyCallback: cb,
		Timeout:         to,
	}
	c, err := ssh.Dial("tcp", o.Addr, cfg)
	if err != nil {
		return nil, classify(err)
	}
	return c, nil
}

// Run executes cmd over a session, returning combined output.
func Run(c *ssh.Client, cmd string) (string, error) {
	s, err := c.NewSession()
	if err != nil {
		return "", err
	}
	defer s.Close()
	out, err := s.CombinedOutput(cmd)
	return string(out), err
}

func classify(err error) error {
	msg := err.Error()
	if contains(msg, "unable to authenticate") || contains(msg, "no supported methods") || contains(msg, "handshake failed") {
		return fmt.Errorf("%w: %v", ErrAuthFailed, err)
	}
	return err
}

func contains(s, sub string) bool {
	return len(s) >= len(sub) && (func() bool {
		for i := 0; i+len(sub) <= len(s); i++ {
			if s[i:i+len(sub)] == sub {
				return true
			}
		}
		return false
	})()
}

// tofuCallback pins first-seen host key into file; mismatch => hard-stop.
func tofuCallback(file, host string) (ssh.HostKeyCallback, error) {
	if b, err := os.ReadFile(file); err == nil && len(bytes.TrimSpace(b)) > 0 {
		strict, err := knownhosts.New(file)
		if err != nil {
			return nil, err
		}
		return func(hostname string, remote net.Addr, key ssh.PublicKey) error {
			if err := strict(hostname, remote, key); err != nil {
				return fmt.Errorf("%w: %v", ErrHostKeyChanged, err)
			}
			return nil
		}, nil
	}
	// First connect: accept and pin (both callback hostname and our host,
	// plain + hashed, so redial matches regardless of hostname form).
	return func(hostname string, remote net.Addr, key ssh.PublicKey) error {
		seen := map[string]bool{}
		var addrs []string
		for _, a := range []string{hostname, host, knownhosts.HashHostname(hostname), knownhosts.HashHostname(host)} {
			if a != "" && !seen[a] {
				seen[a] = true
				addrs = append(addrs, a)
			}
		}
		line := knownhosts.Line(addrs, key)
		f, err := os.OpenFile(file, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o600)
		if err != nil {
			return err
		}
		defer f.Close()
		_, err = f.WriteString(line + "\n")
		return err
	}, nil
}
