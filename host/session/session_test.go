// SPDX-License-Identifier: GPL-3.0-or-later
package session

import "testing"

func TestHerdrHiddenWhenMissing(t *testing.T) {
	adapters := []Adapter{{Provider: Herdr, Available: false}, {Provider: Tmux, Available: true}}
	vis := Visible(adapters)
	for _, a := range vis {
		if a.Provider == Herdr {
			t.Fatal("herdr must be hidden when not installed")
		}
	}
}
