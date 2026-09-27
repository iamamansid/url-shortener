-- V3: link ownership. Links created while signed in belong to that user;
-- anonymous links keep owner_id NULL and stay visible to admins only.

ALTER TABLE short_urls
    ADD COLUMN IF NOT EXISTS owner_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_short_urls_owner_id ON short_urls (owner_id);
