// SPDX-License-Identifier: GPL-3.0-or-later
package gateway

import (
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

// PreviewFetch GETs only loopback targets, 1MB cap, 5s timeout (P11 SSRF gate).
func PreviewFetch(host string, port int, path string) (string, string, error) {
	if err := LoopbackOnly(host); err != nil {
		return "", "", err
	}
	if port < 1 || port > 65535 {
		return "", "", fmt.Errorf("bad port")
	}
	if !strings.HasPrefix(path, "/") {
		return "", "", fmt.Errorf("bad path")
	}
	url := fmt.Sprintf("http://%s:%d%s", host, port, path)
	c := &http.Client{Timeout: 5 * time.Second}
	resp, err := c.Get(url)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()
	b, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20+1))
	if err != nil {
		return "", "", err
	}
	truncated := len(b) > 1<<20
	if truncated {
		b = b[:1<<20]
	}
	ct := resp.Header.Get("Content-Type")
	return string(b), ct, nil
}
