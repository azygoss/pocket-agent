// SPDX-License-Identifier: GPL-3.0-or-later
// Package gateway: 127.0.0.1:24543 only; workspace-root jail + loopback-only preview.
package gateway

import (
	"errors"
	"net"
	"path/filepath"
	"strings"
)

const Addr = "127.0.0.1:24543"

var (
	ErrTraversal = errors.New("path traversal rejected")
	ErrSSRF      = errors.New("non-loopback preview rejected")
	ErrBinary    = errors.New("binary not rendered")
)

// Jail joins workspaceRoot + rel and rejects escape/symlink-ish ".." upfront.
// (Symlink resolution check happens at open time with O_NOFOLLOW + EvalSymlinks.)
func Jail(workspaceRoot, rel string) (string, error) {
	if filepath.IsAbs(rel) {
		return "", ErrTraversal
	}
	// Strict: any ".." segment is rejected (no normalization games).
	for _, seg := range strings.Split(filepath.ToSlash(rel), "/") {
		if seg == ".." {
			return "", ErrTraversal
		}
	}
	clean := filepath.Clean("/" + rel)[1:]
	if clean == ".." || strings.HasPrefix(clean, "../") || strings.Contains(clean, "../") {
		return "", ErrTraversal
	}
	joined := filepath.Join(workspaceRoot, clean)
	rel2, err := filepath.Rel(workspaceRoot, joined)
	if err != nil || rel2 == ".." || strings.HasPrefix(rel2, "../") {
		return "", ErrTraversal
	}
	return joined, nil
}

// LoopbackOnly accepts only 127.0.0.0/8 and ::1.
func LoopbackOnly(host string) error {
	ip := net.ParseIP(host)
	if ip == nil {
		if strings.EqualFold(host, "localhost") {
			return nil
		}
		return ErrSSRF
	}
	if ip.IsLoopback() {
		return nil
	}
	return ErrSSRF
}

// TextOnly refuses known binary extensions from text render (listed, not rendered).
func TextOnly(name string) error {
	l := strings.ToLower(name)
	for _, ext := range []string{".png", ".jpg", ".jpeg", ".gif", ".mp4", ".zip", ".apk", ".so"} {
		if strings.HasSuffix(l, ext) {
			return ErrBinary
		}
	}
	return nil
}
