# CLAUDE.md — ecommerce-service

Module-local guidance for `ecommerce-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A study-project e-commerce vertical slice: catalog, cart/checkout, order lifecycle/inventory,
payments, and reviews/recommendations. Package root: `com.ttg.devknowledgeplatform.ecommerce.*`.
User stories for all five epics: `docs/user-stories/` (`README.md` + `01-catalog-search.md`
through `05-reviews-recommendations.md`) — read the relevant epic's file before extending that
part of the domain; it captures the scope decisions and trade-offs behind what's built.

**Epic 1 (Catalog & Search)** is fully built — all 7 user stories
(`docs/user-stories/01-catalog-search.md`) have working code: admin CRUD for
`ProductCategory`/`Product` including independent variant/image mutation, the outbox relay, and a
public browse/search/detail surface with attribute-value filtering — see below. **Epic 2 (Cart &
Checkout) is now fully built** — both the cart half (US-2.1–2.4, `service/Cart*`/`api/CartApi`)
and checkout (US-2.5–2.7, `Address`/`Order`/`OrderLine`/`CheckoutService`/`api/CheckoutApi`) — see
below.

**This module is now a standalone Spring Boot application, not part of the monolith** — see the
`project-ecommerce-service-module` memory for the full extraction history. Concretely: its own
`EcommerceServiceApplication` entry point, its own `ecommerce` Postgres schema (the same
`dev-premier` database as the monolith, not a separate database instance — per-service-per-schema,
see root `CLAUDE.md`'s Database Conventions), its own port (`8081`), and its own Liquibase
changelog/docker-compose file. `gateway` no longer has a Maven dependency on this module at all.
**`gateway`-side HTTP proxying to this service is built** — `GatewayRoutesConfig`'s
`ecommerceServiceRoutes()` bean routes `/api/v1/admin/products/**`,
`/api/v1/admin/product-categories/**`, and `/api/v1/public/products/**` to this service's
`app.services.ecommerce-service-base-url` (an earlier revision of this file said this proxy wasn't
built yet; it is, and `gui`'s admin GUI — see below — calls it through `gateway` accordingly, not
this service's own port directly).

**`EcommerceServiceApplication` carries
`@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
StorageProperties.class, StorageConfig.class, StorageServiceImpl.class})`**, naming the exact
`infra` beans this module uses — `SlugServiceImpl` for `ProductServiceImpl`/
`ProductCategoryServiceImpl`'s product/category slug generation, the Keycloak pair for this
module's own `SecurityConfig`, and the `StorageProperties`/`StorageConfig`/`StorageServiceImpl`
trio for `ProductServiceImpl.uploadImage`/`ProductMapper`'s presigned-URL resolution (see below) —
same trio `identity-service` imports for its own avatar upload — instead of widening
`@ComponentScan`/`@ConfigurationPropertiesScan` to the whole sibling `infra` package the way an
earlier revision did. That broad-scan approach took three rounds of real startup failures on
`task-service` (a sibling in the identical shape) to get right before this reactor moved to
explicit imports instead — see `infra/CLAUDE.md`'s note and `docs/CHANGELOG.md`'s `[Unreleased]`
entry for the full history. `AsyncEventThreadPoolConfig` is deliberately **not** imported here —
this module has no `@EventHandler` to dispatch.

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
issues tokens or handles a login flow. `SecurityConfig` wires `.oauth2ResourceServer(...)` with
`infra.security.KeycloakJwtAuthenticationConverter` (builds the `CustomOAuth2User` principal
directly from the verified JWT's claims — `sub` stands in for `userUuid`, since there's no
locally-generated one — no DB read/write at all) and `infra.security.KeycloakRealmRoleConverter`
(maps the token's `realm_access.roles` claim to `ROLE_*` authorities) — both shared beans now,
picked up via this module's `@ComponentScan` reaching `infra`, not a local copy anymore (see
`infra/CLAUDE.md`). This module keeps no `security/KeycloakRealmRoleConverter`/
`KeycloakJwtAuthenticationConverter` classes of its own. The old JJWT-based `JwtVerifier`/
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

Liquibase migrations: `ecommerce-service/.../database/sql/2026/0.0.2/202608040001__0.0.2__DKP-0023__add_ecommerce_catalog_tables.sql`
(Epic 1 — categories/products/variants/images/search view/outbox) and
`202608240001__0.0.2__DKP-0034__add_ecommerce_order_tables.sql` (Epic 2's checkout half —
`CUSTOMER_ORDER`/`ORDER_LINE`; see the entity Javadoc above for why the table isn't named `ORDER`),
under this module's **own** changelog tree (`database/sql/ecommerce-service.xml` +
`2026/0.0.2/*.sql`) — no longer under `gateway`'s, now that this module migrates its own schema. A
short-lived `DKP-0027__add_ecommerce_user_table.sql` existed briefly alongside `identity-service`'s
extraction (an `ecommerce.USER` table, added when this module was thought to need a persisted
`User` copy) and was removed once that assumption turned out to be wrong — see
`EcommerceServiceApplication`'s Javadoc above. Applied via the consolidated `services-liquibase`
job in `docker-compose.apps.yml` — this module has **no** standalone
`ecommerce-service-liquibase.yml` file of its own (unlike `task-service`/`social-service`, which
each kept a leftover single-service compose file from before this consolidation existed;
`gateway`'s own old `dev-knowledge-platform-liquibase.yml` was the template for those two, back
when `gateway` still had a Liquibase changelog of its own — that file and the changelog behind it
were both deleted outright once `gateway` retired its own local `User` persistence entirely, see
root `CLAUDE.md`'s Database Conventions section). The app itself still runs with
`spring.liquibase.enabled: false`.

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
- `mapper/` — `ProductCategoryMapper`, `ProductMapper`. **`ProductMapper` is an abstract class, not
  a plain interface** — it injects `infra`'s `StorageService` (`@Autowired protected`) so an
  `@AfterMapping` step (`resolveImageUrl`) can resolve each `ProductImage.storageKey` into a
  time-limited presigned `url` field on `ProductImageResponse`, the field the admin GUI actually
  renders as a thumbnail — same pattern as `identity-service`'s `UserMapper` resolving an avatar
  URL. `toImageResponse` carries `@Mapping(target = "url", ignore = true)` since `url` has no
  source field on the entity; the `@AfterMapping` step fills it in afterward. `ProductMapper` also
  maps `ProductVariant`→`ProductVariantResponse` and `ProductImage`→`ProductImageResponse` for the
  nested lists on `ProductResponse`.
- `dto/` — `ProductCategoryResponse`/`CreateProductCategoryRequest`/`UpdateProductCategoryRequest`,
  `ProductResponse`/`CreateProductRequest`/`UpdateProductRequest`,
  `ProductVariantRequest`/`ProductVariantResponse`,
  `ProductImageRequest`/`ProductImageResponse` (the latter now also carries `url`, see above).
- `api/` (+ `api/impl/`) — `ProductCategoryApi`/`Controller` (`/api/v1/admin/product-categories`:
  create/update/getById/list — no delete yet), `ProductApi`/`Controller`
  (`/api/v1/admin/products`: create-with-variants-and-images/update-basic-fields/
  `PATCH .../deactivate`/getById/paginated-list, plus **independent variant/image mutation**
  (US-1.6) — `POST`/`DELETE .../variants/{variantId}` (removing the last remaining variant is
  rejected — `Product`'s "always ≥1 variant" invariant applies here too, not just at creation) and
  `POST`/`DELETE`/`PATCH .../images/{imageId}` (add/remove/reorder; a product *can* end up with
  zero images, unlike variants), plus `POST .../images/upload` (multipart `file`+`sortOrder`) —
  the real upload path, backed by `ProductServiceImpl.uploadImage` calling `infra`'s
  `StorageService.uploadImage` (image-only content type, ≤5 MB, both enforced there) before
  persisting the `ProductImage` row. `addImage` still exists for a caller that already knows a
  `storageKey`; nothing in `gui` calls it, since the admin GUI only ever uploads real files.).
  All admin-gated automatically via **this module's own**
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
  **`ProductSearchViewMapper` is an abstract class, not a plain interface** — same reason as
  `ProductMapper`: it injects `StorageService` to resolve `ProductSearchView.primaryImageStorageKey`
  into a presigned `primaryImageUrl` on `ProductSearchResponse` via `@AfterMapping` (null-safe —
  a product with no images yet has a `null` key and stays `null`). Without this, the storefront
  grid (`gui`'s `@ecommerce/pages/shop/ShopPage.tsx`) would have nothing but an unusable private
  MinIO key to render as a thumbnail — the raw key alone was the shape `toResponse` produced before
  the storefront GUI was built against it.
- **`api/PublicProductCategoryApi`+`Controller` at `/api/v1/public/product-categories`** — a
  read-only counterpart to `ProductCategoryApi`'s admin-gated
  `/api/v1/admin/product-categories`, added once the storefront needed a category filter rail: a
  logged-out (or logged-in, non-admin) shopper can't reach the admin endpoint at all
  (`/api/v1/admin/**` requires `ROLE_ADMIN`). Delegates to the exact same
  `ProductCategoryService.list(null)`/`ProductCategoryMapper.toResponse` the admin controller
  already uses — no new service logic, just a second, unauthenticated entry point onto the same
  read. Falls under this module's existing `/api/v1/public/**` permit-all rule automatically, no
  `SecurityConfig` change needed. `gateway`'s `GatewayRoutesConfig.ecommerceServiceRoutes()` gained
  a matching `/api/v1/public/product-categories/**` route alongside its existing
  `/api/v1/public/products/**` one.

**`service/seed/`** — a starter sample catalog (developer-swag theme: apparel, drinkware,
stickers, office, accessories), gated by `app.seed.enabled` (`${APP_SEED_ENABLED:false}`, `"true"`
in `docker-compose.apps.yml`'s `ecommerce-service` block) same shape as `content-service`'s/
`social-service`'s own seeders:

- `ProductCategorySeeder` (`data/csv/product_categories.csv`, column: `name`) — extends `infra`'s
  `CsvSeeder<ProductCategory>`, same Template Method shape as `content-service`'s `CategorySeeder`.
  **Idempotency key is `name` itself, not a decoupled `seedId` column** — unlike
  `content-service`'s seeders, which persist a permanent `seedId` specifically so a category's
  display name stays freely editable across re-seeds. This is a small, fixed sample dataset, not
  long-lived content coexisting with user edits, so that decoupling wasn't judged worth a schema
  migration for a first pass; renaming a seeded category through the admin GUI would make a
  re-run treat the CSV row as new. Revisit with a real `seedId` column if this outgrows "fixed
  sample catalog."
- `ProductSeeder` (`data/csv/products.csv` joined with `data/csv/product_variants.csv` by product
  name) — **implements `infra`'s `Seeder` directly, not `CsvSeeder<T>`**, for two reasons: it needs
  every matching variant row before it can build one create command, and it routes through
  `ProductService.create`/`deactivate` rather than a bare repository save. The service-layer route
  matters for correctness, not just style — `ProductServiceImpl.create`/`deactivate` both publish
  `PRODUCT_CHANGED`, which is what gets a seeded product into `ProductSearchView` at all; inserting
  rows directly would leave every seeded product invisible to public browse/search (US-1.1/1.3/1.4)
  until something else touched them, since nothing else re-derives that CQRS read model. Attribute
  maps are encoded in one CSV cell as `key1=value1;key2=value2` (a CSV cell can't hold a nested
  map). `products.csv`'s optional `active` column demonstrates US-1.7 for one row
  ("TODO Fix This Later Notebook") for free — created normally, then immediately deactivated.
- `ProductImageSeeder` (`data/csv/product_images.csv`) — a first gallery image for five featured
  products, via `PlaceholderImageGenerator` (renders a solid-color-plus-label JPEG with
  `java.awt`/`ImageIO`, no checked-in binary assets — no real product photography exists for this
  sample catalog) wrapped in `InMemoryMultipartFile` (a minimal byte-array-backed
  `MultipartFile` — Spring's own `MockMultipartFile` is `spring-test`-scoped, not available to main
  source) and fed through `ProductService.uploadImage` — the same real upload path the admin GUI
  uses (see `gui/CLAUDE.md`'s `@ecommerce` note), and this seeder's first real exercise of it.
  Extends `CsvSeeder<Void>` (buildEntity's real work is the `uploadImage` call itself; `persist` is
  a no-op) — `alreadyExists` checks for any existing image at the target `sortOrder` on that
  product, not a permanent identifier, same fixed-sample-dataset tradeoff as `ProductCategorySeeder`.
- `EcommerceDataSeedingRunner` — `ApplicationRunner`, `@ConditionalOnProperty("app.seed.enabled")`,
  explicit order (categories → products → images, each depending on the previous existing) — same
  pattern as `content-service`'s/`social-service`'s own runners, not a `List<Seeder>` loop (see
  `infra`'s `Seeder` Javadoc for why).

**Epic 2 (Cart & Checkout) — the cart half (US-2.1–2.4) is built; checkout (US-2.5–2.7) is not
yet.** Showcases this epic's own locked pattern: **Redis as a primary store, not a cache** (see
`docs/user-stories/02-cart-checkout.md`'s "Key decisions locked for this epic") — no Postgres table
backs a cart at all.

- **This module's first use of Redis, and its first use of `@CurrentUserId`.** New
  `spring-boot-starter-data-redis` dependency; connection properties (`spring.data.redis.*`) are
  the exact same property names/env vars (`REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`/etc.)
  `ai-service`'s own Redis connection already uses (its Bucket4j rate limiter) — same shared
  instance, this module's own `cart:` key prefix keeps the two services' keys from colliding. **No
  custom `RedisConfig` class needed** — a plain autoconfigured `StringRedisTemplate` is exactly
  what a `variantId -> quantity` hash needs; `ai-service`'s own `RedisConfig` only exists because
  Bucket4j needs a raw Lettuce client with a mixed `String`/`byte[]` codec Spring's connection
  factory can't provide, which doesn't apply here. `config/web/WebMvcConfig` registers `infra`'s
  shared `CurrentUserIdArgumentResolver` — this module never needed `@CurrentUserId` in Epic 1 (no
  entity with an owner column). A local copy of the resolver class existed briefly (copied
  verbatim from `content-service`'s own) before moving to `infra.security` once it turned out to
  be byte-identical to that module's/`task-service`'s/`ai-service`'s own copies too; see
  `infra/CLAUDE.md`'s note.
- `service/{Cart,CartLine,CartService}` — plain domain records (`Cart`/`CartLine`), not JPA
  entities — there's no table to map. `CartLine.available()`/`unavailable()` factory methods carry
  whether the variant (or its parent product) still exists/is active (US-2.7's revalidation logic,
  reused by the plain cart view too, not just checkout) — an unavailable line keeps its
  `variantId`/`quantity` (so the shopper can see something changed) but no resolved `ProductVariant`.
- `service/impl/CartServiceImpl` — one Redis hash per cart, key `cart:{userUuid}`
  (`StringRedisTemplate`'s hash operations: `increment` for add — US-2.1's "increments quantity
  rather than creating a duplicate line" is `HINCRBY`, not read-then-write — `put`/`delete` for
  `setQuantity`). `cartTtl` (`@Value("${app.ecommerce.cart.ttl:P30D}")`, declared in
  `application.yml` — externalized rather than a hardcoded constant, same convention
  `OutboxRelay`'s own `app.ecommerce.outbox.relay.poll-interval` already established for a timing
  knob) is refreshed via `EXPIRE` after **every** mutation, including
  a removal that just emptied the hash (Redis already drops an emptied hash on its own, so that
  `EXPIRE` is a harmless no-op) — never on a plain read. The expiry is a deliberate, silent
  abandoned-cart cleanup (US-2.4), not a bug to guard against. `addItem` validates the variant's
  existence/active status *before* ever touching Redis (a soft check only — no stock reservation
  at add-to-cart time; that's Epic 3's concern); `setQuantity`'s removal branch (`quantity <= 0`)
  skips that validation entirely, since a shopper must always be able to remove an already-invalid
  line.
- `mapper/CartMapper` — **hand-written, not MapStruct**, unlike every other mapper in this module:
  it computes `subtotal`/`itemCount` by accumulating across only the `available` lines, which
  doesn't fit MapStruct's per-field object-mapping model at all. `toLineResponse(CartLine)` is
  extracted as its own public method (used both by `toResponse`'s own loop and by
  `CheckoutMapper`, below) so the "unavailable line → every field but `variantId`/`quantity` stays
  null" branching lives in exactly one place.
- `dto/{CartResponse,CartLineResponse,AddCartItemRequest,UpdateCartItemRequest}` — an unavailable
  line's response has only `variantId`/`quantity`/`available=false`; every other field is omitted
  (`@JsonInclude(NON_NULL)`), not zeroed out, so a GUI can't mistake "no data" for "priced at $0."
- `api/CartApi`+`Controller` at `/api/v1/cart` — **authenticated-only, no new `SecurityConfig` rule
  needed** (no `/public/**`/`/admin/**` prefix, so it falls under the existing default
  `anyRequest().authenticated()` rule). Every mutating endpoint (`addItem`/`updateItem`/`removeItem`)
  returns the freshly-resolved `CartResponse`, not just a bare `200`/the one mutated line — avoids
  a wasted second `GET` round trip from whatever GUI is showing a live cart. `gateway`'s
  `GatewayRoutesConfig` gained a new `/api/v1/cart/**` route — the app's first genuinely new
  top-level prefix (no shared-prefix disambiguation needed, unlike `/api/v1/public`/`/api/v1/admin`).

**Epic 2's checkout half (US-2.5–2.7) is built on top of the cart above.**

- `entity/Address` — a plain JPA `@Embeddable` value object (`fullName`/`line1`/`line2`/`city`/
  `state`/`postalCode`/`country`), embedded directly on `Order` rather than its own table/entity —
  this epic locked "single inline address, no saved address book" (US-2.5), so there's no
  independent lifecycle or reuse to justify a standalone entity; it's captured fresh at checkout
  and snapshotted onto whichever order used it, the same "frozen at purchase time" treatment
  applied to price below.
- `entity/Order` — **table is `CUSTOMER_ORDER`, not `ORDER`** (a reserved SQL keyword in
  PostgreSQL — same reason `social-service`'s `Group` maps to `MESSAGE_GROUP` instead of `GROUP`).
  `ownerUuid` is a plain column (the Keycloak JWT's `sub` claim), never a `@ManyToOne User` FK —
  same claims-based "Option C" shape every other module in this reactor already follows.
  `shippingAddress`/`subtotal`/`shippingFee`/`total` are all snapshotted at creation, never
  re-derived later — a flat shipping fee read from config today could change after this order was
  placed, so the order itself, not `CheckoutServiceImpl`'s config value, is the source of truth for
  what a given order actually cost. `lines` cascades `ALL`/`orphanRemoval = true` (an order and its
  lines are created together, in one transaction, and never independently).
- `enums/OrderStatus` — **only `PENDING` exists today**, deliberately: this epic's own
  responsibility ends at order creation (US-2.6); Epic 3's reservation step and Epic 4's payment
  step will each add the status values they need when they're actually built, rather than
  speculatively added now (mirrors `OutboxAggregateType`'s own "one value per epic" growth pattern).
- `entity/OrderLine` — **`productVariantId` is a plain column, deliberately not a `@ManyToOne` FK**
  onto `ProductVariant`: `ProductServiceImpl.removeVariant` can hard-delete a variant outright, and
  an already-placed order must stay valid/displayable regardless. `sku`/`productName`/`unitPrice`
  are copied from the variant/product at the moment of purchase for the same reason — this row
  must keep telling the truth about what was bought even if the catalog changes afterward. No
  stored `lineTotal` column — `unitPrice × quantity` is derived at read time by `CheckoutMapper`,
  the same way `CartMapper` already derives a cart line's total, rather than persisting a value
  that could only ever drift from its own inputs.
- `repository/OrderRepository` — plain `JpaRepository<Order, Integer>`. **No
  `findByOwnerUuid`/"list my orders" query yet** — this epic's scope is checkout (order creation)
  only; a shopper-facing order-history view belongs to a later epic, added here when that's
  actually built rather than speculatively now.
- `service/{CheckoutCommands,CheckoutPreview,CheckoutResult,CheckoutService,impl/CheckoutServiceImpl}`
  — a **two-step preview + confirm flow** on top of `CartService`, not a single call: `preview`
  revalidates the current cart (reusing `CartService.getCart`'s own existence/`active` check —
  US-2.7's revalidation is exactly that check, already built for the cart) and returns what
  confirming right now would produce; `confirm` **re-validates fresh rather than trusting a
  client-cached preview** (guards the short window between the two calls, e.g. an admin
  deactivating a variant moments before the shopper clicks "place order") and only then creates the
  `Order`. Both calls share one `requireCheckoutableCart` guard: an empty cart
  (`EcommerceErrorCode.CHECKOUT_CART_EMPTY`) or a cart where every line failed revalidation
  (`CHECKOUT_NO_VALID_ITEMS`) rejects before anything is computed. `confirm` deletes the Redis cart
  key **only after** `orderRepository.save` succeeds, never before (US-2.6) — verified in
  `CheckoutServiceImplTest` via `Mockito.inOrder`. `flatShippingFee` is externalized via
  `app.ecommerce.checkout.flat-shipping-fee` (`CHECKOUT_FLAT_SHIPPING_FEE` env var, default
  `5.00`), same `@Value`-on-a-field convention `CartServiceImpl`'s own `cartTtl` already
  established for a tunable business value — flat-rate shipping only, per this epic's locked
  decisions.
- `mapper/CheckoutMapper` — **hand-written, not MapStruct**, and injects `CartMapper` to reuse
  `toLineResponse` for every cart-line shape this mapper surfaces (a preview's lines, and any lines
  silently dropped at confirm time) rather than duplicating that branching a second time.
- `dto/{AddressRequest,AddressResponse,OrderLineResponse,CheckoutPreviewResponse,CheckoutConfirmResponse}`.
- `api/CheckoutApi`+`Controller` at `/api/v1/checkout` — **authenticated-only, same as `CartApi`,
  no new `SecurityConfig` rule needed.** `GET /preview` (US-2.6's "review... before confirming") and
  `POST /confirm` (creates the order, `201`). `gateway`'s `GatewayRoutesConfig` gained a matching
  `/api/v1/checkout/**` route alongside its existing `/api/v1/cart/**` one.
- `exception/EcommerceErrorCode` gained `CHECKOUT_CART_EMPTY`/`CHECKOUT_NO_VALID_ITEMS` (`CHECKOUT_*`
  namespace, mirroring the `CART_*` one `CartServiceImpl` already established for Epic 2).
- **`CheckoutServiceImplTest`** covers both guards (empty cart, all-lines-unavailable) for
  `preview`/`confirm` alike, the subtotal/shipping/total computation, the save-then-clear ordering,
  and that a dropped line is reported but never included in the created order's own `lines`.
- **Deliberately not built as part of this pass**: an outbox event on order creation (nothing
  consumes it until Epic 3's reservation step exists — publishing one now would just sit `PENDING`/
  fail with "no handler registered," the same failure `OutboxEventProcessor` already logs for an
  unregistered event type in its own test fixture; add `OutboxAggregateType.ORDER` + a real handler
  when Epic 3 actually needs one), and any "list/view my orders" endpoint (no query for it exists
  in `OrderRepository` either — see above).

**Not built yet** (do not assume these exist): `ProductCategory` delete, combo-accurate attribute
filtering (the current filter checks "some variant has size M" and "some variant has color Blue"
independently, not "one variant with both together" — see `ProductSearchView`'s Javadoc), and
everything for Epics 3–5 (stock reservation, order-status transitions beyond `PENDING`, payments,
reviews/recommendations). Check `docs/CHANGELOG.md`'s `[Unreleased]` entry and this file's own
freshness before assuming more exists than what's listed above. **Compiles cleanly** (verified via
a targeted `-pl ecommerce-service,gateway -am compile`/`test`; needs `JAVA_HOME` pointed at a JDK
21 install, see the `reference-jdk21-location` memory) but has **not** been run: the app hasn't
been booted against a real Postgres, so the Liquibase migration against the new `ecommerce` schema
(including the new `CUSTOMER_ORDER`/`ORDER_LINE` tables), Hibernate's `ddl-auto: validate` check
against it, the native SQL in `ProductSearchViewRepository.search`, the JWT verification path, and
the `app.storage.*` MinIO wiring (bucket auto-creation, `uploadImage`, presigned-URL resolution)
are all still unverified at runtime. A new `Dockerfile` + `docker-compose.apps.yml` (repo root)
exist to run this module in a container for the first time — see root `CLAUDE.md`'s Build & Run
Commands — which will be the first real exercise of all of the above. Container datasource config
is supplied via plain `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD`/`_DRIVER_CLASS_NAME`
environment variables in that compose file (this module still has no base-profile
`spring.datasource` block and no `application-docker.yml` — don't add one for this; env vars are
the deliberate choice here). The `app.storage.*` block, by contrast, *does* live in this module's
base `application.yml` (not a docker-only file) — same `${VAR:default}` placeholder convention
this file's own `issuer-uri` property already used, and the same shape `identity-service`'s own
`app.storage` block follows.

**`gui`'s admin GUI for this module's Epic 1 now exists** — a `@ecommerce` feature folder
(`ProductCategoryListPage`/`FormDialog`, `ProductListPage`/`FormPage` +
`ProductVariantEditor`/`Dialog`/`ProductImageGallery`), calling this service through `gateway`
(not this service's own port directly) — see `gui/CLAUDE.md`. It's the first real client of the
`uploadImage` endpoint above.

## Rules specific to this module

- **Standalone app now — this module is not part of the monolith's Maven/Spring graph.** It
  compiles against `common`+`infra` as ordinary library dependencies (shared-kernel style, no
  runtime call) but has **no Maven dependency on any other feature module, and `gateway` has none
  on it.** Never re-add a `gateway` → `ecommerce-service` Maven dependency — that would put this
  module's beans (the outbox relay, controllers) back on `gateway`'s classpath and cause both
  apps to run them simultaneously. Cross-service communication happens over HTTP (`gateway`'s
  proxy layer, already built — see above) or, for anything genuinely async, the Outbox/Inbox
  pattern discussed for Epic 4's webhooks — never a compile-time dependency again.
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
- **This module's own test suite now lives here** (`src/test/java/.../`) — the first true unit-test
  suite in this whole reactor (`social-service`'s is the only other test suite anywhere, and it's
  Testcontainers integration tests for STOMP, not mocked unit tests). Plain JUnit 5 + Mockito +
  AssertJ, `@ExtendWith(MockitoExtension.class)`, `@Nested` classes grouping tests by method —
  `ProductCategoryServiceImplTest`, `ProductServiceImplTest` (the richest — every US-1.6/1.7
  validation branch: no-variants, duplicate/conflicting SKU, inconsistent attribute keys,
  last-variant-removal rejection, sort-order conflicts, cross-product ownership checks, and the
  upload-validates-before-touching-`StorageService` ordering), `ProductSearchServiceImplTest`
  (only the service's own blank-`q`/attribute-JSON-building/unsorted-`Pageable` logic — see
  below), `ProductChangedOutboxEventHandlerTest` (the projection logic behind US-1.5/1.7),
  `outbox/OutboxEventDispatcherTest`, `outbox/OutboxEventProcessorTest` (the generic claim-
  dispatch-mark mechanism, independent of any one handler), `CheckoutServiceImplTest` (Epic 2's
  checkout half — both guards on `preview`/`confirm` alike, subtotal/shipping/total computation,
  the save-then-clear ordering via `Mockito.inOrder`, and that a dropped line is reported but never
  included in the created order's own lines). 80 tests, all passing, no Docker needed for any of
  them.
  - **`repository/ProductSearchViewRepositoryIT` is the one exception — a real Postgres
    Testcontainers integration test, not a unit test**, because US-1.3's `tsvector`/`pg_trgm`
    ranking and US-1.4's price-range/JSONB-containment filtering are native SQL
    (`ProductSearchViewRepository.search`) that a mocked repository structurally cannot verify —
    `ProductSearchServiceImplTest` only covers the thin service wrapper around that query, never
    the query's own matching behavior. This test applies the module's own real Liquibase migration
    SQL directly via JDBC in `@BeforeAll` (not Spring Boot's Liquibase autoconfiguration, which
    `@DataJpaTest` doesn't wire up) — guaranteeing it runs against the exact real DDL (extension,
    generated column, GIN indexes included), not a hand-rolled approximation. `@DataJpaTest` +
    `@AutoConfigureTestDatabase(replace = NONE)` + `@DynamicPropertySource` pointing at the
    container avoids ever booting this app's Spring Security/OAuth2 wiring (a JPA-only slice has
    no reason to reach Keycloak). **Confirmed to compile and to fail at exactly the expected
    point** (`Could not find a valid Docker environment`) — this reactor's sandboxed build
    environment has no Docker daemon reachable, so this one test is unverified beyond that; run it
    yourself (`./mvnw -pl ecommerce-service test -Dtest=ProductSearchViewRepositoryIT`) wherever
    Docker is available. Added `org.testcontainers:junit-jupiter`/`:postgresql` as new test-scope
    dependencies to this module's `pom.xml` for it (versions managed by `spring-boot-dependencies`,
    same as `social-service`'s own Testcontainers dependencies).
  - No test-vs-integration-test phase separation (e.g. Failsafe) exists anywhere in this reactor —
    `social-service`'s own Testcontainers suite runs via plain Surefire's `test` phase too, so this
    module's Docker-dependent IT follows that same existing convention rather than introducing new
    build machinery; `mvn test` on this module will fail outright wherever Docker isn't reachable,
    same as it already would for `social-service`.
- **Liquibase migrations for this module's tables live in this module's own changelog tree now**
  (`database/sql/ecommerce-service.xml` + `2026/0.0.2/*.sql`), applied via the consolidated
  `services-liquibase` job in `docker-compose.apps.yml` — this module has no standalone
  `ecommerce-service-liquibase.yml` file of its own (see the Liquibase note above) — the opposite
  of every other feature module (which still migrate via `gateway`'s changelog tree per root
  `CLAUDE.md`'s Database Conventions). Don't move future migrations back under `gateway`'s tree;
  this module owns its own schema lifecycle now.
