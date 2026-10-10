# Final Engineering Summary

## Plan and rationale
Build the smallest system that is genuinely production-shaped: a layered Spring Boot service with a versioned schema, explicit error contract, security validation on the only user-controlled input that matters (the destination URL), and tests at unit and integration level. Complexity (Redis, message queues, auth) was deliberately deferred and documented rather than added speculatively. Work was sequenced greenfield → brownfield → ambiguous, so each scenario builds on real code.

## Artifacts

| Artifact | Location |
|---|---|
| Runnable service | `src/main`, `mvn spring-boot:run` or `docker compose up --build` |
| API definition | OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`, table in README |
| Schema | `db/migration/V1__create_short_url.sql`, `V2__add_click_analytics.sql` |
| Tests | 6 test classes: unit (service, validator, generator, limiter, analytics) + integration (HTTP → DB) |
| Quality gates | `mvn verify` (tests + JaCoCo ≥ 70%), GitHub Actions CI, Docker build |
| Docs | README, ARCHITECTURE, SCENARIOS, AI_LOG, this summary |

## Testing approach

| Level | What it proves | Classes |
|---|---|---|
| Unit | Business rules in isolation with a fixed `Clock` and mocked repositories: collision retry, alias rules, expiry, validation rules, rate windows | `*ServiceTest`, `UrlValidatorTest`, `CodeGeneratorTest`, `FixedWindowRateLimiterTest` |
| Integration | Real HTTP stack, Bean Validation, exception mapping, Flyway schema, JPA, cache and async analytics together | `UrlShortenerIntegrationTest`, `RateLimitIntegrationTest` |
| Negative/security | Unsafe schemes, loops, blocked domains, malformed JSON, oversized input, 429 | Across both levels |

Async behaviour is verified with Awaitility, not sleeps. Time-dependent logic uses an injected `Clock`.

## Risks, trade-offs and failure scenarios

| Scenario | Impact | Mitigation in place | Residual risk |
|---|---|---|---|
| Code collision | Create fails | Unique constraint + up to 5 retries, 503 if exhausted | Negligible at 62^7 |
| Concurrent same alias | Duplicate alias | DB constraint decides; loser gets 409 | None |
| Process crash with clicks queued | Some clicks lost | Analytics treated as best effort | Accepted; outbox/queue for exact counts |
| Analytics queue full | Clicks dropped | Rejection caught; redirect still succeeds | Accepted, logged |
| Database down | Creates fail; uncached redirects fail | Cached redirects still served for up to 10 min; health endpoint reports DOWN | Needs HA database in production |
| Malicious destination | Phishing via our domain | Scheme allowlist, blocklist, no self-links | Static list only; reputation API needed |
| Creation flooding | Storage abuse | Per-IP rate limit | Per instance only; IP rotation bypasses |
| Hot link traffic | DB load | Caffeine cache | Per-instance cache; Redis for multi-instance |
| Stale cache after future delete feature | Deleted link still redirects | Not applicable today (links immutable) | Delete must add `@CacheEvict` |

**Main trade-offs:** 302 over 301 (analytics/expiry over fewer requests); random over sequential codes (security over compactness); async analytics (latency over exactness); in-memory cache and limiter (simplicity over multi-instance consistency); H2 for dev/tests with PostgreSQL mode (zero setup over full production parity).

## Validation and safety guardrails
- Input: Bean Validation on DTOs plus `UrlValidator` security rules.
- Data: unique constraints are the source of truth; atomic counter updates; additive migrations.
- Errors: uniform ProblemDetail, generic 500s, no stack traces to clients.
- Runtime: rate limiting, bounded queue, bounded limiter memory, header truncation, non-root container.
- Process: CI gate on every push, coverage threshold, human sign-off for high-impact changes (AI_LOG).

## Assumptions
Anonymous public API; single instance for the prototype; no trusted proxy (client IP from the socket); links are immutable once created; analytics may be approximate.

## Limitations and next steps
1. No authentication, link ownership or delete/update API (would need `@CacheEvict`).
2. Rate limiting and cache are per instance → move to Redis for horizontal scaling.
3. Integration tests use H2 → add Testcontainers PostgreSQL for full parity.
4. Blocklist is static → integrate a URL reputation service.
5. No dependency vulnerability scan in CI yet → add OWASP Dependency-Check or Dependabot.
6. Analytics is not exactly-once → transactional outbox or Kafka for billing-grade counts.
7. URLs with unencoded spaces are rejected rather than normalized.
