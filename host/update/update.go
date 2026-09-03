// SPDX-License-Identifier: GPL-3.0-or-later
// Package update: signed manifest required (P03/P18). Unsigned => refuse.
package update

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"os"
)

type Manifest struct {
	Version string
	SHA256  string // hex of binary
	Sig     string // non-empty required (ed25519/cosign verified in release job)
}

var ErrUnsigned = errors.New("update refused: missing signature")

func Verify(binPath string, m Manifest) error {
	if m.Sig == "" {
		return ErrUnsigned
	}
	b, err := os.ReadFile(binPath)
	if err != nil {
		return err
	}
	h := sha256.Sum256(b)
	if hex.EncodeToString(h[:]) != m.SHA256 {
		return errors.New("checksum mismatch")
	}
	return nil
}
