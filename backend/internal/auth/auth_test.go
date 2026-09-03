// SPDX-License-Identifier: GPL-3.0-or-later
package auth

import "testing"

func TestMintVerify(t *testing.T) {
	tok, hash, err := Mint("pk")
	if err != nil {
		t.Fatal(err)
	}
	if !Verify(tok, hash) {
		t.Fatal("minted token must verify")
	}
	if Verify(tok+"x", hash) {
		t.Fatal("forged token must not verify")
	}
	if hash == tok {
		t.Fatal("hash must not equal token (hash-only store)")
	}
}

func TestTenantGuard(t *testing.T) {
	if err := GuardTenant("t1", "t1"); err != nil {
		t.Fatal(err)
	}
	if err := GuardTenant("t1", "t2"); err == nil {
		t.Fatal("cross-tenant read must fail")
	}
	if err := GuardTenant("", "t1"); err == nil {
		t.Fatal("empty tenant must fail")
	}
}
