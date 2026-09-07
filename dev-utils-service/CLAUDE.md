# CLAUDE.md — dev-utils-service

Module-local guidance for `dev-utils-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A stateless developer-utility API: JSON format/validate, YAML↔JSON conversion, HTML beautify.
Package root: `com.ttg.devknowledgeplatform.devutils.*`.

**A standalone Spring Boot application from day one — not an extraction from anything.** Unlike
`ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service`/
`ai-service` (all six pulled out of an embedded monolith module — see the
`project-microservices-extraction-plan` memory), this module never lived inside `gateway`; it was
built standalone from the start, following the "new big feature area gets its own module" rule in
root `CLAUDE.md`. It is **not part of the microservices-extraction-plan project** (that project is
closed — see root `CLAUDE.md`'s Long-term direction section) — don't read this module's existence
as reopening it.

**The one deployable in this reactor with genuinely nothing to persist and no authenticated
caller.** Every operation is a pure text-in/text-out transform:

- **No Postgres schema, no JPA entity, no Liquibase changelog at all** — the only deployable in
  the reactor with zero database story of any kind. Not in `services-liquibase`'s `for svc in ...`
  loop, no changelog directory to mount.
- **Every endpoint is public — no JWT verification at all.** `security/SecurityConfig` declares an
  explicit `.anyRequest().permitAll()` filter chain rather than the `.anyRequest().authenticated()`
  every other service in this reactor uses. See that class's own Javadoc for the two-layer reason
  this needed a real filter chain rather than just omitting Spring Security: `gateway`'s own
  `SecurityConfig` gates `/api/v1/**` behind `.anyRequest().authenticated()` *before* it ever
  proxies anywhere, so `/api/v1/dev-utils/**` needed its own `permitAll()` carve-out there too (see
  `gateway/CLAUDE.md`) — and even with that carve-out in place, this module's own classpath still
  needs `spring-boot-starter-security` regardless of whether it authenticates anyone: `common`'s
  shared `GlobalExceptionHandler` declares `@ExceptionHandler` methods over
  `org.springframework.security.access.AccessDeniedException`/
  `org.springframework.security.core.AuthenticationException`, both of which Spring resolves via
  reflection when that bean registers at context startup — without the dependency present, that
  bean fails to load with a `NoClassDefFoundError` before this app ever serves a request. Given the
  dependency is unavoidable, `SecurityConfig` exists so Spring Boot's own autoconfigured default
  (HTTP Basic + a generated per-boot password, `.anyRequest().authenticated()`) never actually
  applies to anything here. No `spring-boot-starter-oauth2-resource-server`/`-oauth2-client` at
  all — nothing here ever verifies a JWT, so neither dependency is needed.
- **No `@CurrentUserId` consumer, no `CustomUserOAuth2` principal, no Keycloak-related import on
  `DevUtilsServiceApplication`** — there is no authenticated caller to resolve.
- Own port **`8087`** (the next free port after `ai-service`'s `8086`).

- `DevUtilsServiceApplication` — `@SpringBootApplication` +
  `@Import({JacksonConfig.class, TraceContextFilter.class, GlobalExceptionHandler.class})`. Names
  the exact `infra`/`common` beans this module uses, same convention every other standalone
  service in this reactor follows (see root `CLAUDE.md`'s "Post-extraction hardening" section) —
  `JacksonConfig` (the JSON format/YAML conversion operations depend on this exact `ObjectMapper`
  bean) and `TraceContextFilter` (reactor-wide tracing/access logging). No Keycloak-related
  import, no `CurrentUserIdArgumentResolver`.
- `security/SecurityConfig` — see above.
- `exception/DevUtilsErrorCode` — `INVALID_JSON`/`INVALID_YAML` only. No `INVALID_HTML` — jsoup's
  parser is deliberately lenient and never throws on malformed markup, so there is no invalid-HTML
  failure path to name.
- `service/DevUtilOperation` — the Strategy interface (`execute(String input): String`) every
  operation implements. See its own Javadoc for the design-pattern discussion (Strategy chosen
  over a flat facade, specifically because more operations — Base64, UUID generation, regex test,
  JWT decode — are a likely next step, not a closed door).
- `service/impl/{JsonFormatOperation,YamlToJsonOperation,JsonToYamlOperation,HtmlBeautifyOperation}`
  — one `@Component` per operation. `JsonFormatOperation` validates and pretty-prints in one pass
  (doubles as "JSON validate"). `YamlToJsonOperation`/`JsonToYamlOperation` both reuse the
  `JacksonConfig`-customized `ObjectMapper` for their JSON side and a plain locally-constructed
  `YAMLMapper` for their YAML side (no second Spring-managed `ObjectMapper` bean needed).
  `HtmlBeautifyOperation` parses input as a body fragment (`Jsoup.parseBodyFragment`), not a full
  document — a snippet in yields a snippet out; a full `<html>` document's `<head>` is dropped, the
  same trade-off most standalone HTML-beautifier tools make.
- `dto/{DevUtilRequest,DevUtilResponse}` — one shared record pair for every operation
  (`input`/`output`, both plain strings) — deliberately not one DTO per operation, since the shape
  really is identical across all four endpoints.
- `api/DevUtilsApi` (+ `api/impl/DevUtilsController`) — `POST /api/v1/dev-utils/json/format`,
  `/yaml-to-json`, `/json-to-yaml`, `/html/beautify`. The controller injects each operation by its
  concrete type rather than dispatching through an enum-keyed registry — with one fixed REST
  endpoint per operation, there's no runtime "which operation" decision left to make (see
  `DevUtilOperation`'s own Javadoc).

## Rules specific to this module

- **Depends only on `common` + `infra`.** Never add a Maven dependency on `gateway`,
  `ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, or
  `ai-service` — and none of them may depend on this module either. A future operation that needs
  data from another service would need a real HTTP call, never a Maven dependency.
- **Don't add persistence to this module without confirming scope first.** If a future feature
  wants saved/shareable snippets or per-user history, that's a real fork in this module's
  architecture (its own Postgres schema, Liquibase changelog, and — since a "per-user" concept
  needs a caller — likely JWT verification too), not a small addition; it changes the "nothing to
  persist, no caller to authenticate" framing this whole module is built around.
- **Don't add JWT/JWT-derived authorization to an existing endpoint without confirming scope
  first**, for the same reason in reverse — every endpoint here being genuinely public is a
  deliberate, documented choice (see "What lives here" above and `gateway/CLAUDE.md`'s matching
  carve-out), not an oversight to "fix."
- **New endpoints need a matching `permitAll()` in *this* module's own `SecurityConfig` and a
  matching route (plus its own `permitAll()` carve-out) in `gateway`'s** — this module's filter
  chain currently permits everything unconditionally (`anyRequest().permitAll()`), so a new
  endpoint under `/api/v1/dev-utils/**` needs no local change, but `gateway`'s own routing still
  needs the same `route(path(...), http(baseUrl))` line every other service's new endpoint needs
  (see `gateway/CLAUDE.md`'s standing warning about this exact class of gap) — a new top-level
  prefix, unlike a new sub-path under `/api/v1/dev-utils/**`, would need its own `permitAll()` line
  in `gateway`'s `SecurityConfig` too.
- **No Dockerfile/compose dependency on `services-liquibase`, Postgres, Redis, MinIO, or
  Keycloak** — this module's own `docker-compose.apps.yml` block has no `depends_on` at all. Don't
  add one without a concrete new reason (e.g. a future feature that genuinely calls another
  service).
