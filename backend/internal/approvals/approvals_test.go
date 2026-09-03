// SPDX-License-Identifier: GPL-3.0-or-later
package approvals

import "testing"

func TestRace(t *testing.T) {
	tb := New()
	if _, err := tb.Decide("event:1", "d", "3", "device:A"); err != nil {
		t.Fatal(err)
	}
	if _, err := tb.Decide("event:1", "d", "3", "device:B"); err != ErrConflict {
		t.Fatal("second device must lose race")
	}
	if _, err := tb.Decide("event:1", "d", "3", "device:A"); err != nil {
		t.Fatal("winner retry idempotent")
	}
}
