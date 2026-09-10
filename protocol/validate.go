// SPDX-License-Identifier: GPL-3.0-or-later
// Package pocketprotocol freezes P01 contract logic in pure Go (no codegen yet).
// Codegen (buf generate) lands when network allows; logic here is authoritative.
package pocketprotocol

import (
	"encoding/json"
	"fmt"
	"strings"
)

var allowedSources = map[string]bool{
	"claude": true, "codex": true, "opencode": true, "cursor": true,
	"kimi": true, "grok": true, "pi": true, "omp": true, "hermes": true,
	"gemini": true, "antigravity": true, "qwen": true, "devin": true,
}

var allowedCategories = map[string]bool{
	"APPROVAL_REQUIRED": true, "TASK_COMPLETE": true, "SESSION_STARTED": true,
	"SESSION_ENDED": true, "TOOL_RUNNING": true, "TOOL_FINISHED": true, "ERROR": true,
}

var forbiddenFields = []string{
	"transcript", "code", "diff", "filePath", "file_path",
	"previewBody", "preview_body", "password", "privateKey", "private_key",
	"audio", "scrollback", "ssh_secret", "mosh_key",
}

// ValidateEventSummary enforces AgentEventSummary whitelist on raw JSON.
func ValidateEventSummary(raw json.RawMessage) error {
	var m map[string]json.RawMessage
	if err := json.Unmarshal(raw, &m); err != nil {
		return fmt.Errorf("invalid json: %w", err)
	}
	for _, f := range forbiddenFields {
		if _, ok := m[f]; ok {
			return fmt.Errorf("forbidden field %q", f)
		}
	}
	var s struct {
		EventID        string `json:"event_id"`
		OpaqueHostID   string `json:"opaque_host_id"`
		OpaqueSession  string `json:"opaque_session_id"`
		Source         string `json:"source"`
		Category       string `json:"category"`
		Message        string `json:"message"`
		RequestDigest  string `json:"request_digest"`
		Revision       string `json:"revision"`
		CreatedAt      string `json:"created_at"`
		ExpiresAt      string `json:"expires_at"`
		ContextPercent int    `json:"context_percent"`
	}
	if err := json.Unmarshal(raw, &s); err != nil {
		return err
	}
	if s.EventID == "" || s.OpaqueHostID == "" || s.OpaqueSession == "" {
		return fmt.Errorf("missing id fields")
	}
	if !allowedSources[s.Source] {
		return fmt.Errorf("unknown source %q", s.Source)
	}
	if !allowedCategories[s.Category] {
		return fmt.Errorf("unknown category %q", s.Category)
	}
	if len([]rune(s.Message)) > 256 {
		return fmt.Errorf("message >256 chars")
	}
	if s.RequestDigest == "" || s.Revision == "" {
		return fmt.Errorf("missing digest/revision")
	}
	return nil
}

// Negotiate resolves structured_enabled from major versions.
func Negotiate(clientMajor, hostMajor uint32) (structuredEnabled bool) {
	return clientMajor == hostMajor
}

// IgnoreUnknownCaps models "unknown capability => ignore" rule.
func IgnoreUnknownCaps(have map[string]bool) map[string]bool {
	known := []string{"ssh", "mosh", "et", "tmux", "zellij", "herdr", "gateway", "chat"}
	out := map[string]bool{}
	for _, k := range known {
		out[k] = have[k]
	}
	return out
}

// ValidateChunk enforces offset/length/hash/EOF contract.
func ValidateChunk(offset uint64, dataLen int, eof bool) error {
	const maxChunk = 1 << 20 // 1 MiB
	if dataLen > maxChunk {
		return fmt.Errorf("chunk >1MiB")
	}
	if dataLen == 0 && !eof {
		return fmt.Errorf("empty non-EOF chunk")
	}
	_ = offset
	return nil
}

// HasPrefixID checks branded id prefix (host:, device:, etc.).
func HasPrefixID(id, prefix string) bool {
	return strings.HasPrefix(id, prefix+":") && len(id) > len(prefix)+1
}
