// SPDX-License-Identifier: GPL-3.0-or-later
// Package store: tenant-isolated persistence. Memory backs tests and
// single-process dev runs; Postgres (pg.go) is used whenever DATABASE_URL
// is set. Both implement Backend with the same guard logic.
package store

import (
	"errors"
	"sort"
	"sync"
	"time"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
)

type Event struct {
	EventID    string
	TenantID   string
	OpaqueHost string
	OpaqueSess string
	Source     string
	Category   string
	Message    string
	Digest     string
	Revision   string
	CreatedAt  time.Time
	ExpiresAt  time.Time
	// Ekleme sırası: event kimlikleri hash olduğundan sıralanamaz; "after"
	// imleci bununla çalışır. Kabloya çıkmaz.
	Seq int64 `json:"-"`
}

// Pairing states: PENDING → CLAIMED (device sent its public key) →
// CONSUMED (host authorized the key) or REJECTED. PENDING past its TTL
// becomes EXPIRED.
type Pairing struct {
	Code     string
	TenantID string
	HostID   string
	// SSH connection hint (host/port/user/host-key fingerprints). Never a
	// private key: the device generates its own key pair.
	Payload         string
	State           string
	ClaimedBy       string
	DeviceLabel     string
	DevicePublicKey string
	ExpiresAt       time.Time
}

type Upload struct {
	ID        string
	TenantID  string
	ShortCode string
	Size      int64
	CreatedAt time.Time
	ExpiresAt time.Time
}

const (
	KindHost   = "host"
	KindDevice = "device"
)

// Token is the stored form of a bearer token: only its hash is kept.
type Token struct {
	Hash      string
	TenantID  string
	Kind      string // KindHost | KindDevice
	SubjectID string // host or device id
}

type Device struct {
	ID        string `json:"id"`
	Label     string `json:"label"`
	PublicKey string `json:"public_key"`
}

// Claim is what a device sends when it redeems a pairing code.
type Claim struct {
	DeviceID  string
	Label     string
	PublicKey string
	TokenHash string // device token minted for this claim
}

var (
	ErrNotFound = errors.New("not found")
	ErrExpired  = errors.New("expired")
	ErrConflict = errors.New("conflict")
	ErrTooLarge = errors.New("too large")
	ErrMismatch = errors.New("digest/revision mismatch")
)

// Backend is everything the API needs from persistence.
type Backend interface {
	CreateTenant(id string) error
	PutToken(t Token) error
	LookupToken(hash string) (Token, bool)
	RevokeTokens(tenant, kind, subject string) error

	AddHost(tenant, hostID, label string) error
	ListHosts(tenant string) []string
	RemoveHost(tenant, hostID string) error
	ListDevices(tenant string) []Device
	RemoveDevice(tenant, deviceID string) error

	CreatePairing(p Pairing) error
	GetPairing(code string) (Pairing, bool)
	// ClaimPairing atomically moves PENDING → CLAIMED, records the device
	// and its key and stores the device token. The same device may claim
	// again (lost response); its previous tokens are replaced.
	ClaimPairing(code string, c Claim, now time.Time) (Pairing, error)
	// FinishPairing moves CLAIMED → CONSUMED (approved) or REJECTED. A
	// rejected claim loses its device and tokens.
	FinishPairing(tenant, code string, approved bool) error

	PutEvent(tenant string, e Event) error
	GetEvents(tenant, after string) []Event
	// Decide records the first decision for an event (CAS); the same
	// device deciding again is idempotent.
	Decide(tenant, eventID, digest, revision, device string) error

	PutUpload(tenant string, u Upload) error
	GetUploadByShort(code string, now time.Time) (Upload, bool)
	DeleteUpload(tenant, id string) error

	SweepTTL(now time.Time) (events, pairings int)
}

func capTTL(created, expires time.Time) time.Time {
	if expires.Sub(created) > 24*time.Hour {
		return created.Add(24 * time.Hour)
	}
	return expires
}

type decision struct {
	digest, revision, winner string
}

type Store struct {
	mu        sync.Mutex
	seq       int64
	tenants   map[string]bool
	tokens    map[string]Token
	events    map[string]Event // tenant + "\x00" + event id
	decisions map[string]decision
	pairings  map[string]Pairing
	users     map[string]string            // userID -> tenantID
	devices   map[string]map[string]Device // tenant -> id -> device
	hosts     map[string]map[string]string // tenant -> id -> label
	uploads   map[string]Upload            // tenant + "\x00" + id
	byShort   map[string]string            // shortCode -> upload key
	webhooks  map[string]string
}

var _ Backend = (*Store)(nil)

func New() *Store {
	return &Store{
		tenants: map[string]bool{}, tokens: map[string]Token{}, events: map[string]Event{},
		decisions: map[string]decision{}, pairings: map[string]Pairing{}, users: map[string]string{},
		devices: map[string]map[string]Device{}, hosts: map[string]map[string]string{},
		uploads: map[string]Upload{}, byShort: map[string]string{}, webhooks: map[string]string{},
	}
}

func key(tenant, id string) string { return tenant + "\x00" + id }

func (s *Store) CreateTenant(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tenants[id] = true
	return nil
}

func (s *Store) PutToken(t Token) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.tokens[t.Hash] = t
	return nil
}

func (s *Store) LookupToken(hash string) (Token, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	t, ok := s.tokens[hash]
	return t, ok
}

func (s *Store) RevokeTokens(tenant, kind, subject string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.revokeLocked(tenant, kind, subject)
	return nil
}

func (s *Store) revokeLocked(tenant, kind, subject string) {
	for h, t := range s.tokens {
		if t.TenantID == tenant && t.Kind == kind && t.SubjectID == subject {
			delete(s.tokens, h)
		}
	}
}

func (s *Store) AddUser(id, tenant string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.users[id] = tenant
}

// PutEvent enforces tenant guard + 24h TTL cap.
func (s *Store) PutEvent(callerTenant string, e Event) error {
	if err := auth.GuardTenant(e.TenantID, callerTenant); err != nil {
		return err
	}
	e.ExpiresAt = capTTL(e.CreatedAt, e.ExpiresAt)
	s.mu.Lock()
	defer s.mu.Unlock()
	k := key(e.TenantID, e.EventID)
	// Aynı olayın tekrar gönderimi (idempotent) sırasını korur.
	if old, ok := s.events[k]; ok {
		e.Seq = old.Seq
	} else {
		s.seq++
		e.Seq = s.seq
	}
	s.events[k] = e
	return nil
}

// GetEvents: tenant'ın olayları eklenme sırasıyla. after, istemcinin aldığı
// son olayın kimliğidir; ondan sonra eklenenler döner. Bilinmeyen ya da
// süresi dolmuş imleçte hepsi döner (istemci kimlikle ayıklar).
func (s *Store) GetEvents(callerTenant, after string) []Event {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := []Event{}
	for _, e := range s.events {
		if e.TenantID == callerTenant {
			out = append(out, e)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].Seq < out[j].Seq })
	if cur, ok := s.events[key(callerTenant, after)]; ok && after != "" {
		i := sort.Search(len(out), func(i int) bool { return out[i].Seq > cur.Seq })
		out = out[i:]
	}
	return out
}

func (s *Store) Decide(tenant, eventID, digest, revision, device string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	k := key(tenant, eventID)
	e, ok := s.events[k]
	if !ok {
		return ErrNotFound
	}
	if d, ok := s.decisions[k]; ok {
		if d.winner == device && d.digest == digest && d.revision == revision {
			return nil
		}
		return ErrConflict
	}
	if e.Digest != digest || e.Revision != revision {
		return ErrMismatch
	}
	s.decisions[k] = decision{digest, revision, device}
	return nil
}

// SweepTTL deletes expired events/uploads and expires pending pairings.
func (s *Store) SweepTTL(now time.Time) (events, pairings int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for k, e := range s.events {
		if !e.ExpiresAt.After(now) {
			delete(s.events, k)
			delete(s.decisions, k)
			events++
		}
	}
	for c, p := range s.pairings {
		if !p.ExpiresAt.After(now) && p.State == "PENDING" {
			p.State, p.Payload = "EXPIRED", ""
			s.pairings[c] = p
			pairings++
		}
	}
	for k, u := range s.uploads {
		if !u.ExpiresAt.After(now) {
			delete(s.byShort, u.ShortCode)
			delete(s.uploads, k)
		}
	}
	return
}

// WipeUser cascade-deletes user data.
func (s *Store) WipeUser(userID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	tenant := s.users[userID]
	for k, e := range s.events {
		if e.TenantID == tenant {
			delete(s.events, k)
			delete(s.decisions, k)
		}
	}
	delete(s.users, userID)
}

func (s *Store) CreatePairing(p Pairing) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if old, ok := s.pairings[p.Code]; ok && old.State != "EXPIRED" {
		return ErrConflict
	}
	if p.State == "" {
		p.State = "PENDING"
	}
	s.pairings[p.Code] = p
	return nil
}

func (s *Store) GetPairing(code string) (Pairing, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.pairings[code]
	return p, ok
}

func (s *Store) ClaimPairing(code string, c Claim, now time.Time) (Pairing, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.pairings[code]
	if !ok {
		return Pairing{}, ErrNotFound
	}
	switch {
	case p.State == "PENDING" && now.After(p.ExpiresAt), p.State == "EXPIRED":
		return Pairing{}, ErrExpired
	case p.State == "CLAIMED" && p.ClaimedBy == c.DeviceID:
		// retry after a lost response: replace the token below
	case p.State != "PENDING":
		return Pairing{}, ErrConflict
	}
	p.State, p.ClaimedBy, p.DeviceLabel, p.DevicePublicKey = "CLAIMED", c.DeviceID, c.Label, c.PublicKey
	s.pairings[code] = p
	if s.devices[p.TenantID] == nil {
		s.devices[p.TenantID] = map[string]Device{}
	}
	s.devices[p.TenantID][c.DeviceID] = Device{ID: c.DeviceID, Label: c.Label, PublicKey: c.PublicKey}
	s.revokeLocked(p.TenantID, KindDevice, c.DeviceID)
	s.tokens[c.TokenHash] = Token{Hash: c.TokenHash, TenantID: p.TenantID, Kind: KindDevice, SubjectID: c.DeviceID}
	return p, nil
}

func (s *Store) FinishPairing(tenant, code string, approved bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	p, ok := s.pairings[code]
	if !ok || p.TenantID != tenant {
		return ErrNotFound
	}
	if p.State != "CLAIMED" {
		return ErrConflict
	}
	p.Payload = ""
	if approved {
		p.State = "CONSUMED"
	} else {
		p.State = "REJECTED"
		delete(s.devices[tenant], p.ClaimedBy)
		s.revokeLocked(tenant, KindDevice, p.ClaimedBy)
	}
	s.pairings[code] = p
	return nil
}

func (s *Store) ListDevices(tenant string) []Device {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := []Device{}
	for _, d := range s.devices[tenant] {
		out = append(out, d)
	}
	sort.Slice(out, func(i, j int) bool { return out[i].ID < out[j].ID })
	return out
}

func (s *Store) RemoveDevice(tenant, deviceID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.devices[tenant][deviceID]; !ok {
		return ErrNotFound // do not leak existence across tenants
	}
	delete(s.devices[tenant], deviceID)
	s.revokeLocked(tenant, KindDevice, deviceID)
	return nil
}

func (s *Store) AddHost(tenant, hostID, label string) error {
	if tenant == "" || hostID == "" {
		return ErrNotFound
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.hosts[tenant] == nil {
		s.hosts[tenant] = map[string]string{}
	}
	s.hosts[tenant][hostID] = label
	return nil
}

func (s *Store) ListHosts(tenant string) []string {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := []string{}
	for hid := range s.hosts[tenant] {
		out = append(out, hid)
	}
	sort.Strings(out)
	return out
}

func (s *Store) RemoveHost(tenant, hostID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, ok := s.hosts[tenant][hostID]; !ok {
		return ErrNotFound
	}
	delete(s.hosts[tenant], hostID)
	s.revokeLocked(tenant, KindHost, hostID)
	return nil
}

// Uploads (P15): 10MB cap + 24h TTL enforced at store layer too.
func (s *Store) PutUpload(tenant string, u Upload) error {
	if u.TenantID != tenant {
		return ErrConflict
	}
	if u.Size > 10<<20 {
		return ErrTooLarge
	}
	u.ExpiresAt = capTTL(u.CreatedAt, u.ExpiresAt)
	s.mu.Lock()
	defer s.mu.Unlock()
	if k, ok := s.byShort[u.ShortCode]; ok && k != key(tenant, u.ID) {
		return ErrConflict
	}
	s.uploads[key(tenant, u.ID)] = u
	s.byShort[u.ShortCode] = key(tenant, u.ID)
	return nil
}

func (s *Store) GetUploadByShort(code string, now time.Time) (Upload, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	k, ok := s.byShort[code]
	if !ok {
		return Upload{}, false
	}
	u := s.uploads[k]
	return u, now.Before(u.ExpiresAt)
}

func (s *Store) DeleteUpload(tenant, id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	u, ok := s.uploads[key(tenant, id)]
	if !ok {
		return ErrNotFound
	}
	delete(s.byShort, u.ShortCode)
	delete(s.uploads, key(tenant, id))
	return nil
}

// Webhook tokens (P02): prefix lookup, hash-only secret.
func (s *Store) PutWebhookToken(prefix, secretHash, tenant string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.webhooks[prefix] = secretHash + "|" + tenant
}

func (s *Store) GetWebhookToken(prefix string) (hash, tenant string, ok bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	v, ok := s.webhooks[prefix]
	if !ok {
		return "", "", false
	}
	for i := 0; i < len(v); i++ {
		if v[i] == '|' {
			return v[:i], v[i+1:], true
		}
	}
	return v, "", true
}
