-- V2: application users for login (ID/password + Google SSO) and link ownership.
-- provider: LOCAL (email+password) or GOOGLE (OAuth2). password_hash is NULL for SSO users.
-- role: USER or ADMIN. Aman's Gmail is granted ADMIN on first login (see auth.admin-emails).

CREATE TABLE IF NOT EXISTS app_users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    display_name    VARCHAR(120),
    password_hash   VARCHAR(255),
    provider        VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    role            VARCHAR(16) NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_users_role ON app_users (role);
CREATE INDEX IF NOT EXISTS idx_app_users_created_at ON app_users (created_at DESC);
