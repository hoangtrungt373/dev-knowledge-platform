# CLAUDE.md — infra

Module-local guidance for `infra`. Read alongside the root `CLAUDE.md`.

## What lives here

- `event/` — the shared async-event-handling framework:
  - `ApplicationEventHandler` — marker interface; "Find Implementations" in any IDE lists every
    active event handler across every module in one view.
  - `EventHandler` — composed `@EventListener` + `@Async("asyncEventExecutor")`; every listener in
    the codebase should use this instead of bare `@EventListener`, so async dispatch and the
    dedicated thread pool are never opted out of by accident.
  - `AsyncEventHandler<E>` — abstract base (Template Method). Subclasses implement only
    `doHandle(event)`; the base class provides the `@EventHandler`-annotated dispatch, MDC
    `traceId` binding (opt-in via `resolveTraceId()`), timing, and exception safety. Subclasses
    that need DB writes declare `@Transactional` themselves — the base class doesn't.
- `config/thread/{AsyncEventThreadPoolConfig,AsyncEventThreadPoolProperties}` — the
  `asyncEventExecutor` bean every `@EventHandler` dispatches through, moved here from `gateway`:
  this module's own event framework is the thing that actually owns this pool's purpose (a
  bulkhead separate from `gateway`'s `sseStreamExecutor`, so a burst of SSE streams can't starve
  background event handling and vice versa), so the bean definition belongs alongside it rather
  than trusting `gateway` to supply it. Bound from `app.threads.async-event` (previously nested
  under `gateway`'s `app.threads.async-event-executor` — no existing `application.yml` override
  referenced the old path, so the prefix changed freely). `gateway`'s own `sseStreamExecutor`
  stays there — `WebMvcConfig.configureAsyncSupport` (a single global MVC-config point) is its
  only real consumer beyond `ai-service`'s `SseStreamTemplate`, which reaches it by
  `@Qualifier`/bean-name regardless of which module defines it.
- `context/MdcKeys` — MDC key constants (`TRACE_ID`), shared so no module hardcodes the string.
- `service/SlugService` (+ impl) — generic slug generation/uniqueness utility. Lives here (not
  `gateway`, not any feature module) specifically because feature modules that need it
  (`content-service`) can't depend on `gateway`, and it's generic enough that `common` (no Spring
  context) isn't the right fit either — `infra` is the "shared Spring infra any feature module can
  reach" layer.
- `service/StorageService` (+ `impl`) — MinIO upload/presigned-URL/delete, moved here from `gateway`
  since both `social-service` (`FriendMapper`/`MessagingMapper`, avatar presigned URLs) and
  `identity-service` (`UserMapper`, avatar upload) need it and neither can depend on the other —
  same reasoning as `SlugService` above, just discovered later.
- `config/storage/{StorageConfig,StorageProperties}` — `MinioClient` bean + `app.storage.*`
  properties, moved here alongside `StorageService` for the same reason.
- `config/cache/{CacheNames,CacheTtlProperties}` — Redis cache-name constants + `app.cache.*` TTL
  binding, moved here because `identity-service`'s `StateTokenServiceImpl` and `gateway`'s
  `RedisCacheConfig` both need them and can't depend on each other.
- `service/seed/CsvSeeder<T>` — Template Method for idempotent CSV-based seeding (read/iterate/
  skip-or-insert). Moved here from `content-service` once `social-service`'s `UserBlockSeeder`
  needed it too — `content-service` and `social-service` are independent siblings that can't
  depend on each other, so a utility needed by both belongs here, same reasoning as `SlugService`.
  Used by `content-service`'s `CategorySeeder`/`TagSeeder`, `identity-service`'s `UserSeeder`, and
  `social-service`'s `UserBlockSeeder`. A seeder whose per-row shape doesn't fit one-entity-per-row
  (e.g. `content-service`'s `QuestionAnswerSeeder`, `social-service`'s `FriendGraphSeeder`/
  `DmThreadSeeder`, which each persist more than one entity per unit of work) implements its own
  `seed()` instead of extending this.
- `security/RsaKeyUtils` — parses PKCS#8/X.509 PEM key material (a Spring `Resource`) into
  `PrivateKey`/`PublicKey`. Moved here because `identity-service`'s `JwtTokenProvider` (needs both
  keys, to sign and verify the RS256 tokens it issues) and `ecommerce-service`'s `JwtVerifier`
  (needs only the public key, to verify tokens issued elsewhere) are independent siblings that
  can't depend on each other — same reasoning as `StorageService`/`CacheNames` below. See
  `docs/CHANGELOG.md`'s `[Unreleased]` entry for the HS512→RS256 switch this supports.
- `service/seed/Seeder` — documentation-only marker interface (`int seed()`) every seeder in the
  reactor implements, `CsvSeeder<T>` included — same "Find Implementations" purpose as this
  module's own `ApplicationEventHandler` marker for event handlers. **Not** used for polymorphic
  invocation: `gateway`'s `DataSeedingRunner` still calls each seeder by name in an explicit,
  hardcoded dependency order, since that order encodes real cross-entity requirements a
  Spring-bean-registration-order `List<Seeder>` loop wouldn't guarantee. See `Seeder`'s own Javadoc
  before "simplifying" `DataSeedingRunner` into such a loop.

## Rules specific to this module

- **This is the layer for a utility class that a feature module needs but can't get from `gateway`**
  (because that dependency runs the other way), **or that's needed by two feature modules that
  can't depend on each other** (e.g. `content-service` and `social-service`; `social-service` and
  `identity-service` — note `social-service` *is* allowed to depend on `identity-service` directly
  for `UserService`/`UserMapper`, so only genuinely-mutual needs like `StorageService` belong here,
  not everything either module touches), **and that isn't a pure entity/enum/exception** (which
  would belong in `common`). Before adding something here, ask: does it need Spring context? Is it
  genuinely generic (not tied to one feature's domain model)? If either answer is no, it probably
  belongs elsewhere (`common` if no Spring context needed and truly cross-cutting; the feature
  module itself if it's domain-specific).
- Depends on `common` + `spring-boot-starter` (for `@Async`/`@EventListener`/`@Transactional`
  annotation support) + `commons-csv` (for `CsvSeeder`) + `spring-boot-starter-web` (for
  `MultipartFile` on `StorageService`) + `io.minio:minio` + `io.micrometer:micrometer-core` (for
  `ExecutorServiceMetrics` on `asyncEventExecutor`). Never add a dependency on any feature module
  or `gateway` here.
- When adding a new event listener anywhere in the codebase, extend `AsyncEventHandler<E>` from
  here rather than rolling a bare `@EventListener` method — that's the whole point of this module.
