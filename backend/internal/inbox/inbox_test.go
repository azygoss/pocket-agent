// SPDX-License-Identifier: GPL-3.0-or-later
package inbox

import (
	"testing"
	"time"
)

func TestMergeAndTTL(t *testing.T) {
	b := New()
	now := time.Now()
	b.Add("s1", "event:1", now)
	b.Add("s1", "event:2", now)
	if len(b.items) != 1 {
		t.Fatal("session merge")
	}
	b.Add("s1", "event:old", now.Add(-25*time.Hour))
	if b.Sweep(now) != 1 {
		t.Fatal("ttl sweep")
	}
}
