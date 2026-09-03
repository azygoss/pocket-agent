// SPDX-License-Identifier: GPL-3.0-or-later
package update

import (
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"
)

func TestVerify(t *testing.T) {
	p := filepath.Join(t.TempDir(), "bin")
	os.WriteFile(p, []byte("data"), 0o600)
	h := sha256.Sum256([]byte("data"))
	m := Manifest{Version: "1", SHA256: hex.EncodeToString(h[:]), Sig: "sig"}
	if err := Verify(p, m); err != nil {
		t.Fatal(err)
	}
	if err := Verify(p, Manifest{Version: "1", SHA256: m.SHA256}); err != ErrUnsigned {
		t.Fatalf("unsigned must refuse: %v", err)
	}
	if err := Verify(p, Manifest{Version: "1", SHA256: "00", Sig: "sig"}); err == nil {
		t.Fatal("bad checksum must fail")
	}
}
