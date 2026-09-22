-- V1: core short_urls table.
-- Codes are generated from the BIGSERIAL id (Base62), or supplied as a custom alias.
-- The UNIQUE constraint on code permits NULLs (Postgres), which we use for the
-- brief insert-then-update window when generating a code from the id.

CREATE TABLE IF NOT EXISTS short_urls (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(32) UNIQUE,
    original_url    TEXT NOT NULL,
    clicks          BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_clicked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_short_urls_code ON short_urls (code);
CREATE INDEX IF NOT EXISTS idx_short_urls_created_at ON short_urls (created_at DESC);
