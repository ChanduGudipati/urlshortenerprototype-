# AI Usage and Traceability Log

**Tool:** Claude (chat), used as a pair programmer inside engineer-defined tasks.
**Principle:** AI drafts within a task; the engineer defines the task, reviews every output, decides accept / edit / reject, and owns correctness.

## Secure AI usage rules followed
- No secrets, credentials, customer data or proprietary employer code in prompts.
- Every generated line reviewed before commit; nothing merged unread.
- Dependencies suggested by AI checked as well-known, actively maintained libraries with pinned versions via the Spring Boot BOM.
- High-impact changes (schema migrations, security validation, redirect semantics, API contract) require explicit engineer sign-off, recorded below.

## Prompt template used per task
```
Intent:        <what and why>
Constraints:   Java 21, Spring Boot 3.3, constructor injection, no new deps without justification
Context:       <relevant existing classes / schema>
Acceptance:    <testable criteria>
Output:        complete files + unit tests; list assumptions
```

## Log

| # | Task | AI output | Decision | Rationale |
|---|---|---|---|---|
| 1 | Normalize requirements | List of ambiguities and suggested defaults | **Edited** | Kept most; changed "dedupe identical URLs" to no dedup (simpler, avoids leaking that a URL was already shortened) |
| 2 | Code generation | Encode auto-increment ID in Base62 | **Rejected** | Sequential codes are enumerable and leak volume. Replaced with `SecureRandom` Base62 |
| 3 | Uniqueness | `existsByCode()` check, then insert | **Rejected** | Check-then-act race under concurrency. Rely on DB unique constraint and retry |
| 4 | Collision retry | `@Transactional` on `create()` with retry loop | **Rejected** | PostgreSQL aborts the transaction after a constraint violation, so retries inside it fail. Each attempt now runs in its own repository transaction |
| 5 | Redirect status | 301 Moved Permanently | **Rejected** | Browsers cache 301, bypassing analytics and expiry. Using 302 + `no-store` |
| 6 | Click counting | Load entity, increment, save | **Rejected** | Lost updates under concurrent clicks. Atomic `UPDATE ... + 1` |
| 7 | Analytics on redirect path | Synchronous insert | **Rejected** | Adds latency and a failure mode to the hot path. Made async; added handling of `TaskRejectedException` (engineer addition) |
| 8 | Redirect caching | `@Cacheable` returning the JPA entity | **Edited** | Cache an immutable `RedirectTarget` record instead of a managed entity; expiry re-checked on every request so cached entries never serve expired links |
| 9 | URL validation | Regex-based URL check | **Rejected** | Regex validation is brittle. Used `java.net.URI` + scheme allowlist; engineer added self-host loop check and subdomain-aware blocklist |
| 10 | Rate limiting | Bucket4j + Redis | **Deferred** | Unnecessary complexity for a single-instance prototype. Simple in-memory fixed window with eviction; Redis documented as the scale-out path |
| 11 | Exception handling | Catch-all `Exception` → 500 | **Edited** | Found in review that it converted 404/405 into 500. Pass through Spring `ErrorResponse` statuses |
| 12 | Unit tests | Service and validator tests | **Edited** | Added collision-retry, retry-exhaustion, reserved alias and lookalike-domain cases the draft missed |
| 13 | Integration tests | Used `Thread.sleep` for async assertions | **Edited** | Replaced with Awaitility to avoid flaky tests |
| 14 | Migrations | Single migration with all tables | **Edited** | Split into V1 (greenfield) and V2 (brownfield, additive) to show safe schema evolution |
| 15 | Docs | README, architecture draft | **Edited** | Rewrote decisions and trade-offs in engineer's own reasoning; verified every command |

## Human sign-off record (high-impact changes)

| Change | Risk | Sign-off |
|---|---|---|
| Flyway V1 / V2 schema | Data integrity, migration safety | Engineer approved: additive, defaults set, FK with cascade reviewed |
| `UrlValidator` rules | Open redirect / phishing abuse | Engineer approved after reviewing negative test cases |
| 302 redirect semantics | Analytics correctness, SEO | Engineer approved |
| Public API contract (`/api/v1`) | Client compatibility | Engineer approved; versioned path |
