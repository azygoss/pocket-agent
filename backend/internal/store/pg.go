// SPDX-License-Identifier: GPL-3.0-or-later
package store

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"sort"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pocket-agent/pocket-agent/backend/internal/auth"
)

//go:embed migrations/*.sql
var migrationFS embed.FS

// PG is the Postgres-backed Backend. Methods use a bounded per-call timeout
// so a stalled database cannot pin request goroutines forever.
type PG struct {
	pool    *pgxpool.Pool
	timeout time.Duration
}

var _ Backend = (*PG)(nil)

// OpenPostgres connects, waits for the database (compose start order) and
// applies pending migrations.
func OpenPostgres(ctx context.Context, url string) (*PG, error) {
	pool, err := pgxpool.New(ctx, url)
	if err != nil {
		return nil, err
	}
	deadline := time.Now().Add(30 * time.Second)
	for {
		if err = pool.Ping(ctx); err == nil || time.Now().After(deadline) {
			break
		}
		time.Sleep(time.Second)
	}
	if err != nil {
		pool.Close()
		return nil, fmt.Errorf("postgres unreachable: %w", err)
	}
	p := &PG{pool: pool, timeout: 5 * time.Second}
	if err := p.migrate(ctx); err != nil {
		pool.Close()
		return nil, err
	}
	return p, nil
}

func (p *PG) Close() { p.pool.Close() }

func (p *PG) migrate(ctx context.Context) error {
	names, err := fs.Glob(migrationFS, "migrations/*.sql")
	if err != nil {
		return err
	}
	sort.Strings(names)
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	// Serialize concurrent replicas starting at once.
	if _, err := tx.Exec(ctx, `SELECT pg_advisory_xact_lock(7420113)`); err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `CREATE TABLE IF NOT EXISTS schema_migrations (name TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())`); err != nil {
		return err
	}
	for _, n := range names {
		var done bool
		if err := tx.QueryRow(ctx, `SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE name=$1)`, n).Scan(&done); err != nil {
			return err
		}
		if done {
			continue
		}
		sql, err := migrationFS.ReadFile(n)
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, string(sql)); err != nil {
			return fmt.Errorf("migration %s: %w", n, err)
		}
		if _, err := tx.Exec(ctx, `INSERT INTO schema_migrations(name) VALUES ($1)`, n); err != nil {
			return err
		}
	}
	return tx.Commit(ctx)
}

func (p *PG) ctx() (context.Context, context.CancelFunc) {
	return context.WithTimeout(context.Background(), p.timeout)
}

func (p *PG) exec(sql string, args ...any) (pgconn.CommandTag, error) {
	ctx, cancel := p.ctx()
	defer cancel()
	return p.pool.Exec(ctx, sql, args...)
}

func (p *PG) CreateTenant(id string) error {
	_, err := p.exec(`INSERT INTO tenants(id) VALUES ($1) ON CONFLICT DO NOTHING`, id)
	return err
}

func (p *PG) PutToken(t Token) error {
	_, err := p.exec(`INSERT INTO tokens(hash, tenant_id, kind, subject_id) VALUES ($1,$2,$3,$4)`, t.Hash, t.TenantID, t.Kind, t.SubjectID)
	return err
}

func (p *PG) LookupToken(hash string) (Token, bool) {
	ctx, cancel := p.ctx()
	defer cancel()
	t := Token{Hash: hash}
	err := p.pool.QueryRow(ctx, `SELECT tenant_id, kind, subject_id FROM tokens WHERE hash=$1`, hash).Scan(&t.TenantID, &t.Kind, &t.SubjectID)
	return t, err == nil
}

func (p *PG) RevokeTokens(tenant, kind, subject string) error {
	_, err := p.exec(`DELETE FROM tokens WHERE tenant_id=$1 AND kind=$2 AND subject_id=$3`, tenant, kind, subject)
	return err
}

func (p *PG) AddHost(tenant, hostID, label string) error {
	if tenant == "" || hostID == "" {
		return ErrNotFound
	}
	_, err := p.exec(`INSERT INTO hosts(tenant_id, id, label) VALUES ($1,$2,$3) ON CONFLICT (tenant_id, id) DO UPDATE SET label=EXCLUDED.label`, tenant, hostID, label)
	return err
}

func (p *PG) strings(sql string, args ...any) []string {
	ctx, cancel := p.ctx()
	defer cancel()
	out := []string{}
	rows, err := p.pool.Query(ctx, sql, args...)
	if err != nil {
		return out
	}
	defer rows.Close()
	for rows.Next() {
		var s string
		if rows.Scan(&s) == nil {
			out = append(out, s)
		}
	}
	return out
}

func (p *PG) ListHosts(tenant string) []string {
	return p.strings(`SELECT id FROM hosts WHERE tenant_id=$1 ORDER BY id`, tenant)
}

func (p *PG) inTx(fn func(ctx context.Context, tx pgx.Tx) error) error {
	ctx, cancel := p.ctx()
	defer cancel()
	tx, err := p.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	if err := fn(ctx, tx); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (p *PG) RemoveHost(tenant, hostID string) error {
	return p.inTx(func(ctx context.Context, tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `DELETE FROM hosts WHERE tenant_id=$1 AND id=$2`, tenant, hostID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return ErrNotFound
		}
		_, err = tx.Exec(ctx, `DELETE FROM tokens WHERE tenant_id=$1 AND kind=$2 AND subject_id=$3`, tenant, KindHost, hostID)
		return err
	})
}

func (p *PG) ListDevices(tenant string) []Device {
	ctx, cancel := p.ctx()
	defer cancel()
	out := []Device{}
	rows, err := p.pool.Query(ctx, `SELECT id, label, public_key FROM devices WHERE tenant_id=$1 ORDER BY id`, tenant)
	if err != nil {
		return out
	}
	defer rows.Close()
	for rows.Next() {
		var d Device
		if rows.Scan(&d.ID, &d.Label, &d.PublicKey) == nil {
			out = append(out, d)
		}
	}
	return out
}

func (p *PG) RemoveDevice(tenant, deviceID string) error {
	return p.inTx(func(ctx context.Context, tx pgx.Tx) error {
		tag, err := tx.Exec(ctx, `DELETE FROM devices WHERE tenant_id=$1 AND id=$2`, tenant, deviceID)
		if err != nil {
			return err
		}
		if tag.RowsAffected() == 0 {
			return ErrNotFound
		}
		_, err = tx.Exec(ctx, `DELETE FROM tokens WHERE tenant_id=$1 AND kind=$2 AND subject_id=$3`, tenant, KindDevice, deviceID)
		return err
	})
}

func (p *PG) CreatePairing(pr Pairing) error {
	if pr.State == "" {
		pr.State = "PENDING"
	}
	tag, err := p.exec(`INSERT INTO pairing_sessions(code, tenant_id, host_id, payload, state, expires_at)
		VALUES ($1,$2,$3,$4,$5,$6)
		ON CONFLICT (code) DO UPDATE SET tenant_id=EXCLUDED.tenant_id, host_id=EXCLUDED.host_id,
			payload=EXCLUDED.payload, state=EXCLUDED.state, expires_at=EXCLUDED.expires_at,
			claimed_by='', device_label='', device_public_key='', token_hash='', created_at=now()
		WHERE pairing_sessions.state='EXPIRED'`,
		pr.Code, pr.TenantID, pr.HostID, pr.Payload, pr.State, pr.ExpiresAt)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrConflict
	}
	return nil
}

const pairingCols = `code, tenant_id, host_id, payload, state, claimed_by, device_label, device_public_key, token_hash, expires_at`

func scanPairing(row pgx.Row) (Pairing, error) {
	var pr Pairing
	err := row.Scan(&pr.Code, &pr.TenantID, &pr.HostID, &pr.Payload, &pr.State, &pr.ClaimedBy, &pr.DeviceLabel, &pr.DevicePublicKey, &pr.TokenHash, &pr.ExpiresAt)
	return pr, err
}

func (p *PG) GetPairing(code string) (Pairing, bool) {
	ctx, cancel := p.ctx()
	defer cancel()
	pr, err := scanPairing(p.pool.QueryRow(ctx, `SELECT `+pairingCols+` FROM pairing_sessions WHERE code=$1`, code))
	return pr, err == nil
}

func (p *PG) ClaimPairing(code string, c Claim, now time.Time) (Pairing, error) {
	var out Pairing
	err := p.inTx(func(ctx context.Context, tx pgx.Tx) error {
		pr, err := scanPairing(tx.QueryRow(ctx, `SELECT `+pairingCols+` FROM pairing_sessions WHERE code=$1 FOR UPDATE`, code))
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if err := claimable(pr, c, now); err != nil {
			return err
		}
		pr.State, pr.ClaimedBy, pr.DeviceLabel, pr.DevicePublicKey, pr.TokenHash = "CLAIMED", c.DeviceID, c.Label, c.PublicKey, c.TokenHash
		if !c.ApproveBy.IsZero() {
			pr.ExpiresAt = c.ApproveBy
		}
		if _, err := tx.Exec(ctx, `UPDATE pairing_sessions SET state='CLAIMED', claimed_by=$2, device_label=$3, device_public_key=$4, token_hash=$5, expires_at=$6 WHERE code=$1`,
			code, c.DeviceID, c.Label, c.PublicKey, c.TokenHash, pr.ExpiresAt); err != nil {
			return err
		}
		out = pr
		return nil
	})
	return out, err
}

func (p *PG) FinishPairing(tenant, hostID, code string, approved bool, now time.Time) error {
	return p.inTx(func(ctx context.Context, tx pgx.Tx) error {
		pr, err := scanPairing(tx.QueryRow(ctx, `SELECT `+pairingCols+` FROM pairing_sessions WHERE code=$1 AND tenant_id=$2 AND host_id=$3 FOR UPDATE`, code, tenant, hostID))
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		if pr.State != "CLAIMED" {
			return ErrConflict
		}
		if now.After(pr.ExpiresAt) {
			return ErrExpired
		}
		state := "REJECTED"
		if approved {
			state = "CONSUMED"
			if _, err := tx.Exec(ctx, `INSERT INTO devices(tenant_id, id, label, public_key) VALUES ($1,$2,$3,$4)
				ON CONFLICT (tenant_id, id) DO UPDATE SET label=EXCLUDED.label, public_key=EXCLUDED.public_key`,
				tenant, pr.ClaimedBy, pr.DeviceLabel, pr.DevicePublicKey); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `DELETE FROM tokens WHERE tenant_id=$1 AND kind=$2 AND subject_id=$3`, tenant, KindDevice, pr.ClaimedBy); err != nil {
				return err
			}
			if _, err := tx.Exec(ctx, `INSERT INTO tokens(hash, tenant_id, kind, subject_id) VALUES ($1,$2,$3,$4)`, pr.TokenHash, tenant, KindDevice, pr.ClaimedBy); err != nil {
				return err
			}
		}
		_, err = tx.Exec(ctx, `UPDATE pairing_sessions SET state=$2, payload='' WHERE code=$1`, code, state)
		return err
	})
}

func (p *PG) PutEvent(callerTenant string, e Event) error {
	if err := auth.GuardTenant(e.TenantID, callerTenant); err != nil {
		return err
	}
	e.ExpiresAt = capTTL(e.CreatedAt, e.ExpiresAt)
	// Re-sending the same event keeps its seq (idempotent cursor order).
	_, err := p.exec(`INSERT INTO agent_events(tenant_id, event_id, opaque_host_id, opaque_session_id, source, category, message, request_digest, revision, created_at, expires_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
		ON CONFLICT (tenant_id, event_id) DO UPDATE SET opaque_host_id=EXCLUDED.opaque_host_id, opaque_session_id=EXCLUDED.opaque_session_id,
			source=EXCLUDED.source, category=EXCLUDED.category, message=EXCLUDED.message, request_digest=EXCLUDED.request_digest,
			revision=EXCLUDED.revision, created_at=EXCLUDED.created_at, expires_at=EXCLUDED.expires_at`,
		e.TenantID, e.EventID, e.OpaqueHost, e.OpaqueSess, e.Source, e.Category, e.Message, e.Digest, e.Revision, e.CreatedAt, e.ExpiresAt)
	return err
}

func (p *PG) GetEvents(callerTenant, after string) []Event {
	ctx, cancel := p.ctx()
	defer cancel()
	out := []Event{}
	rows, err := p.pool.Query(ctx, `SELECT tenant_id, event_id, seq, opaque_host_id, opaque_session_id, source, category, message, request_digest, revision, created_at, expires_at
		FROM agent_events WHERE tenant_id=$1
		AND seq > COALESCE((SELECT seq FROM agent_events WHERE tenant_id=$1 AND event_id=$2 AND $2 <> ''), 0)
		ORDER BY seq`, callerTenant, after)
	if err != nil {
		return out
	}
	defer rows.Close()
	for rows.Next() {
		var e Event
		if rows.Scan(&e.TenantID, &e.EventID, &e.Seq, &e.OpaqueHost, &e.OpaqueSess, &e.Source, &e.Category, &e.Message, &e.Digest, &e.Revision, &e.CreatedAt, &e.ExpiresAt) == nil {
			e.CreatedAt, e.ExpiresAt = e.CreatedAt.UTC(), e.ExpiresAt.UTC()
			out = append(out, e)
		}
	}
	return out
}

func (p *PG) Decide(tenant, eventID, digest, revision, device string) error {
	return p.inTx(func(ctx context.Context, tx pgx.Tx) error {
		var d, r string
		err := tx.QueryRow(ctx, `SELECT request_digest, revision FROM agent_events WHERE tenant_id=$1 AND event_id=$2 FOR UPDATE`, tenant, eventID).Scan(&d, &r)
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		if err != nil {
			return err
		}
		var wd, wr, winner string
		err = tx.QueryRow(ctx, `SELECT request_digest, revision, winner FROM approval_decisions WHERE tenant_id=$1 AND event_id=$2`, tenant, eventID).Scan(&wd, &wr, &winner)
		if err == nil {
			if winner == device && wd == digest && wr == revision {
				return nil
			}
			return ErrConflict
		}
		if !errors.Is(err, pgx.ErrNoRows) {
			return err
		}
		if d != digest || r != revision {
			return ErrMismatch
		}
		_, err = tx.Exec(ctx, `INSERT INTO approval_decisions(tenant_id, event_id, request_digest, revision, state, winner) VALUES ($1,$2,$3,$4,'RESOLVED',$5)`,
			tenant, eventID, digest, revision, device)
		return err
	})
}

func (p *PG) PutUpload(tenant string, u Upload) error {
	if u.TenantID != tenant {
		return ErrConflict
	}
	if u.Size > 10<<20 {
		return ErrTooLarge
	}
	u.ExpiresAt = capTTL(u.CreatedAt, u.ExpiresAt)
	_, err := p.exec(`INSERT INTO uploads(tenant_id, id, short_code, size_bytes, created_at, expires_at) VALUES ($1,$2,$3,$4,$5,$6)
		ON CONFLICT (tenant_id, id) DO UPDATE SET short_code=EXCLUDED.short_code, size_bytes=EXCLUDED.size_bytes, expires_at=EXCLUDED.expires_at`,
		tenant, u.ID, u.ShortCode, u.Size, u.CreatedAt, u.ExpiresAt)
	var pe *pgconn.PgError
	if errors.As(err, &pe) && pe.Code == "23505" {
		return ErrConflict
	}
	return err
}

func (p *PG) GetUploadByShort(code string, now time.Time) (Upload, bool) {
	ctx, cancel := p.ctx()
	defer cancel()
	var u Upload
	err := p.pool.QueryRow(ctx, `SELECT tenant_id, id, short_code, size_bytes, created_at, expires_at FROM uploads WHERE short_code=$1`, code).
		Scan(&u.TenantID, &u.ID, &u.ShortCode, &u.Size, &u.CreatedAt, &u.ExpiresAt)
	if err != nil {
		return Upload{}, false
	}
	return u, now.Before(u.ExpiresAt)
}

func (p *PG) DeleteUpload(tenant, id string) error {
	tag, err := p.exec(`DELETE FROM uploads WHERE tenant_id=$1 AND id=$2`, tenant, id)
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return ErrNotFound
	}
	return nil
}

func (p *PG) SweepTTL(now time.Time) (events, pairings int) {
	if tag, err := p.exec(`DELETE FROM agent_events WHERE expires_at <= $1`, now); err == nil {
		events = int(tag.RowsAffected())
	}
	if tag, err := p.exec(`UPDATE pairing_sessions SET state='EXPIRED', payload='' WHERE state IN ('PENDING','CLAIMED') AND expires_at <= $1`, now); err == nil {
		pairings = int(tag.RowsAffected())
	}
	// Finished/expired pairings are kept a day for audit, then dropped.
	_, _ = p.exec(`DELETE FROM pairing_sessions WHERE state <> 'PENDING' AND state <> 'CLAIMED' AND expires_at <= $1`, now.Add(-24*time.Hour))
	_, _ = p.exec(`DELETE FROM uploads WHERE expires_at <= $1`, now)
	return
}
