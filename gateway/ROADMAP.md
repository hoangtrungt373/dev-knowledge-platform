# Gateway Roadmap

Living backlog for `gateway`'s remaining edge-concern features — tracked here separately from
`gateway/CLAUDE.md` (which documents what's *already built* and why) so in-progress planning
doesn't get lost between sessions. Update status inline as work lands; move a finished item's
detail into `gateway/CLAUDE.md` + `docs/CHANGELOG.md` and delete it from here once it's real,
per this repo's usual Documentation Protocol.

## Already built (context, not part of this backlog)

- **Routing** — Spring Cloud Gateway Server MVC, 23 routes across all six standalone services
  (`routing/GatewayRoutesConfig`).
- **Path rewriting** — deliberately *not* built: every backend already expects the exact path it
  was called with from before `gateway` existed, so paths forward unchanged.
- **Auth** — JWT verification (OAuth2 resource server against Keycloak's JWKS); pre-existing, not
  gateway-specific work.
- **CORS** — fully consolidated at `gateway`, zero exceptions (`security/CorsConfig`).
- **SSE streaming proxy** — `routing/ChatStreamProxyController` hand-relays
  `/api/v1/chat/stream` since Gateway Server MVC can't safely proxy SSE.
- **Load balancing / service discovery** — deliberately out of scope. Exactly one instance of each
  service at a fixed address exists today; nothing to balance across or discover.
- **Correlation ID + structured access logging** (2026-08-11) — built as W3C Trace Context
  `traceparent` propagation, not a flat custom header, and built once in `infra` (auto-registered
  in all seven apps, not just `gateway`) rather than scoped to just this module — both of this
  item's original open questions ("custom header or a real standard?", "does every backend service
  need to read it too?") resolved in favor of the more thorough answer. Detail:
  `infra/CLAUDE.md`'s `tracing/` entry, `gateway/CLAUDE.md`'s `ChatStreamProxyController` bullet,
  `ai-service/CLAUDE.md`'s `TraceparentClientHttpRequestInterceptor` bullet,
  `docs/CHANGELOG.md`'s `[Unreleased]` entry.

## Backlog, in recommended order

### 1. Per-route timeouts
**Status:** not started
**Why first (of what's left):** right now only the hand-rolled SSE proxy
(`StreamingProxyAsyncConfig`, 60s) has any timeout at all. Every one of the other 22 routes has
none — a hung/slow backend can hang a gateway thread indefinitely. This is also a prerequisite for
#2: a circuit breaker needs a timeout to know when to count a call as failed. Now that item #1's
access logging is built, its `tookMs` field is exactly what should inform the default value chosen
here, rather than guessing.
- Spring Cloud Gateway Server MVC's `HandlerFunctions.http(...)` — check what timeout knobs the
  underlying `RestClient`/`HttpComponentsClientHttpRequestFactory` expose per-route vs. reactor-wide;
  may need one shared `RestClient` bean with a sane default (e.g. 10s) rather than per-route config,
  unless a specific route (e.g. `ai-service`'s indexing endpoints, which can run long) needs its own.
- Decide the default value with real numbers in mind, not a guess — check what `ai-service`'s own
  `okhttp` timeout config already uses for its OpenAI/Anthropic calls as a reference point.

### 2. Circuit breaker
**Status:** not started
**Why second, paired with #3:** once a timeout can detect an unhealthy backend, a circuit breaker
decides what to do about repeated failures — stop hammering a downed service instead of piling up
timeouts on every request.
- **Option A:** Resilience4j directly (`resilience4j-spring-boot3`) — more control, this reactor's
  first real dependency on it.
- **Option B:** Spring Cloud Circuit Breaker's abstraction over Resilience4j — more idiomatic if
  more of this reactor ever needs circuit breakers (e.g. `ai-service`'s own calls to
  `content-service`/OpenAI/Anthropic), but an extra abstraction layer for a need that, today, is
  `gateway`-only.
- Compare properly before picking, per this repo's "always offer an alternative" convention — don't
  default to whichever is faster to wire up without weighing the trade-off.
- Per-service circuit (one breaker per backend, not one global one) — a downed `ai-service`
  shouldn't trip the breaker for `content-service` traffic.

### 3. Retry
**Status:** not started
**Depends on:** #1 (a retry needs a timeout to know when to give up and try again) and should be
designed alongside #2 (a retry must never fire into an already-open circuit — that's exactly the
hammering behavior the circuit breaker exists to stop).
- Only for idempotent-safe methods (`GET`, maybe `PUT`) — never blindly retry a `POST`/`PATCH`
  without an idempotency-key story, which this reactor doesn't have yet.
- Small bounded attempt count + backoff, not unbounded — needs a concrete number, not a guess;
  revisit once the now-built access logging shows real latency/failure patterns to size against.

### 4. Rate limiting at the edge
**Status:** not started
**Why last:** lowest urgency of what's left — `ai-service` already has its own per-endpoint limiter
(`config/chat/ChatRateLimiter`, Bucket4j + Redis) protecting the one endpoint that actually needs
it today. This item is about *centralizing/generalizing* that pattern at `gateway` so all six
services get uniform protection, not filling a gap that exists nowhere yet.
- **Option A:** reuse `ai-service`'s Bucket4j + Redis pattern at `gateway` too — consistent
  reactor-wide, but reintroduces a Redis dependency to `gateway`, which currently has zero (it lost
  its last one, the cache config, during the `ai-service` extraction — see `infra/CLAUDE.md`).
  Worth re-confirming that's still wanted before treating it as the default choice.
  - Design pattern: **Strategy** — different limiting policies (per-IP vs per-authenticated-user)
    behind one interface, following this repo's own "recommend a GoF pattern for non-trivial
    design problems" convention.
- **Option B:** a simple in-memory bucket per gateway instance (no Redis) — fine given exactly one
  gateway instance exists today (see "Load balancing" above), but would need revisiting the moment
  a second instance is ever added, since in-memory buckets don't share state across instances.
- Per-IP vs per-authenticated-user (JWT `sub`) limiting — probably both, different limits, but
  needs a real decision, not an assumption.

## Explicitly out of scope for this backlog

- **Load balancing / service discovery** — see "Already built" above; revisit only if a second
  instance of any service is ever actually deployed.
- **A general-purpose cross-service orchestration endpoint** — a different, unrelated `gateway`
  concern (see `gateway/CLAUDE.md`'s "Rules specific to this module" section); not part of this
  edge-concerns backlog.
