# CLAUDE.md — ecommerce-service

Module-local guidance for `ecommerce-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A study-project e-commerce vertical slice: catalog, cart/checkout, order lifecycle/inventory,
payments, and reviews/recommendations. Package root: `com.ttg.devknowledgeplatform.ecommerce.*`.
User stories for all five epics: `docs/user-stories/` (`README.md` + `01-catalog-search.md`
through `05-reviews-recommendations.md`) — read the relevant epic's file before extending that
part of the domain; it captures the scope decisions and trade-offs behind what's built.

Only **Epic 1 (Catalog & Search)** has code so far, but it's now fully built — all 7 user stories
(`docs/user-stories/01-catalog-search.md`) have working code: admin CRUD for
`ProductCategory`/`Product` including independent variant/image mutation, the outbox relay, and a
public browse/search/detail surface with attribute-value filtering — see below.

**This module is now a standalone Spring Boot application, not part of the monolith** — see the
`project-ecommerce-service-module` memory for the full extraction history. Concretely: its own
`EcommerceServiceApplication` entry point, its own `ecommerce` Postgres schema (the same
`dev-premier` database as the monolith, not a separate database instance — per-service-per-schema,
see root `CLAUDE.md`'s Database Conventions), its own port (`8081`), and its own Liquibase
changelog/docker-compose file. `gateway` no longer has a Maven dependency on this module at all.
**`gateway`-side HTTP proxying to this service is not built yet** — until it is, this service is
only reachable directly on its own port, not through `gateway`.

**No `@EntityScan`/`@EnableJpaRepositories` on `EcommerceServiceApplication`, and no dependency on
`common.entity.User`/`common.repository.UserRepository` anywhere in this module** — this module
doesn't persist a local `User` row at all (see `KeycloakJwtAuthenticationConverter`, below, and the
"Option C" decision in the `project-microservices-extraction-plan` memory). An earlier revision of
this module briefly added `@EntityScan`/`@EnableJpaRepositories` plus an `ecommerce.USER` table
migration (`DKP-0027`), on the assumption this module needed a real local `User` copy the way
`identity-service` does — reverted once it became clear the only real need was resolving the
current caller's identity, which the verified JWT already answers on its own. Don't re-add either
annotation or a `USER` table migration here without a concrete new reason (e.g. a future entity
that actually needs a foreign key onto a user — see the Rules section below for how to handle that
without resurrecting a full `User` copy).

**JWT verification is Keycloak-backed** (`security/`, below): this service is a pure OAuth2
resource server, verifying bearer tokens against Keycloak's JWKS
(`spring.security.oauth2.resourceserver.jwt.issuer-uri`, same realm as `gateway`) — it never
issues tokens or handles a login flow. `SecurityConfig` wires `.oauth2ResourceServer(...)` with a
`KeycloakJwtAuthenticationConverter` (builds the `CustomOAuth2User` principal directly from the
verified JWT's claims — `sub` stands in for `userUuid`, since there's no locally-generated one — no
DB read/write at all) and `KeycloakRealmRoleConverter` (maps the token's `realm_access.roles` claim
to `ROLE_*` authorities, duplicated from `gateway`'s/`identity-service`'s converter of the same
name — this module has no Maven dependency on either). The old JJWT-based `JwtVerifier`/
`JwtAuthenticationFilter` (manual RSA public-key loading via `infra`'s now-deleted `RsaKeyUtils`)
are gone — see `docs/CHANGELOG.md`'s Keycloak migration entries (Phase 3) for the full history.

`entity/`:

- `ProductCategory` — flat product taxonomy (table `PRODUCT_CATEGORY`). Deliberately not named
  `Category` and not reusing `content-service`'s `Category` — same `product` schema, unrelated
  domain (product taxonomy vs. knowledge-base taxonomy); reusing it would also violate module
  boundaries (`content-service` owns `Category`, and this module doesn't depend on
  `content-service`).
- `Product` — always has ≥1 `ProductVariant` (no variant-less products); `active` soft-deletes.
- `ProductImage` — ordered gallery (`sortOrder` unique per product); references a MinIO object key
  via `infra`'s `StorageService`, never file bytes directly.
- `ProductVariant` — SKU/price/stock per purchasable configuration; `attributes` is a free-form
  `Map<String, String>` stored as JSONB (`@JdbcTypeCode(SqlTypes.JSON)`, same approach as
  `ai-service`'s `ContentEmbedding.metadata`) rather than an EAV child table, since attribute keys
  are genuinely dynamic per category. `stockQuantity`/`reservedQuantity` implement the two-column
  reservation model Epic 3 will drive; a DB CHECK constraint (`reservedQuantity` between 0 and
  `stockQuantity`) is defense in depth against a bug in that future reservation logic.
- `ProductSearchView` — the CQRS read model for browse/search/filter; **written only by the
  outbox-driven projection relay** (`outbox/` + `ProductChangedOutboxEventHandler`, both below —
  built now). Carries `primaryImageStorageKey` (US-1.1's "first gallery image," the product's
  first `ProductImage` by `sortOrder`, denormalized at projection time — nullable, since a product
  can momentarily have zero images). Its `SEARCH_VECTOR` (`tsvector`) column is DB-generated
  (`GENERATED ALWAYS AS (to_tsvector('english', SEARCH_TEXT)) STORED`) and is deliberately **not**
  mapped as a Java field — see the entity's Javadoc and the Liquibase migration comment for why
  the two-argument `to_tsvector` form is what makes this legal in a generated column.
- `OutboxEvent` — the shared transactional-outbox table every epic will reuse (catalog read-model
  sync now; payment/webhook events and embedding re-index later). Tracks its own dispatch
  lifecycle via `status` (`enums.OutboxEventStatus`: `PENDING`/`PROCESSING`/`PROCESSED`/`FAILED`)
  plus `attemptCount`/`lastError` for diagnosing a poison message, not just a bare
  processed/unprocessed flag. `aggregateType` is `enums.OutboxAggregateType` (DB `CHECK`-backed —
  only `PRODUCT` exists today); `eventType` stays a plain string, deliberately not an enum — see
  the entity's Javadoc for why the two columns are treated differently despite looking similar (in
  short: `aggregateType`'s set barely grows, `eventType`'s grows with every future epic's business
  events, and a single Java field can only ever be backed by one enum type).

Liquibase migration: `ecommerce-service/.../database/sql/2026/0.0.1/202608040001__0.0.1__DKP-0023__add_ecommerce_catalog_tables.sql`
under this module's **own** changelog tree (`database/sql/ecommerce-service.xml` +
`2026/0.0.1/*.sql`) — no longer under `gateway`'s, now that this module migrates its own schema. A
short-lived `DKP-0027__add_ecommerce_user_table.sql` existed briefly alongside `identity-service`'s
extraction (an `ecommerce.USER` table, added when this module was thought to need a persisted
`User` copy) and was removed once that assumption turned out to be wrong — see
`EcommerceServiceApplication`'s Javadoc above. Applied via the standalone
`ecommerce-service-liquibase.yml` docker-compose file at the repo root
(`docker-compose -f ecommerce-service-liquibase.yml up`), mirroring the same one-shot-runner shape
every standalone service's own `*-liquibase.yml` file follows (`gateway`'s own
`dev-knowledge-platform-liquibase.yml` used to be the template for this, back when `gateway` still
had a Liquibase changelog of its own — that file and the changelog behind it were both deleted
outright once `gateway` retired its own local `User` persistence entirely, see root `CLAUDE.md`'s
Database Conventions section). The app itself still runs with `spring.liquibase.enabled: false`.

The minimal admin vertical slice now built on top of those entities:

- `exception/EcommerceErrorCode` — `PRODUCT_CATEGORY_*`/`PRODUCT_*`/`PRODUCT_VARIANT_*`/
  `PRODUCT_IMAGE_*` codes, implementing `common`'s `ErrorCode`, mirroring `content-service`'s
  `ContentErrorCode`.
- `repository/` — `ProductCategoryRepository`, `ProductRepository`, `ProductVariantRepository`,
  `ProductImageRepository` (plain Spring Data JPA) + `repository/spec/` —
  `ProductCategorySpecification`, `ProductSpecification` (dynamic filtering, per this repo's
  Specification-pattern convention — no JPQL strings).
- `service/` (+ `impl/`) — `ProductCategoryService`/`Impl`, `ProductService`/`Impl`. Both return
  entities, never this module's own `dto/` classes — `ProductCategoryMapper`/`ProductMapper` do
  that translation, same split as `content-service`'s `CategoryService`. `ProductCommands`
  (create/update input records, mirroring `content-service`'s `QuestionAnswerCommands`) is how
  `api/impl` passes multi-field input (including nested variant/image lists) into `ProductService`
  without threading REST DTOs into the service layer.
  - `ProductServiceImpl.create` enforces: at least one variant, no duplicate SKU/sort-order
    *within the same request*, no SKU conflicting with an existing variant, and every variant
    sharing the same attribute-key set (US-1.6) — all as friendly `ApiException`s raised before
    any DB write, not caught constraint violations.
  - After persisting each `ProductVariant`/`ProductImage` via its own repository, the code also
    appends it to `savedProduct.getVariants()`/`getImages()` explicitly, rather than relying on a
    later lazy-load to pick up siblings created earlier in the same transaction — a brand-new
    parent's collection can already be treated as "initialized empty" by Hibernate at persist
    time, so a lazy re-fetch isn't guaranteed to see rows inserted moments later in the same unit
    of work. Keep this pattern for any future bidirectional create-with-children flow in this
    module.
- `mapper/` — `ProductCategoryMapper`, `ProductMapper` (MapStruct; `ProductMapper` also maps
  `ProductVariant`→`ProductVariantResponse` and `ProductImage`→`ProductImageResponse` for the
  nested lists on `ProductResponse`).
- `dto/` — `ProductCategoryResponse`/`CreateProductCategoryRequest`/`UpdateProductCategoryRequest`,
  `ProductResponse`/`CreateProductRequest`/`UpdateProductRequest`,
  `ProductVariantRequest`/`ProductVariantResponse`, `ProductImageRequest`/`ProductImageResponse`.
- `api/` (+ `api/impl/`) — `ProductCategoryApi`/`Controller` (`/api/v1/admin/product-categories`:
  create/update/getById/list — no delete yet), `ProductApi`/`Controller`
  (`/api/v1/admin/products`: create-with-variants-and-images/update-basic-fields/
  `PATCH .../deactivate`/getById/paginated-list, plus **independent variant/image mutation**
  (US-1.6) — `POST`/`DELETE .../variants/{variantId}` (removing the last remaining variant is
  rejected — `Product`'s "always ≥1 variant" invariant applies here too, not just at creation) and
  `POST`/`DELETE`/`PATCH .../images/{imageId}` (add/remove/reorder; a product *can* end up with
  zero images, unlike variants)). All admin-gated automatically via **this module's own**
  `security/SecurityConfig` URL-pattern rule for `/api/v1/admin/**` — no method-level
  `@PreAuthorize` needed, same shape as `gateway`'s equivalent rule, but a separate filter chain
  now that this app runs standalone.

The outbox relay (US-1.5) — generic mechanism in `outbox/`, the one concrete handler alongside
the rest of the catalog service code:

- `outbox/OutboxEventHandler` — Strategy interface (`eventType()` + `handle(OutboxEvent)`); a new
  handler for a new event type is just a new `@Component` implementing this, no registry edits.
- `outbox/OutboxEventDispatcher` — builds a `Map<String, OutboxEventHandler>` from every handler
  bean Spring finds, keyed by `eventType()`.
- `outbox/OutboxEventProcessor` — claims one event (`OutboxEventRepository.claim`, an atomic
  conditional `UPDATE ... WHERE status = 'PENDING'`) and dispatches it, `@Transactional`. **Kept
  as its own bean, not a second method on `OutboxRelay`** — Spring's `@Transactional` is
  proxy-based, so a bean calling its own `@Transactional` method via `this.foo()` bypasses the
  proxy and silently runs with no transaction (a classic self-invocation pitfall). Splitting the
  scheduled entry point and the transactional processor into separate beans sidesteps it entirely;
  don't merge them back for "simplicity."
- `outbox/OutboxRelay` — the `@Scheduled` poller (`app.ecommerce.outbox.relay.poll-interval`,
  default `PT5S`, following `ai-service`'s `CorpusStatisticsServiceImpl` convention for
  configurable `@Scheduled` intervals). `@EnableScheduling` is declared on this module's own
  `EcommerceServiceApplication` — back when this module ran inside the monolith it could rely on
  `ai-service`'s `AiServiceConfig` enabling scheduling app-wide, but not anymore, now that this is
  a standalone app that doesn't include `ai-service` at all.
- `service/impl/ProductChangedOutboxEventHandler` — the `PRODUCT_CHANGED` handler. Re-derives the
  whole `ProductSearchView` row from current `Product`/`ProductVariant` state (never trusts
  anything in the event payload beyond the id). **Deactivating a product deletes its
  `ProductSearchView` row rather than updating it** — `ProductSearchView` has no `active` column
  of its own, and US-1.7 says a deactivated product must "disappear from browse/search," so a
  missing row *is* the not-visible state. `ProductServiceImpl` publishes `PRODUCT_CHANGED` (via a
  private `publishProductChanged` helper) after every create/update/deactivate, in the same
  transaction as the underlying `Product` write.

The public browse/search/detail surface (US-1.1, US-1.2, US-1.3, US-1.4):

- `repository/ProductSearchViewRepository.search` — one native query (`nativeQuery = true`)
  handling every filter combination via the `(:param IS NULL OR ...)` idiom, rather than building
  SQL dynamically. Combines `tsvector` exact-token matching (`@@`) with `pg_trgm` similarity
  (typo tolerance `tsvector` alone misses) for the keyword filter; price filter is an overlap
  check (`MAX_PRICE >= :minPrice AND MIN_PRICE <= :maxPrice`), not exact match. Attribute-value
  filtering (US-1.4, e.g. `?size=M&color=Blue`) is one JSONB containment check
  (`AVAILABLE_ATTRIBUTES @> CAST(:attributesFilter AS JSONB)`) against **one** combined JSON
  object built in `ProductSearchServiceImpl` from every non-reserved query param — Postgres's
  `@>` on a JSON object recursively ANDs across every key on the right-hand side, so this one
  parameter correctly implements "AND across attribute keys" without dynamic SQL construction per
  filter. The `SELECT` list is spelled out explicitly, never `SELECT *` — `SEARCH_VECTOR` is a
  DB-only generated column with no Java field, and an unmapped column returned by `SELECT *` would
  break the entity-result mapping. **The `FROM`/`countQuery` schema prefix was briefly stale after
  the extraction** (`product.PRODUCT_SEARCH_VIEW` instead of `ecommerce.PRODUCT_SEARCH_VIEW`) —
  the entity's `@Table(schema=...)` got updated everywhere the schema rename touched Java
  annotations, but this native query hardcodes the schema as a plain SQL string, which that
  find/replace never saw. Caught and fixed; a reminder that any *other* native/raw-SQL schema
  references in this module need checking by hand if the schema ever moves again.
- `service/ProductSearchService`(`Impl`) — resolves the trigram threshold constant, blanks out an
  empty `q`, builds the combined attribute-filter JSON (via the injected `ObjectMapper`, not
  manual string concatenation — correctness/escaping, not a SQL-injection concern since it's
  bound as a parameter either way), and calls the repository with an **unsorted** `PageRequest` —
  the native query already bakes in its own `ORDER BY`, so a `Sort` here would make Spring Data
  append a second, conflicting one.
- `mapper/ProductSearchViewMapper`, `dto/ProductSearchResponse`,
  `api/ProductSearchApi`+`Controller` at `/api/v1/public/products` (list/search;
  `GET /{slug}` for full detail — US-1.2, backed by `ProductService.getActiveBySlug`, which
  treats a deactivated product's slug the same as a nonexistent one, so a public caller can't tell
  the difference) — under this module's own `security/SecurityConfig` `/api/v1/public/**`
  permit-all rule, no auth required, matching the public nature of browsing.

**Not built yet** (do not assume these exist): the `gateway`-side HTTP proxy to this service,
`ProductCategory` delete, combo-accurate attribute filtering (the current filter checks "some
variant has size M" and "some variant has color Blue" independently, not "one variant with both
together" — see `ProductSearchView`'s Javadoc), and everything for Epics 2–5. Check
`docs/CHANGELOG.md`'s `[Unreleased]` entry and this file's own freshness before assuming more
exists than what's listed above. **Compiles cleanly** (full reactor including the extraction
changes, `./mvnw -pl gateway -am compile`, needs `JAVA_HOME` pointed at a JDK 21 install, see the
`reference-jdk21-location` memory) but has **not** been run: the app hasn't been booted against a
real Postgres, so the Liquibase migration against the new `ecommerce` schema, Hibernate's
`ddl-auto: validate` check against it, the native SQL in `ProductSearchViewRepository.search`,
and the JWT verification path are all still unverified at runtime. A new `Dockerfile` +
`dev-knowledge-platform-apps-docker-compose.yml` (repo root) exist to run this module in a
container for the first time — see root `CLAUDE.md`'s Build & Run Commands — which will be the
first real exercise of all of the above. Container datasource config is supplied via plain
`SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`/`_DRIVER_CLASS_NAME` environment variables in that
compose file (this module still has no base-profile `spring.datasource` block and no
`application-docker.yml` — don't add one for this; env vars are the deliberate choice here).

## Rules specific to this module

- **Standalone app now — this module is not part of the monolith's Maven/Spring graph.** It
  compiles against `common`+`infra` as ordinary library dependencies (shared-kernel style, no
  runtime call) but has **no Maven dependency on any other feature module, and `gateway` has none
  on it.** Never re-add a `gateway` → `ecommerce-service` Maven dependency — that would put this
  module's beans (the outbox relay, controllers) back on `gateway`'s classpath and cause both
  apps to run them simultaneously. Cross-service communication happens over HTTP (once the
  `gateway` proxy layer is built) or, for anything genuinely async, the Outbox/Inbox pattern
  discussed for Epic 4's webhooks — never a compile-time dependency again.
- **Epic 5's originally-planned `ecommerce-service` → `ai-service` dependency needs rethinking**
  now that both modules are standalone: `ai-service` is a separate deployable now too (its own `ai`
  schema, own port `8086` — see root `CLAUDE.md`), so a plain Maven dependency (the original plan,
  see `docs/user-stories/05-reviews-recommendations.md`) was never available and is even less
  relevant now — that pattern only ever worked between modules that were *both* still inside the
  same deployable, which stopped being true for `ai-service` once it was extracted. When Epic 5 is
  actually built, it will need a real network call — likely through `gateway` once a general-purpose
  proxy layer exists, or the same `ContentServiceClient`-style `RestClient` pattern `ai-service`
  itself already uses to reach `content-service` — never a `pom.xml` entry. Don't add the dependency
  as originally planned without revisiting this.
- **Table names no longer risk colliding with anything** — this module has its own `ecommerce`
  schema now, not the monolith's shared `product` schema. `ProductCategory`/`PRODUCT_CATEGORY`'s
  name was originally chosen to avoid colliding with `content-service`'s `Category`/`CATEGORY`
  in that shared schema; the naming stayed after the extraction (the underlying domain distinction
  is still real) even though the schema-collision risk that motivated it no longer applies.
- **JSONB over EAV for dynamic key/value data** (`ProductVariant.attributes`,
  `ProductSearchView.availableAttributes`, `OutboxEvent.payload`) — established precedent in this
  module now; don't introduce an EAV child table for a similar need without a concrete reason the
  JSONB approach doesn't fit.
- **`ProductSearchView` is read-only from every code path except `ProductChangedOutboxEventHandler`
  (and, when future epics register their own handlers, whichever of those touch it).** No other
  service method should ever `save()`/`update()`/`delete()` this entity directly — if a new
  feature seems to need that, it's a sign the projection/outbox design needs revisiting, not a
  green light to bypass it. Any code that changes `Product`/`ProductVariant` must publish
  `PRODUCT_CHANGED` afterward (see `ProductServiceImpl.publishProductChanged`) — the read model
  only ever catches up because something told the relay to re-derive it.
- **`OutboxEvent.aggregateType` is an enum (`OutboxAggregateType`) with a DB `CHECK`;
  `OutboxEvent.eventType` stays a plain string, permanently, not just for now.** A new aggregate
  root (Epic 3's `Order`, Epic 4's `Payment`, etc.) gets a new `OutboxAggregateType` case plus a
  migration widening `CKC_OUTBOX_EVENT_AGGREGATE_TYPE`. A new business event (e.g.
  `ORDER_CREATED`, `PAYMENT_SUCCEEDED`) is just a new string value for `eventType` — no enum edit,
  no migration. Don't "fix the inconsistency" by enum-ifying `eventType` later; the asymmetry is
  deliberate (see `OutboxEvent`'s Javadoc).
- **Every `OutboxEventHandler` exposes its own `public static final String EVENT_TYPE` constant**
  (see `ProductChangedOutboxEventHandler.EVENT_TYPE`), and whatever publishes that event
  references the handler's constant instead of retyping the literal — e.g.
  `ProductServiceImpl.publishProductChanged` sets `event.setEventType(ProductChangedOutboxEventHandler.EVENT_TYPE)`,
  never a bare `"PRODUCT_CHANGED"` string. This is what gives `eventType` typo-safety without a
  shared enum: each handler is its own single source of truth for its own event type, so a future
  epic's handler needs no coordination with this one's.
- **Every `OutboxEventHandler` with a non-trivial payload also declares its own nested
  `Payload` record** (see `ProductChangedOutboxEventHandler.Payload`, with `toMap()`/`from(event)`
  for the producer/consumer sides) rather than a shared payload DTO — same reasoning as
  `EVENT_TYPE`: different event types carry different fields, so one shared payload class would
  just recreate the same "every future epic edits the same file" problem. Producers should always
  build the map via the handler's `Payload.toMap()`, never by hand, so the map's keys live in
  exactly one place.
- **Liquibase migrations for this module's tables live in this module's own changelog tree now**
  (`database/sql/ecommerce-service.xml` + `2026/0.0.1/*.sql`), applied via the standalone
  `ecommerce-service-liquibase.yml` docker-compose file at the repo root — the opposite of every
  other feature module (which still migrate via `gateway`'s changelog tree per root `CLAUDE.md`'s
  Database Conventions). Don't move future migrations back under `gateway`'s tree; this module
  owns its own schema lifecycle now.
