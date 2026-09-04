// SPDX-License-Identifier: GPL-3.0-or-later
package main

import (
	"crypto/rand"
	"encoding/hex"
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"path/filepath"

	"github.com/pocket-agent/pocket-agent/host/gateway"
)

// `pocket-agent gateway serve`: P11 dosya/diff sunucusu. YALNIZ loopback
// (127.0.0.1:24543); Android yalnız SSH local-forward üzerinden erişir.
// Token 0600 dosyada durur, yoksa üretilir; env ile override test içindir.
func cmdGateway(args []string) {
	fs := flag.NewFlagSet("gateway", flag.ExitOnError)
	root := fs.String("root", "", "workspace root (default: cwd)")
	addr := fs.String("addr", gateway.Addr, "bind adresi (loopback zorunlu)")
	if len(args) == 0 || args[0] != "serve" {
		fmt.Fprintln(os.Stderr, "usage: gateway serve [--root dir] [--addr 127.0.0.1:24543]")
		os.Exit(2)
	}
	// flag paketi ilk non-flag argümanı görünce durur — "serve"i atlayarak parse et.
	fs.Parse(args[1:])

	host, _, err := net.SplitHostPort(*addr)
	if err != nil || (host != "127.0.0.1" && host != "localhost" && host != "::1") {
		fmt.Fprintln(os.Stderr, "gateway bind yalnız loopback olabilir (P11)")
		os.Exit(2)
	}

	r := *root
	if r == "" {
		r, _ = os.Getwd()
	}

	token := os.Getenv("POCKET_GATEWAY_TOKEN")
	if token == "" {
		token, err = gatewayToken()
		if err != nil {
			fmt.Fprintln(os.Stderr, "token:", err)
			os.Exit(1)
		}
	}

	srv := &gateway.Server{Token: token, Root: r}
	fmt.Fprintf(os.Stderr, "gateway listening on %s (root=%s)\n", *addr, r)
	if err := http.ListenAndServe(*addr, srv); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// Token dosyası: $POCKET_HOME/.config/pocket-agent/gateway.token (0600).
// Yoksa 32B crypto-random üretilir ve 0600 ile yazılır.
func gatewayToken() (string, error) {
	p := filepath.Join(home(), ".config", "pocket-agent", "gateway.token")
	if b, err := os.ReadFile(p); err == nil && len(b) >= 32 {
		return stringTrimNL(b), nil
	}
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	tok := hex.EncodeToString(raw)
	if err := os.MkdirAll(filepath.Dir(p), 0o700); err != nil {
		return "", err
	}
	if err := os.WriteFile(p, []byte(tok), 0o600); err != nil {
		return "", err
	}
	return tok, nil
}

func stringTrimNL(b []byte) string {
	for len(b) > 0 && (b[len(b)-1] == '\n' || b[len(b)-1] == '\r') {
		b = b[:len(b)-1]
	}
	return string(b)
}
