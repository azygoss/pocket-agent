// SPDX-License-Identifier: GPL-3.0-or-later
// Package auth: token mint/verify (prefix + secret, hash-only store) + tenant guard.
package auth

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
)

func Mint(prefix string) (token, storedHash string, err error) {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "", "", err
	}
	secret := base64.RawURLEncoding.EncodeToString(b)
	token = prefix + "_" + secret
	h := sha256.Sum256([]byte(token))
	storedHash = fmt.Sprintf("sha256:%x", h)
	return token, storedHash, nil
}

func Verify(token, storedHash string) bool {
	h := sha256.Sum256([]byte(token))
	candidate := fmt.Sprintf("sha256:%x", h)
	return subtle.ConstantTimeCompare([]byte(candidate), []byte(storedHash)) == 1
}

var ErrTenantEscape = errors.New("tenant mismatch")

// GuardTenant enforces tenant_id on every query path.
func GuardTenant(recordTenant, callerTenant string) error {
	if recordTenant == "" || callerTenant == "" {
		return ErrTenantEscape
	}
	if subtle.ConstantTimeCompare([]byte(recordTenant), []byte(callerTenant)) != 1 {
		return ErrTenantEscape
	}
	return nil
}
