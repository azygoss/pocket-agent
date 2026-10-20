// SPDX-License-Identifier: GPL-3.0-or-later
package api

import (
	"crypto/ed25519"
	"crypto/rand"
	"encoding/json"
	"net/http/httptest"
	"strings"
	"testing"

	"golang.org/x/crypto/ssh"

	"github.com/pocket-agent/pocket-agent/backend/internal/store"
)

type harness struct {
	t   *testing.T
	s   *Server
	st  *store.Store
	ips int
}

func newHarness(t *testing.T) *harness {
	st := store.New()
	return &harness{t: t, s: New(st), st: st}
}

func (h *harness) do(method, path, token, body string) (int, string) {
	h.t.Helper()
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	h.s.Handler().ServeHTTP(w, req)
	return w.Code, w.Body.String()
}

func (h *harness) register(hostID string) (tenant, token string) {
	h.t.Helper()
	code, body := h.do("POST", "/v1/hosts/register", "", `{"host_id":"`+hostID+`","label":"laptop"}`)
	if code != 201 {
		h.t.Fatalf("register %d %s", code, body)
	}
	var v struct{ TenantID, HostToken string }
	v.TenantID = field(body, "tenant_id")
	v.HostToken = field(body, "host_token")
	return v.TenantID, v.HostToken
}

func field(body, k string) string {
	var m map[string]any
	_ = json.Unmarshal([]byte(body), &m)
	s, _ := m[k].(string)
	return s
}

func pubKey(t *testing.T) string {
	pub, _, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	sp, _ := ssh.NewPublicKey(pub)
	return strings.TrimSpace(string(ssh.MarshalAuthorizedKey(sp))) + " phone"
}

const evOK = `{"event_id":"event:1","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"APPROVAL_REQUIRED","title":"t","message":"hi","request_digest":"d","revision":"3","created_at":"2026-09-03T12:00:00Z","expires_at":"2099-09-03T13:00:00Z"}`

func TestUnauthenticatedRejected(t *testing.T) {
	h := newHarness(t)
	for _, r := range [][2]string{
		{"GET", "/v1/events"}, {"POST", "/v1/hosts/h/events"}, {"POST", "/v1/pairing-sessions"},
		{"GET", "/v1/hosts"}, {"GET", "/v1/devices"}, {"POST", "/v1/uploads"},
		{"POST", "/v1/approvals/event:1/actions"}, {"GET", "/v1/pairing-sessions/ABCD2345"},
	} {
		if code, _ := h.do(r[0], r[1], "", `{}`); code != 401 {
			t.Fatalf("%s %s without token: %d", r[0], r[1], code)
		}
		if code, _ := h.do(r[0], r[1], "pad_forged", `{}`); code != 401 {
			t.Fatalf("%s %s with forged token: %d", r[0], r[1], code)
		}
	}
	// Legacy X-Tenant header grants nothing.
	req := httptest.NewRequest("GET", "/v1/events", nil)
	req.Header.Set("X-Tenant", "default")
	w := httptest.NewRecorder()
	h.s.Handler().ServeHTTP(w, req)
	if w.Code != 401 {
		t.Fatalf("X-Tenant must not authenticate: %d", w.Code)
	}
}

func TestEnrollTokenRequired(t *testing.T) {
	h := newHarness(t)
	h.s.EnrollToken = "s3cret"
	if code, _ := h.do("POST", "/v1/hosts/register", "", `{"host_id":"h"}`); code != 403 {
		t.Fatalf("missing enroll token: %d", code)
	}
	if code, _ := h.do("POST", "/v1/hosts/register", "", `{"host_id":"h","enroll_token":"s3cret"}`); code != 201 {
		t.Fatalf("valid enroll token: %d", code)
	}
}

// Full keyless pairing: host opens a code, device claims with its public
// key, host sees the key and completes; the device token then reads events.
func TestKeylessPairingFlow(t *testing.T) {
	h := newHarness(t)
	tenant, hostTok := h.register("host:laptop")
	if !strings.HasPrefix(hostTok, "pah_") {
		t.Fatalf("host token prefix: %q", hostTok)
	}
	if st, _ := h.st.LookupToken(hostTok); st.TenantID != "" {
		t.Fatal("raw token must never be a store key")
	}
	// Key material in the payload is refused.
	if code, _ := h.do("POST", "/v1/pairing-sessions", hostTok, `{"code":"ABCD2345","payload":"{\"private_key\":\"-----BEGIN OPENSSH PRIVATE KEY-----\"}"}`); code != 400 {
		t.Fatalf("private key payload must be 400, got %d", code)
	}
	payload := `{\"ssh_host\":\"10.0.0.2\",\"ssh_port\":22,\"ssh_user\":\"berk\"}`
	if code, body := h.do("POST", "/v1/pairing-sessions", hostTok, `{"code":"ABCD-2345","payload":"`+payload+`"}`); code != 201 || field(body, "code") != "ABCD2345" {
		t.Fatalf("create %d %s", code, body)
	}
	if code, _ := h.do("POST", "/v1/pairing-sessions", hostTok, `{"code":"ABCD2345"}`); code != 409 {
		t.Fatalf("live code reuse: %d", code)
	}
	// Bad public key.
	if code, _ := h.do("POST", "/v1/pairing-sessions/ABCD2345/claim", "", `{"device_id":"device:A","public_key":"nope"}`); code != 400 {
		t.Fatalf("bad public key: %d", code)
	}
	pk := pubKey(t)
	body, _ := json.Marshal(map[string]string{"device_id": "device:A", "device_name": "iPhone", "public_key": pk})
	code, resp := h.do("POST", "/v1/pairing-sessions/abcd-2345/claim", "", string(body))
	if code != 200 {
		t.Fatalf("claim %d %s", code, resp)
	}
	devTok := field(resp, "device_token")
	if !strings.HasPrefix(devTok, "pad_") || field(resp, "tenant_id") != tenant || !strings.Contains(field(resp, "payload"), "berk") {
		t.Fatalf("claim response: %s", resp)
	}
	if strings.Contains(strings.ToLower(resp), "private") {
		t.Fatal("claim response must not carry key material")
	}
	// Another device loses the race.
	body2, _ := json.Marshal(map[string]string{"device_id": "device:B", "public_key": pubKey(t)})
	if code, _ := h.do("POST", "/v1/pairing-sessions/ABCD2345/claim", "", string(body2)); code != 409 {
		t.Fatalf("race must be 409, got %d", code)
	}
	// Host sees the device's public key; device sees only state.
	code, hv := h.do("GET", "/v1/pairing-sessions/ABCD2345", hostTok, "")
	if code != 200 || field(hv, "state") != "CLAIMED" || field(hv, "public_key") != strings.TrimSuffix(pk, " phone") || field(hv, "device_name") != "iPhone" {
		t.Fatalf("host view %d %s", code, hv)
	}
	if _, dv := h.do("GET", "/v1/pairing-sessions/ABCD2345", devTok, ""); field(dv, "public_key") != "" || field(dv, "state") != "CLAIMED" {
		t.Fatalf("device view: %s", dv)
	}
	// Device cannot complete; host can.
	// Before approval the claim token is not a bearer token at all.
	if code, _ := h.do("GET", "/v1/events", devTok, ""); code != 401 {
		t.Fatalf("unapproved claimant must not read events: %d", code)
	}
	if code, _ := h.do("POST", "/v1/pairing-sessions/ABCD2345/complete", devTok, `{"approved":true}`); code != 401 {
		t.Fatalf("device complete: %d", code)
	}
	if code, _ := h.do("POST", "/v1/pairing-sessions/ABCD2345/complete", hostTok, `{"approved":true}`); code != 200 {
		t.Fatalf("complete: %d", code)
	}
	if _, dv := h.do("GET", "/v1/pairing-sessions/ABCD2345", devTok, ""); field(dv, "state") != "CONSUMED" {
		t.Fatalf("device view after complete: %s", dv)
	}
	// Events: host writes, device reads; tenant from token.
	if code, _ := h.do("POST", "/v1/hosts/host:laptop/events", hostTok, evOK); code != 202 {
		t.Fatalf("post event %d", code)
	}
	if code, _ := h.do("POST", "/v1/hosts/host:laptop/events", devTok, evOK); code != 403 {
		t.Fatalf("device must not post host events: %d", code)
	}
	if code, evs := h.do("GET", "/v1/events", devTok, ""); code != 200 || !strings.Contains(evs, "event:1") {
		t.Fatalf("device events %d %s", code, evs)
	}
	if _, who := h.do("GET", "/v1/whoami", devTok, ""); field(who, "subject") != "device:A" || field(who, "kind") != "device" {
		t.Fatalf("whoami: %s", who)
	}
	// Approval: deciding device is the token subject.
	if code, _ := h.do("POST", "/v1/approvals/event:1/actions", hostTok, `{"digest":"d","revision":"3"}`); code != 403 {
		t.Fatalf("host approval must be 403: %d", code)
	}
	if code, _ := h.do("POST", "/v1/approvals/event:1/actions", devTok, `{"digest":"other","revision":"3"}`); code != 409 {
		t.Fatalf("mismatch must be 409: %d", code)
	}
	if code, _ := h.do("POST", "/v1/approvals/event:1/actions", devTok, `{"digest":"d","revision":"3"}`); code != 202 {
		t.Fatalf("approval: %d", code)
	}
	if code, _ := h.do("POST", "/v1/approvals/event:404/actions", devTok, `{"digest":"d","revision":"3"}`); code != 404 {
		t.Fatalf("unknown approval: %d", code)
	}
	// Revoking the device kills its token.
	if code, _ := h.do("DELETE", "/v1/devices/device:A", hostTok, ""); code != 204 {
		t.Fatalf("revoke device: %d", code)
	}
	if code, _ := h.do("GET", "/v1/events", devTok, ""); code != 401 {
		t.Fatalf("revoked device token must be 401: %d", code)
	}
}

func TestRejectedPairingRevokesDeviceToken(t *testing.T) {
	h := newHarness(t)
	_, hostTok := h.register("host:1")
	h.do("POST", "/v1/pairing-sessions", hostTok, `{"code":"WXYZ2345"}`)
	body, _ := json.Marshal(map[string]string{"device_id": "device:X", "public_key": pubKey(t)})
	_, resp := h.do("POST", "/v1/pairing-sessions/WXYZ2345/claim", "", string(body))
	devTok := field(resp, "device_token")
	if code, _ := h.do("POST", "/v1/pairing-sessions/WXYZ2345/complete", hostTok, `{"approved":false}`); code != 200 {
		t.Fatalf("reject: %d", code)
	}
	if code, _ := h.do("GET", "/v1/events", devTok, ""); code != 401 {
		t.Fatalf("rejected device token must be 401: %d", code)
	}
}

func TestCrossTenantIsolation(t *testing.T) {
	h := newHarness(t)
	_, tokA := h.register("host:a")
	_, tokB := h.register("host:b")
	h.do("POST", "/v1/hosts/host:a/events", tokA, evOK)
	if _, evs := h.do("GET", "/v1/events", tokB, ""); strings.Contains(evs, "event:1") {
		t.Fatal("tenant B must not read tenant A events")
	}
	if _, hosts := h.do("GET", "/v1/hosts", tokB, ""); strings.Contains(hosts, "host:a") {
		t.Fatal("tenant B must not list tenant A hosts")
	}
	if code, _ := h.do("DELETE", "/v1/hosts/host:a", tokB, ""); code != 403 {
		t.Fatalf("cross-tenant delete: %d", code)
	}
	h.do("POST", "/v1/pairing-sessions", tokA, `{"code":"PQRS2345"}`)
	if code, _ := h.do("GET", "/v1/pairing-sessions/PQRS2345", tokB, ""); code != 404 {
		t.Fatalf("foreign pairing must be 404: %d", code)
	}
	if code, _ := h.do("POST", "/v1/pairing-sessions/PQRS2345/complete", tokB, `{"approved":true}`); code != 404 {
		t.Fatalf("foreign complete must be 404: %d", code)
	}
}

func TestClaimRateLimited(t *testing.T) {
	h := newHarness(t)
	h.s.limiter.Limit = 5
	body, _ := json.Marshal(map[string]string{"device_id": "d", "public_key": pubKey(t)})
	got429 := false
	for i := 0; i < 10; i++ {
		if code, _ := h.do("POST", "/v1/pairing-sessions/NOPE2345/claim", "", string(body)); code == 429 {
			got429 = true
			break
		}
	}
	if !got429 {
		t.Fatal("brute-forcing codes must hit 429")
	}
}

func TestPostEventWhitelist(t *testing.T) {
	h := newHarness(t)
	_, tok := h.register("host:h")
	bad := `{"event_id":"event:2","transcript":"SECRET","opaque_host_id":"h","opaque_session_id":"s","source":"codex","category":"ERROR","message":"x","request_digest":"d","revision":"1","created_at":"2026-09-03T12:00:00Z","expires_at":"2026-09-03T13:00:00Z"}`
	if code, _ := h.do("POST", "/v1/hosts/someone-else/events", tok, evOK); code != 403 {
		t.Fatalf("host must not post as another host: %d", code)
	}
	code, body := h.do("POST", "/v1/hosts/host:h/events", tok, bad)
	if code != 400 {
		t.Fatalf("forbidden field must be 400, got %d", code)
	}
	if strings.Contains(body, "SECRET") {
		t.Fatal("reject response must not echo secret")
	}
}

func TestUploadCap(t *testing.T) {
	h := newHarness(t)
	_, tok := h.register("host:h")
	if code, _ := h.do("POST", "/v1/uploads", tok, `{"id":"u1","short_code":"s1","size":10485761}`); code != 400 {
		t.Fatalf("10MB+1 must be 400, got %d", code)
	}
	if code, _ := h.do("POST", "/v1/uploads", tok, `{"id":"u1","short_code":"s1","size":10}`); code != 201 {
		t.Fatalf("upload: %d", code)
	}
	if code, _ := h.do("GET", "/i/s1", "", ""); code != 200 {
		t.Fatalf("short url: %d", code)
	}
}
