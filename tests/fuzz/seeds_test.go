// SPDX-License-Identifier: GPL-3.0-or-later
package fuzz

import (
	"encoding/json"
	"os"
	"testing"

	pocketprotocol "github.com/pocket-agent/pocket-agent/protocol"
)

// Seed corpus: golden + adversarial payloads must never panic, only accept/reject.
func TestSeeds(t *testing.T) {
	seeds := []string{
		`{"event_id":"event:1","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"ERROR","message":"x","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`,
		`{"event_id":"event:2","transcript":"SECRET","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"ERROR","message":"x","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`,
		`{"event_id":"event:3","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"ERROR","message":"` + string(make([]byte, 300)) + `","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`,
		`not-json-at-all`,
		``,
	}
	for i, sd := range seeds {
		_ = pocketprotocol.ValidateEventSummary(json.RawMessage(sd)) // must not panic
		_ = i
	}
	_ = os.Getenv("HOME")
}
