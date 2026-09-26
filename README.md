# URL Shortener

A production-style URL shortening service built with **Java 17, Spring Boot 3, PostgreSQL, Redis, and Kafka** — the kind of backend system that maps directly onto SDE2 system-design interviews (caching, async event pipelines, rate limiting, idempotent writes).

**What it does:** `POST /api/v1/urls` turns a long URL into a short code (e.g. `http://localhost:8080/0003d7`). Visiting the short link 302-redirects to the original URL, fires a click event to Kafka, and a consumer aggregates click counts back into Postgres. Redis sits in front of Postgres on the hot redirect path.

**Web UI:** the root path (`/`) now serves **Snip**, a full single-page app — shorten links, pick custom aliases, copy to clipboard, view live click stats, and manage recent links, all from the browser. Interactive API docs remain at `/swagger-ui/index.html`.

## Architecture

```mermaid
flowchart LR
    Client -->|POST /api/v1/urls| App[Spring Boot App]
    Client -->|GET /{code}| App

    subgraph Write path
        App -->|insert + Base62 id| PG[(PostgreSQL)]
        App -->|cache mapping| Redis[(Redis)]
    end

    subgraph Redirect path
        App -->|1. cache-aside lookup| Redis
        Redis -. miss .-> App
        App -->|2. fallback| PG
        App -->|3. 302 redirect| Client
        App -->|4. fire-and-forget| K[Topic: url-clicks]
    end

    subgraph Click pipeline
        K -->|batch poll| Consumer[click-aggregator group]
        Consumer -->|UPDATE clicks = clicks + n| PG
    end

    Client -->|GET /api/v1/urls/{code}/stats| App
    App -->|read-through| PG
```

### Key design decisions

| Decision | Rationale |
|---|---|
| **Base62-encoded DB id as the code** | Monotonic ids → unique codes *by construction*, no retry loop on collision; codes stay short and are roughly time-ordered. Custom aliases are validated (`^[A-Za-z0-9_-]{3,32}$`) and uniqueness-checked. |
| **Redis cache-aside on redirects** | Redirects are read-heavy; Redis absorbs the hot path, Postgres is the source of truth on miss. Entries get a configurable TTL and are evicted on delete. |
| **Async click tracking via Kafka** | The redirect never waits for analytics. The producer sends with the code as the key (same-partition ordering) and only logs failures. |
| **Batch consumer with atomic increments** | The `click-aggregator` group polls up to 500 events, groups by code in memory, and issues one `UPDATE … SET clicks = clicks + n` per code — no read-modify-write, so concurrent batches never lose increments. |
| **Bucket4j token bucket per client IP** | 20 URL creations/minute per IP (configurable), enforced in a servlet filter with a JSON `429` + `Retry-After` header. |
| **Flyway migrations** | Schema is versioned SQL; Hibernate runs in `validate` mode so the mapping can never silently drift from the schema. |

## Quick start (Docker Compose)

Prerequisites: Docker + Docker Compose.

```bash
docker compose up --build
```

This starts Postgres 16, Redis 7, Kafka (single-broker KRaft, no ZooKeeper), and the app on port 8080. Flyway runs the migration automatically; the `url-clicks` topic is created by the app on startup.

Health check: `curl http://localhost:8080/actuator/health`

## API examples

**Create a short URL**
```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://spring.io/projects/spring-boot"}'
# → 201 {"code":"0003d7","shortUrl":"http://localhost:8080/0003d7","originalUrl":"https://spring.io/projects/spring-boot"}
```

**Create with a custom alias**
```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com", "customAlias": "my-link"}'
```

**Redirect** (302; click is counted asynchronously)
```bash
curl -i http://localhost:8080/0003d7
# → HTTP/1.1 302 ... Location: https://spring.io/projects/spring-boot
```

**Stats**
```bash
curl http://localhost:8080/api/v1/urls/0003d7/stats
# → {"code":"0003d7","shortUrl":"...","originalUrl":"...","clicks":3,"createdAt":"...","lastClickedAt":"..."}
```

**List (paginated)**
```bash
curl 'http://localhost:8080/api/v1/urls?page=0&size=20'
```

**Delete**
```bash
curl -X DELETE http://localhost:8080/api/v1/urls/0003d7
# → 204 (row deleted, Redis entry evicted)
```

**Error shape** (consistent JSON for 400/404/409/429/500):
```json
{"timestamp":"2026-09-23T00:00:00Z","status":404,"error":"Not Found","message":"No URL found for code 'nope'","path":"/nope"}
```

## Configuration

Everything is driven by environment variables (see `src/main/resources/application.yml` for defaults):

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/urlshortener` | Full JDBC URL (or set `SPRING_DATASOURCE_URL` for Heroku-style `postgres://` URLs) |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `REDIS_URL` | *(unset)* | Full Redis URL, e.g. `redis://:pass@host:6379/0` — preferred for managed Redis |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | `localhost` / `6379` / *(empty)* | Used when `REDIS_URL` is unset |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka brokers |
| `KAFKA_SASL_JAAS_CONFIG` | *(unset)* | Set for SASL/PLAIN cloud Kafka (e.g. Redpanda Cloud) — enables `SASL_SSL` automatically |
| `KAFKA_CLICK_TOPIC` | `url-clicks` | Click-event topic name |
| `KAFKA_ENABLED` | `true` | Set to `false` to run with **no Kafka broker** — clicks are counted with a direct atomic DB update instead of the event pipeline. Also set `MANAGEMENT_HEALTH_KAFKA_ENABLED=false` in that mode |
| `BASE_URL` | `http://localhost:8080` | Public base used to build short URLs |
| `RATE_LIMIT_PER_MINUTE` | `20` | URL creations per minute per client IP |
| `CACHE_TTL_HOURS` | `24` | Redis TTL for code→URL mappings |
| `CODE_MIN_LENGTH` | `6` | Zero-padding width for generated codes |
| `PORT` | `8080` | HTTP port |

## How the Kafka click pipeline works

1. `GET /{code}` resolves the URL (Redis → Postgres) and returns the 302 **immediately**.
2. In parallel, `ClickEventProducer` sends `{code, clickedAt}` to `url-clicks`, keyed by code. `send()` returns a future that is never blocked on — a send failure is logged but never fails the redirect.
3. `ClickEventConsumer` (group `click-aggregator`) batch-polls the topic, counts events per code in memory, and runs a single atomic `UPDATE short_urls SET clicks = clicks + :n, last_clicked_at = :now WHERE code = :code` per code inside one transaction, then commits offsets (batch ack mode).
4. Because the counter is incremented in the database rather than read-modify-written in Java, concurrent consumer batches can't lose clicks. Delivery is at-least-once, so a redelivered batch could double-count within its small window — acceptable for analytics counters, and called out explicitly rather than hidden.

### Running without Kafka

There is no perpetual free managed-Kafka tier, so the app supports a no-broker mode for free-tier deploys: set `KAFKA_ENABLED=false` (and `MANAGEMENT_HEALTH_KAFKA_ENABLED=false` so the health check doesn't probe Kafka). `KafkaConfig` and `ClickEventConsumer` are skipped entirely via `@ConditionalOnProperty`, and `ClickEventProducer` falls back to the same atomic `incrementClicks` update the consumer uses — stats stay accurate, only the async aggregation step is skipped. Flip the flag back to `true` and point `KAFKA_BOOTSTRAP_SERVERS` at a real broker (or a 30-day Confluent Cloud trial, promo code `CONFLUENTDEV1` delays the credit-card requirement) to restore the full pipeline with zero code changes.

## Deploying free (no credit card)

A low-cost stack for this app (free tiers researched Sep 2026 — confirm current terms at signup, they change often):

| Layer | Provider | Free tier | Notes |
|---|---|---|---|
| App (Docker) | [Render](https://render.com) | Free web service | Sleeps after inactivity; use the provided `Dockerfile`. Set `PORT`, `BASE_URL`, `KAFKA_ENABLED=false`, `MANAGEMENT_HEALTH_KAFKA_ENABLED=false` |
| Postgres | [Neon](https://neon.tech) | Permanent free tier, no card: 0.5 GB, 100 CU-hours/mo, scale-to-zero | Set `DATABASE_URL` to the Neon JDBC URL; Flyway migrates on boot |
| Redis | [Upstash](https://upstash.com) | Free tier, standard Redis protocol | Set `REDIS_URL` to the Upstash URL |
| Kafka | — | *none perpetual* | Run with `KAFKA_ENABLED=false`, or trial Confluent Cloud ($400 credits / 30 days, code `CONFLUENTDEV1`) |

Notes:
- **Railway is not free for always-on apps**: the $0 plan includes only ~$1/mo of usage credit (a minimal service burns ~$25/mo), and the $5 trial is one-time. Don't use it for this.
- Everything also runs locally with `docker-compose up` (Postgres 16, Redis 7, Kafka 3.8 KRaft) for the full pipeline demo.

## Running locally without Docker

You need Java 17, Maven, and running Postgres/Redis/Kafka instances:

```bash
# point the app at your infra via env vars, then:
mvn spring-boot:run
```

Run the unit tests (no infra needed — pure unit + Mockito):

```bash
mvn test
```

## Project layout

```
src/main/java/com/iamamansid/urlshortener/
├── controller/   # UrlController (CRUD API), RedirectController (302s)
├── service/      # UrlService (core logic), RateLimiterService (Bucket4j)
├── repository/   # ShortUrlRepository (incl. atomic incrementClicks)
├── entity/       # ShortUrl JPA entity
├── kafka/        # ClickEventProducer, ClickEventConsumer
├── config/       # KafkaConfig, RedisConfig, RateLimitFilter, RequestLoggingFilter, AppProperties
├── dto/          # Request/response records, ClickEvent, ErrorResponse
├── exception/    # Domain exceptions + GlobalExceptionHandler
└── util/         # Base62 codec
src/main/resources/db/migration/V1__create_short_urls.sql
```

## Future improvements

- **Redis-backed rate limiting** (Bucket4j `bucket4j-redis`) so limits hold across multiple app instances; the current in-memory bucket map is per-instance.
- **Collision-safe custom aliases vs generated codes** is handled with a defensive loop today; a reserved-code check at alias creation would be cleaner.
- **Click deduplication**: store per-click rows (or a HyperLogLog in Redis) for unique-visitor counts and time-series analytics instead of a single counter.
- **Link expiry**: `expires_at` column + a scheduled sweeper, and TTL-aligned Redis entries.
- **Auth**: API keys/OAuth2 on the management endpoints; per-user namespaces and quotas.
- **Load testing**: a k6/Gatling script proving p99 redirect latency under cache-hit vs miss.
