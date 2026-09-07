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
  loop, no changelog directory to mount. **`DevUtilsServiceApplication` excludes
  `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration` — not optional, found by an actual
  boot attempt.** `common` declares `spring-boot-starter-data-jpa` non-optional (for
  `AbstractEntity`'s `@MappedSuperclass`), which every consumer inherits transitively regardless of
  whether it maps any entities. Every other service in this reactor never notices, since each one
  already configures a real `spring.datasource.url` + `org.postgresql:postgresql`; left
  un-excluded here, Spring Boot tried to build a `HikariDataSource` anyway and the app failed to
  start at all (`DataSourceBeanCreationException: Failed to determine a suitable driver class`) —
  caught by a real `@SpringBootTest` context-load failure, not anticipated up front.
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
  - **Fixed, per direct follow-up request, a bug originally found while investigating a `gui`
    error-message complaint (see that complaint's own history below for the client-side half of
    this story).** `INVALID_JSON`/`INVALID_YAML`'s own `"Invalid JSON: {0}"`/`"Invalid YAML: {0}"`
    templates were defined but never actually applied — `JsonFormatOperation`/`JsonToYamlOperation`/
    `YamlToJsonOperation` all caught their `JsonProcessingException` and called
    `new BusinessException(errorCode, e.getMessage())`, a plain `String` argument that
    overload-resolves to `BusinessException(ErrorCode, String message)` (the raw-message
    constructor), never the varargs `(ErrorCode, Object... templateArgs)` overload that runs the
    message through `ErrorCode.formatMessage()`. So the client-facing `errorMessage` used to be
    Jackson's own raw exception text verbatim — no "Invalid JSON:"/"Invalid YAML:" prefix despite
    the enum implying one, and, worse, it leaked Jackson's own parser-internals diagnostics
    (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION`, `[Source: REDACTED (...)]`) straight to any
    API caller. **Two-part fix, both confirmed with a real standalone Java harness against the
    actual resolved Jackson 2.19.2 (not just read and assumed):**
    1. **New `exception/ParsingExceptionMessages` (`friendlyMessage(JsonProcessingException e)`)**
       builds a clean detail string structurally, not by string-parsing `e.getMessage()`: it starts
       from `e.getOriginalMessage()` (Jackson's own pre-location-suffix message, so
       `getMessage()`'s own separately-appended `[Source: ...]` clause is gone for free), strips
       two further noise shapes via regex (Jackson's own inline `"(start marker at [Source:
       ...])"` clause some structural errors bake directly into their original message — confirmed
       real via the harness, e.g. an unclosed `{` — and SnakeYAML's own `"in '<name>', line N,
       column M:"` mark blocks, each followed by a 2-line source-snippet + `^`-pointer, which a
       single YAML error can carry *twice* — a "context" mark and a "problem" mark, interleaved
       with the two sentences that actually explain the failure, confirmed via the harness against
       a real malformed-flow-sequence YAML input), then re-appends the real location from
       `e.getLocation()` (a structured `JsonLocation` — `getLineNr()`/`getColumnNr()`, never
       string-parsed). Harness output for 4 real inputs: an unclosed JSON object → `"Unexpected
       end-of-input: expected close marker for Object (line 1, column 14)"`; a missing-comma JSON
       object → `"Unexpected character ('"' (code 34)): was expecting comma to separate Object
       entries (line 1, column 15)"`; a malformed YAML flow sequence (`tools: [JSON,,Base64]`,
       both SnakeYAML marks) → `"while parsing a flow node expected the node content, but found
       ',' (line 1, column 13)"`; a bad YAML indent → `"mapping values are not allowed here (line
       2, column 6)"` — all four clean, single-line, zero Jackson/SnakeYAML internals.
    2. **Each operation's catch block now passes that clean message through a `(Object)` cast**:
       `new BusinessException(errorCode, (Object) ParsingExceptionMessages.friendlyMessage(e))`.
       The cast is load-bearing, not decorative — it's what actually fixes the "template never
       applied" half of the bug: a plain `String` argument always resolves to
       `BusinessException(ErrorCode, String message)` in Java's overload resolution (phase 1, no
       boxing/varargs needed, since the argument already *is* a `String`); casting to `Object`
       makes that overload inapplicable (an `Object` doesn't implicitly narrow to `String`),
       forcing resolution onto the varargs `(ErrorCode, Object... templateArgs)` overload instead
       — the one that actually calls `ErrorCode.formatMessage()`. Confirmed end-to-end via the same
       harness (not just each half in isolation): a real `readTree` failure fed through both fixes
       together produced `errorMessage = "Invalid JSON: Unexpected end-of-input within/between
       Object entries (line 1, column 79)"` — the template prefix finally applies, and the message
       is clean.
    - **`gui`'s own `dev-utils/utils/errorFormatting.ts` keeps its client-side logic regardless of
      this fix — it was never purely a workaround for this bug, and this fix doesn't make it dead
      code.** For a JSON-input operation, the browser's own `JSON.parse` still produces a *more*
      precise, more familiar message (native V8 phrasing) than this service's Jackson-based one
      ever could, so that client-side path stays as the better option, not a stopgap. For
      `yaml-to-json` (the one operation whose message the GUI still shows verbatim from this
      service), `errorFormatting.ts`'s own `simplifyBackendMessage` was updated in the same pass to
      stay idempotent against this now-already-clean message (it used to unconditionally re-append
      its own `"(line N, column M)"` suffix, which would have doubled up against this service's own
      new one) — see that file's own doc comment.
- `service/DevUtilOperation` — a bare **marker interface** (no method), purely for IDE "Find
  Implementations" grouping — the same role `infra.event.ApplicationEventHandler`/
  `infra.service.seed.Seeder` already play in this reactor. Deliberately **not** a textbook GoF
  Strategy with a shared `execute(...)` signature — an earlier revision forced every operation
  through `execute(String input, boolean minify): String`, which broke down once a genuinely
  different-shaped operation (a future Unix Time Converter needing timestamp+timezone+format, a
  Number Base Converter needing value+two integer bases) was considered — neither fits "one string
  in, one bool flag, one string out," and packing them into that shape would mean hand-parsing a
  packed string apart instead of real typed parameters. A shared method signature only pays for
  itself when something dispatches through it polymorphically; nothing here does — the controller
  injects and calls each operation by its own concrete type. See its own Javadoc for the full
  reasoning. Each operation is free to declare whatever parameter/return shape actually fits it.
- `config/YamlMapperConfig` — a `YAMLMapper` `@Bean`, the YAML-side counterpart to `infra`'s
  shared `ObjectMapper` (`JacksonConfig`). Lives here, not `infra` — this module is the only
  consumer today; promote it there only once a second module genuinely needs the same bean.
  Mirrors `JacksonConfig`'s own customization (`JavaTimeModule`, tolerant deserialization,
  ISO-8601 dates) for consistency, even though neither operation below can currently observe a
  difference (both work over a generic `JsonNode` tree, never a typed POJO).
- `service/impl/{JsonFormatOperation,YamlToJsonOperation,JsonToYamlOperation,HtmlBeautifyOperation}`
  — one `@Component` per operation. `JsonFormatOperation` validates and pretty-prints (or, with
  `minify`, compact-serializes) in one pass (doubles as "JSON validate"). `YamlToJsonOperation`
  applies the same pretty/minify choice to its JSON output; both it and `JsonToYamlOperation`
  inject the shared `ObjectMapper`/`YAMLMapper` beans — no operation constructs its own mapper.
  **`JsonToYamlOperation` accepts but ignores `minify`** — `jackson-dataformat-yaml` has no
  supported single-line/flow-style toggle, so output is always the same block-style YAML
  regardless of the flag. `HtmlBeautifyOperation` parses input as a body fragment
  (`Jsoup.parseBodyFragment`), not a full document — a snippet in yields a snippet out; a full
  `<html>` document's `<head>` is dropped, the same trade-off most standalone HTML-beautifier
  tools make. `minify` maps to jsoup's own `prettyPrint(false)` mode — not a true single-line
  guarantee (whitespace already present inside a source text node is preserved as-is).
- `dto/DevUtilsLimits` — one shared `MAX_INPUT_LENGTH` constant (`100_000` characters, a
  deliberately generous but finite first-version bound), referenced by both request DTOs' `@Size`
  constraint below. This is the one fully public, unauthenticated endpoint in the reactor — an
  unbounded `input` would be a real resource-exhaustion vector (a large body fully buffered/parsed
  before any other check runs) — so every operation shares one cap rather than each endpoint
  guessing its own number; split it per operation later if a real use case needs a different bound
  for one of them.
- `dto/{MinifiableTextRequest,TextRequest,DevUtilResponse}` — request DTOs are shared **only where
  the shape genuinely matches**: `MinifiableTextRequest` (`input`/`minify`) backs
  `json/format`/`yaml-to-json`/`html/beautify`, which really do share that shape; `TextRequest`
  (`input` only) backs `json-to-yaml`, which has no minify concept at all — not the same type with
  an ignored field. Both `input` fields carry `@NotBlank @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH)`.
  `DevUtilResponse` (`output`) stays shared across all four today, but a future
  operation with a genuinely richer output (e.g. a Number Base Converter's several
  representations) should get its own response type rather than being forced into this one. See
  `DevUtilOperation`'s own Javadoc for the full reasoning against one shared request/response pair.
- `api/DevUtilsApi` (+ `api/impl/DevUtilsController`) — `POST /api/v1/dev-utils/json/format`,
  `/yaml-to-json`, `/json-to-yaml`, `/html/beautify`. The controller injects each operation by its
  concrete type rather than dispatching through an enum-keyed registry — with one fixed REST
  endpoint per operation, there's no runtime "which operation" decision left to make (see
  `DevUtilOperation`'s own Javadoc).

**Test suite:** `src/test/java/.../service/impl/` — one plain JUnit 5 test class per operation
(`JsonFormatOperationTest`, `YamlToJsonOperationTest`, `JsonToYamlOperationTest`,
`HtmlBeautifyOperationTest`), no Mockito — each constructs real `ObjectMapper`/`YAMLMapper`
instances rather than mocking Jackson, since the whole point is verifying real parse/serialize
behavior (pretty vs. minified output, malformed-input rejection, round-trip structural equality
via `readTree`, jsoup's lenient-parsing/indent behavior). Plus
`DevUtilsServiceApplicationTests` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
`@AutoConfigureMockMvc`) — boots the real Spring context and hits all four endpoints with **no**
`Authorization` header through the real filter chain, confirming end to end (not just by static
reasoning) that the app actually starts and every endpoint is genuinely public. This is exactly
the test that caught the `DataSourceAutoConfiguration` boot failure above, and it also covers the
`MAX_INPUT_LENGTH` boundary (accepted at exactly the cap, rejected one over it — the latter caught
by `@Size` before ever reaching an operation). 21 tests total,
verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).

## Rules specific to this module

- **Don't force a new operation's request/response shape (or its `execute(...)` signature) to
  match an existing one just for consistency.** `DevUtilOperation` is a bare marker interface and
  `dto/` DTOs are shared only where the shape genuinely matches (see both their own Javadoc) —
  this was a real, corrected mistake: an earlier revision forced every operation through one
  shared `execute(String input, boolean minify): String` signature and one shared request/response
  DTO pair, which only looked reasonable because every operation at the time really was "text in, a
  minify flag, text out." A future operation with a genuinely different shape (Unix Time Converter:
  timestamp+timezone+format; Number Base Converter: value+two integer bases) gets its own request/
  response type and its own `execute(...)` signature, not a bent version of an existing one.
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
