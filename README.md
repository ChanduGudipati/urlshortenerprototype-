# URL Shortener — AI-Assisted Engineering Prototype

A production-style URL shortener built with Spring Boot, demonstrating **engineer-led, AI-assisted execution**: requirement normalization, task decomposition, traceable AI usage, quality gates, and explicit risk control.

**Features:** short link creation (random Base62 or custom alias), 302 redirects, optional expiry (410 Gone), asynchronous click analytics, redirect caching, URL safety validation, per-client rate limiting, RFC 7807 error responses, health/metrics, OpenAPI docs.

## Quick start

**Option A — zero setup (in-memory H2), requires JDK 21 + Maven**
```bash
mvn spring-boot:run
```

**Option B — full stack with PostgreSQL, requires only Docker**
```bash
docker compose up --build
```

The service runs on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

**Run tests and quality gates**
```bash
mvn verify          # unit + integration tests + JaCoCo coverage gate (>= 70% lines)
# coverage report: target/site/jacoco/index.html
```

## API

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/urls` | Create short link | 201 + `Location` | 400 invalid, 409 alias taken, 429 rate limited, 503 code exhaustion |
| GET | `/{code}` | Redirect | 302 | 404 unknown, 410 expired |
| GET | `/api/v1/urls/{code}/stats` | Click analytics | 200 | 404 |
| GET | `/actuator/health` | Health check | 200 | |

```bash
# Create
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.schwab.com","customAlias":"schwab","expiresInDays":30}'

# Redirect (shows the 302 and Location header)
curl -i http://localhost:8080/schwab

# Stats
curl -s http://localhost:8080/api/v1/urls/schwab/stats

# Rejected: unsafe scheme
curl -s -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" -d '{"url":"javascript:alert(1)"}'
```

Request body: `url` (required, http/https, max 2048), `customAlias` (optional, 4–32 chars `[A-Za-z0-9_-]`), `expiresInDays` (optional, 1–3650).

## Configuration

| Property | Env var | Default |
|---|---|---|
| `spring.datasource.url` | `DB_URL` | H2 in-memory |
| `app.base-url` | `APP_BASE_URL` | `http://localhost:8080` |
| `app.rate-limit-per-minute` | `RATE_LIMIT_PER_MINUTE` | 30 (0 disables) |
| `app.code-length` | | 7 |
| `app.blocked-domains` | | sample list |

## Project structure

```
src/main/java/com/example/urlshortener
├── config/        AppProperties, Clock
├── domain/        ShortUrl, ClickEvent, RedirectTarget
├── repository/    Spring Data JPA repositories
├── service/       UrlShortenerService, AnalyticsService, CodeGenerator,
│                  UrlValidator, FixedWindowRateLimiter
├── web/           Controllers, RateLimitFilter, GlobalExceptionHandler, DTOs
└── exception/     Domain exceptions
src/main/resources/db/migration   Flyway V1 (greenfield), V2 (brownfield analytics)
```

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Components, control flow, data model, key decisions, execution approach |
| [docs/SCENARIOS.md](docs/SCENARIOS.md) | Greenfield, brownfield and ambiguous scenarios: decomposition, execution, validation |
| [docs/AI_LOG.md](docs/AI_LOG.md) | AI traceability: generated / edited / rejected, with rationale |
| [docs/ENGINEERING_SUMMARY.md](docs/ENGINEERING_SUMMARY.md) | Plan, risks, trade-offs, testing approach, assumptions, limitations |
