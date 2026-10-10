# Scenarios

Each scenario follows the same loop: **understand → decompose → execute (AI-assisted) → validate**.

---

## Scenario 1 — Greenfield: core shorten and redirect

**Requirement as given:** "Users should be able to shorten a long URL and be redirected when they open the short link."

### Understanding and normalization

| Ambiguity | Decision |
|---|---|
| Code format and length? | 7-char Base62, random (non-enumerable) |
| Custom aliases? | Optional, 4–32 chars `[A-Za-z0-9_-]`, reserved words blocked |
| Same URL submitted twice? | New code each time (no dedup): simpler, avoids leaking that a URL was already shortened |
| Expiry? | Optional `expiresInDays`; expired links return 410 Gone |
| Redirect status? | 302 (see Scenario 2 for why) |
| Error format? | RFC 7807 ProblemDetail |

**Normalized problem:** Provide a REST API that persists a mapping from a unique, non-guessable code to a validated http/https URL, and a redirect endpoint that resolves the code with low latency and well-defined errors.

### Decomposition

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| G1 | Project skeleton, config, Flyway V1 | — | App starts on H2 and PostgreSQL |
| G2 | `ShortUrl` entity + repository | G1 | Unique constraint on `code` |
| G3 | `CodeGenerator` | G1 | Correct length/alphabet; no duplicates in 10k |
| G4 | `UrlValidator` | G1 | Rejects blank, non-http(s), hostless, overlong |
| G5 | `UrlShortenerService.create` with collision retry and alias | G2–G4 | Retries on collision; 409 on taken alias |
| G6 | Controllers + DTOs + Bean Validation | G5 | 201 + Location; 302 redirect; field-level 400s |
| G7 | `GlobalExceptionHandler` | G6 | Every error is a ProblemDetail; no stack traces |
| G8 | Redirect cache | G6 | Cache hit avoids DB; expiry still enforced |
| G9 | Unit + integration tests, Docker, README | G1–G8 | `mvn verify` green, coverage ≥ 70% |

### Execution highlights
AI drafted the entity, DTOs, controllers and test scaffolding from task specs. Engineer rejected sequential-ID codes and an exists-then-insert uniqueness check, and removed `@Transactional` from `create` so retry works on PostgreSQL (see AI_LOG entries 2–4).

### Validation
`UrlShortenerServiceTest` (collision retry, retry exhaustion, alias rules, expiry calculation), `CodeGeneratorTest`, `UrlValidatorTest`, and `UrlShortenerIntegrationTest` (create → redirect → 404/410/400 paths through the real HTTP stack and Flyway schema).

---

## Scenario 2 — Brownfield: add click analytics to the existing service

**Requirement as given:** "Product wants to know how many times each short link is clicked and when it was last used."

### Codebase reasoning — impact analysis

| Area | Impact | Change |
|---|---|---|
| Schema | Changed | Flyway **V2**: `click_count` column (default 0) + `click_event` table + index. Additive only, so existing rows and old code keep working |
| `ShortUrl` entity | Changed | Map `click_count` |
| `RedirectController` (hot path) | Changed, **high risk** | Trigger analytics without adding latency or new failure modes |
| New `AnalyticsService`, `ClickEvent`, `ClickEventRepository` | New | Asynchronous recording |
| `UrlController` / `UrlShortenerService` | Changed | New `GET /api/v1/urls/{code}/stats` |
| Redirect cache | Constraint | Cached value must carry the `id` for analytics → introduced `RedirectTarget` record |
| Redirect status | Revisited | Confirmed 302 + `Cache-Control: no-store`; a 301 would be cached by browsers and clicks would never reach the service |
| Existing API contract | Unchanged | Backward compatible |

### Decomposition

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| B1 | Flyway V2 migration | — | Applies cleanly on top of V1 data |
| B2 | `ClickEvent` + repository | B1 | Last-click query works |
| B3 | Atomic counter `UPDATE ... + 1` | B1 | No lost updates under concurrency |
| B4 | `AnalyticsService` with `@Async` | B2, B3 | Redirect does not wait on DB write |
| B5 | Wire into redirect, tolerate queue saturation | B4 | `TaskRejectedException` is logged, never fails a redirect |
| B6 | Stats endpoint | B2 | Returns total, last click, expired flag |
| B7 | Tests | B1–B6 | Integration test proves click counted end to end |

### Bug fixes found during this change
1. **Lost-update risk:** AI's first draft incremented `clickCount` in the entity and saved it (read-modify-write). Under concurrent clicks this loses counts. Fixed with an atomic JPQL update.
2. **Error mapping regression:** a catch-all `@ExceptionHandler(Exception.class)` turned framework errors (404 for unknown paths, 405) into 500. Fixed by passing Spring `ErrorResponse` exceptions through with their own status.

### Validation
Integration test performs a redirect and then uses Awaitility to assert `totalClicks == 1` (no `Thread.sleep`). `AnalyticsServiceTest` covers header truncation. Migration validated by running the full integration suite on the V1→V2 schema.

---

## Scenario 3 — Ambiguous: "Make it reliable and stop people abusing it"

### Clarifying questions (and the assumption taken where no answer was available)

| Question | Assumption for the prototype |
|---|---|
| What counts as abuse: spam volume, malicious destinations, or both? | Both |
| Is there authentication / are there API keys? | No; anonymous API, so limit per client IP |
| What availability target? | Single instance; redirects must keep working if analytics degrades |
| Can we call external reputation services (e.g. Google Safe Browsing)? | Not in the prototype; static blocklist with a clear extension point |
| Is a deployment behind a proxy/load balancer? | Not assumed; `X-Forwarded-For` is not trusted |

### Normalized into concrete, testable requirements

| ID | Requirement | Implementation | Test |
|---|---|---|---|
| A1 | Only safe schemes | Allowlist http/https (blocks `javascript:`, `data:`, `file:`) | `UrlValidatorTest` |
| A2 | No redirect loops | Reject destinations on the service's own host | `UrlValidatorTest` |
| A3 | Block known bad domains incl. subdomains, without false positives on lookalikes | Configurable blocklist with suffix match on `.` boundary | `UrlValidatorTest` |
| A4 | Throttle bulk creation | Per-IP fixed window, 429 + `Retry-After` | `FixedWindowRateLimiterTest`, `RateLimitIntegrationTest` |
| A5 | Bounded memory for limiter | Scheduled eviction of stale windows | `FixedWindowRateLimiterTest` |
| A6 | Prevent alias hijacking of app paths | Reserved alias list | `UrlShortenerServiceTest` |
| A7 | Analytics never breaks redirects | Async, bounded queue, rejection tolerated | Code review + integration test |
| A8 | Bound stored header size | Referrer/User-Agent truncated to 512 | `AnalyticsServiceTest` |
| A9 | Observable health | Actuator health/metrics | Integration test |
| A10 | No information leakage in errors | ProblemDetail, generic 500 message | Integration tests |

### Explicitly out of scope (documented, not silently skipped)
Authentication and per-user quotas, distributed rate limiting (Redis), real-time URL reputation checks, CAPTCHA.
