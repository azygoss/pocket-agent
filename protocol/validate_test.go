// SPDX-License-Identifier: GPL-3.0-or-later
package pocketprotocol

import (
	"encoding/json"
	"os"
	"testing"
)

func loadGolden(t *testing.T) map[string]json.RawMessage {
	t.Helper()
	b, err := os.ReadFile("fixtures/golden-v1.json")
	if err != nil {
		t.Fatal(err)
	}
	var g map[string]json.RawMessage
	if err := json.Unmarshal(b, &g); err != nil {
		t.Fatal(err)
	}
	return g
}

func TestGoldenOK(t *testing.T) {
	g := loadGolden(t)
	if err := ValidateEventSummary(g["event_summary_ok"]); err != nil {
		t.Fatalf("golden ok rejected: %v", err)
	}
}

func TestGoldenRejected(t *testing.T) {
	g := loadGolden(t)
	if err := ValidateEventSummary(g["event_summary_rejected_forbidden_fields"]); err == nil {
		t.Fatal("forbidden fixture must be rejected")
	}
}

func TestMajorMismatchDisablesStructured(t *testing.T) {
	if Negotiate(1, 1) != true {
		t.Fatal("same major must enable structured")
	}
	if Negotiate(1, 2) != false {
		t.Fatal("major mismatch must disable structured (SSH stays open)")
	}
}

func TestUnknownCapsIgnored(t *testing.T) {
	have := map[string]bool{"ssh": true, "future_feature_x": true}
	got := IgnoreUnknownCaps(have)
	if !got["ssh"] {
		t.Fatal("ssh must survive")
	}
	if _, ok := got["future_feature_x"]; ok {
		t.Fatal("unknown cap must be ignored")
	}
}

func TestChunkContract(t *testing.T) {
	if err := ValidateChunk(0, 100, false); err != nil {
		t.Fatal(err)
	}
	if err := ValidateChunk(0, (1<<20)+1, false); err == nil {
		t.Fatal(">1MiB must fail")
	}
	if err := ValidateChunk(0, 0, false); err == nil {
		t.Fatal("empty non-EOF must fail")
	}
	if err := ValidateChunk(0, 0, true); err != nil {
		t.Fatal("empty EOF ok")
	}
}

func TestBrandedIDs(t *testing.T) {
	if !HasPrefixID("host:abc", "host") {
		t.Fatal("host: prefix")
	}
	if HasPrefixID("abc", "host") {
		t.Fatal("missing prefix must fail")
	}
}
