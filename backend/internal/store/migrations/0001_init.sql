-- SPDX-License-Identifier: GPL-3.0-or-later
-- Initial schema: every row carries tenant_id; tokens are stored hash-only.
CREATE TABLE IF NOT EXISTS tenants (
  id TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS tokens (
  hash TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  subject_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tokens_subject ON tokens(tenant_id, kind, subject_id);
CREATE TABLE IF NOT EXISTS hosts (
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  label TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS devices (
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  label TEXT NOT NULL DEFAULT '',
  public_key TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, id)
);
CREATE TABLE IF NOT EXISTS pairing_sessions (
  code TEXT PRIMARY KEY,
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  host_id TEXT NOT NULL,
  payload TEXT NOT NULL DEFAULT '',
  state TEXT NOT NULL DEFAULT 'PENDING',
  claimed_by TEXT NOT NULL DEFAULT '',
  device_label TEXT NOT NULL DEFAULT '',
  device_public_key TEXT NOT NULL DEFAULT '',
  token_hash TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS agent_events (
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  event_id TEXT NOT NULL,
  seq BIGSERIAL,
  opaque_host_id TEXT NOT NULL DEFAULT '',
  opaque_session_id TEXT NOT NULL DEFAULT '',
  source TEXT NOT NULL DEFAULT '',
  category TEXT NOT NULL DEFAULT '',
  message TEXT NOT NULL DEFAULT '',
  request_digest TEXT NOT NULL DEFAULT '',
  revision TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, event_id)
);
CREATE INDEX IF NOT EXISTS idx_events_seq ON agent_events(tenant_id, seq);
CREATE INDEX IF NOT EXISTS idx_events_expiry ON agent_events(expires_at);
CREATE TABLE IF NOT EXISTS approval_decisions (
  tenant_id TEXT NOT NULL,
  event_id TEXT NOT NULL,
  request_digest TEXT NOT NULL,
  revision TEXT NOT NULL,
  state TEXT NOT NULL,
  winner TEXT NOT NULL,
  decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, event_id),
  FOREIGN KEY (tenant_id, event_id) REFERENCES agent_events(tenant_id, event_id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS uploads (
  tenant_id TEXT NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  short_code TEXT UNIQUE NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
