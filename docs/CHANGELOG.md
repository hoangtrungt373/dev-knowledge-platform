# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

`0.0.1` is the original monolith; `0.0.2` is when the break-up into standalone microservices
began (the full six-service extraction — `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service` — plus `gateway`'s routing/CORS consolidation and
the reactor-wide `@ComponentScan` fix); `0.0.3` is `ecommerce-service`'s feature build-out (checkout
shipping/coupon pricing strategies, payment countdown + reconciliation) and its `gui` counterpart
work that accumulated under `[Unreleased]` afterward — cut into a real release for the same reason
`0.0.2` was: this file had grown past ~3700 lines under a single ever-growing `[Unreleased]`
section again. Full unabridged entry-by-entry history for all three lives in
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md). New entries start fresh below `[Unreleased]`.

---

## [Unreleased]

### Added

- **New module `dev-utils-service` — a stateless developer-utility API (JSON format/validate,
  YAML↔JSON conversion, HTML beautify), own port `8087`.** Unlike every other standalone service in
  this reactor, this one was built directly standalone from day one, never embedded in `gateway` at
  all — not part of the (closed) microservices-extraction-plan project. It's also the one
  deployable with genuinely nothing to persist and no authenticated caller: no Postgres schema, no
  JPA entity, no Liquibase changelog, and every endpoint public (`security.SecurityConfig`'s own
  `.anyRequest().permitAll()`, replacing Spring Boot's autoconfigured HTTP Basic default). It still
  carries `spring-boot-starter-security` on its classpath regardless of authenticating no one — a
  real finding made while scaffolding it: `common`'s shared `GlobalExceptionHandler` declares
  `@ExceptionHandler` methods over `org.springframework.security.access.AccessDeniedException`/
  `org.springframework.security.core.AuthenticationException`, which Spring resolves via reflection
  when that bean registers at context startup; without the dependency present (which `common`
  declares `optional=true`, so it isn't inherited for free the way every other service's own JWT
  dependency already provides it), that bean fails to load with a `NoClassDefFoundError` before the
  app ever serves a request.
  - `service.DevUtilOperation` — a GoF **Strategy** (Behavioral) interface, one implementation per
    operation (`JsonFormatOperation`, `YamlToJsonOperation`, `JsonToYamlOperation`,
    `HtmlBeautifyOperation`), each its own Spring bean injected by concrete type into
    `api.impl.DevUtilsController` — chosen over a flat facade specifically because more operations
    (Base64, UUID generation, regex test, JWT decode) are a likely next step for this module, not a
    closed door. `HtmlBeautifyOperation` uses jsoup (new dependency, version-managed in the root
    `pom.xml`); YAML support uses Jackson's `jackson-dataformat-yaml` (no explicit version — managed
    by `spring-boot-dependencies`' own Jackson BOM).
  - `exception.DevUtilsErrorCode` — `INVALID_JSON`/`INVALID_YAML` only; no `INVALID_HTML`, since
    jsoup's parser is deliberately lenient and never throws on malformed markup.
  - `api.DevUtilsApi`/`api.impl.DevUtilsController` — `POST /api/v1/dev-utils/{json/format,
    yaml-to-json,json-to-yaml,html/beautify}`, one shared `DevUtilRequest`/`DevUtilResponse` record
    pair for every operation (the shape really is identical across all four).
  - `gateway` wiring: `routing.GatewayRoutesConfig` gained a new `devUtilsServiceRoutes()` bean
    (`/api/v1/dev-utils/**`) and `routing.GatewayServicesProperties` a matching
    `devUtilsServiceBaseUrl`, in both `application.yml` (localhost default) and
    `application-docker.yml` (Compose DNS name). `security.SecurityConfig` gained a new
    `.requestMatchers("/api/v1/dev-utils/**").permitAll()` rule — `gateway` gates `/api/v1/**`
    behind `.anyRequest().authenticated()` before ever proxying anywhere, so a public downstream
    service needs its own carve-out at that layer too, the same two-layer reasoning
    `identity-service`'s own registration-endpoint carve-out already established.
  - `docker-compose.apps.yml` gained a `dev-utils-service` container block — no
    `SPRING_DATASOURCE_*`/`KEYCLOAK_ISSUER_URI` env vars and no `depends_on` at all (not even
    `services-liquibase`), since there is nothing here for it to migrate and no other container it
    needs up first. Every other service's own `Dockerfile` gained a new
    `COPY dev-utils-service/pom.xml dev-utils-service/pom.xml` line in its poms-only stage — Maven
    needs every module's `pom.xml` present to parse the root reactor's `<modules>` list, even for a
    build that never touches this module's own sources.
  - Root `pom.xml`: new module registered, new internal `dependencyManagement` entry, new
    `jsoup.version` property + managed dependency.
  - **Follow-up: `YamlToJsonOperation`/`JsonToYamlOperation` now inject a shared `YAMLMapper` bean
    (new `config.YamlMapperConfig`) instead of each constructing its own `new YAMLMapper()`**, per
    request — the YAML-side counterpart to `infra`'s shared `ObjectMapper` (`JacksonConfig`).
    Mirrors that bean's own customization (`JavaTimeModule`, tolerant deserialization, ISO-8601
    dates) for consistency, even though neither operation can currently observe a difference (both
    work over a generic `JsonNode` tree, never a typed POJO) — kept anyway so a future operation
    that does deserialize into a typed object doesn't hit a silent inconsistency between the two
    mappers. Lives in this module, not `infra` — it's the only consumer today.
  - **Follow-up: a `minify` flag on the shared `DevUtilRequest`, per request** — compact/
    single-line output instead of pretty-printed (the default, `false`, when omitted — non-breaking
    for any existing caller). `service.DevUtilOperation#execute` gained a `boolean minify`
    parameter; `JsonFormatOperation`/`YamlToJsonOperation` honor it on their JSON output
    (`objectMapper.writeValueAsString` vs. `.writerWithDefaultPrettyPrinter()`);
    `HtmlBeautifyOperation` maps it to jsoup's own `prettyPrint(false)` mode (not a true
    single-line guarantee — whitespace already present inside a source text node is preserved
    as-is, the standard content-safe way jsoup distinguishes formatted from unformatted output).
    **`JsonToYamlOperation` accepts but ignores it** — `jackson-dataformat-yaml` has no supported
    single-line/flow-style toggle, so there's no safe way to produce a compact YAML document;
    output is always the same block-style YAML regardless of the flag.
  - **Follow-up: `DevUtilOperation`/the shared request DTOs were corrected away from a forced
    uniform shape, per a direct question about future operations (Unix Time Converter, Number Base
    Converter) that wouldn't fit it.** The `minify` follow-up above had forced every operation
    through one `execute(String input, boolean minify): String` signature and one shared
    `DevUtilRequest`/`DevUtilResponse` DTO pair — reasonable while every operation really was "text
    in, a minify flag, text out," but a genuinely different-shaped future operation (a timestamp+
    timezone+format, or a value+two integer bases) wouldn't fit either, and forcing it through would
    mean hand-packing multiple values into one string instead of real typed parameters.
    `DevUtilOperation` is now a bare **marker interface** (no method at all) — the same "Find
    Implementations" role `infra.event.ApplicationEventHandler`/`infra.service.seed.Seeder` already
    play in this reactor — since nothing dispatches through it polymorphically anyway (the
    controller always calls each operation by its own concrete type). New `dto.MinifiableTextRequest`
    (`input`/`minify`) replaces the shared `DevUtilRequest` for the three operations that genuinely
    share that shape (`json/format`, `yaml-to-json`, `html/beautify`); new `dto.TextRequest`
    (`input` only) replaces it for `json-to-yaml`, which never had a minify concept — dropped
    entirely from that operation's own `execute` signature rather than kept as an ignored
    parameter. `DevUtilResponse` stays shared across all four today, documented as not a rule going
    forward. Old `dto.DevUtilRequest` deleted outright.
  - **Follow-up: unit test suite for all four operations, per request.** One plain JUnit 5 class
    per operation (`JsonFormatOperationTest`, `YamlToJsonOperationTest`, `JsonToYamlOperationTest`,
    `HtmlBeautifyOperationTest`) — no Mockito, each constructs real `ObjectMapper`/`YAMLMapper`
    instances rather than mocking Jackson, since the point is verifying real parse/serialize
    behavior: pretty-vs-minified output, malformed-input rejection with the correct
    `DevUtilsErrorCode`, round-trip structural equality (`readTree` comparison, avoiding brittle
    exact-string assertions against YAML's own quoting/marker formatting), and jsoup's
    lenient-parsing/indent behavior. 12 tests total, verified via a real
    `mvn -pl dev-utils-service -am test` run (JDK 21) — this module's first real runtime
    verification of any kind since it was scaffolded.
  - **Bug fix, found by an actual boot attempt: the app could not start at all.** New
    `DevUtilsServiceApplicationTests` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
    `@AutoConfigureMockMvc`) — a context-load + end-to-end `MockMvc` smoke test hitting all four
    endpoints with no `Authorization` header — immediately failed with
    `DataSourceBeanCreationException: Failed to determine a suitable driver class`. Root cause:
    `common` declares `spring-boot-starter-data-jpa` as a **non-optional** dependency (needed there
    for `AbstractEntity`'s `@MappedSuperclass`/`@Entity` support), which every consumer inherits
    transitively regardless of whether it maps any entities — every other service in this reactor
    never notices because each one already configures a real `spring.datasource.url` +
    `org.postgresql:postgresql`; this module deliberately has neither. Fixed with
    `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class})` on `DevUtilsServiceApplication`. Re-ran: 19/19 tests
    pass, including all four endpoints returning `200` with zero authentication and a malformed-
    input case correctly returning `400` with `DEVUTILS_001` through the shared
    `GlobalExceptionHandler`. This is the module's first genuine confirmation that it actually
    boots — every claim in its own `CLAUDE.md` about "no schema, no auth" had, until this test,
    only ever been verified by static reasoning, not by starting the app.
  - **Follow-up: an `input` size cap, per request — a deliberately generous but finite first-version
    bound (`100_000` characters), since this is the one fully public, unauthenticated endpoint in
    the reactor.** New `dto.DevUtilsLimits.MAX_INPUT_LENGTH`, referenced by a new
    `@Size(max = ...)` on both `MinifiableTextRequest.input`/`TextRequest.input` (alongside the
    existing `@NotBlank`) — one shared constant rather than each DTO guessing its own number.
    Verified via two new `DevUtilsServiceApplicationTests` cases: input one character over the cap
    is rejected `400` by Bean Validation before ever reaching an operation; input at exactly the
    cap is accepted. The boundary test's own first draft used a 100,000-digit run (simultaneously
    valid JSON — a single large integer literal — and trivial to size exactly), which incidentally
    tripped a *different*, pre-existing Jackson safety limit
    (`StreamReadConstraints.getMaxNumberLength()`, default 1000 digits per JSON number token) once
    `JsonFormatOperation` tried to parse it — not a bug in the new `@Size` cap, just the wrong test
    fixture; switched to a JSON array wrapping one long string (`["aaa...a"]`) to sidestep it. 21
    tests total, verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).
  - See `dev-utils-service/CLAUDE.md` for the full module writeup, and root `CLAUDE.md`'s Module
    Structure table, Long-term direction, Security, Database Conventions, and Architecture →
    Routing sections for the reactor-wide documentation updates this addition required.

## [0.0.3] — 2026-09-07

Retroactive cut of everything that had accumulated under `[Unreleased]` since the `0.0.2` cut —
almost entirely `ecommerce-service`'s feature build-out (shipping/coupon pricing strategies,
payment countdown + reconciliation job) and its `gui` counterpart work. See
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the complete, unabridged entry-by-entry history.

---

## [0.0.2] — 2026-08-11

Retroactive cut of everything that had accumulated under `[Unreleased]` up to this point — the
start of the microservices break-up. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the
complete, unabridged entry-by-entry history.

---

## [0.0.1] — Initial release

The original monolith. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the full entry.
