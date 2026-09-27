-- V4: one row per click, for per-day click analytics in the admin dashboard.
-- The lifetime counter on short_urls remains the fast path for per-link stats.

CREATE TABLE IF NOT EXISTS link_clicks (
    id              BIGSERIAL PRIMARY KEY,
    short_url_id    BIGINT NOT NULL REFERENCES short_urls(id) ON DELETE CASCADE,
    clicked_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_link_clicks_clicked_at ON link_clicks (clicked_at DESC);
CREATE INDEX IF NOT EXISTS idx_link_clicks_short_url_id ON link_clicks (short_url_id);
