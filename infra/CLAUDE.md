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
  bulkhead separate from the `sseStreamExecutor` pool, so a burst of SSE streams can't starve
  background event handling and vice versa), so the bean definition belongs alongside it rather
  than trusting `gateway` to supply it. Bound from `app.threads.async-event` (previously nested
  under `gateway`'s `app.threads.async-event-executor` — no existing `application.yml` override
  referenced the old path, so the prefix changed freely). The `sseStreamExecutor` pool itself no
  longer lives in `gateway` either — it moved to `ai-service`'s own `config/thread/` once that
  module became standalone (`gateway` has no SSE endpoint left to feed); `ai-service`'s own
  `SseStreamTemplate`/`ChatMvcConfig.configureAsyncSupport` reach it by `@Qualifier`/bean-name, same
  as before the move.
- `context/MdcKeys` — MDC key constants (`TRACE_ID`, `SPAN_ID`), shared so no module hardcodes the
  string. Only takes effect where a module's own `logging.pattern.console` actually renders
  `%X{traceId}`/`%X{spanId}` — see `tracing/TraceContextFilter`'s Javadoc and the note below; an
  MDC value a log pattern never references is silently invisible, which is exactly what had been
  true for `TRACE_ID` here until the `tracing/` package below landed.
- `tracing/{TraceContext,TraceContextFilter}` — distributed-request tracing via the
  [W3C Trace Context](https://www.w3.org/TR/trace-context/) `traceparent` header, gateway
  roadmap item #1 (see `gateway/ROADMAP.md`). `TraceContext` is a plain record (`traceId`, `spanId`,
  `sampled`) with `parse`/`fresh`/`withNewSpan`/`toHeaderValue` — pure parsing/formatting logic, no
  Spring dependency. `TraceContextFilter` (`OncePerRequestFilter`, `@Component`) binds a
  `TraceContext` to `MdcKeys.TRACE_ID`/`SPAN_ID` for every inbound request, rewrites the request's
  own `traceparent` header to carry this app's own span before handing it to the rest of the filter
  chain (so Gateway Server MVC's default full-header-forwarding proxy behavior carries it
  downstream with no change needed in `routing/GatewayRoutesConfig`), and logs one structured
  access-log line (method/path/status/latency) per request. Auto-registered as a servlet filter in
  **all seven** of this reactor's Spring Boot apps — a plain `@Component` implementing `Filter` is
  picked up by Spring Boot for any app whose scan reaches it, which is every one of them now (see
  the component-scan note below). **Carries `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`, not
  optional** — every one of this reactor's seven `SecurityConfig`s auto-registers its own Spring
  Security filter chain at `SecurityProperties.DEFAULT_FILTER_ORDER` (`HIGHEST_PRECEDENCE + 100`,
  very early); a filter bean with no `@Order` defaults to `LOWEST_PRECEDENCE` (last), which would
  put this filter *inside* Security's chain instead of wrapping around it — an
  unauthenticated/rejected request (401/403) would short-circuit inside Security and never reach
  this filter at all, meaning no trace-id or access-log line for exactly the requests (failed JWT
  verification, an expired token) most worth tracing. Caught via a user question while reviewing
  this feature, not by any automated check. **`+10`, not the bare extreme
  `HIGHEST_PRECEDENCE` — a second follow-up question caught that too:** two filters sharing the
  exact same `@Order` value have an undefined relative order, so a future "must run before
  Security" filter (e.g. a rate-limiter, `gateway/ROADMAP.md` item #4) needs its own distinct
  value, not a collision with this one — the `+10` leaves headroom on both sides for that. See the
  class's own Javadoc for the full mechanics, including the one real remaining limitation: it does
  **not** propagate across an `@Async` boundary (MDC is thread-local
  and `@Async`'s default executor doesn't copy it), which matters for `ai-service`'s background
  indexing pipeline — see that module's `CLAUDE.md`.
  - **`logging.pattern.console` had to be updated in all seven apps' own `application.yml`** to
    add `[traceId=%X{traceId:-}, spanId=%X{spanId:-}]` to the pattern — this is a YAML property,
    not a Java bean, so unlike everything else in this module it genuinely can't be centralized
    through `infra`'s component scan; it has to be duplicated seven times. (An earlier revision of
    this note claimed `KeycloakRealmRoleConverter` was duplicated for "the same reason" — that
    turned out to be incorrect: a `@Component` *can* be centralized through this module's component
    scan, same as `JacksonConfig` below. See `security/` below — it's centralized now.)
- `service/SlugService` (+ impl) — generic slug generation/uniqueness utility. Lives here (not
  `gateway`, not any feature module) specifically because feature modules that need it
  (`content-service`) can't depend on `gateway`, and it's generic enough that `common` (no Spring
  context) isn't the right fit either — `infra` is the "shared Spring infra any feature module can
  reach" layer.
- `service/StorageService` (+ `impl`) — MinIO upload/presigned-URL/delete, moved here from `gateway`
  since both `social-service` (`FriendMapper`/`MessagingMapper`, avatar presigned URLs) and
  `identity-service` (`UserMapper`, avatar upload) need it and neither can depend on the other —
  same reasoning as `SlugService` above, just discovered later. **Gained `uploadPublicImage`, per
  `ecommerce-service`'s request** (its own `ProductDescriptionImageService` is the first caller) —
  every other method here (`upload`/`uploadImage`/`getPresignedUrl`) only ever produces a
  *time-limited* presigned URL, correct for content re-resolved fresh on every read (a product's
  gallery, an avatar) but wrong for content that gets embedded once into a stored document and
  never revisited (an image inside a rich-text field's saved HTML) — that presigned URL would
  silently expire and break with nothing left to regenerate it. `uploadPublicImage` uploads under
  a fixed `description-images/` prefix (`StorageServiceImpl.PUBLIC_IMAGE_PREFIX`, forced
  regardless of the caller's own `keyPrefix` — a caller can't accidentally publish outside it) and
  returns a permanent, unsigned URL built directly from `endpoint`/`bucket`/`objectKey`, no
  signature involved at all. That URL only actually resolves because
  `StorageServiceImpl.ensurePublicReadPolicy` (called from the existing `@PostConstruct
  ensureBucketExists`, on every startup — not just first bucket creation, since an
  already-existing bucket from before this prefix existed would otherwise never pick up the
  grant) sets a bucket policy granting anonymous `s3:GetObject` on that one prefix via MinIO's
  `setBucketPolicy` — every other object in the bucket (product galleries, avatars) is
  unaffected and stays reachable only through `getPresignedUrl`. **Deliberately not applied to
  the product gallery itself** — `ProductImage` rows can belong to a not-yet-published/deactivated
  product, so the presigned mechanism is real (if modest) access control worth keeping; a
  permanent public URL would make a deactivated product's photos reachable by link forever, bypassing
  the fact that the product itself is hidden. This was a real, deliberate asymmetry discussed with
  the user, not an oversight — don't "fix" it by making the gallery public too without revisiting
  that discussion.
- `config/storage/{StorageConfig,StorageProperties}` — `MinioClient` bean + `app.storage.*`
  properties, moved here alongside `StorageService` for the same reason.
- **No `config/cache/{CacheNames,CacheTtlProperties}` here anymore — deleted outright, not moved,
  during `ai-service`'s standalone extraction.** These used to back `gateway`'s
  `config/cache/RedisCacheConfig` (`@EnableCaching` + a per-cache-TTL `RedisCacheManager`). While
  extracting `ai-service`, a reactor-wide grep for `@Cacheable`/`@CacheEvict`/`@CachePut` found
  **zero real usages anywhere in the whole codebase** — that half of `RedisCacheConfig` (and these
  two classes backing it) had been wired up and shipped but never actually consumed by a single
  Spring-managed cache annotation. Only `RedisCacheConfig`'s other bean, the dedicated Bucket4j
  Redis connection (`ChatRateLimiter`'s per-user rate-limit buckets), was real — that one moved to
  `ai-service`'s own `config/RedisConfig` as its sole bean; `CacheNames`/`CacheTtlProperties` had no
  real consumer left once `RedisCacheConfig` itself was deleted, so they were deleted too rather
  than moved anywhere. See `ai-service/CLAUDE.md`'s Rules section for the full finding. If a real
  `@Cacheable` use case shows up in this reactor later, rebuild the cache-name-constant +
  TTL-binding pair from scratch in whichever module first needs it — don't assume the deleted
  classes are worth resurrecting as-is.
- `service/seed/CsvSeeder<T>` — Template Method for idempotent CSV-based seeding (read/iterate/
  skip-or-insert). Moved here from `content-service` once `social-service`'s `UserBlockSeeder`
  needed it too — `content-service` and `social-service` are independent siblings that can't
  depend on each other, so a utility needed by both belongs here, same reasoning as `SlugService`.
  Used by `content-service`'s `CategorySeeder`/`TagSeeder` and `social-service`'s `UserBlockSeeder`
  — not by any `UserSeeder`: `gateway`'s own `UserSeeder` was deleted outright once `product.USER`
  was dropped (see root `CLAUDE.md`'s Database Conventions section), and `identity-service` never
  needed one — a seeded demo account has no matching Keycloak identity, so `identity.USER` only
  ever fills via real JIT-provisioning on login (see `identity-service/CLAUDE.md`). A seeder whose
  per-row shape doesn't fit one-entity-per-row
  (e.g. `content-service`'s `QuestionAnswerSeeder`, `social-service`'s `FriendGraphSeeder`/
  `DmThreadSeeder`, which each persist more than one entity per unit of work) implements its own
  `seed()` instead of extending this.
- `service/seed/Seeder` — documentation-only marker interface (`int seed()`) every seeder in the
  reactor implements, `CsvSeeder<T>` included — same "Find Implementations" purpose as this
  module's own `ApplicationEventHandler` marker for event handlers. **Not** used for polymorphic
  invocation: `gateway`'s `DataSeedingRunner` still calls each seeder by name in an explicit,
  hardcoded dependency order, since that order encodes real cross-entity requirements a
  Spring-bean-registration-order `List<Seeder>` loop wouldn't guarantee. See `Seeder`'s own Javadoc
  before "simplifying" `DataSeedingRunner` into such a loop.
- `security/{KeycloakRealmRoleConverter,KeycloakJwtAuthenticationConverter}` — shared Keycloak JWT
  conversion, moved here from seven near-identical per-service copies. `KeycloakRealmRoleConverter`
  (maps `realm_access.roles` to `ROLE_*` `GrantedAuthority`s) is used by **all seven** services —
  it had zero module-specific logic, so consolidating it was a pure win. `KeycloakJwtAuthenticationConverter`
  (builds a `CustomOAuth2User` principal straight from the verified JWT's claims, zero DB access) is
  used by the **five** services with no need to persist a local identity row — `gateway`,
  `ecommerce-service`, `task-service`, `content-service`, `ai-service`. `identity-service` and
  `social-service` keep their **own** local `KeycloakJwtAuthenticationConverter` in their own
  `security` package, since both genuinely JIT-provision a real local row (`identity.USER`/
  `social.PROFILE` respectively) as part of the conversion — that's real divergent logic, not
  duplication, so it wasn't moved here. Both of those two still delegate to this module's shared
  `KeycloakRealmRoleConverter` for the role-mapping half of the work, though. Requires
  `spring-boot-starter-oauth2-resource-server` on this module's own `pom.xml` (added for this
  purpose) — adds nothing new to any consumer's effective classpath, since every one of the seven
  services already declares that same starter itself.
  **`identity-service`'s and `social-service`'s own local converters each need an explicit,
  distinct `@Component` bean name** (`identityKeycloakJwtAuthenticationConverter`/
  `socialKeycloakJwtAuthenticationConverter`) — a real bug caught right after this consolidation
  landed: Spring's default bean-name generation uses only the simple class name, not the
  fully-qualified one, so `identity.security.KeycloakJwtAuthenticationConverter`/
  `social.security.KeycloakJwtAuthenticationConverter` would otherwise register under the identical
  default name as this shared bean (`keycloakJwtAuthenticationConverter`) the moment their own
  `@ComponentScan` reaches this package too — a `ConflictingBeanDefinitionException` at context
  startup, not a silent override (`allow-bean-definition-overriding` isn't set anywhere in this
  reactor, so Spring Boot's default `false` applies). Injection itself needed no change — every
  consumer is typed to one specific class, so autowiring-by-type is unaffected by the bean name
  either way; only the registration step needed disambiguating. Any future module that keeps its
  own local converter alongside this shared one needs the same explicit name.
- `security/KeycloakJwtConstants.java` — claim/authority string constants for the pieces Spring
  Security's own `org.springframework.security.oauth2.core.oidc.StandardClaimNames` doesn't cover:
  `TYPE_CLAIM`/`ACCESS_TOKEN_TYPE` (`typ`/`Bearer`), `REALM_ACCESS_CLAIM`/`ROLES_CLAIM`
  (`realm_access`/`roles`), `ROLE_PREFIX` (`ROLE_`). Deliberately does **not** duplicate
  `email`/`preferred_username`/`given_name`/`family_name` — those are standard OIDC claims, so
  `KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` (here and in
  `identity-service`'s/`social-service`'s own local converters) reference `StandardClaimNames`
  directly instead of a project-specific constant for something the framework already names.
  **Also deliberately has no `ROLE_ADMIN` constant** — "ADMIN" is a business-domain role name owned
  by whichever module has its own role enum (`identity-service`'s `UserRole`), not a generic
  mechanic like `ROLE_PREFIX` is, and `infra` has zero dependency on any feature module so it
  couldn't reference that enum here even if it wanted to. `identity-service`'s own converter
  composes its admin check as `KeycloakJwtConstants.ROLE_PREFIX + UserRole.ADMIN.name()` instead —
  see that class's own Javadoc. Replaced magic string literals that had been re-typed identically
  across four files (`KeycloakRealmRoleConverter`, this module's `KeycloakJwtAuthenticationConverter`,
  and `identity-service`'s/`social-service`'s local converters).
- `security/CurrentUserResolver.java` — a second de-duplication pass alongside the two converters
  above: `task-service`, `content-service`, and `ai-service` each carried an identical claims-only
  `resolveXxxUuid(Principal)` helper (reads `CustomOAuth2User.getUserUuid()`, no DB access), differing
  only in method name (`resolveOwnerUuid`/`resolveAuthorUuid`/`resolveUserUuid`, matching each
  module's own column vocabulary). Moved here as one method, `resolveUserUuid`, with each of those
  three modules' own `CurrentUserIdArgumentResolver` calling it and assigning the result to
  whatever locally-named variable it needs. **Not used by** `social-service` (resolves a real local
  `SocialProfile` numeric PK via a repository lookup — genuinely different, not duplication) or
  `identity-service` (resolves the caller via `@AuthenticationPrincipal` directly in the controller
  instead of this helper-class pattern) — both keep doing what they were already doing.
  `ecommerce-service` used to have no `@CurrentUserId` consumer at all (Epic 1 had no entity with
  an owner column) but gained one once Epic 2's cart needed to resolve the caller — see
  `security/CurrentUserIdArgumentResolver.java` immediately below for the class this method backs.
- `security/CurrentUserIdArgumentResolver.java` — a third de-duplication pass, and the Spring MVC
  plumbing layer sitting directly on top of `CurrentUserResolver` above: `content-service`,
  `task-service`, `ai-service`, and (once Epic 2 needed it) `ecommerce-service` each carried a
  byte-identical `HandlerMethodArgumentResolver` resolving `@CurrentUserId String`-annotated
  controller parameters via `CurrentUserResolver.resolveUserUuid` — differing only in Javadoc
  wording (each module's own `ownerUuid`/`authorUuid`/`userUuid` column vocabulary), same as
  `CurrentUserResolver`'s own consolidation. Each consuming module's own `WebMvcConfig`/
  `ChatMvcConfig` still registers it locally via `addArgumentResolvers` — only the resolver class
  itself moved, not the registration step (registration is inherently per-module, since it's
  `addArgumentResolvers` on that module's own `WebMvcConfigurer` bean). **Not used by**
  `social-service` (its own copy resolves an `Integer` local `SocialProfile` PK via a repository
  lookup — genuinely divergent logic, plus a STOMP-side `CurrentUserIdMessageArgumentResolver`
  counterpart this class has no equivalent for) or `identity-service`/`gateway` (neither has ever
  had a copy — see `CurrentUserResolver`'s own note above for why).
- `security/JsonAuthenticationEntryPoint.java` — a fourth de-duplication pass: `gateway` and
  `ai-service` (the only two services with an explicit `.exceptionHandling().authenticationEntryPoint(...)`
  wired up) carried a byte-identical bean returning a JSON `401` body instead of Spring Security's
  default redirect/HTML response. Zero module-specific dependency, so this one had no wrinkle at
  all — straight move, no method renaming, no caller-side logic change. The other five services
  never wired this up at all and fall back to Spring Security's own default `401` behavior for a
  resource server (no redirect either, since there's no `.oauth2Login()` anywhere in this
  reactor — but also no machine-readable `ErrorResponse` body); adopting this bean in any of them
  is a config-only change (reference `infra.security.JsonAuthenticationEntryPoint` in that
  service's own `SecurityConfig.exceptionHandling()`), not something that needs a new class.
- `config/json/JacksonConfig` — shared `ObjectMapper` customization (`JavaTimeModule`, tolerant
  deserialization, ISO-8601 dates instead of epoch-millis), moved here from `gateway`. Before this
  move, this bean only ever applied to `gateway`'s own (nonexistent, since it has no REST
  controllers) JSON serialization — every one of the six standalone services silently fell back to
  Spring Boot's un-customized default `ObjectMapper` instead, since none of them had a copy of
  their own. Fixed alongside a deeper, reactor-wide gap this move exposed: see the note below.

**Post-2026-08-16: every consumer reaches this module's beans via explicit `@Import`/
`@EnableConfigurationProperties`, not package scanning.** This is the end state of three rounds of
component-scan bugs, all stemming from the same root cause: Spring Boot's default `@ComponentScan`/
`@ConfigurationPropertiesScan` is rooted at the annotated class's own package and does not recurse
into a sibling, so none of the six standalone services' `@SpringBootApplication` classes ever
reached this module's sibling package by default (only `gateway`, whose main class sits at this
reactor's root package, picked it up "for free"). The first fix — widening
`@ComponentScan`/`@ConfigurationPropertiesScan` to `basePackages = {"<own-package>",
"com.ttg.devknowledgeplatform.infra"}` — worked, but only after two of its own false starts: a bare
`@ConfigurationPropertiesScan` (no `basePackages`) doesn't reach a sibling package any more than a
bare `@ComponentScan` does, and even once `basePackages` was right, the broad scan couldn't tell
"reachable" from "actually needed" — `config/thread/AsyncEventThreadPoolConfig` got instantiated
(and failed to construct, its constructor needing `AsyncEventThreadPoolProperties`, a bare
`@ConfigurationProperties` POJO with no `@Component` of its own, unlike
`config/storage/StorageProperties`, which does carry one) on every service whose scan reached
`infra`, regardless of whether that service dispatches any `@EventHandler` at all.

That whole bug class is why this reactor moved off broad scanning into `infra` entirely: each of
the six non-`gateway` entry-point classes now names the exact beans it uses —
`@Import({KeycloakJwtAuthenticationConverter.class, ...})` for `@Component`/`@Configuration`
classes, `@EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)` for the one bare
properties POJO. Concretely: `KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` are
imported by every service that uses claims-only auth (`task-service`, `ecommerce-service`,
`content-service`, `ai-service`) or delegates role-mapping to `KeycloakRealmRoleConverter` alone
(`identity-service`'s/`social-service`'s own local converters); `CurrentUserIdArgumentResolver` is
imported by those same four claims-only services (`ecommerce-service` only once Epic 2's cart
needed a `@CurrentUserId` consumer for the first time — Epic 1 never did); `StorageConfig`/
`StorageProperties`/`StorageServiceImpl` are imported by `identity-service`/`social-service` and
(for product-image uploads, not avatars) `ecommerce-service`; `SlugServiceImpl`
only by `ecommerce-service`/`content-service`; `JsonAuthenticationEntryPoint` only by `ai-service`
(`gateway` reaches it via its own accidental full scan); `AsyncEventThreadPoolConfig` +
`AsyncEventThreadPoolProperties` only by `ai-service`/`social-service` — the two services that
actually extend `AsyncEventHandler`. `JacksonConfig`/`TraceContextFilter` are the two truly
reactor-wide beans every service (including `gateway`, whose full scan already covers them) needs,
so every non-`gateway` entry point imports both explicitly now rather than relying on scan reach. A
service keeps a bare, own-package-scoped `@ConfigurationPropertiesScan` only for its own local
`@ConfigurationProperties` classes (`content-service`'s `InternalApiProperties`, `ai-service`'s
dozen-plus `config/*`/`config/chat/*` classes) — never extended to reach `infra` anymore. See each
service's own `CLAUDE.md`/entry-point Javadoc and `docs/CHANGELOG.md`'s `[Unreleased]` entry for the
full per-service import list and the complete three-round bug history. **Any *new* module added to
this reactor should use this same explicit `@Import`/`@EnableConfigurationProperties` shape from
the start** for whichever specific beans in this module it actually needs — don't reach for a
broad `@ComponentScan(basePackages = {..., "com.ttg.devknowledgeplatform.infra"})`, which is exactly
what caused all three rounds of bugs here.

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
