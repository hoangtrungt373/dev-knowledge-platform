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
Checkout) is fully built** — both the cart half (US-2.1–2.4, `service/Cart*`/`api/CartApi`)
and checkout (US-2.5–2.7, `Address`/`Order`/`OrderLine`/`CheckoutService`/`api/CheckoutApi`) — see
below. **Epic 3 (Order Lifecycle & Inventory,
`docs/user-stories/03-order-lifecycle-inventory.md`) is in progress — Phase 1 (the data-model
foundation) is built; US-3.1–3.8's actual service/API logic is not yet** — see the dedicated Epic 3
section below.

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

- `ProductCategory` — product taxonomy (table `PRODUCT_CATEGORY`). Deliberately not named
  `Category` and not reusing `content-service`'s `Category` — same `product` schema, unrelated
  domain (product taxonomy vs. knowledge-base taxonomy); reusing it would also violate module
  boundaries (`content-service` owns `Category`, and this module doesn't depend on
  `content-service`). **Now supports an optional parent/child hierarchy** (per request, migration
  `DKP-0037`) — a self-referential `parent`/`children` adjacency list (`@ManyToOne parent` +
  `@OneToMany(mappedBy = "parent") children`, both excluded from `equals`/`hashCode`/`toString`),
  mirroring `content-service`'s own `Category` shape exactly, chosen over a materialized-path or
  closure-table representation for the same reason `content-service` picked it: this taxonomy is
  shallow (a handful of levels) and read-light enough that the extra `N`-deep parent walk a cycle
  check does (`ProductCategoryServiceImpl.validateParentAssignment`) is cheap, so the simpler
  adjacency list beats a representation that pays a write-time cost (materialized path) or a
  schema/query-complexity cost (closure table) for read patterns this taxonomy doesn't have. The
  entity's own Javadoc used to say "Flat by design" — no longer true, and callers should not
  assume the old ceiling.
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
(Epic 1 — categories/products/variants/images/search view/outbox),
`202608240001__0.0.2__DKP-0034__add_ecommerce_order_tables.sql` (Epic 2's checkout half —
`CUSTOMER_ORDER`/`ORDER_LINE`; see the entity Javadoc above for why the table isn't named `ORDER`),
`202608300001__0.0.2__DKP-0035__add_order_reservation_and_status_history.sql` (Epic 3 Phase 1
— see the dedicated Epic 3 section below), and `202608310001__0.0.2__DKP-0037__add_product_category_parent_id.sql`
(adds `PRODUCT_CATEGORY`'s nullable, self-referential `PARENT_CATEGORY_ID` FK + index — see the
entity note above), under this module's **own** changelog tree (`database/sql/ecommerce-service.xml` +
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
  `ContentErrorCode`. Gained `PRODUCT_CATEGORY_CYCLIC_PARENT` (`PRODUCT_CATEGORY_004`) alongside
  the hierarchy support below.
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
  - **`ProductCategoryService.create`/`.update` are now `parentId`-aware** (per request) and the
    interface gained `listTree()` — returns `List<ProductCategoryTreeNode>` (a new
    `service/ProductCategoryTreeNode` record, `category` + resolved `children`, byte-identical
    shape to `content-service`'s own `CategoryTreeNode`). `ProductCategoryServiceImpl` mirrors
    `content-service`'s `CategoryServiceImpl` exactly for the hierarchy mechanics:
    `resolveParent`/`validateParentAssignment` (rejects self-parent and any assignment where
    walking `newParent.getParent()` upward reaches the category being edited — a cycle guard, not
    a depth limit) and `listTree`'s two-pass build (index every row into a `Map<Integer,
    ProductCategoryTreeNode>` by id, then re-walk assigning each into its parent's `children` or,
    for a root/orphaned row, into the result list — an orphaned child, whose parent id doesn't
    resolve to a node in the map, is defensively treated as a root rather than silently dropped).
    Chosen over a materialized-path/closure-table representation for the same reason
    `content-service` picked adjacency-list originally — see the entity note above.
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
  - **`ProductServiceImpl.create`/`.update` now sanitize `Product.description` before persisting,
    Phase 1 of a request to give products a real, rich "article" description instead of plain
    text.** New `service/ProductDescriptionSanitizer` (a plain concrete `@Component`, not an
    interface+impl split — a single-implementation utility with nothing to swap, same shape as
    `mapper/CartMapper`/`CheckoutMapper` rather than `ProductCategoryService`/`Impl`) wraps the
    OWASP Java HTML Sanitizer, composing its own `Sanitizers.BLOCKS.and(FORMATTING).and(LINKS)
    .and(IMAGES).and(TABLES)` presets rather than a hand-written allowlist. **Chosen over Markdown**
    (the format `content-service`'s `Article`/`QuestionAnswer` use) **after discussion**: a product
    description's audience is an end shopper, not a developer, and needs layout Markdown can't
    produce (inline images beside text, spec callouts) — sanitized HTML authored through a WYSIWYG
    editor is what real storefronts (Shopify et al.) actually do. Sanitized **on write, not on
    read** — `Product.description` always holds already-safe HTML, so every reader (the public
    product API, this module's own seeder, a future admin preview) can render it directly without
    re-sanitizing. Deliberately **silent stripping, not a rejection** — a WYSIWYG editor/a paste
    from Word routinely produces markup outside the allowlist, and erroring on every stray tag
    would make the editor unusable. New Maven dependency
    `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer`, version-managed in the
    root `pom.xml`'s `dependencyManagement` (this reactor's convention for every third-party
    dependency, even single-module ones — see `commons-csv`/`minio` for precedent) but declared
    **only in this module's own `pom.xml`**, not `infra` — `infra/CLAUDE.md`'s own rule for
    promoting a utility there is "needed by two feature modules that can't depend on each other,"
    and `content-service` doesn't store HTML, so there's no second consumer yet.
    `CreateProductRequest`/`UpdateProductRequest.description` both gained `@Size(max = 50_000)` as
    a sane abuse bound (no schema change — `Product.description` was already `TEXT`, unlimited).
    New `ProductDescriptionSanitizerTest` (11 cases: null passthrough, allowed
    blocks/formatting/tables/images preserved, `<script>`/event-handler attributes/`javascript:`
    hrefs stripped, plain text passes through unchanged; a realistic "pasted from Google Docs"
    case — verified against the sanitizer's real output rather than assumed, added while explaining
    this class; documents that Docs' `style`-encoded bold is lost entirely since `STYLES` isn't in
    this policy, and that stripping Docs' own `<b style="font-weight:normal;">` cancel-wrapper
    while keeping the bare `<b>` ironically makes the result render as "everything bold" — a real
    fidelity-vs-safety trade-off, not a bug; and 2 more cases — `<hr>` is dropped entirely and
    `<pre><code>` degrades to a bare inline `<code>`, vs. headings/`<u>`/`<blockquote>` surviving
    untouched — that became the reference `gui`'s `ProductDescriptionEditor.tsx` (below) was
    designed against, so its toolbar never offers a formatting option the backend would silently
    strip) plus 2 new `ProductServiceImplTest` cases (`Create`/`Update` each assert sanitization
    runs before persisting) — 163 unit tests total (up from 150), verified via a real `mvn test`
    run (JDK 21) in this session.
    **`ProductServiceImplTest`'s `@InjectMocks` needed a `@Spy`, not a `@Mock`, for this
    collaborator** — the real sanitizer runs against every test's actual input (so assertions see
    genuinely sanitized output, not a mocked passthrough) without every existing test needing its
    own new stubbing.
    **Phase 2 (the `gui` WYSIWYG editor) is now built too** — see `gui/CLAUDE.md`'s
    `ProductDescriptionEditor.tsx` note. **Phase 3 (the storefront-facing "article" render) is
    now built too** — `ProductDetailPage.tsx`'s "Product Details" section, with a client-side
    DOMPurify pass as defense in depth on top of this class's own on-write sanitization — see
    `gui/CLAUDE.md`. This closes out all 3 phases of the accepted plan for this feature.
  - **Post-Phase-3 follow-up: real image *upload* for the description editor (was URL-paste-only),
    per request.** New `service/ProductDescriptionImageService`/`Impl` — deliberately **not** a
    method on `ProductService`, since it touches zero `Product` state (no `productId`, no
    `ProductImage` row; the description is a plain HTML string, and an image referenced inside it
    is an asset of the content, not a gallery photo). Delegates to `infra`'s new
    `StorageService.uploadPublicImage` (see `infra/CLAUDE.md`) rather than the presigned
    `uploadImage`/`getPresignedUrl` pair `ProductServiceImpl`'s gallery upload uses — a presigned
    URL baked once into stored `description` HTML would silently expire, since (unlike a gallery
    image, re-resolved fresh by `ProductMapper` on every read) nothing ever re-processes
    `description` to mint a new one; this was worked through in detail with the user before
    building it, including *why* the product gallery itself deliberately stays presigned-only
    (see `infra/CLAUDE.md`'s `StorageService` note for the full asymmetry). New
    `api/ProductDescriptionImageApi`+`Controller` at
    `POST /api/v1/admin/products/description-images/upload` — a separate resource from
    `ProductApi`'s own gallery upload, nested under `/api/v1/admin/products` purely so `gateway`'s
    existing `/api/v1/admin/products/**` route already covers it, not because it's part of that
    resource. **Needs no existing product — works in create mode too**, unlike the gallery upload
    (which needs a real `productId` first). New `dto/ProductDescriptionImageResponse` (`url` only).
    New `ProductDescriptionImageServiceImplTest`. A further follow-up (answering "does copy-paste
    work?") added `ProductDescriptionSanitizerTest.stripsDataUriImageSourcesEntirely` — confirms a
    `data:` URI `<img src>` is stripped entirely (only `alt` survives), since neither `LINKS` nor
    `IMAGES` allows that protocol; documents that even if a pasted image somehow reached the
    editor as a base64 data URI, it could never have survived being saved anyway. 165 unit tests
    total (up from 163).
- `mapper/` — `ProductCategoryMapper` (gained `toTreeNodeResponse(ProductCategoryTreeNode)` and a
  `parent.id -> parentId` mapping on `toResponse`, mirroring `content-service`'s `CategoryMapper`),
  `ProductMapper`. **`ProductMapper` is an abstract class, not
  a plain interface** — it injects `infra`'s `StorageService` (`@Autowired protected`) so an
  `@AfterMapping` step (`resolveImageUrl`) can resolve each `ProductImage.storageKey` into a
  time-limited presigned `url` field on `ProductImageResponse`, the field the admin GUI actually
  renders as a thumbnail — same pattern as `identity-service`'s `UserMapper` resolving an avatar
  URL. `toImageResponse` carries `@Mapping(target = "url", ignore = true)` since `url` has no
  source field on the entity; the `@AfterMapping` step fills it in afterward. `ProductMapper` also
  maps `ProductVariant`→`ProductVariantResponse` and `ProductImage`→`ProductImageResponse` for the
  nested lists on `ProductResponse`.
- `dto/` — `ProductCategoryResponse`/`CreateProductCategoryRequest`/`UpdateProductCategoryRequest`
  (all three now carry/accept `parentId`) plus `ProductCategoryTreeNodeResponse` (`id`/`name`/
  `slug`/`parentId`/nested `children`, mirroring `content-service`'s `CategoryTreeNodeResponse`),
  `ProductResponse`/`CreateProductRequest`/`UpdateProductRequest`,
  `ProductVariantRequest`/`ProductVariantResponse`,
  `ProductImageRequest`/`ProductImageResponse` (the latter now also carries `url`, see above).
- `api/` (+ `api/impl/`) — `ProductCategoryApi`/`Controller` (`/api/v1/admin/product-categories`:
  create/update (both `parentId`-aware now)/getById/list/`GET /tree` (roots with nested `children`,
  sorted by name at each level, mirroring `content-service`'s `CategoryApi#tree`) — still no
  delete), `ProductApi`/`Controller`
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

- `ProductCategorySeeder` (`data/csv/product_categories.csv`, columns: `name`, `parentName`) —
  extends `infra`'s `CsvSeeder<ProductCategory>`, same Template Method shape as `content-service`'s
  `CategorySeeder`. **Idempotency key — and parent reference — is `name` itself, not a decoupled
  `seedId` column** — unlike `content-service`'s seeders, which persist a permanent `seedId`
  specifically so a category's display name stays freely editable across re-seeds. This is a small,
  fixed sample dataset, not long-lived content coexisting with user edits, so that decoupling
  wasn't judged worth a schema migration, including when hierarchy support (below) was added;
  renaming a seeded category through the admin GUI would make a re-run treat the CSV row as new
  (and orphan any child row's `parentName` reference). Revisit with a real `seedId` column if this
  outgrows "fixed sample catalog." **`parentName` (blank for a root category) added once
  `ProductCategory` gained hierarchy support, per request** — resolved via
  `productCategoryRepository.findByNameIgnoreCase`, same "parent rows must appear before their
  children" ordering requirement as `content-service`'s `CategorySeeder` (`CsvSeeder` persists each
  row in file order before moving to the next), just keyed by name instead of `seedId`. The seed
  data itself now nests the 5 original leaf categories under 2 new root categories — `Wearables`
  (→ `Apparel`) and `Desk & Drinkware` (→ `Drinkware`/`Stickers`/`Office`/`Accessories`) — with
  `products.csv` untouched, since products still reference the same 5 leaf category names.
- `ProductAttributeSeeder` (`data/csv/product_attributes.csv`, columns: `name`, `values` —
  semicolon-joined, same compact inline convention as `product_variants.csv`'s own `attributes`
  cell) — the "Option B" global attribute registry's sample data, per request. Extends
  `CsvSeeder<ProductAttribute>`, mirrors `ProductTagSeeder`'s "bypass the service, no outbox
  concern" shape exactly; each value's list position in the CSV cell becomes its
  `displayOrder` (no caller-supplied order number, matching `ProductAttributeServiceImpl
  .applyValues`'s own convention). Seven sample attributes: `size` (`XS;S;M;L;XL;XXL`, an
  apparel-scale vocabulary), `color` (`Black;White;Navy;Silver;Gray;Kraft;Natural` — deliberately
  the *union* of every color value already used anywhere in `product_variants.csv`, since this one
  attribute is reused across four categories below), `capacity` (`12oz;16oz`), `packSize`
  (`5-pack;10-pack`), and — added once category-schema coverage was extended to `Office`/
  `Accessories` (see `ProductCategoryAttributeSeeder`'s own note below) — three narrow,
  effectively single-product attributes: `matSize` (`Small;Large`, Segfault Desk Mat's own
  dimension), `sleeveSize` (`13in;15in`, Merge Conflict Laptop Sleeve's own size), `sockSize`
  (`S-M;L-XL`, Compile Time Socks' own size). These three exist specifically because
  `ProductAttribute.name` is matched *literally* against a variant's map key — `Office`'s Desk Mat,
  `Accessories`' Laptop Sleeve, and `Accessories`' own Socks each use a "size"-shaped concept with
  a genuinely different, mutually-incompatible vocabulary, so none of them could reuse the global
  `size` attribute (or each other) without silently colliding; a distinct attribute name per
  product's own size concept was the way to close that gap without renaming any real product data
  into a shared vocabulary that would misrepresent it (e.g. calling a 13-inch sleeve "S" reads as
  simply wrong, not just inconvenient).
- `ProductCategoryAttributeSeeder` (`data/csv/product_category_attributes.csv`, columns:
  `categoryName`, `attributeName`, `required`) — assigns the attributes above to categories.
  **Implements `infra`'s `Seeder` directly, not `CsvSeeder<T>`**, for the same reason `ProductSeeder`
  does: one unit of work is a whole category's *complete* schema (clear-and-rebuild, list position
  → `displayOrder`), not one CSV row, so every row for one category must be gathered (grouped by
  `categoryName`) before anything is persisted. Bypasses `ProductCategoryService` and persists via
  `ProductCategory.categoryAttributes`' own cascade (mirrors `ProductCategorySeeder`); idempotency
  key is the category's name — a category that already has any assignment is skipped whole.
  **All 5 sample categories now get a schema, per a follow-up request — `Office`/`Accessories`
  were originally left deliberately unassigned (free-form) because their existing sample variants
  didn't share one clean vocabulary for a key their products happened to collide on** (`Office`'s
  Segfault Desk Mat used `size=Small`/`Large` while `Apparel`'s own `size` key means `XS`-`XXL` —
  the exact "size means different things in Clothes vs. elsewhere" tension the original design
  discussion raised). Closed by introducing the three narrow attributes above instead of
  retrofitting `Office`/`Accessories` into the existing `size` vocabulary: `Apparel` = `size`
  (required) + `color` (optional); `Drinkware` = `color` (required) + `capacity` (optional);
  `Stickers` = `packSize` (optional, since one of its two products has no attributes at all);
  `Office` = `color` (optional, TODO Fix This Later Notebook) + `matSize` (optional, Segfault Desk
  Mat); `Accessories` = `color` (optional, Merge Conflict Laptop Sleeve/I Love Semicolons Tote
  Bag) + `sleeveSize` (optional, the Laptop Sleeve) + `sockSize` (optional, Compile Time Socks) —
  every attribute on `Office`/`Accessories` is optional, deliberately, since none of them applies
  to *every* product in that category (unlike `Apparel`'s required `size`, which every apparel
  product actually has). **Every value in `product_variants.csv` for all five categories was
  hand-verified against the schema above before this seed data was written** (a small throwaway
  script replaying `ProductServiceImpl.validateAttributesAgainstCategory`'s exact rules — plus the
  separate US-1.6 "every variant of one product shares the same key set" check — against the real
  CSV data, confirming zero violations) — this matters because, unlike every other seeder here, a
  mistake in this one's data doesn't fail loudly in this seeder itself; it fails later, inside
  `ProductSeeder`, the first time it tries to create a variant that violates the schema this
  seeder just assigned.
  **Bug fix: `seed()` needed its own `@Transactional`** — first surfaced as a real
  `org.hibernate.LazyInitializationException: ... categoryAttributes ... no Session` when this
  seeder actually ran. Unlike `CsvSeeder`'s per-row `repository.save()` calls (each its own
  short-lived transaction via `SimpleJpaRepository`), this method fetches a `ProductCategory` via
  `findByNameIgnoreCase`, then reads its lazy `categoryAttributes` collection
  (`category.getCategoryAttributes().isEmpty()`), then saves it back — all in one unit of work.
  Without a surrounding transaction, the Hibernate session `findByNameIgnoreCase` opened closes the
  instant that call returns, so the very next line's lazy-collection read has no session left to
  initialize against. `seed()` is now `@Transactional`, mirroring `ProductCategoryServiceImpl`'s own
  class-level annotation — keeps one session open across the fetch/lazy-read/save for a given
  category's whole batch of rows.
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
- `CouponSeeder` (`data/csv/coupons.csv`, columns: `code`/`target`/`type`/`value`/`active`/
  `startAt`/`endAt`/`minSubtotal`/`maxRedemptions`/`maxRedemptionsPerUser`/`maxDiscountAmount`/
  `description` — the last two added in the same follow-up that added both columns to `Coupon`
  itself) — 8 realistic sample coupons for the Coupon ("ProductDiscount") feature, added once
  Phase 4's checkout code-entry UI had nothing to actually exercise without an admin manually
  creating one first. Covers every eligibility branch `CouponRedemptionService.resolve` checks in
  one small dataset: `WELCOME10`/`SAVE5`/`VIP20` (all `SUBTOTAL`, exercising plain percentage, a
  fixed amount gated by `minSubtotal`, and a redemption-capped percentage that's **also**
  `maxDiscountAmount`-capped — `VIP20` is the literal "reduce 20%, max $20, for a subtotal >
  $100" example that motivated that field), `FREESHIP`/`SHIPHALF` (both
  `SHIPPING_FEE` — the first fully covers the flat fee, the second only halves it, deliberately
  covering both branches of `gui`'s corrected "Free" vs. partial-discount shipping display — see
  `gui/CLAUDE.md`'s own Coupon note for the display bug this combination caught),
  `SUMMER2026`/`BLACKFRIDAY2025` (both date-ranged — one currently active, one already expired, so
  `COUPON_EXPIRED` has a real row to trigger against), and `OLDPROMO` (`active=false`, so
  `COUPON_INACTIVE` and the admin list's Inactive filter both have a real row too). Mirrors
  `ProductTagSeeder`'s shape exactly — extends `CsvSeeder<Coupon>`, bypasses `CouponService` and
  persists directly via `CouponRepository` (creating a `Coupon` has no outbox/read-model side
  effect that would require the service layer), and its idempotency key is `code` itself
  (normalized to uppercase before the existence check, mirroring `CouponServiceImpl`'s own
  normalization) — unlike `ProductTagSeeder`'s/`ProductCategorySeeder`'s `name`, this is already
  the entity's own real natural key (a database `UNIQUE` constraint backs it), not a stand-in for
  a decoupled `seedId` this feature doesn't have either. Blank-tolerant parsing for every optional
  column (`active` defaults `true`; `startAt`/`endAt`/`minSubtotal`/`maxRedemptions`/
  `maxRedemptionsPerUser` all default `null`, i.e. "no bound"), matching `ProductSeeder`'s own
  blank-cell conventions.
- `EcommerceDataSeedingRunner` — `ApplicationRunner`, `@ConditionalOnProperty("app.seed.enabled")`,
  explicit order (categories → tags → attributes → category-attribute assignments → products →
  images → coupons, each depending on the previous existing except coupons, which are independent
  of every other seeder here and run last purely by convention) — same pattern as
  `content-service`'s/`social-service`'s own runners, not a `List<Seeder>` loop (see `infra`'s
  `Seeder` Javadoc for why). **Category-attribute assignment must run before `ProductSeeder`** —
  once a category has a schema, `ProductServiceImpl`'s own enforcement applies to every variant
  seeded into it from that point on, so this ordering is what makes the seed data's own internal
  consistency (see `ProductCategoryAttributeSeeder`'s own note above) actually get exercised at
  startup, not just asserted by a throwaway script.
- `scripts/purge-seed-data.sql` (repo root of this module, a plain dev utility — see its own header
  comment) gained `ecommerce.COUPON`/`ecommerce.COUPON_REDEMPTION` to its `TRUNCATE` list alongside
  this seeder — without both, a re-run with `app.seed.enabled=true` would silently skip every
  coupon row on the natural-key check, same staleness trap every other seeder's own idempotency
  check already carries. **A later follow-up added `ecommerce.SAVED_ADDRESS` to the same list
  too** — the script's own header promises "every ecommerce-service table," which excluding it
  contradicted (no seeder creates a `SAVED_ADDRESS` row, but the script purges real checkout/
  order data too, not just CSV-seeded rows, so a shopper's own AddressBook entries belong in the
  same "genuinely clean slate" scope). **A further follow-up added
  `ecommerce.PRODUCT_CATEGORY_ATTRIBUTE`/`ecommerce.PRODUCT_ATTRIBUTE_VALUE`/
  `ecommerce.PRODUCT_ATTRIBUTE` (DKP-0047) to the same list** — the same staleness trap as
  `COUPON`/`COUPON_REDEMPTION` above: `ProductAttributeSeeder`'s/`ProductCategoryAttributeSeeder`'s
  own idempotency checks are natural-key existence checks too (`ProductAttribute.name`,
  `ProductCategoryAttribute`'s per-category "already has any assignment" check), so a surviving
  row here would silently skip reseeding on the next `app.seed.enabled=true` run the same way a
  surviving `PRODUCT_TAG` row would. The `TRUNCATE` list now covers all 17 tables this module's
  migrations create — re-derive that count from a reactor-wide grep for
  `CREATE TABLE IF NOT EXISTS ecommerce\.` rather than trusting this number if a new table lands
  here later.

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
  null" branching lives in exactly one place. Also injects `infra`'s `StorageService` (plain
  constructor injection, not the `@Autowired protected` field `ProductMapper`/
  `ProductSearchViewMapper` use — those are MapStruct-generated abstract classes, this one isn't)
  to resolve each available line's `primaryImageUrl` from `product.getImages()`'s minimum
  `sortOrder` entry — null if the product has no images yet, same nullable shape
  `ProductSearchViewMapper.resolvePrimaryImageUrl` already uses for the storefront grid's own
  thumbnail. `Product.images` carries no `@OrderBy` of its own, so this picks the minimum by
  `sortOrder` explicitly rather than trusting collection order. **A post-Epic-2 GUI-driven
  follow-up** added `availableQuantity` (`variant.getStockQuantity() - variant.getReservedQuantity()`)
  to `toLineResponse` — the GUI had no way to cap a quantity picker at real stock or show a
  "only N left" nudge, since `CartLineResponse` only ever carried a boolean `available`, never a
  number; `ProductVariantResponse` already exposed both raw columns for the product detail page,
  so this just extends the same idea to the cart.
- `dto/{CartResponse,CartLineResponse,AddCartItemRequest,UpdateCartItemRequest}` — an unavailable
  line's response has only `variantId`/`quantity`/`available=false`; every other field
  (`primaryImageUrl`/`availableQuantity` included) is omitted (`@JsonInclude(NON_NULL)`), not
  zeroed out, so a GUI can't mistake "no data" for "priced at $0" (or "zero left").
- `api/CartApi`+`Controller` at `/api/v1/cart` — **authenticated-only, no new `SecurityConfig` rule
  needed** (no `/public/**`/`/admin/**` prefix, so it falls under the existing default
  `anyRequest().authenticated()` rule). Every mutating endpoint (`addItem`/`updateItem`/`removeItem`)
  returns the freshly-resolved `CartResponse`, not just a bare `200`/the one mutated line — avoids
  a wasted second `GET` round trip from whatever GUI is showing a live cart. `gateway`'s
  `GatewayRoutesConfig` gained a new `/api/v1/cart/**` route — the app's first genuinely new
  top-level prefix (no shared-prefix disambiguation needed, unlike `/api/v1/public`/`/api/v1/admin`).

**Post-Epic-2 follow-up, all 3 phases now built: multi-select cart (bulk delete + "checkout only the
selected items"), per request. Researched before building anything**: `CheckoutServiceImpl.confirm`
used to always operate on the caller's *entire* cart — no variant/line filter existed anywhere in
it or in `CheckoutApi`'s `AddressRequest` — and the only existing removal path was
single-`variantId` `DELETE /api/v1/cart/items/{variantId}`. Both halves of the feature reduced to
one missing primitive: removing an arbitrary set of variantIds from the cart in one call — built
in Phase 1, then spent by Phase 2.

- **Phase 1 — the primitive.** `CartService.removeItems(userUuid, variantIds)` /
  `CartServiceImpl.removeItems` — a single Redis `HDEL` across every requested field
  (`opsForHash().delete(key, variantIds...)`), not N round trips; refreshes the cart's TTL
  afterward the same way every other mutation does. Same no-availability-validation stance as
  `setQuantity`'s removal branch — a shopper must always be able to remove an already-invalid line.
  New `dto/RemoveCartItemsRequest` (`@NotEmpty List<Integer> variantIds`).
  **`CartApi.removeItems` is `POST /items/remove-batch`, not `DELETE /items` with a body** — asked
  the user, chose `POST` over `DELETE`-with-a-body because not every HTTP client/proxy layer
  reliably forwards a body on `DELETE`; a small naming-purity trade-off for avoiding that whole
  class of transport bug. No `gateway` routing change needed — `/api/v1/cart/**` already covers
  the new sub-path. New `CartServiceImplTest` — **this module's first test of `CartServiceImpl` at
  all**, a pre-existing gap this change did not introduce; only the new `removeItems` path is
  covered, existing untested methods (`addItem`/`setQuantity`/`getCart`/`clear`) weren't
  retroactively backfilled.
- **Phase 2 — spending it on checkout.** `CheckoutService.preview`/`.confirm` both gained a
  `List<Integer> selectedVariantIds` parameter (`null` = the whole cart, exactly the pre-existing
  behavior — fully backward compatible for every caller that doesn't pass one). New private
  `CheckoutServiceImpl.filterBySelection(lines, selectedVariantIds)` narrows the cart's lines down
  to just that set (or returns them unchanged when `null`) — the one seam both methods filter
  through before the existing `requireCheckoutableCart` empty/no-available-lines guards run
  (that method's own signature changed from taking a `Cart` to taking the already-filtered
  candidate `List<CartLine>`), so "no selection" and "every line happens to be selected" both flow
  through identical downstream logic to before this feature existed.
  **`confirm`'s final `cartService.clear(userUuid)` is now `cartService.removeItems(userUuid,
  orderedVariantIds)`** — the actual point of this phase: anything excluded by
  `selectedVariantIds`, or dropped by `confirm`'s own final revalidation, now stays in the cart
  afterward instead of being wiped along with whatever was actually ordered. **This changes
  behavior for the no-selection case too** (a previously-dropped/unavailable line used to get
  cleared away for free by the whole-cart `clear()` — it no longer does) — flagged and accepted as
  part of this phase's own plan, not an accidental side effect.
  `AddressRequest` (the `confirm` request body) gained a `selectedVariantIds` field, bolt-on
  alongside the address fields rather than its own request DTO — same pragmatic-extension
  precedent as `CartLineResponse.availableQuantity`. `CheckoutApi.preview` is a `GET` with no body
  to carry a selection in, so it gained a `@RequestParam(required = false) List<Integer>
  selectedVariantIds` instead (repeated query param,
  `?selectedVariantIds=1&selectedVariantIds=2`). A selection matching nothing currently in the
  cart reuses the existing `CHECKOUT_CART_EMPTY` error code rather than a new one — same
  shopper-facing meaning either way ("nothing to check out").
  `CheckoutServiceImplTest` gained 4 new cases (`preview`'s totals/lines correctly narrow to a
  selection; a selection matching nothing rejects on both `preview` and `confirm`; `confirm`
  orders/removes only the selected lines, leaving the rest in the cart) plus every existing case's
  `verify(cartService).clear(...)` assertion updated to `verify(cartService).removeItems(...)`.
  **`CartService.clear`/`CartServiceImpl.clear` were deleted outright once this left them with zero
  remaining callers anywhere in production code** — dead code, not kept around for a hypothetical
  future "empty cart" GUI action; add it back if that ever becomes a real, concrete need. 147 unit
  tests total (up from 138), all passing, no Docker needed; verified via a real `mvn test` run.
- **Phase 3 — the `gui` checkbox UI, now built.** Per-row checkboxes (`CartPage.tsx`), "select
  all," a "Delete Selected" button calling Phase 1's bulk-remove endpoint, and a
  "Checkout Selected (N)"/"Proceed to Checkout" button that passes the selection through to
  `/checkout`'s `preview`/`confirm` calls (Phase 2's `selectedVariantIds`) — falling back to the
  whole cart when nothing is selected, exactly the pre-Phase-2 behavior. See `gui/CLAUDE.md`'s
  `CartPage.tsx`/`CheckoutPage.tsx` notes for the full detail. This closes out the multi-select
  cart feature — all 3 planned phases are done.

**Epic 2's checkout half (US-2.5–2.7) is built on top of the cart above.**

- `entity/Address` — a plain JPA `@Embeddable` value object (`fullName`/`phone`/`email`/`line1`/
  `line2`/`city`/`state`/`postalCode`/`country` — `phone`/`email` are later, nullable-at-the-DB-level
  additions, see this file's own AddressBook-follow-up notes below), embedded directly on `Order`
  rather than its own table/entity —
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
  lines are created together, in one transaction, and never independently). **Epic 3 Phase 1** (see
  below) added `idempotencyKey`/`paymentProcessingStartedAt`/`cancelRequested` plus a
  `statusHistory` collection (same cascade/orphanRemoval/`@OrderBy("id ASC")` shape as `lines`).
- `enums/OrderStatus` — **now the full 8-value Epic 3 state machine**
  (`PENDING`/`PAYMENT_PROCESSING`/`CONFIRMED`/`EXPIRED`/`FAILED`/`CANCELLED`/`SHIPPED`/`DELIVERED`),
  added in one pass rather than incrementally — see the Epic 3 section below and this enum's own
  Javadoc for why that's different from `OutboxAggregateType`'s "one value per epic" growth pattern
  (this whole state machine is fully specified by one epic's user stories, not something that grows
  over the app's entire lifetime).
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
  `CheckoutServiceImplTest` via `Mockito.inOrder`. **The shipping fee itself is now computed by a
  `shipping.ShippingFeeCalculator` seam, not a field on this class (post-Epic-2 follow-up, per
  request)** — a GoF **Strategy** (Behavioral), mirroring `payment.PaymentGatewayPort`'s own
  "interface today, swap the implementation later" shape: `CheckoutServiceImpl` depends on
  `ShippingFeeCalculator` only, never a concrete pricing rule, so a future strategy
  (weight-tiered once `ProductVariant` gains a `weight` column, a real carrier-rate API call) can
  replace or sit alongside whichever strategy is active without touching checkout at all.
  `calculate(lines, subtotal)` deliberately doesn't take a resolved shipping `Address` — `preview`
  has no address available at all today (the shopper hasn't chosen one yet at preview time), so a
  genuinely zone/carrier-based strategy needing a destination would need to widen this method's
  signature (and thread an address through `preview` too) when that's actually built.
  **`calculate` returns `shipping.ShippingFeeQuote(fee, originalFee)`, not a bare `BigDecimal`**
  (a follow-up, per request — needed so the GUI's checkout preview could show a waived fee as
  "was $5.00, now free" instead of just a silent `$0.00`) — `originalFee` is what would have been
  charged absent any promotional waiver, equal to `fee` whenever nothing was waived
  (`FlatRateShippingFeeCalculator` always reports the two equal, having no discount concept at
  all). This generalizes past today's all-or-nothing free-shipping case — a future
  percentage-off-shipping strategy fits the same two-field shape. `CheckoutServiceImpl.preview`
  threads both fields into a new `CheckoutPreview.originalShippingFee` (and
  `CheckoutPreviewResponse.originalShippingFee`, via `CheckoutMapper`); `confirm` uses
  `quote.fee()` for `Order.shippingFee`/`total` (the *actual* charge) as before, and — a follow-up,
  per request, once `OrderDetailPage` needed the same "was $5.00, now free" treatment for an
  already-placed order — **also persists `quote.originalFee()` onto a new
  `Order.originalShippingFee` column** (`ORIGINAL_SHIPPING_FEE`, migration `DKP-0040`, added
  nullable/backfilled-from-`SHIPPING_FEE`/tightened-to-`NOT NULL` in the same changeset, the usual
  three-step shape for a new required column on an already-populated table), mapped onto
  `OrderResponse.originalShippingFee` via `OrderMapper.toResponse`. This *reverses* an earlier,
  narrower scope decision in this same section's history (originally "no consumer for it on
  `Order` today") — kept only until the very next request actually needed it, which is exactly why
  that kind of deferral is framed as a scope decision, not a closed door.
  **`shipping.FreeOverThresholdShippingFeeCalculator` was briefly the active `@Component` bean**
  (a follow-up swap, per request) — free shipping once `subtotal` reaches
  `app.ecommerce.checkout.free-shipping-threshold` (`CHECKOUT_FREE_SHIPPING_THRESHOLD` env var,
  default `50.00`), else the same flat fee `shipping.FlatRateShippingFeeCalculator` always charged
  (`app.ecommerce.checkout.flat-shipping-fee`/`CHECKOUT_FLAT_SHIPPING_FEE`, default `5.00`, reused
  as "the fee below the threshold" rather than a second redundant property). Both properties follow
  the same `@Value`-on-a-field convention `CartServiceImpl`'s own `cartTtl` already established for
  a tunable business value. **`FlatRateShippingFeeCalculator` is the active bean again now, per a
  later request — see the Coupon feature section below for why.** In short: once the Coupon
  feature's own `SHIPPING_FEE`-target coupons existed, this automatic threshold-based waiver could
  zero out the shipping fee *before* a coupon was even considered, so a shopper's `SHIPPING_FEE`
  coupon could "apply" successfully (redeeming a real, possibly limited-use
  `CouponRedemption`) for zero actual benefit — `CouponRedemptionServiceImpl.calculateDiscount`
  clamps any discount to the base amount it's discounting off of, already `0` in that case. Rather
  than add cross-mechanism guard logic, the fix was to stop running two independent shipping-
  discount mechanisms at once: coupons are now the only thing that ever discounts a shipping fee,
  so `FreeOverThresholdShippingFeeCalculator` was demoted back out. Both classes stay in the
  codebase either way — whichever isn't active is kept as a reference implementation/an easy
  strategy to switch back to, deliberately carrying **no `@Component`** — same "don't leave two
  ambiguous candidates wired in at once" reasoning `payment.NoOpPaymentGatewayPort`'s own Javadoc
  documents for that seam's future real-adapter swap; re-add `@Component` to whichever strategy
  should be active (and remove it from the other) to switch again. `CheckoutServiceImplTest` mocks
  `ShippingFeeCalculator` (`lenient()`, since several early-rejection tests never reach it) instead
  of the old `ReflectionTestUtils.setField` hack a plain `@Value` field needed, plus cases pinning
  that `preview`'s `total` and `confirm`'s `Order.total` are both built from the actually-charged
  `quote.fee()`, never the waived `originalFee` (while `originalShippingFee` itself still reports
  the pre-waiver amount on both) — these mocked-collaborator tests are unaffected by which concrete
  strategy is the real active bean. `FreeOverThresholdShippingFeeCalculatorTest` (still present,
  still passing — it instantiates the class directly, independent of Spring wiring) pins the
  at/above/below-threshold boundary and that `originalFee` keeps reporting the below-threshold fee
  even once waived — **205 unit tests total** (up from 200 as of the AddressBook entry above; treat
  this figure, not `165` further down this file, as the current one — see that paragraph's own note
  on why it can go stale).
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

**Epic 3 (Order Lifecycle & Inventory, `docs/user-stories/03-order-lifecycle-inventory.md`) — all
6 phases are now built, including the REST surface (Phase 5) and the cross-handler integration
test pass (Phase 6).** This epic was built in phases (Phase 1: data model; Phase 2: US-3.1
reserve-at-checkout; Phase 3: State-pattern skeleton + US-3.2/3.6/3.7/3.8; Phase 4: US-3.3/3.4
payment handoff + reconciliation behind a `PaymentGatewayPort` stub; Phase 5: REST surface;
Phase 6: integration tests across the real wiring, plus a documentation consistency pass):

- `enums/OrderStatus` widened to all 8 values (see above) in one migration rather than
  incrementally, since the full state machine is already specified by this epic's own user stories.
- `entity/Order` gained `idempotencyKey` (US-3.3, nullable — has no meaning before the
  `PENDING`→`PAYMENT_PROCESSING` transition Phase 4 will add), `paymentProcessingStartedAt`
  (US-3.4's reconciliation-job clock), `cancelRequested` (US-3.6's queued-cancel-mid-payment flag),
  and a `statusHistory` collection.
- New `entity/OrderStatusHistory` (US-3.5's audit trail) — `order` FK, nullable `fromStatus`
  (null only for the very first row), `toStatus`, optional `reason`. No dedicated repository;
  writers populate it via `order.getStatusHistory().add(...)`, relying on `Order.statusHistory`'s
  `cascade = ALL` the same way `OrderLine` already does — add a direct
  `OrderStatusHistoryRepository` only if a later phase genuinely needs to query it independently of
  its parent `Order`. `CheckoutServiceImpl.confirm` (Phase 2, below) is the first real writer,
  appending the very first row (`fromStatus = null`, `toStatus = PENDING`) at order creation.
- `repository/ProductVariantRepository` gained three atomic conditional `@Modifying` updates —
  `reserve`/`release`/`confirmSale` — the concurrency-safe mechanism US-3.1's "two shoppers can
  never oversell the same limited stock" requirement actually needs: each is a single `UPDATE ...
  WHERE` statement re-checking availability in the same statement as the write, the identical
  claim-style shape `OutboxEventRepository.claim` already established in this module, rather than a
  separate read-then-write that a second transaction could interleave with under Postgres's default
  READ COMMITTED isolation, or pessimistic row locking. `release`/`confirmSale` aren't called from
  anywhere yet — that's Phase 3/4's job.
- Migration `202608300001__0.0.2__DKP-0035__add_order_reservation_and_status_history.sql`: widens
  `CKC_CUSTOMER_ORDER_STATUS`, adds the three new `CUSTOMER_ORDER` columns plus a partial unique
  index on `IDEMPOTENCY_KEY` (`WHERE IDEMPOTENCY_KEY IS NOT NULL` — most orders never reach payment
  and so never get one), a `(STATUS, DTE_CREATION)` index for Phase 3/4's scheduled-job poll
  queries, and the new `ORDER_STATUS_HISTORY` table/sequence.
- **Phase 2 (US-3.1) — stock reservation wired into checkout.**
  `CheckoutServiceImpl.confirm` now calls `ProductVariantRepository.reserve` for every available
  cart line **before** the `Order` is ever built, inside the same `@Transactional` method checkout
  already runs in: an insufficient-stock line throws
  `EcommerceErrorCode.ORDER_INSUFFICIENT_STOCK` (`ORDER_001`, `409 CONFLICT`), and since nothing has
  committed yet, the surrounding transaction rolls back both the new order and every reservation
  already claimed by an earlier line in the same request — genuinely atomic "create the order and
  reserve stock" per this epic's own locked decision (a single local ACID transaction, not a saga).
  `confirm` also appends the order's first `OrderStatusHistory` row (see above). New private
  `CheckoutServiceImpl.reserveStock(CartLine)` helper wraps the `reserve` call +
  `Validator.isTrue` guard. `CheckoutServiceImplTest` gained cases for: the reservation succeeding
  (asserting call order `reserve` → `save` → `clear`, and the new status-history row), a dropped
  (unavailable) line never being a reservation candidate, a single-line insufficient-stock rejection
  (no save/clear), and a multi-line request where an earlier line's reservation is claimed before a
  later line fails (documented as the real DB transaction's job to undo, not something a mocked-repo
  unit test can exercise) — 2 new test methods, 82 unit tests total (up from 80), all still passing
  without Docker.
- **Phase 3 (US-3.2, 3.6–3.8) — the GoF State-pattern skeleton, all wired up.** New
  `orderstatus/` package (mirrors `outbox/`'s own self-contained-mechanism shape):
  - `OrderStatusHandler` — the Strategy interface (`status()` + `expire`/`cancel`/`ship`/`deliver`,
    each defaulting to a rejection via `EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION`
    (`ORDER_003`) so a concrete handler only needs to override what's actually valid from its own
    status).
  - `PendingOrderStatusHandler` (`expire`/`cancel`, both release-only — nothing was sold from a
    `PENDING` order), `PaymentProcessingOrderStatusHandler` (`cancel` only sets
    `Order.cancelRequested`, doesn't transition — a gateway call is in flight),
    `ConfirmedOrderStatusHandler` (`cancel` restocks — stock here was already sold via
    `confirmSale`, so undoing it means giving stock back, not releasing a reservation that no
    longer exists; `ship` — no inventory action), `ShippedOrderStatusHandler` (`deliver` only —
    deliberately doesn't override `cancel`, so the interface's own default rejection enforces
    "blocked once shipped" for free). **No handler classes for the terminal statuses**
    (`EXPIRED`/`FAILED`/`CANCELLED`/`DELIVERED`, none of which have any outgoing transition) — see
    `OrderStatusHandlerRegistry`'s Javadoc for why that's a deliberate design choice, not a gap.
  - `OrderStatusTransitions` — static helpers (`releaseReservations`/`restockSoldLines`/
    `transitionTo`) shared by the handlers above; plain static methods rather than a shared
    abstract base class, since `PaymentProcessingOrderStatusHandler` doesn't need
    `ProductVariantRepository` at all and forcing it to accept one anyway would be exactly the
    unnecessary-dependency smell this module's own conventions warn against.
  - `OrderStatusHandlerRegistry` — builds a `Map<OrderStatus, OrderStatusHandler>` from every
    handler bean (same registry shape as `outbox.OutboxEventDispatcher`) and dispatches
    `expire`/`cancel`/`ship`/`deliver` through it; a status with no registered handler falls back to
    a handler with no overrides at all, so "no handler for this status" and "handler exists but
    doesn't support this action" both reject identically, for free.
  - `OrderReservationExpiryJob`/`OrderReservationExpiryProcessor` (US-3.2) — same
    poller/single-item-processor split as `outbox.OutboxRelay`/`OutboxEventProcessor`, for the
    identical self-invocation-bypasses-`@Transactional` reason. Polls
    `OrderRepository.findIdsByStatusAndDteCreationBefore(PENDING, now - reservation-timeout)`
    (new query, mirrors `OutboxEventRepository.findIdsByStatus`), re-checks each order is still
    `PENDING` after loading (a defensive guard against a stale id in the poll batch, e.g. the
    shopper cancelled it themselves in the gap — **not** a distributed-concurrency mechanism; this
    reactor runs exactly one instance of each service today, so no `OrderRepository.claim`-style
    atomic `UPDATE` was added, unlike `OutboxEventRepository.claim` — add one first if this job is
    ever run with more than one instance). New `app.ecommerce.order.reservation-timeout`
    (`ORDER_RESERVATION_TIMEOUT`, default `PT15M`) and
    `app.ecommerce.order.expiry-check.poll-interval` (`ORDER_EXPIRY_CHECK_POLL_INTERVAL`, default
    `PT1M`) properties.
  - New `service/OrderService`/`impl/OrderServiceImpl` — thin `cancel(orderId, callerUuid)` /
    `ship(orderId)` / `deliver(orderId)` wrappers around the registry (find-or-404, dispatch, save).
    `cancel` hides ownership the same way `ProductService.getActiveBySlug` hides a deactivated
    product's slug: an order that doesn't exist and one that belongs to someone else both surface
    as `ORDER_NOT_FOUND` (`ORDER_002`, new). No REST layer yet — Phase 5's job; these are the exact
    seam Phase 5's `OrderApi`/`OrderController` will call.
  - Tests: one test class per handler (`PendingOrderStatusHandlerTest`,
    `PaymentProcessingOrderStatusHandlerTest`, `ConfirmedOrderStatusHandlerTest`,
    `ShippedOrderStatusHandlerTest`), `OrderStatusHandlerRegistryTest` (dispatch-by-status,
    terminal-status fallback, mirrors `OutboxEventDispatcherTest`),
    `OrderReservationExpiryProcessorTest` (vanished/no-longer-`PENDING`/real-expiry cases, mirrors
    `OutboxEventProcessorTest`'s shape), and `OrderServiceImplTest`. 25 new test methods, 107 unit
    tests total (up from 82), all still passing without Docker.
- **Phase 4 (US-3.3, 3.4) — payment handoff + reconciliation, behind a stub gateway.** New
  `payment/` package (Epic 4's eventual home, seeded now since Epic 3 needs a seam to call):
  - `PaymentGatewayPort` — GoF **Adapter** (Structural): `charge(idempotencyKey, amount)` /
    `checkStatus(idempotencyKey)`, both returning `PaymentOutcome` (`SUCCEEDED`/`DECLINED`/
    `PENDING`). The rest of Epic 3 depends on this interface only, never a concrete gateway SDK, so
    Epic 4 can swap the implementation below for a real one without touching anything in
    `orderstatus/`.
  - `NoOpPaymentGatewayPort` — the only implementation today: always returns `SUCCEEDED`
    instantly, logging a warning each call. Exists purely so the mechanism below has something
    real to call end-to-end; **delete it outright once Epic 4 adds a real adapter** (or gate both
    behind profiles if a fake gateway stays useful for local/test envs) rather than leaving two
    ambiguous candidates for the same interface.
  - `OrderStatusHandler` gained `startPaymentProcessing`/`confirmPayment`/`failPayment` (same
    default-rejects shape as the Phase 3 methods). `PendingOrderStatusHandler.startPaymentProcessing`
    stamps `idempotencyKey` (the order's own id — "a reasonable default" per this epic's own
    decisions) and `paymentProcessingStartedAt`, transitions to `PAYMENT_PROCESSING`,
    no inventory action. `PaymentProcessingOrderStatusHandler.confirmPayment`/`failPayment`
    resolve the in-flight attempt (`confirmSale`/`release` respectively) — both check
    `Order.getCancelRequested()` first: **a queued cancel wins over whatever the gateway
    answered**, ending the order `CANCELLED` either way (restocking if payment had actually
    succeeded, since money was captured and stock was sold, if only for a moment — refund
    deferred to Epic 4, same as `ConfirmedOrderStatusHandler.cancel`; the release path needed no
    change since `FAILED`'s own compensation was already identical, only the final label changes).
    New `OrderStatusTransitions.confirmSaleForLines` helper alongside the Phase 3 ones.
  - New `orderstatus/PaymentHandoffService` — the two independent `@Transactional` steps US-3.3
    needs: `startPaymentProcessing(orderId, callerUuid)` (durably commits
    `PENDING -> PAYMENT_PROCESSING` **before** any gateway call) and
    `resolvePayment(orderId, outcome)` (applies the verdict afterward, in a second transaction).
    Deliberately two transactions, not one wrapping the whole handoff: a crash between the gateway
    call and applying its result must leave the order durably `PAYMENT_PROCESSING` with its
    idempotency key intact (exactly what `OrderReconciliationJob` below exists to recover), not
    silently roll back the whole attempt and risk a double charge on retry.
  - New `orderstatus/OrderReconciliationJob` (US-3.4, `@Scheduled`) — polls a new
    `OrderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore` query for orders stuck in
    `PAYMENT_PROCESSING` past `app.ecommerce.order.reconciliation.grace-period` (default `PT2M`),
    re-checks each is still stuck after loading (same defensive-guard reasoning as
    `OrderReservationExpiryProcessor`), calls `PaymentGatewayPort.checkStatus` for the ground
    truth, and applies it via `PaymentHandoffService.resolvePayment` — **never assumes an outcome**.
    No separate `@Transactional` processor bean needed (unlike the Phase 3 expiry job) since this
    job never calls a `@Transactional` method on itself — `PaymentHandoffService` is a different
    bean, so the call already goes through Spring's proxy correctly. One poison order's exception
    is caught and logged per-order so it doesn't stop the rest of the batch from reconciling. New
    `app.ecommerce.order.reconciliation.poll-interval` (default `PT1M`).
  - `service/OrderServiceImpl` gained `initiatePayment(orderId, callerUuid)` — the orchestrator:
    calls `PaymentHandoffService.startPaymentProcessing`, then `PaymentGatewayPort.charge`
    (outside any transaction), then `PaymentHandoffService.resolvePayment`. Deliberately **not**
    itself `@Transactional` (the class-level annotation from Phases 2–3 moved to individual methods
    for exactly this reason) — if the gateway call throws, the order is left `PAYMENT_PROCESSING`
    on purpose, for `OrderReconciliationJob` to resolve later, rather than force-failed here.
  - Tests: `NoOpPaymentGatewayPortTest`, `PaymentHandoffServiceTest`, `OrderReconciliationJobTest`
    (stuck/vanished/already-resolved/one-poison-order-doesn't-block-the-batch cases, mirrors
    `OrderReservationExpiryProcessorTest`'s shape), plus new cases on
    `PendingOrderStatusHandlerTest`/`PaymentProcessingOrderStatusHandlerTest`/
    `OrderStatusHandlerRegistryTest`/`OrderServiceImplTest`. 19 new test methods, 126 unit tests
    total (up from 107), all passing, no Docker needed — **verified via a real
    `mvn test` run in this session**, not just claimed.
- **Phase 5 (US-3.5, plus exposing 3.3/3.6/3.7/3.8 over HTTP) — the REST surface.**
  `repository/OrderRepository` gained `findByOwnerUuidOrderByIdDesc` (a fully-derived query — the
  sort is baked into the method name, since "most recent first" is the only ordering a shopper's
  own order history needs), backed by a new `IDX_CUSTOMER_ORDER_OWNER_UUID` index (migration
  `202608300002__0.0.2__DKP-0036__add_customer_order_owner_uuid_index.sql` — this index was
  deliberately deferred until this exact query needed it, per the note this repository used to
  carry). **This derived query was later deleted outright** once the status-tabs follow-up (see
  below) needed an optional `IN` filter alongside the ownership check — `listOrders` moved onto
  `findAll(Specification, Pageable)` instead, with the sort moved to an explicit `Sort` on the
  `Pageable`; the index still backs the replacement query's `ownerUuid` predicate the same way.
  `OrderService` gained `getOrder(orderId, callerUuid)` (US-3.5, same ownership-hiding
  shape as `cancel`) and `listOrders(callerUuid, pageable)`.
  - New `dto/OrderStatusHistoryResponse`/`OrderResponse` (the latter reused for both the list-mine
    and get-by-id endpoints — same "one response DTO for list and detail" convention
    `ProductResponse` already established, since an order's lines/history are always small,
    unlike a product's variant/image gallery only being a genuine concern at that larger scale).
  - New `mapper/OrderMapper` — hand-written, not MapStruct (matches `CartMapper`/`CheckoutMapper`
    in this package). `toOrderLineResponse`/`toAddressResponse` were originally both `public
    static` so `CheckoutMapper` could reuse them for its own `CheckoutConfirmResponse` —
    `OrderMapper` is the canonical owner of that mapping, since Epic 3 Phase 5 is the second
    caller; `CheckoutMapper`'s own private copies were deleted. **`toOrderLineResponse` stopped
    being `static` in a post-Epic-3 follow-up** (per request: `OrderHistoryPage`'s redesign needed
    each line's product image/variant attributes, which `OrderLine` never snapshots — see its own
    Javadoc) — it now injects `ProductVariantRepository`/`infra`'s `StorageService` and does a
    best-effort live lookup by `OrderLine.productVariantId`, same "resolve a presigned primary
    image URL, null if the product has none yet" shape `CartMapper.resolvePrimaryImageUrl` already
    established, with the added twist that the variant itself might no longer exist at all
    (`productVariantId` is a plain column, not a real FK — both `attributes`/`primaryImageUrl` on
    `OrderLineResponse` are simply `null` in that case). `CheckoutMapper` now constructor-injects
    `OrderMapper` and calls `orderMapper::toOrderLineResponse` instead of the old static method
    reference; `toAddressResponse` is untouched, still `public static` (needs no lookup).
    `OrderMapper.toResponse` reads `Order.getLines()`/`getStatusHistory()`, both lazy collections —
    this only works because `spring.jpa.open-in-view` is left at Spring Boot's default `true` in
    this module (unchanged), keeping the Hibernate session open for the whole request;
    `ProductMapper` already relies on the identical behavior for `Product.variants`/`images`.
  - New `api/OrderApi`+`Controller` at `/api/v1/orders` — **authenticated-only, same as
    `CartApi`/`CheckoutApi`, no new `SecurityConfig` rule needed.** `GET` (list mine, paginated,
    most recent first), `GET /{id}` (US-3.5's detail view with full status timeline),
    `POST /{id}/cancel` (US-3.6), `POST /{id}/pay` (US-3.3's `initiatePayment` — not in the
    originally-scoped Phase 5 plan, added since Phase 4 had already built the capability and
    leaving it REST-unreachable would defeat the point of this phase).
  - New `api/AdminOrderApi`+`Controller` at `/api/v1/admin/orders` — `POST /{id}/ship` (US-3.7),
    `POST /{id}/deliver` (US-3.8). **A separate interface from `OrderApi`, not folded together** —
    mirrors this module's existing `ProductCategoryApi`/`PublicProductCategoryApi` split (same
    underlying resource, genuinely different audience/security rule). Admin-gated automatically via
    the existing `/api/v1/admin/**` rule, no `SecurityConfig` change needed either.
  - `gateway`'s `GatewayRoutesConfig.ecommerceServiceRoutes()` gained `/api/v1/orders/**` (a new
    top-level prefix, no shared-prefix disambiguation needed — same shape `/api/v1/cart/**` was)
    and `/api/v1/admin/orders/**` (joining the existing shared `/api/v1/admin/**` prefix's
    resource-specific routes alongside `/products/**`/`/product-categories/**`). See root
    `CLAUDE.md`'s Routing section.
  - `OrderServiceImplTest` gained `GetOrder`/`ListOrders` nested test classes — 4 new test methods,
    130 unit tests total (up from 126), all passing, no Docker needed; verified via a real
    `mvn test` run in this session. No dedicated `OrderMapperTest`/controller tests — matches this
    module's existing convention of not unit-testing thin hand-written mappers or controllers
    directly (`CartMapper`/`CheckoutMapper` have none either).
- **Phase 6 — cross-handler integration tests, plus a documentation consistency pass.** Every test
  written across Phases 2–5 isolates exactly one handler/processor/service at a time, with its
  collaborators mocked — none of them actually drove the real `OrderStatusHandlerRegistry`, wired
  with every real handler, through more than one transition in sequence. New
  `orderstatus/OrderLifecycleIntegrationTest` closes that gap: constructs the registry from real
  `PendingOrderStatusHandler`/`PaymentProcessingOrderStatusHandler`/`ConfirmedOrderStatusHandler`/
  `ShippedOrderStatusHandler` instances (mocking only `ProductVariantRepository`, the actual
  persistence boundary) and drives a single `Order` through 6 scenarios: the full happy path
  (`PENDING` → `PAYMENT_PROCESSING` → `CONFIRMED` → `SHIPPED` → `DELIVERED`, asserting the exact
  inventory calls and the full history trail), cancel-before-payment (release), cancel-after-
  confirmation (restock), and — the two cases no single-handler unit test can observe, since they
  span `PaymentProcessingOrderStatusHandler`'s `cancel`/`confirmPayment`/`failPayment` methods
  together — a queued cancel winning over a *subsequent* gateway success, and a queued cancel
  winning over a subsequent gateway decline. Also verifies cancel is genuinely blocked once
  `SHIPPED`, end to end through the registry rather than by calling `ShippedOrderStatusHandler`
  directly. 6 new test methods, 136 unit tests total (up from 130), all passing, no Docker needed —
  verified via a real `mvn test` run in this session. The documentation pass is this file itself:
  every phase's own section above was re-read against the current code for this final pass, and one
  real staleness was caught and fixed along the way — `OrderStatusTransitions`'s own Javadoc still
  said `PaymentProcessingOrderStatusHandler` didn't need `ProductVariantRepository`, true only
  through Phase 3; Phase 4's `confirmPayment`/`failPayment` gave it that dependency, leaving
  `ShippedOrderStatusHandler` as the only handler without it.
- **Not built yet** (do not assume these exist): a real payment gateway adapter (replacing
  `NoOpPaymentGatewayPort`) and everything else in Epic 4 beyond what Epic 3 needed as a seam.
- **Post-Epic-3 follow-up: admin order-fulfillment list (US-3.7/3.8), added once the admin GUI
  actually needed it.** `AdminOrderApi.ship`/`.deliver` had no way to *find* which orders needed
  action — new `GET /api/v1/admin/orders` (optional `?status=` filter, e.g. `CONFIRMED` for
  "ready to ship") closes that gap. `OrderRepository` gained `JpaSpecificationExecutor<Order>` and
  a new `repository/spec/OrderSpecification.withFilters(OrderStatus)` — this module's usual
  Specification-pattern convention for dynamic filtering, even for a single optional filter, same
  shape as `ProductCategorySpecification`/`ProductSpecification` (no dedicated unit test for the
  Specification's own filtering logic either, matching that same precedent — only the delegation
  is unit-tested). `OrderService.listAllOrders(status, pageable)` — deliberately **not**
  ownership-checked, unlike `listOrders` (the shopper's own-orders query); admin-only, enforced at
  the REST layer, not by a service-level owner check. `AdminOrderController.list` sorts oldest-first
  (plain `id ASC`, no client-configurable sort — a FIFO fulfillment queue, not a general browser)
  and reuses the existing `OrderMapper.toResponse`/`OrderResponse` — no new DTO needed. No gateway
  change needed either — `/api/v1/admin/orders/**` (routed since Phase 5) already covers the bare
  `GET /api/v1/admin/orders` path, same as `/api/v1/admin/products/**` already covers
  `ProductApi.list`'s equivalent bare `GET`. New `OrderServiceImplTest.ListAllOrders` test — 1 new
  test method, 137 unit tests total (up from 136), all passing, no Docker needed; verified via a
  real `mvn test` run in this session.

- **Post-Epic-3 follow-up: shopper-facing order-history status tabs (`gui`'s `OrderHistoryPage`),
  per request — grouped Shopee-style ("All | To Pay | Processing | Shipped | Delivered |
  Cancelled"), chosen over one tab per raw `OrderStatus` after asking.** Grouped tabs mean a
  status filter that's an `IN` over several statuses (e.g. "To Pay" = `PENDING` +
  `PAYMENT_PROCESSING`), which `OrderSpecification.withFilters`'s single-equality shape (built for
  the admin queue above) can't express. New `OrderSpecification.withOwnerAndStatuses(ownerUuid,
  Collection<OrderStatus>)` — always filters to `ownerUuid` (unlike the admin-only `withFilters`),
  plus an optional `status IN (...)` when `statuses` is non-empty.
  `OrderService.listOrders`/`OrderServiceImpl.listOrders` gained a `Collection<OrderStatus>
  statuses` parameter (`null`/empty = every status, "All") and now delegates to
  `orderRepository.findAll(OrderSpecification.withOwnerAndStatuses(...), pageable)` instead of a
  dedicated derived query. **`OrderRepository.findByOwnerUuidOrderByIdDesc` was deleted outright**
  once this left it with no callers — its "most recent first" ordering is now the caller's own
  job via an explicit `Sort` on the `Pageable` (`OrderController.list` builds
  `PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))`), the same shape
  `AdminOrderController.list` already used for its own oldest-first sort — `findAll(Specification,
  Pageable)` has no inherent ordering the way a derived query does, so this had to become explicit
  once the query moved off one. `OrderApi.list` gained `@RequestParam(required = false)
  List<OrderStatus> statuses` (repeated query param, e.g.
  `?statuses=PENDING&statuses=PAYMENT_PROCESSING`) alongside its existing `page`/`size`. No
  `gateway` change needed — `/api/v1/orders/**` already covers the same bare `GET` path.
  `OrderServiceImplTest$ListOrders` gained a second case (the existing one updated to assert
  delegation via a mocked `Specification` instead of the deleted derived query, same "verify
  delegation, not the Specification's own filtering logic" precedent `ListAllOrders` already
  established); 143 unit tests total at the time, all passing, no Docker needed, verified via a
  real `mvn test` run in that session. See `gui/CLAUDE.md`'s `OrderHistoryPage.tsx` note for the tab
  grouping itself and the GUI-side wiring.

- **Post-Epic-3 follow-up: `OrderHistoryPage` redesign — per-line product image + variant info,
  order id no longer shown, per request.** `OrderLineResponse` gained `attributes`
  (`Map<String, String>`) and `primaryImageUrl` (`String`), both resolved live by
  `OrderMapper.toOrderLineResponse` (see that mapper's own updated note above for the full
  reasoning — `OrderLine` itself snapshots none of this, only `sku`/`productName`/`unitPrice`/
  `quantity`). Both fields are `null` when the variant no longer exists — a real possibility since
  `productVariantId` isn't a foreign key. No new endpoint, no migration — purely additive fields on
  an existing response, resolved at read time. See `gui/CLAUDE.md`'s `OrderHistoryPage.tsx` note
  for the new per-line layout these fields feed.
  - **Follow-up: `OrderLineResponse` gained `productSlug` too**, once `OrderDetailPage` needed the
    same per-line layout with a click-through to the product page — `OrderMapper` resolves it in
    the same live variant lookup as `attributes`/`primaryImageUrl`
    (`variant.getProduct().getSlug()`), `null` under the identical since-deleted-variant condition.
    The GUI's `OrderLineRow` component (below) was extracted into a shared component once both
    `OrderHistoryPage` and `OrderDetailPage` needed byte-identical rendering of it, including the
    new link — see `gui/CLAUDE.md`'s `components/orders/OrderLineRow.tsx` note.

- **Post-Epic-3 follow-up: `ProductCategory` parent/child hierarchy support, per request.** See the
  `entity/ProductCategory` and `service/`/`mapper/`/`dto/`/`api/` bullets above for the full detail
  (self-referential adjacency list, `DKP-0037`, `GET /tree`, cycle-guarded `create`/`update`). The
  admin GUI's `ProductCategoryListPage.tsx`/`ProductCategoryFormDialog.tsx` were rebuilt to match
  `content-service`'s own hierarchical `CategoryListPage.tsx`/`CategoryFormDialog.tsx` — a "Parent"
  column (resolved via the fetched tree, not a second lookup per row) and an indented parent-picker
  `Select` that excludes the category's own subtree (can't become its own descendant's child) — see
  `gui/CLAUDE.md`'s note. **Deliberately not touched by this follow-up**: `ProductCategoryApi` still
  has no delete endpoint (unrelated to hierarchy — see below), and `ShopPage`'s storefront category
  filter was not rewired to walk the new hierarchy (still a flat, single-select sidebar list) — a
  natural follow-up now that the data model supports it, not done here. **`ProductDetailPage`'s
  breadcrumb *was* deepened to a real root→leaf ancestor trail in a later follow-up** — see
  `gui/CLAUDE.md`'s `ProductDetailPage.tsx` note (`utils/categoryPath.ts`).

- **Post-Epic-3 follow-up: Product Tags — a many-to-many relationship (a product can have
  multiple tags, a tag can be attached to multiple products), per request.** Mirrors
  `content-service`'s existing `Tag`/`ContentItemTag` pattern closely, the primary precedent
  researched and followed rather than designed from scratch — three explicit scope decisions were
  asked and confirmed before building: (1) `ProductTag` has **no status/lifecycle field** — just
  `name`/`slug`, unlike `content-service`'s `Tag` (`TagStatus: ACTIVE/INACTIVE`), matching
  `ProductCategory`'s own original pre-hierarchy simplicity; (2) `ProductResponse.tagIds` exposes
  **ids only**, matching `content-service`'s own `QuestionAnswerResponse.tagIds` (not names/full
  objects); (3) **admin-only scope for this pass** — the public storefront (`ProductSearchView`/
  `ShopPage`) is *not* wired up to tags at all, deferred the same way storefront category-filtering
  was deferred after the category-hierarchy work.
  - New `entity/ProductTag` (`name`/`slug`, case-insensitive-unique via a `LOWER(NAME)` functional
    index, same as `ProductCategory`) and `entity/ProductTagAssignment` — an **explicit join
    entity**, not a bare `@ManyToMany`/`@JoinTable`, mirroring `content-service`'s
    `ContentItemTag` exactly: the assignment row itself needs the same audit columns
    (`usrCreation`/`dteCreation`/etc.) every other entity in this reactor carries, which a plain
    join table can't provide. `UNIQUE(PRODUCT_ID, PRODUCT_TAG_ID)`. `Product` gained a
    `productTagAssignments` nav-only collection — but unlike `variants`/`images` (no cascade, "own
    repository owns the write"), this one **is** `cascade = ALL, orphanRemoval = true`, mirroring
    `ContentItem.contentItemTags`' ownership of its own join rows.
  - Migration `DKP-0038` (`202609010001__0.0.2__DKP-0038__add_product_tag_tables.sql`) — confirmed
    against every changelog tree in the reactor for the next free ticket number before writing it,
    not assumed.
  - New `EcommerceErrorCode.PRODUCT_TAG_*` (`NOT_FOUND`/`NAME_CONFLICT`/`SLUG_CONFLICT`/`IN_USE`,
    `PRODUCT_TAG_001`–`004`), `repository/ProductTagRepository` (mirrors
    `ProductCategoryRepository`'s shape), `repository/ProductTagAssignmentRepository`
    (`existsByProductTagId`, backing the in-use delete guard — read-only otherwise, every write
    path is via `Product.productTagAssignments`' own cascade, never a direct
    save/delete through this repository), `repository/spec/ProductTagSpecification` (`q` filter
    only, no status), `service/ProductTagService`/`Impl` (create/update/delete/getById/**paginated**
    list — paginated, not unpaginated like `ProductCategoryService.list`, since free-form tags are
    expected to proliferate more than a taxonomy would, mirroring `content-service`'s own paginated
    `TagService.list`), `mapper/ProductTagMapper`, `dto/{ProductTagResponse,
    CreateProductTagRequest,UpdateProductTagRequest}`, and `api/ProductTagApi`+`Controller` at
    `/api/v1/admin/product-tags` (CRUD, no attach/detach endpoints of its own — see below).
  - **Tag assignment doesn't live on `ProductTagService` at all** — it travels with
    `ProductCommands.Create`/`Update`'s own new `tagIds: Set<Integer>` field, exactly mirroring
    `content-service`'s split between `TagService` (pure CRUD) and
    `QuestionAnswerServiceImpl.applyTagIds` (assignment folded into the owning entity's own
    create/update). New `ProductServiceImpl.applyTagIds(Product, Set<Integer>)` private helper —
    byte-for-byte the same shape as `QuestionAnswerServiceImpl.applyTagIds` minus the "reject
    inactive tags" step that class has (no status field to check here): reject any `null` id in the
    input, dedupe via `LinkedHashSet`, bulk-fetch via `findAllById` and assert the count matches
    (`PRODUCT_TAG_NOT_FOUND` if any are missing), then clear and rebuild
    `product.getProductTagAssignments()` using `productTagRepository.getReferenceById` (a proxy
    reference, no extra `SELECT`). **Same three-state semantics as `QuestionAnswerCommands.Update`**:
    on `create`, a `null`/omitted `tagIds` just means "no tags" (always applied); on `update`,
    `null` **leaves tags unchanged**, empty **clears** them, non-empty **replaces** them —
    `ProductServiceImpl.update` only calls `applyTagIds` when `command.tagIds() != null`.
  - `mapper/ProductMapper` gained a new `@AfterMapping resolveTagIds` step (alongside the existing
    `resolveImageUrl` one) computing `ProductResponse.tagIds` from
    `product.getProductTagAssignments()` — same "derived from a nav-only collection, not a direct
    entity field" shape as the presigned-URL resolution, just no injected dependency needed for
    this one.
  - `repository/spec/ProductSpecification.withFilters` gained a new overload taking an optional
    `Set<Integer> tagIds` (the 3-arg overload delegates to it with `null`, so every existing caller
    is unaffected) — joins to `ProductTagAssignment` and matches **any** of the given tag ids (an
    OR, not "must have all of them"), with `query.distinct(true)` to avoid a duplicate row per
    matching assignment when a product carries more than one of the requested tags.
    `ProductService.list`/`ProductServiceImpl.list`/`ProductApi.list`/`ProductController.list` all
    gained the matching `Set<Integer> tagIds` parameter.
  - New `ProductTagServiceImplTest` (mirrors `ProductCategoryServiceImplTest`'s shape, minus
    anything hierarchy-related) plus 4 new `ProductServiceImplTest` cases (tag assignment on
    create, an unknown-tag-id rejection, tag replacement on update, and the
    null-leaves-tags-unchanged case) — **182 unit tests total** (up from 165), verified via a real
    `mvn test` run (JDK 21) in this session.
    **`ProductTagServiceImplTest` needed an explicit `(JpaRepository<ProductTag, Integer>)` cast at
    two `verify(productTagRepository)...delete(...)` call sites** — `ProductTagRepository` extends
    both `JpaRepository` (`delete(T)`) and `JpaSpecificationExecutor` (which gained its own
    `delete(Specification<T>)` in a recent Spring Data JPA release), and `javac` can't disambiguate
    the two inherited overloads inside a `verify()` chain without the cast, even though production
    code's own direct `productTagRepository.delete(tag)` call has no such issue. Watch for the same
    ambiguity in any future test verifying a `delete(entity)` call against a repository that
    extends both interfaces.
  - **Not built as part of this pass** (deliberately, per the scope decision above): no
    `ProductSearchView` tag data (would need denormalizing tag ids/names into that CQRS read model
    via `ProductChangedOutboxEventHandler`, plus native-SQL query changes in
    `ProductSearchViewRepository`), and no `ShopPage` tag filter rail. `gui`'s own tag management
    page, `ProductFormPage`'s tag picker, and the admin product list's tag filter were Phase 2 —
    **now built**, see `gui/CLAUDE.md`'s own Product Tags note.
  - **Follow-up: seed data for `ProductTag`/`ProductTagAssignment`, per request.** New
    `service/seed/ProductTagSeeder` — mirrors `ProductCategorySeeder` exactly (extends `infra`'s
    `CsvSeeder<ProductTag>`, idempotency key is `name` itself rather than a decoupled `seedId`, same
    "fixed sample catalog, not long-lived production content" reasoning that class's own Javadoc
    gives — consistent with `ProductTag` itself having no status/lifecycle field either). Reads new
    `data/csv/product_tags.csv` (single `name` column: New Arrival, Best Seller, Limited Edition,
    Sale, Eco-Friendly, Staff Pick) and persists directly via `ProductTagRepository`, bypassing
    `ProductTagService` — creating a tag has no outbox event/read-model side effect the way creating
    a `Product` does, so there's no reason to route through the service layer here (unlike
    `ProductSeeder` itself, which must).
    - **Assignment doesn't get its own seeder** — there is no dedicated "seed a
      `ProductTagAssignment` row" step, because assignment already travels with
      `ProductCommands.Create.tagIds` (see the Phase 1 note above), and `ProductSeeder` already
      calls `productService.create` for every seed row. `products.csv` gained a new optional
      `tagNames` column (semicolon-joined tag names, the same compact inline convention
      `product_variants.csv`'s `attributes` cell already uses for its own key=value pairs) —
      `ProductSeeder` resolves each name to a `ProductTag` id via a new
      `ProductTagRepository.findByNameIgnoreCase` and passes the resulting `Set<Integer>` straight
      into `ProductCommands.Create` instead of the previous hardcoded `Set.of()`. An unknown tag
      name fails loudly (`IllegalStateException`, same "seed data typo should surface immediately"
      reasoning `categoryName`'s own lookup already uses) rather than being silently dropped; a
      blank/missing `tagNames` cell is a normal, valid "no tags" row, not an error — several seed
      products (e.g. "Segfault Desk Mat") are deliberately left untagged so the admin GUI's own
      empty-tags state isn't only ever exercised by hand.
    - **`EcommerceDataSeedingRunner` ordering**: `productTagSeeder.seed()` now runs right after
      `productCategorySeeder.seed()` and before `productSeeder.seed()` — tags and categories are
      independent of each other, but both must exist before `products.csv` can reference either by
      name (`categoryName`, now also `tagNames`).
    - `ProductTagRepository` gained `findByNameIgnoreCase(String): Optional<ProductTag>` — the seed
      lookup this whole change needed; the repository previously only had the `existsByNameIgnoreCase`
      variants used by `ProductTagServiceImpl`'s own uniqueness checks.
    - Verified via a targeted `mvn -pl ecommerce-service -am clean compile test` — all **182**
      existing unit tests still pass unchanged (no seeder has its own dedicated test in this
      reactor, matching `ProductCategorySeeder`/`ProductSeeder`'s own precedent — seeders are
      exercised by actually booting the app with `app.seed.enabled=true`, not unit-tested in
      isolation). Not run against a real database in this session, same caveat every seeder change
      in this reactor carries until the `docker compose` stack is actually started.
    - New `scripts/purge-seed-data.sql` (a plain dev utility, not a Liquibase changeset — lives
      outside `database/sql/` so it's never picked up as a migration) — `TRUNCATE ... RESTART
      IDENTITY CASCADE`s every table in the `ecommerce` schema in one statement, so every seeder's
      natural-key idempotency check (`name` existence, per each seeder's own Javadoc) sees a
      genuinely empty table on the next `app.seed.enabled=true` boot instead of silently skipping
      every row. Cart state (Redis, not Postgres) is out of scope — the script's own header comment
      notes the separate `redis-cli FLUSHDB` for that. **Correction to this note's own first draft**:
      it originally shipped with a redundant, separate `ALTER SEQUENCE ... RESTART WITH 1` per
      table, on the mistaken assumption that `RESTART IDENTITY` is a no-op for this reactor's
      explicit `*_SEQ` sequences — checking the actual migrations found every one of them **is**
      linked via `ALTER SEQUENCE ... OWNED BY` back to its column (e.g. `ALTER SEQUENCE
      ecommerce.PRODUCT_SEQ OWNED BY ecommerce.PRODUCT.PRODUCT_ID`), which is exactly the
      association `TRUNCATE ... RESTART IDENTITY` looks for — so plain `RESTART IDENTITY` already
      resets them correctly on its own, and the per-table statements were dead weight, not a
      necessary workaround. Root `CLAUDE.md`'s own "Sequences" convention note doesn't claim
      `OWNED BY` either way — don't assume "own sequence per table" implies "no `OWNED BY` link"
      without actually checking the migration.

- **AddressBook — a shopper's own reusable, multi-address book with a designated default,
  per request. Phase 1 (backend) complete; Phase 2 (GUI, including checkout wiring) is next.**
  Reverses `Address`'s own original Epic 2 scope lock ("single inline address, no saved address
  book" — see that class's own Javadoc, updated to point here). Three scope decisions asked and
  confirmed before building: (1) this pass also wires `CheckoutPage` to actually use the
  AddressBook (pick a saved address, not just build a separate management page); (2) each address
  gets an optional `label` field (e.g. "Home"/"Work"); (3) checkout also gets a "save this address
  for next time" quick-save checkbox, not just a dedicated management page.
  - New `entity/SavedAddress` — a full first-class entity (own lifecycle: create/edit/delete/
    set-default), deliberately **not** a reuse of `Address` (which stays a plain `@Embeddable`
    snapshotted onto `Order`, frozen at purchase time, no lifecycle of its own — see its own
    updated Javadoc). `ownerUuid` is a plain column, not a `User` foreign key — same
    "claims-only, no persisted row" shape `Order.ownerUuid` already established for this module.
    `defaultAddress` (not `isDefault`) is the boolean field name — deliberately, so Lombok's
    generated accessors (`isDefaultAddress()`/`setDefaultAddress(boolean)`) stay unambiguous
    (`isIsDefault()` is exactly the kind of accessor-naming landmine this dodges).
  - Migration `DKP-0039` (`202609020001__0.0.2__DKP-0039__add_saved_address_table.sql`) —
    `SAVED_ADDRESS` table, own `SAVED_ADDRESS_SEQ` (`OWNED BY`, same convention every other
    sequence here already uses). A plain btree index on `OWNER_UUID` (a genuine "list my
    addresses" query exists from day one, unlike `CUSTOMER_ORDER`'s own `OWNER_UUID` when that
    table was first created). **"At most one default per owner" is enforced twice** — a partial
    unique index (`UX_SAVED_ADDRESS_OWNER_DEFAULT ON SAVED_ADDRESS (OWNER_UUID) WHERE IS_DEFAULT
    = TRUE`, not a plain `UNIQUE(OWNER_UUID)`, since any number of *non*-default addresses is
    allowed per owner) as the real database-level guarantee, plus `SavedAddressServiceImpl`'s own
    app-level unset-then-set dance (via a new bulk `SavedAddressRepository.clearDefaultForOwner`)
    that's what actually makes the common path succeed instead of just failing loudly against the
    index.
  - New `EcommerceErrorCode.SAVED_ADDRESS_NOT_FOUND` (`SAVED_ADDRESS_001`) and
    `CHECKOUT_ADDRESS_REQUIRED` (`CHECKOUT_003`, for an incomplete ad-hoc checkout address — see
    below), `repository/SavedAddressRepository`, `service/SavedAddressCommands`
    (`Create`/`Update` records), `service/SavedAddressService`/`Impl`, `mapper/SavedAddressMapper`,
    `dto/{SavedAddressResponse,CreateSavedAddressRequest,UpdateSavedAddressRequest}`, and
    `api/SavedAddressApi`+`Controller` at `/api/v1/addresses` — **shopper-facing, never
    admin-gated** (no admin surface exists for this resource at all, unlike `ProductTag`/
    `ProductCategory` — an AddressBook entry has exactly one legitimate reader/writer, its own
    owner), same shape as `CartApi`/`OrderApi`. Every method resolves the caller via
    `@CurrentUserId` and is ownership-scoped (`findByIdAndOwnerUuid`, mirroring `OrderRepository`'s
    own `.filter(o -> o.getOwnerUuid().equals(callerUuid))` pattern as a derived query instead) —
    someone else's address id behaves exactly like a nonexistent one, same convention
    `OrderService` already established.
  - **Business rules, all in `SavedAddressServiceImpl`**: the caller's very first address is
    always auto-defaulted, regardless of whether `makeDefault` was requested — there's no sensible
    "no default while at least one address exists" state, and forcing an explicit set-default call
    just for the first address would be a pointless extra step for the overwhelmingly common case.
    Deleting the current default auto-promotes the caller's own most-recently-created remaining
    address (a no-op when it was the last one) — same "never leave the invariant broken" reasoning.
    `update` never touches the default flag at all — promoting an address is its own dedicated
    action (`POST /{id}/set-default`), kept separate from a plain field edit.
  - **Checkout integration (`AddressRequest`/`CheckoutCommands`/`CheckoutServiceImpl`)**: an order's
    shipping address can now come from either an existing AddressBook entry (`savedAddressId`) or a
    fresh, one-off entry (the original fields) — `AddressRequest`'s address fields are no longer
    `@NotBlank` (can't declaratively enforce "one or the other" anymore), so
    `CheckoutServiceImpl.resolveAddress` validates the actual choice imperatively (`Validator`,
    same idiom every other cross-field business rule in this reactor's service layer already
    uses) instead — throws `CHECKOUT_ADDRESS_REQUIRED` when neither a valid `savedAddressId` nor a
    complete ad-hoc address was given. New `CheckoutCommands.AddressSelection` wraps the two-shape
    choice (`savedAddressId`, `adHocAddress`, `saveAddress`, `label`) — `CheckoutService.confirm`'s
    signature changed to take this instead of a bare `AddressInput` (every call site — production
    and test — updated accordingly). Whichever address is actually chosen still gets copied into
    the same frozen `Address` `@Embeddable` snapshot as before (a new `toAddress(SavedAddress)`
    overload alongside the existing `toAddress(AddressInput)`) — an order's own shipping address
    must never change just because the `SavedAddress` it was copied from is later edited/deleted.
  - **The "save this address for next time" quick-save is deliberately best-effort** — a new
    `maybeSaveAddressForFuture`, called right after the order (and its stock reservations) has
    already been durably saved, wraps its `SavedAddressService.create` call in a bare try/catch
    (log-and-swallow, no rethrow): the order succeeding matters far more than this convenience side
    effect, so a failure here (unexpected, since nothing about this write depends on anything the
    checkout itself validated) must never roll back an order that has already reserved real stock.
    A no-op whenever `savedAddressId` was used instead (nothing new to save).
  - New `SavedAddressServiceImplTest` (mirrors `ProductTagServiceImplTest`'s shape) plus 6 new
    `CheckoutServiceImplTest` cases (saved-address resolution ignoring a simultaneously-present
    ad-hoc address, incomplete-ad-hoc-address rejection, quick-save firing with the right
    `SavedAddressCommands.Create`, quick-save *not* firing when a saved address was used, and the
    best-effort save-failure case not blocking the order) — **200 unit tests total** (up from 182),
    verified via a real `mvn test` run (JDK 21).
  - **`gateway`'s `GatewayRoutesConfig` gained a matching `/api/v1/addresses/**` route** in the
    same change — a lesson learned the hard way on Product Tags (see this file's own note on that
    feature, and `gateway/CLAUDE.md`'s matching note): a new endpoint on this service is not
    reachable from the GUI at all without it.
  - **GUI Phase 2 — now built**: `gui`'s "My Addresses" page, the `CheckoutPage` picker (saved
    address vs. a new one, with the quick-save checkbox), and the corresponding
    `types.ts`/`api/addressApi.ts` additions — see `gui/CLAUDE.md`'s own AddressBook note.

- **Coupon ("ProductDiscount" feature) — code-driven discounts targeting either the cart subtotal
  or the shipping fee, by percentage or fixed amount, with eligibility conditions. Phase 1 (data
  model + basic admin CRUD), Phase 2 (checkout integration/redemption), and Phase 4 (`gui`
  admin/checkout UI) are all built; only Phase 3 (product/category eligibility scoping) remains,
  deferred per the original phased plan.** Three scope decisions confirmed before building: (1) **coupon-code entry only** — no automatic/code-free
  promotions (that stays
  `shipping.FreeOverThresholdShippingFeeCalculator`'s own separate, unrelated mechanism); (2) a
  shopper may apply **at most 2 coupons per order — one per `CouponTarget`** (one `SUBTOTAL`, one
  `SHIPPING_FEE`), not open-ended stacking; (3) **"Full" eligibility conditions** — active flag,
  date range, minimum subtotal, and (Phase 3) per-product/category scoping plus global/per-user
  redemption limits, the last two enforced against a `CouponRedemption` ledger.
  - New `entity/Coupon` — `code` (normalized to uppercase before persisting, so a plain database
    `UNIQUE` constraint is correctly case-insensitive without needing `ProductTag.name`'s own
    functional `LOWER(NAME)` index trick), `target` (`enums.CouponTarget`: `SUBTOTAL`/
    `SHIPPING_FEE`), `type` (`enums.CouponType`: `PERCENTAGE`/`FIXED_AMOUNT`), `value`, `active`,
    `startAt`/`endAt` (both nullable — no lower/upper bound), `minSubtotal` (nullable — no
    minimum), `maxRedemptions`/`maxRedemptionsPerUser` (both nullable — no cap; enforced in Phase
    2). **Deliberately one entity with two small orthogonal enums, not four separate Strategy
    classes** (`PercentageOffSubtotal`/`FixedOffShipping`/etc.) — `target`×`type` is a genuine 2×2
    of independent choices, and four classes for that would be exactly the
    unnecessary-abstraction smell this reactor's own conventions warn against; a single
    `type`-branching calculation method (Phase 2) covers all four combinations. **Code is
    immutable after creation** — no rename via `update` (unlike `ProductTag.name`), since a coupon
    code is typically printed/shared externally once created.
  - New `entity/CouponRedemption` — the ledger `maxRedemptions`/`maxRedemptionsPerUser` will be
    enforced against once Phase 2 writes rows here, and the audit trail for "which coupon, how
    much" on a given order (`discountAmount` is a snapshot, not re-derived from the coupon's own
    — possibly since-edited — `value`). Real `@ManyToOne` FKs to both `Coupon` and `Order` —
    unlike `OrderLine.productVariantId`'s deliberately-plain-column shape, neither parent can ever
    be hard-deleted out from under a redemption row (a `Coupon` still in use is rejected at delete
    time; `Order` rows are permanent with no delete path anywhere in this module).
  - Migration `DKP-0041` (`202609020003__0.0.2__DKP-0041__add_coupon_tables.sql`) — `COUPON` +
    `COUPON_REDEMPTION`, own sequences, `CHECK` constraints on both enum columns
    (`CKC_COUPON_TARGET`/`CKC_COUPON_TYPE`) and on `VALUE > 0`, indexes on `COUPON_REDEMPTION`
    backing both the global and per-user redemption-count queries Phase 2 will run.
  - New `EcommerceErrorCode.COUPON_*` (`NOT_FOUND`/`CODE_CONFLICT`/`IN_USE`/`INVALID_VALUE`/
    `INVALID_DATE_RANGE`, `COUPON_001`–`005`), `repository/CouponRepository` (mirrors
    `ProductTagRepository`'s shape — `existsByCode`/`existsByCodeAndIdNot`/`findByCode`, the last
    for Phase 2's redemption lookup), `repository/CouponRedemptionRepository`
    (`existsByCouponId` backing the in-use delete guard, plus `countByCouponId`/
    `countByCouponIdAndOwnerUuid` already added for Phase 2 even though nothing calls them yet),
    `repository/spec/CouponSpecification` (`q`/`active`/`target` filters, this module's usual
    Specification-pattern convention for dynamic filtering), `service/CouponCommands`
    (`Create`/`Update` records — `Update` has no `code` field, since it's immutable),
    `service/CouponService`/`Impl` (create/update/delete/getById/paginated list — paginated, not
    unpaginated like `ProductCategoryService.list`, mirroring `ProductTagService`'s own paginated
    shape since coupons are expected to proliferate the same way free-form tags do),
    `mapper/CouponMapper`, `dto/{CouponResponse,CreateCouponRequest,UpdateCouponRequest}`, and
    `api/CouponApi`+`Controller` at `/api/v1/admin/coupons` (CRUD only — no redemption/validation
    endpoint yet, that's Phase 2's job). `CouponServiceImpl.validateValue` rejects a non-positive
    value and, for `PERCENTAGE` coupons specifically, a value over 100 — imperative validation via
    `Validator`, not Bean Validation annotations, since it's a cross-field rule (depends on
    `type`); `validateDateRange` similarly rejects `endAt` not after `startAt` when both are given.
  - **`gateway`'s `GatewayRoutesConfig` gained a matching `/api/v1/admin/coupons/**` route in the
    same change** — continuing the discipline established on Product Tags/AddressBook (see this
    file's own notes on both, and `gateway/CLAUDE.md`'s matching note): a new endpoint on this
    service is not reachable from the GUI at all without it.
  - New `CouponServiceImplTest` (mirrors `ProductTagServiceImplTest`'s shape) — value/date-range
    validation cases plus the standard CRUD/in-use-delete-guard cases — 220 unit tests total
    (up from 205) as of Phase 1, verified via a real `mvn test` run (JDK 21).
  - **Phase 2 — checkout integration/redemption, now built.** New
    `service/CouponRedemptionService`/`Impl` — deliberately **not** folded into `CouponService`
    (that interface stays pure admin CRUD): `resolve(code, target, ownerUuid, subtotal)` normalizes
    the code (uppercase/trim — the same normalization `Coupon.setCode` applies at creation, so a
    shopper's casing never matters), then checks, in order, `active` /
    `target == coupon.getTarget()` / `startAt`/`endAt` / `minSubtotal` (always checked against the
    cart subtotal, regardless of target) / global `maxRedemptions` (via
    `CouponRedemptionRepository.countByCouponId`) / per-user `maxRedemptionsPerUser` (via
    `countByCouponIdAndOwnerUuid`) — each its own `EcommerceErrorCode`
    (`COUPON_INACTIVE`/`COUPON_TARGET_MISMATCH`/`COUPON_NOT_YET_ACTIVE`/`COUPON_EXPIRED`/
    `COUPON_MIN_SUBTOTAL_NOT_MET`/`COUPON_REDEMPTION_LIMIT_REACHED`/
    `COUPON_ALREADY_REDEEMED_BY_USER`, `COUPON_006`–`012`). `calculateDiscount(coupon, baseAmount)`
    is a **separate** method from `resolve`, not folded into it — `minSubtotal` eligibility is
    always checked against the cart subtotal no matter which target the coupon has, but the
    discount arithmetic itself needs a *target-specific* base amount (the subtotal for a
    `SUBTOTAL` coupon, the shipping fee for a `SHIPPING_FEE` one); a single method computing both
    would need two different amounts passed in for what looks like one concern. Computes
    `value% × baseAmount` (percentage, `HALF_UP`, scale 2) or `value` directly (fixed amount),
    clamped to `[0, baseAmount]` — a fixed-amount coupon can never make a line go negative.
    `redeem(coupon, order, ownerUuid, discountAmount)` persists one `CouponRedemption` row — **the
    redemption-count check inside `resolve` is a plain re-check inside the caller's own
    transaction, not an atomic claim-style `UPDATE`** (unlike `ProductVariantRepository.reserve`'s
    stronger guarantee) — a deliberate v1 simplification; two concurrent redemptions of the last
    slot on a tightly-capped coupon could both pass the check in the same instant. Revisit with a
    conditional `@Modifying` claim (mirroring `reserve`) if that gap ever matters in practice.
  - `CheckoutService.preview`/`.confirm` both gained `subtotalCouponCode`/`shippingCouponCode`
    parameters (`null`/blank = none — the type-level embodiment of Phase 1's "at most 2 coupons,
    one per target" decision, not a `List`). `CheckoutServiceImpl`'s new private `resolveDiscounts`
    resolves each non-null code independently (either, neither, or both may be present) before
    building totals, returning a private `Discounts` record (`subtotalCoupon`/`subtotalDiscount`/
    `shippingCoupon`/`shippingDiscount`/`finalShippingFee`). `preview` **never redeems** — no
    `CouponRedemption` row, no limit consumed, so previewing repeatedly (or with different
    candidate codes) never burns down a coupon's own redemption cap; `confirm` calls
    `couponRedemptionService.redeem` for each non-null coupon **only after** `orderRepository.save`
    succeeds (`Mockito.inOrder`-verified — same discipline the existing
    save-then-clear-cart/save-then-reserve orderings already follow in this class). New
    `Order.subtotalDiscountAmount`/`subtotalCouponCode`/`shippingCouponCode` columns (migration
    `DKP-0042`, `202609020004`) — `subtotalDiscountAmount` needed its own column since
    `Order.subtotal` itself is never reduced (the true pre-discount sum, same "never re-derive a
    historical value" principle `shippingFee`/`originalShippingFee` already established); the
    shipping discount needed **no** equivalent amount column — a coupon-waived shipping fee reuses
    the exact same `shippingFee`/`originalShippingFee` pair the free-shipping-threshold follow-up
    already added, since "actual charged vs. what it would have been absent a waiver" is exactly
    the same shape whether the waiver came from the automatic strategy or a coupon. `confirm` sets
    `order.setShippingFee(discounts.finalShippingFee())` (replacing the value the shipping
    strategy quoted) while `originalShippingFee` keeps reporting the pre-discount amount from the
    strategy's own quote — a shipping coupon and the automatic free-over-threshold waiver compose
    for free through this same pair, with no special-casing needed for "both applied at once."
    `CheckoutPreview`/`CheckoutPreviewResponse` gained a matching `subtotalDiscountAmount` field
    (via `CheckoutMapper.toPreviewResponse`); `OrderResponse` gained
    `subtotalDiscountAmount`/`subtotalCouponCode`/`shippingCouponCode` (via `OrderMapper.toResponse`).
    **`CheckoutMapper.toConfirmResponse` was deliberately not updated** — the GUI navigates away
    from the confirm response immediately with nothing reading it, the same precedent
    `originalShippingFee` set when it was first added to this class. `AddressRequest` (the
    `confirm` request body) gained matching `@Size(max = 50)` `subtotalCouponCode`/
    `shippingCouponCode` fields; `CheckoutApi.preview` gained matching
    `@RequestParam(required = false)` params.
  - New `CouponRedemptionServiceImplTest` (`Resolve`/`CalculateDiscount`/`Redeem` nested classes —
    every eligibility branch including exact-boundary cases for `startAt`/`minSubtotal`, both
    redemption-limit checks, percentage/fixed-amount/clamped-to-base-amount arithmetic, and a
    `redeem` case asserting every field on the persisted `CouponRedemption` via `ArgumentCaptor`)
    plus new `CheckoutServiceImplTest` cases (a subtotal coupon applied without touching the
    shipping fee, a shipping coupon applied on top of whatever the automatic strategy already
    charges, an ineligible coupon's rejection propagating straight through `preview`, and
    `confirm` persisting both coupon codes/amounts and redeeming only after the order itself
    saved) — **242 unit tests total** (up from 220), verified via a real `mvn test` run (JDK 21).
  - **Phase 4 — `gui` admin coupon-management page + checkout code-entry UI, now built** (no
    backend change needed — everything this pass needed was already exposed by Phases 1–2). See
    `gui/CLAUDE.md`'s own Coupons note for the full detail: `CouponListPage`/`CouponFormDialog`
    (admin CRUD, mirroring `ProductTagListPage`'s shape), `CheckoutPage.tsx`'s per-target Apply/
    Remove code fields, and `OrderDetailPage.tsx`'s persisted-coupon/discount display. That pass
    also caught and fixed a GUI display bug this phase's own partial-discount shipping coupons
    exposed: the existing "struck-through original + Free label" shipping treatment assumed any
    waiver meant a $0 charge, true only before this phase (when the only waiver source was
    `FreeOverThresholdShippingFeeCalculator`'s all-or-nothing threshold) — a `SHIPPING_FEE`
    coupon can discount partially, leaving `shippingFee > 0` while `originalShippingFee >
    shippingFee` still holds.
  - **Follow-up: `maxDiscountAmount` (a cap on a single redemption's discount) and `description`
    (a shopper-facing summary), per request.** `maxDiscountAmount` closes a real gap in the
    original design: a `PERCENTAGE` coupon's discount was otherwise unbounded on a large-enough
    cart (e.g. "20% off" on a $500 subtotal is a $100 discount) — the exact motivating example was
    "reduce 20%, maximum $20, for an order with subtotal > $100," which the seed data's `VIP20`
    coupon now reproduces verbatim (`value=20`, `type=PERCENTAGE`, `minSubtotal=100.00`,
    `maxDiscountAmount=20.00`). New `Coupon.maxDiscountAmount`/`description` columns (migration
    `DKP-0043`, `202609020005`, both nullable — `maxDiscountAmount` has a `CHECK (... IS NULL OR
    ... > 0)`, `description` is purely presentational with no constraint beyond a 255-char
    `@Size`). `CouponRedemptionServiceImpl.calculateDiscount` applies the cap **after** the raw
    percentage/fixed calculation but **before** the existing base-amount clamp, and **uniformly to
    both `CouponType` values, not just `PERCENTAGE`** — a `FIXED_AMOUNT` coupon capped below its
    own `value` is a valid, if unusual, admin choice (further reducing an already-fixed discount),
    not a state worth rejecting; see `Coupon`'s own updated Javadoc. `description` is never read
    by `CouponRedemptionService` at all — it exists purely for a future `gui` dialog letting a
    shopper browse which coupons they can apply, rather than requiring they already know a code
    (not built yet — this follow-up is the data-model half only). `CouponCommands.Create`/`Update`,
    `CreateCouponRequest`/`UpdateCouponRequest` (`@DecimalMin`/`@Size` validation, mirroring the
    existing `value`/`code` fields' own annotations), `CouponResponse`, and `CouponController`'s
    command construction all gained both fields — `CouponMapper` needed no change (plain
    field-name auto-mapping already covers them). `CouponSeeder`/`coupons.csv` gained both columns
    too, with every one of the 8 seed coupons now carrying a real `description`. New
    `CouponServiceImplTest` cases (`persistsMaxDiscountAmountAndDescription`/
    `updatesMaxDiscountAmountAndDescription`) and 4 new `CouponRedemptionServiceImplTest.
    CalculateDiscount` cases (a percentage discount actually capped, one left untouched below its
    cap, a fixed-amount discount capped below its own value, and confirmation that the cap never
    overrides the base-amount clamp) — **248 unit tests total** (up from 242), verified via a real
    `mvn test` run (JDK 21). `gui`'s `CouponFormDialog`/`CouponListPage` updated to match — see
    `gui/CLAUDE.md`'s own Coupons note.
  - **Follow-up: `Coupon.imageUrl` — a promo banner/icon for the same future coupon-picker
    dialog, per request.** New `Coupon.imageUrl` column (migration `DKP-0044`, `202609020006`,
    nullable `VARCHAR(500)`). **Deliberately a permanent, unsigned URL, not a presigned one** —
    same choice `ProductDescriptionImageService` already made for `Product.description`'s own
    inline images, and for the identical reason: a `Coupon` has no "not-yet-published/deactivated"
    access-control concern the way a `Product`'s gallery does (see `StorageService`'s own Javadoc),
    so there's no reason to pay a presigned URL's re-signing-on-every-read cost here — a coupon's
    whole purpose is being shown to shoppers. New `service/CouponImageService`/`Impl` — **not**
    part of `CouponService`, mirroring `ProductDescriptionImageService`'s own split from
    `ProductService` for the identical reason: this touches no existing `Coupon` row (no
    `couponId`), so it works in create mode too, and the returned URL is set as a plain field on
    the create/update request afterward rather than resolved by an id. Delegates to `infra`'s
    `StorageService.uploadPublicImage` (already imported by this module's `EcommerceServiceApplication`
    for the description-image use case — no new `@Import` needed). New `api/CouponImageApi`+
    `Controller` at `POST /api/v1/admin/coupons/images/upload` — a separate resource from
    `CouponApi`, nested under `/api/v1/admin/coupons` purely so `gateway`'s existing
    `/api/v1/admin/coupons/**` route already covers it, not because it's part of that resource
    (byte-for-byte the same reasoning `ProductDescriptionImageApi`'s own Javadoc documents). New
    `dto/CouponImageResponse` (`url` only, mirrors `ProductDescriptionImageResponse`). `Coupon`/
    `CouponCommands`/`Create`+`UpdateCouponRequest`/`CouponResponse`/`CouponController` all gained
    a matching `imageUrl` field. New `CouponImageServiceImplTest` (mirrors
    `ProductDescriptionImageServiceImplTest`) plus a new `CouponServiceImplTest.persistsImageUrl`
    case — **250 unit tests total** (up from 248), verified via a real `mvn test` run (JDK 21). No
    `gateway` route change needed. `CouponSeeder`/`coupons.csv` were **not** extended with real
    images — every seeded coupon has `imageUrl = null` for now (a legitimate, documented state —
    "an admin simply never bothered to illustrate," per the migration's own comment), since there's
    no real static asset to reference from seed data without generating placeholder banners the
    way `ProductImageSeeder` does for the catalog; revisit if the future picker dialog's own design
    review wants seeded coupons to look fully populated. `gui`'s `CouponFormDialog`/`CouponListPage`
    updated to match — see `gui/CLAUDE.md`'s own Coupons note.
  - **Follow-up: the shopper-facing coupon picker dialog itself, per request — the "future gui
    dialog" `description`/`imageUrl` were built for is now real.** New `service/CouponRedemptionService
    #listAvailable(target, ownerUuid)` — lists every currently-redeemable coupon for a target
    (active, within its date range, not yet exhausted globally or by the caller) — deliberately
    does **not** filter on `minSubtotal` the way `resolve` does: a browsable list is more useful
    showing every offered coupon with its own condition attached than silently hiding ones the
    shopper doesn't yet qualify for (see that method's own Javadoc). New
    `CouponRepository.findAllByTargetAndActiveTrueOrderByValueDesc` backs the cheap part of that
    query (active+target); date-range and redemption-limit filtering happen afterward in Java,
    mirroring `resolve`'s own per-coupon checks rather than a complex correlated-subquery
    Specification — the list is small enough that the same N+1-count-query shape `resolve` already
    uses per code is fine applied to a handful of coupons too. New `dto/AvailableCouponResponse`
    — deliberately leaner than admin's own `CouponResponse` (no `id`/`active`/`startAt`/redemption-
    limit counters — all irrelevant once a coupon is already filtered to "currently redeemable"),
    plus one field with no admin counterpart at all: `eligible`, computed per-request against the
    caller's live cart subtotal (not persisted) via a new hand-written `CouponMapper
    #toAvailableResponse(Coupon, BigDecimal subtotal)` default method — hand-written for the same
    reason `CartMapper`/`CheckoutMapper` hand-write their own aggregate methods: `eligible` isn't a
    `Coupon` field MapStruct could ever discover on its own. New `api/CouponPickerApi`+`Controller`
    at `GET /api/v1/coupons/available?target=&subtotal=` — a **new top-level prefix**
    (`/api/v1/coupons/**`, distinct from admin's own `/api/v1/admin/coupons/**`), authenticated-
    shopper-facing same as `CartApi`/`CheckoutApi`/`OrderApi` (falls under the existing default
    `anyRequest().authenticated()` rule, no `SecurityConfig` change needed) — mirrors the
    `OrderApi`/`AdminOrderApi` split for the identical "same resource, different audience" reason.
    **`gateway`'s `GatewayRoutesConfig` gained a matching `/api/v1/coupons/**` route in the same
    change** — continuing the discipline this file's own AddressBook/Product-Tags/Coupon-Phase-1
    notes already established (see `gateway/CLAUDE.md`'s matching note): a genuinely new top-level
    prefix needs its own route, not just a sub-path of an existing one. Never validates/redeems
    anything itself — `CheckoutApi`'s own `preview`/`confirm` remain the real source of truth once
    a shopper actually picks a code and it gets submitted. New `CouponRedemptionServiceImplTest
    .ListAvailable` (8 cases: no-conditions success, not-yet-started, exactly-at-start-boundary,
    expired, at/below the global limit, the caller's own per-user limit exhausted, and that the
    query is scoped by the requested target) — **258 unit tests total** (up from 250), verified via
    a real `mvn test` run (JDK 21). `gui`'s new `components/CouponPickerDialog.tsx` (opened from
    `CheckoutPage.tsx`'s own coupon-entry section) is the actual consumer — see `gui/CLAUDE.md`'s
    own Coupons note.
  - **Follow-up: a real conflict between this feature and `shipping.FreeOverThresholdShippingFeeCalculator`
    (which had been the active `ShippingFeeCalculator` bean since an earlier, pre-Coupon-feature
    follow-up), found and fixed once both mechanisms coexisted, per request.** Once a shopper's
    cart already qualified for that threshold's automatic waiver (`shippingFee` already `0`), a
    `SHIPPING_FEE` coupon could still "apply" successfully — `CouponRedemptionServiceImpl
    .calculateDiscount` clamps to the base amount it's discounting off of, so the computed discount
    was silently `0` — yet `redeem()` still ran regardless, consuming a real, possibly
    limited-use `CouponRedemption` for zero actual benefit, with the `gui` showing the exact same
    "Free" line before and after. Rather than add cross-mechanism guard logic (e.g. rejecting a
    `SHIPPING_FEE` coupon in `resolve()` whenever the strategy's own quote already zeroed the fee),
    the simpler fix was to stop running two independent shipping-discount mechanisms at once:
    `FlatRateShippingFeeCalculator` is the active `ShippingFeeCalculator` bean again (swapped back
    via the usual `@Component` move — see both classes' own updated Javadoc, and this file's own
    checkout section above for the full incident writeup), so coupons are now the *only* thing that
    ever discounts a shipping fee. No code/entity/migration changes were needed beyond the
    `@Component` swap itself and updating `application.yml`'s own comment
    (`free-shipping-threshold` is left defined but unread, in case
    `FreeOverThresholdShippingFeeCalculator` is ever switched back in). `gui`'s `CheckoutPage.tsx`/
    `OrderDetailPage.tsx` had their own explanatory comments (not their actual logic — the
    struck-through-original-fee display was already correct and remains exactly as needed) updated
    to stop citing the now-inactive automatic waiver as a possible source of a shown discount.
  - **Follow-up: the coupon picker now sorts by what's actually best for this order, per request —
    not by a coupon's own declared `value`.** The `CouponRepository` query behind `listAvailable`
    still orders by `value DESC` at the DB level (a cheap default, now purely a tie-break), but a
    `PERCENTAGE` coupon's raw `value` alone doesn't say how much money it actually saves —
    especially once `maxDiscountAmount` caps it, or once two coupons of different `type`
    (`PERCENTAGE` vs `FIXED_AMOUNT`) are compared at all. New `CouponRedemptionService
    .listAvailableRanked(target, ownerUuid, subtotal, baseAmount)` — computes each coupon's real
    `eligible` flag and `discountAmount` (via the existing `calculateDiscount`) and sorts
    eligible-first, then by `discountAmount` descending within each group; an ineligible coupon
    always sorts after every eligible one regardless of size, since it can't be applied right now
    no matter how much it would theoretically save. Returns a new nested
    `CouponRedemptionService.RankedCoupon(Coupon, boolean eligible, BigDecimal discountAmount)`
    record — deliberately not the REST DTO itself, so the service stays free of any web-layer
    dependency. **`CouponPickerController` stays a thin pass-through** (per this reactor's own
    "business logic belongs in the service layer" convention) — it resolves which amount each
    coupon's discount should be computed against (`subtotal` for `SUBTOTAL`, the new
    `shippingFee` request param for `SHIPPING_FEE` — the same target-based choice
    `CheckoutServiceImpl.resolveDiscounts` already makes for the real checkout path) and maps each
    `RankedCoupon` straight to an `AvailableCouponResponse`, nothing more. `CouponMapper
    .toAvailableResponse` simplified to accept the already-computed `eligible`/`discountAmount`
    directly (no longer needs `subtotal` itself, since it no longer derives `eligible` on its
    own). `AvailableCouponResponse` gained `discountAmount` — informational, same as `eligible`,
    and now what the `gui` shows as "Save $X" per row too. `CouponPickerApi.listAvailable` gained
    an optional `shippingFee` query param (the caller's current quoted fee, e.g. the checkout
    preview's own `originalShippingFee` — ignored for `SUBTOTAL` requests, treated as `0` if
    omitted for `SHIPPING_FEE` ones rather than erroring). New `CouponRedemptionServiceImplTest
    .ListAvailableRanked` (3 cases: ranks by real discount over declared value, eligible always
    before ineligible regardless of size, `eligible` computed against `subtotal` independently of
    `baseAmount`) — **261 unit tests total** (up from 258), verified via a real `mvn test` run
    (JDK 21). `gui`'s `CouponPickerDialog.tsx` passes the results straight through in the order
    they arrive (no client-side sort of its own) — see `gui/CLAUDE.md`'s own Coupons note.
  - **Not built as part of this pass**: product/category eligibility scoping (Phase 3, needs join
    tables mirroring `ProductTagAssignment`'s explicit-join shape) — the one remaining deferred
    phase. A shopper can now redeem a coupon end-to-end and browse which ones are available without
    already knowing a code; the only gap left is that a coupon can't yet be restricted to specific
    products/categories.

- **Follow-up: a `phone` field on `Address`/`SavedAddress`/`Order`, per request** — a shipping
  contact number, threaded through both address paths (an existing AddressBook entry and a fresh,
  one-off address at checkout) the same way `fullName` already is. `entity/Address` (the
  `@Embeddable` snapshotted onto `Order`) and `entity/SavedAddress` both gained a `phone` column —
  **nullable at the DB level**, unlike every sibling address column (all `NOT NULL` from each
  table's very first migration), because this one was added after both tables already had real
  rows with nothing to backfill it from; there's no historically-correct placeholder the way
  `SUBTOTAL_DISCOUNT_AMOUNT` (DKP-0042) could default to `0`. Required going forward regardless, at
  the application layer only: `CreateSavedAddressRequest`/`UpdateSavedAddressRequest` both add
  `@NotBlank`/`@Size(max = 30)`, and `CheckoutServiceImpl.resolveAddress`'s own imperative
  completeness check (the same `Validator`-based check `fullName`/`line1`/`city`/`state`/
  `postalCode`/`country` already go through) now requires `phone` too for a fresh, ad-hoc checkout
  address. An order copied from an old `SavedAddress` that predates this column is the only way a
  `null` can still reach a real order today. New migration `202609020007__...__DKP-0045__
  add_address_phone_column.sql` — `ALTER TABLE ... ADD COLUMN IF NOT EXISTS PHONE VARCHAR(30)` on
  both `ecommerce.CUSTOMER_ORDER` and `ecommerce.SAVED_ADDRESS`. Threaded through every layer in
  between: `AddressRequest`/`AddressResponse`/`CreateSavedAddressRequest`/
  `UpdateSavedAddressRequest`/`SavedAddressResponse` (all gained `phone`), `CheckoutCommands
  .AddressInput`/`SavedAddressCommands.Create`/`.Update` (all gained a `phone` component, positioned
  right after `fullName` in every one, purely by convention), `OrderMapper.toAddressResponse`, and
  both `toAddress(...)` overloads in `CheckoutServiceImpl`. `SavedAddressMapper` needed no change at
  all — MapStruct auto-maps the new same-named field on both sides. Existing
  `CheckoutServiceImplTest`/`SavedAddressServiceImplTest` cases updated for the new constructor
  arity (no new test cases — this is a pure field threading exercise, not new branching logic) —
  **261 unit tests total, unchanged**, verified via a real `mvn test` run (JDK 21). `gui`'s
  `AddressFormDialog.tsx`/`CheckoutPage.tsx`'s own address form both gained a required Phone Number
  field; `AddressBookPage.tsx`/`OrderDetailPage.tsx`/`CheckoutPage.tsx`'s `formatSavedAddress` all
  show it where present — see `gui/CLAUDE.md`'s own note.

- **Follow-up: an `email` field on `Address`/`SavedAddress`/`Order` too, per request —
  deliberately independent of the caller's Keycloak login email.** Prompted by a direct question:
  "the application normally sends invoice info to the user's email — shall we add the email column
  into those entities too?" The recommendation given first was to *not* add one and instead resolve
  the invoice recipient from the JWT's own `email` claim (`KeycloakJwtAuthenticationConverter` →
  `CustomOAuth2User.email`, shared via `infra.security` — the same claims-only shape `ownerUuid`
  already uses), since this reactor already has that claim on every authenticated request. The user
  clarified the actual requirement: **a shopper's login email and the email they want an invoice
  sent to can legitimately differ** (a shared inbox, an accountant, etc.) — a real per-address
  contact detail, not an account attribute, which the JWT claim alone can't capture. Implemented by
  mirroring `phone` exactly, field-for-field: `entity/Address`/`entity/SavedAddress` both gained an
  `email` column, positioned right after `phone` everywhere — **nullable at the DB level** (new
  migration `202609020008__...__DKP-0046__add_address_email_column.sql`, same "no backfill" reasoning
  as `phone`'s own `DKP-0045`, `VARCHAR(255)` matching this reactor's existing `EMAIL` column
  convention — `identity.USER.EMAIL`/`social.PROFILE.EMAIL`), but required for every fresh write at
  the application layer: `CreateSavedAddressRequest`/`UpdateSavedAddressRequest` both gained
  `@NotBlank`/`@Email`/`@Size(max = 255)` (the one difference from `phone` — an actual format
  constraint, since an email has real syntax `phone` doesn't), and
  `CheckoutServiceImpl.resolveAddress`'s own imperative completeness check now requires `email` too
  for a fresh, ad-hoc checkout address. Threaded through the exact same layers `phone` was:
  `AddressRequest`/`AddressResponse`/`SavedAddressResponse`, `CheckoutCommands.AddressInput`,
  `SavedAddressCommands.Create`/`.Update`, `OrderMapper.toAddressResponse`, both
  `CheckoutServiceImpl.toAddress(...)` overloads — `SavedAddressMapper` again needed no change
  (MapStruct auto-maps). `CheckoutServiceImplTest`/`SavedAddressServiceImplTest` updated for the new
  constructor arity only — **261 unit tests total, unchanged**, verified via a real `mvn test` run
  (JDK 21). `gui`'s `Address`/`SavedAddress` types gained an optional `email` (same "doubles as
  local form state" reasoning as `phone`); `CreateSavedAddressPayload`/`UpdateSavedAddressPayload`
  gained a required one. `AddressFormDialog.tsx`/`CheckoutPage.tsx`'s own address form both gained a
  required "Email" field with a lightweight client-side format check (a plain regex — the backend's
  own `@Email` is the real validation); `AddressBookPage.tsx`/`OrderDetailPage.tsx` show it
  conditionally, same idiom as `phone`/`line2`; `CheckoutPage.tsx`'s `formatSavedAddress` folds
  `phone`/`email` together into one contact summary (`fullName · phone · email, line1, ...`) when
  present. See `gui/CLAUDE.md`'s own note.

- **`ProductVariant.attributes` category-schema follow-up — the "Option B" global attribute
  registry, per request.** Prompted by a design discussion: variant attributes vary by category
  (Clothes → size/color; Computer Accessories → model/color) and are sometimes split into separate
  keys vs. combined into one ("50x50 Black"). Two options were sketched and compared before
  building either: **Option A** (each `ProductCategory` types its own attribute names inline, no
  reuse) vs. **Option B** (a shared, global `ProductAttribute` registry with a controlled
  `ProductAttributeValue` vocabulary, assigned to categories via a join) — **Option B was chosen**,
  specifically so a concept like "Color" has exactly one spelling and one shared value list
  everywhere it's assigned, rather than typed independently (and inconsistently) per category. New
  entities, named with this module's own `Product*` prefix convention (not the bare `Attribute`
  sketched during the design discussion) to fit alongside `ProductTag`/`ProductCategory`, and to
  dodge any ambiguity with `ATTRIBUTE` as a SQL identifier:
  - `entity/ProductAttribute` — global, e.g. "color", `name` unique case-insensitively (same
    functional-index treatment as `ProductTag.name`) and matched *literally, case-sensitively*
    against a `ProductVariant.attributes` map key (there's no id to look up by inside that map —
    see the entity's own Javadoc). Owns `values` by cascade (`ALL`/`orphanRemoval`, ordered by
    `displayOrder`) — an attribute's vocabulary is always edited as one whole list in one admin
    form, not independent add/remove-one-value endpoints, so `ProductAttributeServiceImpl.create`/
    `.update` both take the complete `List<String>` and clear-and-rebuild, list position becoming
    `displayOrder` (no caller-supplied order number at all — this sidestepped an entire class of
    "duplicate sort order" validation `ProductImage`'s own sort order needed).
  - `entity/ProductAttributeValue` — one controlled-vocabulary entry (e.g. "Red"), cascade-owned by
    `ProductAttribute.values`, never written independently.
  - `entity/ProductCategoryAttribute` — the many-to-many join declaring a category expects a given
    attribute, plus a per-assignment `required` flag — an explicit join entity (not a bare
    `@ManyToMany`), mirroring `ProductTagAssignment`'s own audit-columns-on-the-join-row reasoning
    exactly. Cascade-owned by the new `ProductCategory.categoryAttributes` collection (mirrors
    `Product.productTagAssignments`' own ownership shape) — `ProductCategoryServiceImpl` gained a
    matching `applyCategoryAttributes` (clear-and-rebuild, list position → `displayOrder`, same
    "no caller-supplied order number" simplification as `ProductAttributeValue` above).
    `ProductCategoryService.create`/`.update` both gained a `List<AttributeAssignmentInput>`
    parameter (a nested record, `{attributeId, required}` — no `displayOrder` field) with the same
    three-state semantics `ProductCommands.Update.tagIds` already established (`create`: `null` →
    empty; `update`: `null` → leave unchanged, `[]` → clear).
  - **Enforcement was originally real, not just advisory — explicitly requested at the time over
    the alternative (leave it purely informational) — but this was later reversed, per a follow-up
    request; see the dedicated follow-up bullet below for the full detail.** The original design:
    `ProductServiceImpl.validateAttributesAgainstCategory` ran in `create`, `addVariant`, and
    (re-validating existing variants) `update` whenever the product's category changed — a category
    with zero `ProductCategoryAttribute` assignments stayed free-form; once a schema existed, every
    key present in a variant's `attributes` map had to be one of the category's assigned attribute
    names, every `required`-flagged name had to be present, and each present value had to be one of
    that attribute's own defined values. **None of this is enforced anymore** — kept here only as
    history for why `ProductCategoryAttribute`/`ProductAttribute` still exist and still shape the
    admin GUI even though nothing server-side validates against them.
  - New `api/ProductAttributeApi`+`Controller` at `/api/v1/admin/product-attributes` (full CRUD,
    paginated list, mirrors `ProductTagApi`'s own shape exactly), `dto/{Create,Update}
    ProductAttributeRequest` (a `name` plus a `List<String> values`, both validated —
    `@NotEmpty`/element-level `@NotBlank`/`@Size` via Bean Validation's container-element-constraint
    support, `List<@NotBlank String>`), `dto/ProductAttribute{,Value}Response`,
    `mapper/ProductAttributeMapper`. `ProductCategoryApi`'s own `Create`/`UpdateProductCategoryRequest`
    gained an `attributes: List<CategoryAttributeAssignmentRequest>` field (same three-state
    semantics as the service layer); `ProductCategoryResponse` gained a matching `attributes` field
    (`CategoryAttributeAssignmentResponse` — ids only, not the full attribute, mirroring
    `ProductResponse.tagIds`'s own "ids only" precedent; a future admin GUI cross-references its
    own attribute list by id rather than this response denormalizing a name).
  - **`gateway`'s `GatewayRoutesConfig` needed its own new `/api/v1/admin/product-attributes/**`
    route** — added in the same change, though only after that module's own standing warning about
    this exact class of gap was re-read (see `gateway/CLAUDE.md`'s own note on this).
  - New migration `202609020009__...__DKP-0047__add_product_attribute_tables.sql` —
    `PRODUCT_ATTRIBUTE`/`PRODUCT_ATTRIBUTE_VALUE`/`PRODUCT_CATEGORY_ATTRIBUTE`, each following this
    module's own established sequence/audit-column/case-insensitive-functional-index conventions.
  - New `ProductAttributeServiceImplTest` (mirrors `ProductTagServiceImplTest`'s shape, plus
    values-list management cases) and a new `CategoryAttributes` nested class in
    `ProductCategoryServiceImplTest`, plus new cases in `ProductServiceImplTest`'s `Create`/
    `AddVariant`/`Update` for the enforcement itself — **288 unit tests total** (up from 261),
    verified via a real `mvn test` run (JDK 21). `ProductServiceImplTest`'s own shared
    `productWithId` fixture had to gain a bare `new ProductCategory()` (previously left `null`) —
    `addVariant`'s new call to `product.getProductCategory().getCategoryAttributes()` would
    otherwise NPE on every pre-existing test built on that fixture, a real gap the full-suite run
    caught immediately.
  - **Backend only this round, per explicit scope decision** — no GUI admin management page,
    category-form integration, or `ShopPage.tsx` facet-rail consumption yet. The API shape
    (`GET /api/v1/admin/product-attributes`, `ProductCategoryResponse.attributes`) is deliberately
    already GUI-ready for that follow-up.
  - **Follow-up: `ProductVariantEditor` GUI adapted for two things, per request — editing an
    existing variant, and suggesting a variant's attribute rows from its product's category
    schema (this feature's own admin GUI, deferred above, landed as part of this same change).**
    This needed a genuinely new backend endpoint first: `ProductApi`/`ProductController` had
    add/remove for a variant but no update — `PUT /api/v1/admin/products/{id}/variants/{variantId}`
    (`ProductApi.updateVariant`/`ProductController.updateVariant`) is new, reusing the existing
    `ProductVariantRequest`/`ProductVariantResponse` DTOs (no new request/response shape needed).
    New `ProductService.updateVariant`/`ProductServiceImpl.updateVariant` — every mutable field
    (SKU, price, stock quantity, attributes) is **fully replaced**, mirroring `update`'s own
    "full replace, not a partial patch" contract for a product's basic fields, not a partial PATCH.
    Runs the same validation chain `addVariant` does, adapted for "editing one of several existing
    variants" rather than "adding a brand-new one": a SKU conflict check only fires when the SKU
    actually changed (new `ProductVariantRepository.existsBySkuAndIdNot`, excluding the variant's
    own row); `validateAttributeKeysMatchExisting` (US-1.6's "every variant shares one key set")
    gained an `excludingVariantId` parameter so a variant being edited is never compared against
    its own about-to-change key set (which would trivially match and defeat the check entirely) —
    it now picks its reference from the product's *other* variants, same reasoning `addVariant`'s
    reference variant already avoided by construction (a brand-new variant is never yet in the
    list to exclude). At the time this was written, `updateVariant` also ran
    `validateAttributesAgainstCategory` against the product's current category — **that call, and
    the method itself, were removed in a later follow-up below**, once category-schema enforcement
    was reversed to advisory-only. **New guard, not present on `addVariant` at all**: a new
    `PRODUCT_VARIANT_STOCK_BELOW_RESERVED` (`PRODUCT_VARIANT_005`) rejects a stock-quantity edit
    that would drop below the variant's own `reservedQuantity` — `addVariant` never needed this
    (a brand-new variant always starts at `reservedQuantity = 0`), but an existing variant may
    already have live reservations (Epic 3's two-column reservation model), and letting
    `stockQuantity` fall below `reservedQuantity` would otherwise only be caught later, as a raw,
    unfriendly DB `CHECK` constraint violation on save. 9 new `ProductServiceImplTest` cases (a new
    `UpdateVariant` nested class) — **297 unit tests total** (up from 288), verified via a real
    `mvn test` run (JDK 21). No `gateway` route change needed — `/api/v1/admin/products/**`
    already covers the new sub-path.
    - **GUI**: `ProductVariantEditor.tsx` gained a per-row Edit `IconButton` (alongside the
      existing Remove one) opening `ProductVariantDialog.tsx` in edit mode — the dialog now takes
      an `editingVariant: DisplayVariant | null` prop that prefills every field and switches the
      title ("Add Variant"/"Edit Variant")/submit label ("Add"/"Save"); the editor owns which
      callback to invoke (`onAdd` vs. the new `onUpdate(id, input)`) based on whether a variant is
      currently being edited. `requiredAttributeKeys` (the cross-variant key-consistency lock) is
      now computed excluding whichever variant is being edited, mirroring the backend's own
      `excludingVariantId` fix above. `useDraftVariants` (create-mode's local unsaved-variant
      list) gained a matching `updateDraftVariant`, and `ecommerceApi.ts` gained `updateVariant`.
    - **GUI, suggested attributes (superseded by the follow-up below — kept as history of the
      original, stricter design).** New `hooks/useCategoryAttributeSuggestions.ts` resolves the
      selected category's own `ProductCategoryAttribute` assignments (ids only, per
      `CategoryAttributeAssignmentResponse`) into a ready-to-render `SuggestedAttribute[]`
      (name/required/controlled-vocabulary), by fetching the full category list and the full
      attribute registry once (the same "just fetch everything for a picker" convention
      `ProductCategoryFormDialog.tsx`'s own Attributes section already uses) and cross-referencing
      by id — this part is unchanged by the follow-up below. `ProductFormPage.tsx` computes this
      once from the selected `productCategoryId` and passes it down to
      `ProductVariantEditor`/`ProductVariantDialog` — also unchanged. At the time this was
      written, when the category had a schema, the dialog rendered *exactly* those attributes —
      locked key labels plus a `Select` restricted to each attribute's own controlled vocabulary,
      required ones blocking submission if left blank — **instead of** the free-form key/value row
      editor, mirroring the backend's own (then-real) enforcement 1:1. **That whole "locked,
      suggestion-only" mode was removed once enforcement itself was reversed** — see the follow-up
      below for the current shape.

  - **Follow-up: category-schema enforcement reversed to advisory-only, per explicit request —
    "the `product_category_attributes` is just a suggestion, don't force the user to follow it."**
    This directly reverses the "Enforcement is real, not just advisory" decision above; the earlier
    choice was made deliberately at the time, but the actual catalog turned out to need attribute
    vocabularies (and combinations) this reactor's sample data/schema couldn't fully anticipate —
    e.g. a "Shirt" product's variant should be free to use `Size`/`Model` even though `Apparel`
    only suggests `size`/`color`. `ProductServiceImpl.validateAttributesAgainstCategory` was
    deleted outright (not disabled/flagged — genuinely dead code once nothing calls it), along with
    its three call sites in `create`/`update`/`addVariant`/`updateVariant` and the now-unused
    `PRODUCT_VARIANT_ATTRIBUTE_NOT_ALLOWED_FOR_CATEGORY`/`PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING`/
    `PRODUCT_VARIANT_ATTRIBUTE_VALUE_NOT_ALLOWED` error codes (`PRODUCT_005`–`007`, left as a
    numbering gap rather than renumbered/reused — see `EcommerceErrorCode`'s own comment there).
    **`ProductCategoryAttribute`/`ProductAttribute` themselves are untouched** — the data model,
    admin CRUD, and category-assignment feature all still exist exactly as before; only the
    *validation* of a variant's `attributes` against that schema is gone. Both entities' own
    Javadoc (and `ProductServiceImpl`'s new class-level Javadoc) now say so explicitly: the schema
    is purely an admin-GUI suggestion/quick-fill aid from here on. The unrelated
    `validateConsistentAttributeKeys`/`validateAttributeKeysMatchExisting` checks (US-1.6's "every
    variant of one product shares one key set") are **not** affected — those were never about the
    category schema, and the user didn't ask to relax them.
    - Every test asserting the old rejection behavior was replaced with one asserting the new
      tolerant behavior instead (e.g. `rejectsVariantAttributeNotInCategorySchema` →
      `toleratesAttributesOutsideTheCategorySchema`), across `Create`/`Update`/`AddVariant`/
      `UpdateVariant` — **293 unit tests total** (down from 297, net of 4 old rejection-only tests
      collapsing into fewer tolerance tests), verified via a real `mvn test` run (JDK 21). Treat
      this figure, not `297` a few paragraphs up, as current.
    - **GUI**: `ProductVariantDialog.tsx`'s "locked, suggestion-only" mode was removed — the
      free-form key/value row editor (add/remove rows, edit any key or value) is now **always**
      shown, regardless of whether the category has a schema. When it does, a row of clickable
      `Chip`s ("Suggested by this product's category — click to add, edit freely below") sits
      above the editor — clicking one quick-adds a pre-filled (empty-value) row for that attribute
      name (a no-op if already present); a `required`-flagged suggestion is labeled
      `"name (usually required)"`, a soft hint only, never blocking. A row whose own `Key` happens
      to match a suggested attribute's name renders its `Value` field as an MUI `Autocomplete`
      (`freeSolo`, options = that attribute's controlled vocabulary) instead of a plain
      `TextField` — offers the suggested values without preventing free typing. All "required
      value missing"/"value not in the allowed list" client-side validation was removed alongside
      this — the only attribute-related validation this dialog still performs is the pre-existing,
      unrelated `requiredAttributeKeys` cross-variant key-consistency check (still real, still
      enforced server-side). Verified via a clean `tsc --noEmit` and a successful `vite build`
      only — no Docker in this sandbox, so the actual quick-add/autocomplete flow is unverified in
      a real browser.

**Epic 4 (Payments, `docs/user-stories/04-payments.md`) — all 8 phases are now built** (data model,
gateway abstraction/Strategy pair, synchronous confirmation, outbox publishing, Stripe webhook
handling, refund on cancellation, user-facing failure reasons, and now the `gui` wiring for the
last one — see `gui/CLAUDE.md`'s own note). Confirmed
shape realized: the Stripe webhook (Phase 5) is exposed directly on this service's own origin,
bypassing `gateway` entirely (signature verification replaces JWT auth, the same way
`content-service`'s `/internal/**` bypasses `gateway`) — not a `gateway`-routed path; the Stripe
integration (Phase 2) uses the real `stripe-java` SDK against Stripe's test-mode API, not a
structural-only adapter.

- New `entity/Payment` — one payment attempt per `Order`, written with `PaymentStatus.PENDING`
  **before** `payment.PaymentGatewayPort#charge` is ever called, inside the same transaction as
  `orderstatus.PaymentHandoffService#startPaymentProcessing`'s own `PENDING -> PAYMENT_PROCESSING`
  transition — this is exactly what Epic 3's own reconciliation job (US-3.4) already queries
  against on a crash, now with a real row behind it instead of just `Order.idempotencyKey`. Real
  `@ManyToOne` FK to `Order` (mirrors `CouponRedemption`'s own FK — an `Order` row is permanent, no
  delete path in this module). `idempotencyKey` is denormalized from `Order.idempotencyKey`
  (unique) rather than read through the association, so a webhook handler (Phase 5) can find the
  right row without loading its `Order` too. `gatewayReference`/`failureCategory`/
  `gatewayFailureMessage` are all added now (nullable) but stay unpopulated until Phase 2 (a real
  gateway's own charge/PaymentIntent id) and Phase 7 (US-4.7's decline-category mapping) actually
  write them — the full row shape was added in one pass, the same way every one of Epic 3's own
  `Order` columns was, since Epic 4's own user stories already specify it end to end.
- New `enums/PaymentStatus` (`PENDING`/`SUCCEEDED`/`DECLINED`/`REFUNDED`) — deliberately not the
  same enum as `payment.PaymentOutcome` (Epic 3's gateway seam) despite the vocabulary overlap; see
  that enum's own Javadoc for why the two `PENDING`s mean different things.
- New `enums/PaymentFailureCategory` (`INSUFFICIENT_FUNDS`/`CARD_DECLINED`/`GATEWAY_ERROR`) — added
  now for the same "full shape up front" reason as the entity's nullable columns; nothing writes it
  yet.
- New `repository/PaymentRepository` — `findByOrderId` (this module's current one-shot charge flow
  never retries a declined charge, so exactly one row per order is correct today; not enforced at
  the schema level, since a future retry flow would legitimately need more than one — see the
  repository's own Javadoc) and `findByIdempotencyKey` (the webhook-correlation lookup Phase 5 will
  use).
- Migration `202609020010__0.0.2__DKP-0048__add_payment_table.sql` — `PAYMENT` table/sequence, FK
  onto `CUSTOMER_ORDER`, a unique constraint on `IDEMPOTENCY_KEY`, `CHECK`s on `STATUS`/
  `FAILURE_CATEGORY`/`AMOUNT > 0`, and an index on `CUSTOMER_ORDER_ID` backing `findByOrderId`.
  `scripts/purge-seed-data.sql` gained `ecommerce.PAYMENT` to its `TRUNCATE` list (18 tables total
  now) — same "real activity, no seeder of its own" reasoning as `SAVED_ADDRESS`/`COUPON_REDEMPTION`.
- **Phase 2 (US-4.1) — the gateway abstraction, a GoF Strategy pair behind the existing Adapter
  seam.** `PaymentGatewayPort` gained `refund(gatewayReference, amount)` — deliberately keyed by
  the gateway's own charge/PaymentIntent id (`entity.Payment#getGatewayReference()`), not this
  module's internal `paymentId` the user story's own prose names, since the gateway itself has no
  notion of our numeric PK. `charge`/`checkStatus` now return a new `payment.PaymentResult` record
  (`outcome` + `gatewayReference` + `failureCategory` + `gatewayFailureMessage`) instead of a bare
  `PaymentOutcome` — widens Epic 3's original return type with exactly what Phase 3 will need to
  persist onto `Payment`, without yet touching persistence itself (the three existing callers —
  `service.impl.OrderServiceImpl#initiatePayment`, `orderstatus.OrderReconciliationJob`, and
  `orderstatus.PaymentHandoffService#resolvePayment`, whose own signature is unchanged — were
  updated mechanically to unwrap `.outcome()`). New `payment.RefundOutcome`/`RefundResult` mirror
  `PaymentOutcome`/`PaymentResult` for the narrower refund vocabulary (deliberately not the same
  enum — a refund failure isn't a charge decline). New `payment.PaymentGatewayException`
  (unchecked) — thrown for a genuine gateway/network/API error, never for a card decline (a
  decline is a definitive `PaymentResult.declined`/`RefundResult.failed`, not an exception) — so a
  transient Stripe outage during `initiatePayment` leaves the order safely `PAYMENT_PROCESSING` for
  `OrderReconciliationJob` to resolve later, the same crash-safety Epic 3 already built.
  - `payment.MockPaymentGateway` — the default active bean (`app.ecommerce.payment.gateway`,
    default `mock`); deterministically declines one magic amount
    (`MockPaymentGateway.MAGIC_DECLINE_AMOUNT`, `13.13`) instead of Epic 3's old
    `NoOpPaymentGatewayPort`'s always-succeed (now deleted outright, superseded by this class and
    `StripePaymentGateway` — same "don't leave an ambiguous placeholder around" precedent
    `shipping`'s own calculators already follow, just via `@ConditionalOnProperty` instead of a
    manual `@Component` swap, since Stripe credentials being absent is a real, common local-dev
    state this reactor's existing `local`/`docker` profiles don't already encode).
  - `payment.StripePaymentGateway` — the real adapter (`app.ecommerce.payment.gateway=stripe` +
    `app.ecommerce.payment.stripe.secret-key`/`STRIPE_SECRET_KEY`, a real test-mode `sk_test_...`
    key). **This bullet originally described `charge()` confirming the `PaymentIntent`
    synchronously against a hardcoded test PaymentMethod id — that shape is gone; see the
    "Option A" follow-up bullet right below for the real, client-side-confirmation flow that
    replaced it.** `PaymentGatewayException` is rethrown for any genuine `StripeException`
    (API/network error). **`checkStatus` has no native Stripe endpoint to call** — Stripe exposes no "look up by
    idempotency key" query, so this method replays the exact same `charge` request under the same
    `Idempotency-Key` header, and Stripe returns its original cached response instead of performing
    the operation again; this is the one concrete reason this class (unlike `MockPaymentGateway`)
    depends on `PaymentRepository` — `checkStatus` only receives an `idempotencyKey`, so it looks
    the original `amount` up from the `Payment` row Phase 1 guarantees exists. New `stripe-java`
    Maven dependency (version-managed in the root `pom.xml`, declared only in this module's own
    `pom.xml` — same "no second consumer yet" precedent as `owasp-java-html-sanitizer`).
  - New `MockPaymentGatewayTest` (its one piece of real logic — the magic-amount decline — pinned
    down, same shape as `shipping.FreeOverThresholdShippingFeeCalculatorTest`).
    **`StripePaymentGateway` has no dedicated unit test** — it makes genuine calls against a real
    external SDK/API with no local test harness for it (unlike Testcontainers for Postgres), so
    it's left unverified-at-runtime the same way this module already treats Docker-dependent code;
    the old `NoOpPaymentGatewayPortTest` was deleted alongside the class it tested. **295 unit
    tests total** (up from 293 as of Epic 3's own count elsewhere in this file — treat this figure
    as current), verified via a real `mvn test` run (JDK 21); a targeted
    `mvn -pl ecommerce-service -am compile` also confirmed the new `stripe-java` SDK usage
    (`PaymentIntent`/`Refund`/`RequestOptions`/`StripeError`) compiles against the
    real dependency, catching one real API mismatch along the way (`StripeError.getPaymentIntent()`
    returns a `PaymentIntent` object, not a bare `String` id, contrary to an initial assumption).
- **Follow-up: `StripePaymentGateway#charge` rebuilt around Option A (Stripe Elements, client-side
  confirmation), replacing the `off_session`/hardcoded-test-PaymentMethod shape Phase 2 originally
  shipped, per request — see root `CLAUDE.md`'s own Stripe discussion for why that original shape
  (`setPaymentMethod(testPaymentMethodId)`/`setConfirm(true)`/`setOffSession(true)`) never
  resembled a real checkout: no card was ever collected for it to charge, since no `gui` code
  existed to collect one.** `charge` now builds an **unconfirmed** `PaymentIntent`
  (`setAutomaticPaymentMethods(enabled=true)`, no `confirm`/`paymentMethod`/`offSession` at all) and
  returns its `client_secret` via a new `PaymentResult.clientSecret` field — the shopper's own
  browser confirms it client-side (`stripe.confirmPayment` against a mounted `PaymentElement`, see
  `gui/CLAUDE.md`'s own note), so the card itself never reaches this backend. `resultFromIntent`'s
  `switch` now treats `"requires_payment_method"` as `PENDING`, not `DECLINED` — that status is the
  intent's normal starting point now that `charge` never confirms, not a synchronous-decline
  signal the way it briefly could be read as before this change (a genuine decline still only ever
  reaches `PaymentOutcome.DECLINED` via the intent's own terminal `"canceled"`/etc. status, or via
  the webhook path once the shopper's own confirmation attempt is declined).
  `app.ecommerce.payment.stripe.test-payment-method-id`/`STRIPE_TEST_PAYMENT_METHOD_ID` were
  deleted outright (dead config — nothing reads them anymore); a new
  `app.ecommerce.payment.stripe.publishable-key`/`STRIPE_PUBLISHABLE_KEY` property was added
  instead, served (never the secret key) by a new, deliberately business-logic-free
  `PaymentConfigApi`/`PaymentConfigController` (`GET /api/v1/public/payment-config` — this
  module's `security/SecurityConfig` already `permitAll()`s `/api/v1/public/**`, no new rule
  needed) returning `{gateway, publishableKey}` — `publishableKey` is `null` whenever `gateway` is
  `mock`, since there's nothing for `loadStripe()` to initialize against.
  `gateway`'s own `GatewayRoutesConfig` gained a matching `/api/v1/public/payment-config` route in
  the same change (see that module's own standing warning about this exact class of gap).
  **`orderstatus.PaymentHandoffService#startPaymentProcessing` is now deliberately re-entrant**
  when the order is already `PAYMENT_PROCESSING` — it just hands the same order back rather than
  re-running the `PENDING`-only registry transition (which would reject) or writing a second
  `Payment` row: a shopper can call `pay()` again before ever confirming the first `PaymentIntent`
  (closing the payment dialog, reloading the page), and `charge()`'s own `Idempotency-Key` (still
  the order id, unchanged from Epic 3) makes a repeat Stripe call return the *same* cached
  `PaymentIntent` — same id, same client secret — rather than creating a second one, so no new
  column/persisted state was needed to remember the first attempt's own secret.
  `OrderService.initiatePayment` return type widened from a bare `Order` to a new nested
  `OrderService.PaymentInitiationResult(order, clientSecret)` record — `clientSecret` is `null`
  whenever the gateway already resolved the charge synchronously (every `MockPaymentGateway`
  verdict, or an outright-declined Stripe charge), and is **never persisted** onto `Payment`
  (`entity.Payment` gained no new column for it) — `api.impl.OrderController#pay` is the only place
  that ever reads it, setting it as a new, deliberately non-mapper-resolved
  `OrderResponse.paymentClientSecret` field (see that DTO's own updated Javadoc for why it's not
  treated like `paymentStatus`/`paymentFailureCategory`'s live-lookup fields). New
  `PaymentHandoffServiceTest.isReentrantWhenTheOrderIsAlreadyPaymentProcessing` and two new
  `OrderServiceImplTest` cases (the `clientSecret` pass-through for both the resolved and the
  still-pending case) — **319 unit tests total**, verified via a real `mvn test` run (JDK 21).
  **Not built as part of this change**: no automatic expiry/failure for an order that sits
  `PAYMENT_PROCESSING` forever because the shopper simply abandoned the payment dialog without
  ever confirming, declining, **or explicitly cancelling** — `OrderReconciliationJob`'s own "still
  `PENDING`, check again later" handling of a `requires_payment_method` intent is exactly correct
  for a shopper who's still filling out the form, but has no distinct signal for "genuinely gave
  up and closed the tab," so a silently-abandoned order in that state is a known, accepted gap, not
  a bug; revisit with a real timeout if it matters. **An explicit Cancel Order click *is* now
  handled — see the follow-up below** — this remaining gap is narrower than it first looks: only
  "walked away with no click at all," not "clicked cancel."
  - **Bug fix, found via a real end-to-end `stripe listen` session: `Payment.gatewayReference`
    was never actually persisted for the common case, breaking both the webhook and the
    reconciliation fallback.** Symptom: `stripe listen --forward-to localhost:8081/webhooks/stripe`
    showed `payment_intent.succeeded` delivered and answered with a real `200`, yet the order stayed
    durably `PAYMENT_PROCESSING` — no exception anywhere, no `400`/`5xx`, correct forwarding target,
    correct `STRIPE_WEBHOOK_SECRET`. Root cause: `orderstatus.PaymentHandoffService
    .applyResultToPayment` returned immediately, without ever loading or touching the `Payment` row
    at all, whenever `result.outcome() == PaymentOutcome.PENDING` — a shortcut that was harmless
    back when `PENDING` was the rare edge case (a still-confirming synchronous charge), but became
    fatal once the Option A follow-up above made `StripePaymentGateway#charge`'s very first call
    *routinely* resolve `PENDING` (an unconfirmed `PaymentIntent`, `"requires_payment_method"`) —
    meaning `Payment.gatewayReference` was never recorded for the common case at all, only for a
    later `SUCCEEDED`/`DECLINED` resolution that, for a real Stripe charge, now only ever arrives
    *via the webhook itself* — a chicken-and-egg gap. Two independent downstream mechanisms depend
    on that column and were both silently broken by it: `webhook.StripeWebhookService
    .applyPaymentIntentEvent` correlates an incoming event to a `Payment` row via
    `PaymentRepository.findByGatewayReference(paymentIntentId)` — with the column always `null`,
    this always missed, logged a `WARN No Payment row found for Stripe PaymentIntent id=...`, and
    still recorded the event as processed (still a `200` to Stripe — no signal at all that anything
    was wrong from the CLI's own output); `StripePaymentGateway#checkStatus` (the reconciliation
    job's own fallback poll) reads `payment.getGatewayReference()` first and short-circuits to
    `PaymentResult.pending(null, null)` when it's `null`, so `OrderReconciliationJob` couldn't
    recover the order either, even given unlimited time. **Fix**: `applyResultToPayment` now always
    loads the `Payment` row and persists `result.gatewayReference()` onto it whenever one is
    present, for every outcome including `PENDING` — `resolvePayment`'s own Javadoc and this
    method's inline comment explain the full reasoning; `PaymentHandoffServiceTest`'s old
    `pendingLeavesTheOrderAndThePaymentRowUntouched...` test (which had asserted the buggy
    behavior — `verify(paymentRepository, never()).findByOrderId(any())` — as correct) was rewritten
    into `pendingLeavesTheOrderStatusUntouchedButStillRecordsTheGatewayReference...` plus a second
    new case confirming a `PENDING` result carrying no reference (the reconciliation poll's own
    still-nothing-to-retrieve case) never clobbers an already-recorded one with `null`. 320 unit
    tests total (up from 319), verified via a real `mvn test` run (JDK 21). **Still not verified
    against a real end-to-end `stripe listen` session in this sandbox** — the fix follows directly
    from the code-level root cause traced through the actual observed symptom (200 response, stuck
    order, matching `WARN` line expected in the app's own log), but re-running the same live Stripe
    CLI session against the fixed code to confirm the order now reaches `CONFIRMED` is still up to
    whoever's running the stack.
  - **Follow-up: an explicit Cancel Order click during the payment phase used to leave the order
    stuck `PAYMENT_PROCESSING` forever too — closed by actively cancelling the still-unconfirmed
    Stripe PaymentIntent at the gateway, per request (Option A chosen over a cheaper,
    correctness-losing alternative — see below).** Symptom, reported directly: clicking `Cancel
    Order` in `gui`'s payment phase called `POST /{id}/cancel` successfully, but neither `Order`
    nor `Payment` ever changed status. Root cause: `PaymentProcessingOrderStatusHandler.cancel`
    only ever sets `Order.cancelRequested` when the order is `PAYMENT_PROCESSING` — it never
    transitions the order itself, on the (once-correct) assumption that "a gateway call is in
    flight, it'll resolve any moment." That assumption held for the old synchronous-charge shape; it
    doesn't hold under Option A, where a charge attempt can sit unconfirmed indefinitely while the
    shopper is simply looking at the card form — no gateway call is "in flight" to wait out at all.
    Nothing was ever going to consult the queued flag either: `resolvePayment`'s own `PENDING`
    branch (what every repeat `checkStatus`/webhook delivery for a never-confirmed intent produces)
    never even looks at `cancelRequested` — only its `SUCCEEDED`/`DECLINED` branches do.
    - **Two designs were compared before building either.** Option A (chosen): actually cancel the
      Stripe PaymentIntent via a real gateway call before transitioning the order, mirroring Phase
      6's own durable-step/gateway-call/durable-step shape for refunds. Option B (rejected, cheaper
      to build): just make `resolvePayment`'s `PENDING` branch check `cancelRequested` and
      transition to `CANCELLED` locally, no gateway call at all — rejected because it's a real
      correctness gap, not just a smaller feature: nothing would ever tell Stripe to stop honoring
      that PaymentIntent, so a shopper who still had the payment form open on another tab could
      complete it moments later, capturing real money against an order this reactor already marked
      `CANCELLED` — with no refund, since nothing would know one was owed.
    - **New `payment.PaymentGatewayPort#cancelUnconfirmed(gatewayReference)`** — a third gateway
      operation alongside `charge`/`checkStatus`/`refund`, returning a new
      `payment.PaymentCancellationResult` (`payment.CancellationOutcome`: `CANCELLED`/
      `ALREADY_RESOLVED`) rather than reusing `PaymentResult`/`PaymentOutcome` — mirrors
      `RefundResult`/`RefundOutcome`'s own "narrow, dedicated vocabulary" precedent (Phase 2's own
      Javadoc) for the identical reason: a shopper-initiated cancel is not a card decline, and
      reusing `PaymentOutcome.DECLINED` would show a misleading "payment declined" reason on an
      order the shopper themselves cancelled. `StripePaymentGateway#cancelUnconfirmed` retrieves the
      `PaymentIntent` and calls its own `cancel` — if Stripe rejects that because the intent already
      reached a real terminal state (the shopper confirmed on another tab a moment earlier), a
      second retrieve reports that real outcome (`ALREADY_RESOLVED`, carrying the gateway's actual
      result) instead of masking a cancellation that never happened. `MockPaymentGateway`'s own
      implementation just logs and reports `CANCELLED` unconditionally — this gateway never actually
      leaves a charge unconfirmed in the first place, so it should never really be reached.
    - **New `enums.PaymentStatus.CANCELLED`** (migration `DKP-0051`, widening
      `CKC_PAYMENT_STATUS` — Postgres has no `ALTER CHECK`, drop-and-recreate, same shape as
      `DKP-0035`'s own `CKC_CUSTOMER_ORDER_STATUS` widening) — deliberately distinct from
      `DECLINED` for the identical shopper-facing-message reason above.
    - **`orderstatus.PaymentHandoffService.applyCancellation`** now detects the exact stuck state
      (order still `PAYMENT_PROCESSING` after the cancel only queued, with a still-`PENDING`
      `Payment` row carrying a real `gatewayReference`) and reports a new
      `CancellationResult.gatewayCancellationNeeded()` flag alongside the existing
      `refundNeeded()` — the two are mutually exclusive states of the same order (a refund only
      ever applies to an order that became `CANCELLED` outright; a gateway cancellation only ever
      applies to one whose cancel merely queued). New `applyGatewayCancellation(orderId,
      PaymentCancellationResult)` — the second durable step: `CANCELLED` dispatches through the
      same `failPayment` release-and-respect-`cancelRequested` logic `resolvePayment`'s own
      `DECLINED` branch already uses (ends the order `CANCELLED`), but marks the `Payment` row
      `CANCELLED` rather than `DECLINED`, and publishes no outbox event (a shopper-initiated cancel
      isn't the `PAYMENT_FAILED` business event); `ALREADY_RESOLVED` delegates straight to the
      existing `resolvePayment` with the gateway's own real result, reusing that method's
      already-correct `cancelRequested`-aware handling rather than duplicating it.
    - **`service.impl.OrderServiceImpl#cancel`** gained a third branch alongside its existing
      no-refund/refund-needed ones: when `gatewayCancellationNeeded()`, it calls
      `paymentGatewayPort.cancelUnconfirmed` (outside any transaction, same reason every other
      gateway call in this class is) and then `applyGatewayCancellation`, returning *that* call's
      result — unlike the refund branch (which returns the already-`CANCELLED` order from step one
      unchanged), this branch's order is still `PAYMENT_PROCESSING` until the gateway call resolves
      it.
    - `PaymentHandoffServiceTest`'s `ApplyCancellation` nested class gained cases for both new
      branches (no-Payment-row-yet, `PENDING`-with-reference, `PENDING`-without-reference-yet) plus
      a new `ApplyGatewayCancellation` nested class (`CANCELLED`/`ALREADY_RESOLVED`/missing-row/
      not-found cases); `OrderServiceImplTest.Cancel` gained the third-branch case;
      `MockPaymentGatewayTest` gained one case. **328 unit tests total** (up from 320), verified via
      a real `mvn test` run (JDK 21). Not verified against a real `stripe listen` session in this
      sandbox, same standing caveat as the bug fix directly above.
  - **Bug fix, found immediately after the fix above by actually running a real `stripe listen`
    session against a freshly-purged database: `PendingOrderStatusHandler.startPaymentProcessing`'s
    idempotency key was the order's own recyclable primary key, which collided with an unrelated
    earlier charge attempt after a local dev reset.** Symptom:
    `com.stripe.exception.IdempotencyException: Keys for idempotent requests can only be used with
    the same parameters they were first used with` thrown from `StripePaymentGateway.charge` →
    `PaymentIntent.create`, immediately after running `scripts/purge-seed-data.sql` and placing a
    new order. Root cause: the key was stamped as `String.valueOf(order.getId())` — a "reasonable
    default" when this was first built, but `purge-seed-data.sql`'s `TRUNCATE ... RESTART IDENTITY`
    resets `CUSTOMER_ORDER_SEQ` back to 1, so the very next order can land on an id a *previous*,
    unrelated order (with a *different* total) already used as its Stripe idempotency key. Stripe's
    own idempotency cache lives server-side for 24 hours, entirely independent of anything in this
    app's own database, so it still remembered the old key/amount pairing and correctly refused to
    treat the new, differently-priced order as a safe retry of it. **Fix**: the key is now a random
    `UUID` (`UUID.randomUUID().toString()`), never derived from the order's own id — confirmed via a
    full-module grep that nothing anywhere parses this column back into a number or compares it to
    `order.getId()` (every consumer — `StripePaymentGateway`, `PaymentRepository
    .findByIdempotencyKey`, `PaymentHandoffService` — already treated it as an opaque string), and
    that both `Order.idempotencyKey`/`Payment.idempotencyKey` (`VARCHAR(64)`) comfortably fit a
    36-character UUID with no migration needed. **This does not touch the actual double-charge
    protection at all** — that guarantee lives entirely in `orderstatus.PaymentHandoffService
    .startPaymentProcessing`'s own status check (`if (order.getStatus() == PAYMENT_PROCESSING)
    return order;`), which is the sole call site for the handler method that stamps this key; a key
    is stamped exactly once per order's whole payment lifetime regardless of whether its value is a
    recyclable integer or a UUID — only *cross-order* collisions after a database reset are what
    changed. Two tests asserted the old numeric-derived value against the real handler
    (`PendingOrderStatusHandlerTest.startPaymentProcessingStampsIdempotencyKeyAndClockAndTransitions`,
    `OrderLifecycleIntegrationTest.happyPathReachesDeliveredWithOneConfirmSaleAndAFullHistoryTrail`)
    and were updated to assert a well-formed UUID instead of a literal id; every other test that
    stubs `idempotencyKey` to a plain string (`"1"`, etc.) does so on a manually-built `Order`
    fixture that never goes through the real handler, so none of those needed any change. 328 unit
    tests total, unchanged, verified via a real `mvn test` run (JDK 21). Fixes only *future*
    collisions — a key already burned at Stripe from before this fix stays unusable for its own
    24-hour window regardless.
  - **Bug fix, reported directly after testing a declined-then-successful-retry flow: a card
    decline permanently blocked the order from ever succeeding afterward, even on the exact same
    still-open `PaymentIntent`.** Symptom: decline a test card → `charge.failed` →
    `payment_intent.payment_failed` → `Payment` marked `DECLINED`, `Order` finalized to `FAILED`.
    Switch to a working card on the *same* `PaymentElement` form (Stripe's own supported retry
    flow — no new `PaymentIntent`, same `gatewayReference`) → `payment_intent.succeeded` arrives
    for that same intent → `StripeWebhookService` correlates it to the same, already-`FAILED`
    order → `resolvePayment` dispatches `confirmPayment` → `OrderStatusHandlerRegistry` has no
    handler registered for the terminal `FAILED` status, so it falls back to the default
    reject-everything handler → `EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION`
    (`ORDER_003: Cannot confirmPayment an order in status FAILED`), returned to Stripe as a `409`
    (and the whole `@Transactional` webhook handler rolls back, so Stripe just keeps retrying the
    same failing delivery). Root cause: `StripeWebhookService.applyPaymentIntentEvent` built a bare
    `PaymentResult.declined(...)` for *every* `payment_intent.payment_failed` event, which
    `PaymentHandoffService.resolvePayment`'s `DECLINED` branch finalizes via
    `orderStatusHandlerRegistry.failPayment` — correct for a genuinely final decline, but
    `payment_intent.payment_failed` under Option A's `PaymentElement` almost never means that: it
    fires when the `PaymentIntent` drops back to `"requires_payment_method"`, the *exact same*
    status a brand-new, never-attempted intent starts in — the shopper can retry with a different
    card against that same intent, exactly as they did here. **Fix**: new
    `PaymentResult.attemptFailed(gatewayReference, failureCategory, gatewayFailureMessage)` — a
    `PaymentOutcome.PENDING` result (not `DECLINED`), carrying the failure detail for display but
    leaving the order untouched; `StripeWebhookService` now builds this instead of `declined(...)`
    for `payment_intent.payment_failed`. `PaymentHandoffService.applyResultToPayment`'s `PENDING`
    branch now persists `failureCategory`/`gatewayFailureMessage` onto the `Payment` row when
    present (defensively — an ordinary "still waiting, no new info" poll carries neither, and must
    not clobber a real decline reason already recorded with `null`), and its `SUCCEEDED` branch now
    clears both, so a since-corrected order doesn't keep showing a stale "your card was declined"
    reason. **`PaymentOutcome.DECLINED` itself is completely unchanged and still finalizes to
    `FAILED`** — that's still correct for `MockPaymentGateway`'s synchronous one-shot decline (no
    retry concept exists in its own model at all) and for `StripePaymentGateway.checkStatus`'s own
    terminal-status branch (in practice, an intent Stripe itself reports `"canceled"`); only the
    webhook's own classification of this one specific event was wrong. Three other options were
    considered and rejected before this one: capping retries at a fixed attempt count (duplicates
    Stripe's own configurable retry limits, still doesn't solve the underlying misclassification);
    registering a real `FAILED`→`CONFIRMED` transition handler instead of changing when `FAILED` is
    reached at all (rejected as actively dangerous — `failPayment` already *releases* the stock
    reservation the moment an order hits `FAILED`, so a later success would need to *re-reserve*
    stock that could have sold out to someone else in the meantime, trading a clear bug for a much
    worse, silent one); and disabling `PaymentElement`'s inline retry entirely, forcing a whole new
    order per declined attempt (the biggest shopper-facing UX regression of the three, even though
    smallest on the backend). `PaymentHandoffServiceTest.ResolvePayment` gained two new cases
    (`attemptFailed` leaves the order at `PAYMENT_PROCESSING` while still recording the decline
    reason; `succeeded` after an earlier `attemptFailed` clears the stale reason);
    `StripeWebhookServiceTest`'s existing decline case updated to assert `PENDING`, not `DECLINED`.
    330 unit tests total (up from 328), verified via a real `mvn test` run (JDK 21). **Backend
    only in this pass** — see `gui/CLAUDE.md`'s own note for the matching frontend follow-up
    (`OrderDetailPage.tsx`/`OrderHistoryPage.tsx`'s decline banner widened to not require
    `order.status === 'FAILED'`, since this fix means a retryable decline no longer reaches it).
  - **Follow-up: a full module-wide code-quality audit was requested and run** (four parallel
    reviews covering catalog/attributes, cart/checkout/coupons, order-lifecycle/payments, and
    cross-cutting hygiene — outbox, seeders, config, error codes, migrations, test coverage). Two
    findings were prioritized and fixed immediately below; the remaining findings (N+1 query risks
    in `CartServiceImpl.getCart`/`CouponRedemptionServiceImpl.listAvailable`/`ProductMapper`'s
    admin-list mapping, `PaymentHandoffService`'s God-class size, several small duplication
    candidates in seeders/mappers/address-handling, a still-accepted "queued cancel loses the race
    to a gateway success" money gap) are documented but not yet acted on — see the audit's own
    findings if picking any of these up later; they weren't re-transcribed here to avoid this file
    drifting out of sync with whichever ones eventually get fixed.
  - **Bug fix #1: `OrderServiceImpl#cancel`'s two gateway-follow-up branches (refund, gateway-
    cancellation) had no protection against losing a race to a concurrent resolution of the same
    order — a shopper could get a raw error response for a Cancel Order click that had, in truth,
    already succeeded.** Because `cancel()` deliberately runs its follow-up steps outside any single
    transaction (see this class's own long-standing Javadoc on why), a double-click, or the Stripe
    webhook/`OrderReconciliationJob` resolving the same order at nearly the same moment, could both
    be mid-flight on one order at once. Whichever commits last previously hit either
    `EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION` (the order is already `CANCELLED` — a
    terminal status with no registered handler — by the time the loser tries to apply its own
    result) or `org.springframework.orm.ObjectOptimisticLockingFailureException` (`Order`'s own
    `@Version` was already bumped by the winner), and either propagated straight to the caller as a
    real error. **Fix**: `cancel()` now catches both, re-fetches the order, and — only when it
    genuinely reached `CANCELLED` (the sole outcome a queued cancel can ever resolve to, per
    `PaymentProcessingOrderStatusHandler`'s own `cancelRequested`-wins rule — never `CONFIRMED`) —
    returns it as a normal success instead of rethrowing. Any other rejection (e.g. a stale page
    trying to cancel an order an admin already shipped) still propagates unchanged; this only
    swallows the one specific "someone else already finished the thing I was trying to do" race,
    never a genuinely invalid request. New private `doCancel`/`recoverFromConcurrentCancelResolution`
    helpers.
  - **Bug fix #2: a genuine Stripe gateway/network outage during `charge`/`refund`/
    `cancelUnconfirmed` propagated as a raw, unmapped `RuntimeException`** — falling through to
    `GlobalExceptionHandler`'s generic `Exception` handler as an unhelpful `500` with no error code,
    violating this reactor's own "services never leak an unmapped exception to a caller" rule (and
    the fact that `EcommerceErrorCode` had zero `PAYMENT_*` codes at all despite Epic 4 being fully
    built). **Fix**: new `EcommerceErrorCode.PAYMENT_GATEWAY_UNAVAILABLE` (`PAYMENT_001`,
    `503 SERVICE_UNAVAILABLE` — mirrors `common.CommonErrorCode.SERVER_EXTERNAL_SERVICE_ERROR`'s own
    "translate an external-service outage into a `503`" shape exactly) and a new private
    `OrderServiceImpl#callGatewayOrFail(Supplier<T>)` helper wrapping every direct
    `payment.PaymentGatewayPort` call in `initiatePayment`/`cancel`, translating a caught
    `payment.PaymentGatewayException` into `new ApiException(PAYMENT_GATEWAY_UNAVAILABLE, e)`. This
    translation happens strictly after whatever durable step already committed (e.g.
    `PaymentHandoffService#startPaymentProcessing` for `initiatePayment`), so it changes only the
    shape of the response the caller receives — it does not touch the existing guarantee that a
    mid-call crash still leaves the order safely `PAYMENT_PROCESSING` for `OrderReconciliationJob`
    to resolve later (see `PaymentGatewayException`'s own Javadoc).
  - 6 new `OrderServiceImplTest` cases (2 concurrent-cancel-recovery cases — one recovering when the
    order reached `CANCELLED`, one rethrowing when it didn't — plus an optimistic-lock-conflict
    recovery case, and 3 gateway-exception-translation cases across `cancel`'s refund/
    gateway-cancellation branches and `initiatePayment`'s charge call). 336 unit tests total (up
    from 330), verified via a real `mvn test` run (JDK 21) and a targeted
    `-pl ecommerce-service,gateway -am compile` (no `gateway`-side change needed — this is a pure
    behavior fix inside an already-routed endpoint).
  - **Follow-up: the audit's three N+1 query findings were fixed next, per request.**
    - **`CartServiceImpl.getCart` — the app's single hottest read path (every cart view *and* every
      checkout `preview`/`confirm` call) — used to call `productVariantRepository.findById(variantId)`
      once per cart line, plus a second, lazily-loaded query the instant anything read
      `variant.getProduct()` (`ProductVariant.product` is `FetchType.LAZY`) — up to `2N` queries for
      an `N`-line cart. New `ProductVariantRepository#findAllByIdWithProduct` (a single `JOIN FETCH`
      query resolving every line's variant *and* its product in one round trip, regardless of cart
      size) replaces the per-line lookup; `resolveLine` now takes the already-resolved
      `ProductVariant` (or `null`, when the id is simply absent from the batch result — a hard-deleted
      variant, same "line stays visible as unavailable" contract as before) instead of looking it up
      itself. 5 new `CartServiceImplTest.GetCart` cases (this method had *zero* test coverage before
      this fix, despite being the hottest path in the app) — including one asserting the batch query
      runs exactly once for a 3-line cart, and one asserting the empty-cart fast path never queries
      the repository at all.
    - **`entity.Product`'s `variants`/`images`/`productTagAssignments` — all `FetchType.LAZY`, all
      mapped unconditionally by `mapper.ProductMapper#toResponse` on every row, including the
      paginated admin product list — used to trigger up to 60 extra lazy-load `SELECT`s for a 20-row
      page (one per collection per row), invisibly (`spring.jpa.open-in-view` means nothing ever
      errors, it just gets slower as the catalog grows).** Fixed with `@BatchSize(size = 20)` on all
      three collections — Hibernate now batches every not-yet-initialized collection of the same type
      still pending in the current persistence context into one `WHERE product_id IN (...)` query
      instead of one query per product, capping a 20-row page at 3 extra queries total (one per
      collection type) regardless of row count. Deliberately the annotation-on-the-mapping fix, not a
      new list-only DTO/`@EntityGraph` — the least invasive option that also benefits every other
      lazy-load site for these same collections, not just this one endpoint (see the entity's own
      updated Javadoc). No test added — this is a pure Hibernate batch-fetching hint with no new
      branching logic to unit-test; would need an actual Hibernate session (an integration test) to
      verify the query count directly, which this module doesn't have infrastructure for outside the
      one existing Testcontainers `ProductSearchViewRepositoryIT`.
    - **`CouponRedemptionServiceImpl.listAvailable` — called every time a shopper opens the coupon-
      picker dialog — used to call `countByCouponId`/`countByCouponIdAndOwnerUuid` once per candidate
      coupon inside its own filter chain.** New `CouponRedemptionRepository#countGroupedByCouponId`/
      `#countGroupedByCouponIdForOwner` (each a single `GROUP BY` query returning a
      `List<CouponRedemptionCount>` — a new small interface projection) replace the per-coupon calls;
      `listAvailable` now collects each grouped result into a `Map<Integer, Long>` and looks counts up
      from there. **Preserves the original short-circuit exactly**: if no candidate coupon has
      `maxRedemptions`/`maxRedemptionsPerUser` set at all, the corresponding grouped query is never
      called — a coupon list with no redemption limits configured still costs zero count queries, the
      same as before this fix; only when at least one candidate needs a count does it cost exactly one
      query, covering every candidate that needs it, not one query per candidate. 3 new test cases
      (a coupon absent from the grouped result — no redemptions yet — correctly reads as zero, not an
      error; the zero-count-queries-when-nothing-is-limited case; and the actual N+1 assertion — two
      candidates, both limits set, exactly one call to each grouped method).
    - 8 new tests total across the three fixes. 344 unit tests total (up from 336), verified via a
      real `mvn test` run (JDK 21) and a targeted `-pl ecommerce-service,gateway -am compile`.
  - **Follow-up: the audit's `PaymentHandoffService` God-class finding was fixed next, per request —
    split into `orderstatus.PaymentHandoffService` (the charge lifecycle:
    `startPaymentProcessing`/`resolvePayment`/private `applyResultToPayment`) and a new
    `orderstatus.PaymentCancellationService` (the cancellation/refund lifecycle:
    `applyCancellation`/`applyGatewayCancellation`/`applyRefundResult`, plus its own
    `CancellationResult` record, moved with them).** `PaymentHandoffService` had grown to ~430 lines
    spanning three lifecycles that only ever interact at one seam: `applyGatewayCancellation`'s
    `ALREADY_RESOLVED` branch (the gateway reports the charge actually reached a real terminal state
    moments before a cancellation could apply) needs to delegate straight into `resolvePayment`'s own
    `cancelRequested`-aware handling rather than reimplement it. **Fix**: `PaymentCancellationService`
    takes a one-directional dependency on `PaymentHandoffService` for exactly that one call
    (`paymentHandoffService.resolvePayment(...)` — a different bean, so it still goes through
    Spring's proxy correctly and joins the caller's already-open transaction, same reasoning
    `OrderReconciliationJob`'s own cross-bean call already relies on); `PaymentHandoffService` has no
    dependency back the other way, so there's no circular bean wiring. Each of the three
    `OutboxEvent`-publish helper methods the original class held (`publishPaymentSucceeded`/
    `publishPaymentFailed`/`publishPaymentRefunded`) turned out to be used by exactly one of the two
    post-split lifecycles apiece — no actual duplication existed once split, so each simply moved
    with its own call site; no shared "outbox publisher" component was needed. `OrderServiceImpl` now
    injects both services (`paymentHandoffService` for `initiatePayment`'s two durable steps,
    `paymentCancellationService` for `cancel`'s three call sites); `webhook.StripeWebhookService` and
    `OrderReconciliationJob` are untouched — both only ever called `resolvePayment`, which stayed on
    `PaymentHandoffService`. `PaymentHandoffServiceTest` now covers only `StartPaymentProcessing`/
    `ResolvePayment`; a new `PaymentCancellationServiceTest` covers `ApplyCancellation`/
    `ApplyGatewayCancellation`/`ApplyRefundResult`, mocking `PaymentHandoffService` for the
    `ALREADY_RESOLVED` delegation (that logic's own correctness is `PaymentHandoffServiceTest`'s job,
    not re-verified here) — same 25 test methods as before, just split across two files, so the
    reactor-wide count is unchanged at 344, verified via a real `mvn test` run (JDK 21) and a
    targeted `-pl ecommerce-service -am test-compile`.
  - **Follow-up: the audit's remaining findings were all fixed next, per request ("go ahead with
    all of them") — six real changes plus one investigated-and-left-alone.**
    - **Not changed: the duplicated `PaymentSucceededOutboxEventHandler.Payload`/
      `PaymentRefundedOutboxEventHandler.Payload` records.** Investigated first — both are
      byte-identical today (`orderId`/`amount`/`gatewayReference`), but
      `service.impl.ProductChangedOutboxEventHandler`'s own Javadoc documents a deliberate,
      reactor-wide decision to keep `Payload` per-handler: "a shared payload DTO across every event
      type would recreate the 'every future epic edits the same file' problem `eventType` already
      avoids by staying per-handler." Merging these two would go against that already-considered
      design, for a coincidence (today's two payloads happening to match) rather than a real bug —
      left as-is.
    - **`payment.StripeFailureCategoryMapper` gained test coverage** — this Stripe
      decline-code-→-category mapping (the single source of truth both
      `StripePaymentGateway#charge` and `webhook.StripeWebhookService` depend on agreeing on) had
      zero tests before this. New `StripeFailureCategoryMapperTest`: null error, no decline-code/
      code at all, `insufficient_funds`, every one of the ten known card-declined codes
      (parameterized), an unrecognized code, falling back to `code` when `decline_code` is absent,
      and `decline_code` taking priority over `code` when both are present — 16 new tests.
    - **New `util.NameNormalizer`** — `ProductCategoryServiceImpl`/`ProductTagServiceImpl`/
      `ProductAttributeServiceImpl` each carried a byte-identical private `normalizeName` (a
      null-safe `trim()`); all three now call the shared static utility instead.
    - **New `infra.service.seed.CsvReader`** — `ecommerce-service`'s own `ProductSeeder`/
      `ProductCategoryAttributeSeeder` each carried a byte-identical private `readCsv` method
      (identical `CSVFormat`/try-with-resources boilerplate to `infra`'s own `CsvSeeder.seed()`),
      justified at the time by `CsvSeeder.seed()` being `final` and not fitting either seeder's
      multi-row-per-unit-of-work shape — that reasoning only ever argued against reusing
      `seed()` itself, never against sharing the read step underneath it. `CsvSeeder.seed()` itself
      now calls this same shared method internally too. The "resolve by name or fail loudly"
      pattern repeated across every seeder's own `orElseThrow` was surveyed and deliberately left
      alone — each site's `IllegalStateException` message is meaningfully different (which CSV
      file, which column, which prerequisite seeder), so a shared helper would only save one line
      while hurting the specific, actionable error messages.
    - **New `mapper.AddressMapper` (MapStruct)** — `CheckoutServiceImpl` used to hand-copy the same
      nine address fields via two private static `toAddress` methods (one from
      `CheckoutCommands.AddressInput`, one from `SavedAddress`), which this module's own convention
      reserves for MapStruct (see root `CLAUDE.md`'s "DTOs ↔ entities" rule). Both source shapes
      carry the identical nine fields in the identical order as `Address` itself, so neither mapper
      method needs a `@Mapping` override. `CheckoutServiceImplTest` gained a mocked
      `AddressMapper` (two `lenient()` stubs replicating the trivial field-for-field mapping, since
      Mockito can't run the real MapStruct-generated impl) — all 25 existing tests pass unchanged.
    - **New `config.PaymentProperties`/`config.OrderJobProperties`** (Java 21 records, constructor-
      bound `@ConfigurationProperties`, registered via `@EnableConfigurationProperties` on
      `EcommerceServiceApplication` — this reactor's own established preference over a
      `@ConfigurationPropertiesScan`, see `infra/CLAUDE.md`'s "post-2026-08-16" note). Five separate
      `@Value`-injected fields across `StripeWebhookService`/`StripePaymentGateway`/
      `api.impl.PaymentConfigController` (`gateway`, `stripe.secret-key`, `stripe.currency`,
      `stripe.publishable-key`, `stripe.webhook-secret`) collapsed into `PaymentProperties`; two
      more across `OrderReservationExpiryJob`/`OrderReconciliationJob` (`reservation-timeout`,
      `reconciliation.grace-period`) collapsed into `OrderJobProperties`. Each poller's own
      `poll-interval` deliberately stays a raw `${...}` placeholder on its own
      `@Scheduled(fixedDelayString = ...)` annotation — Spring's scheduling infrastructure resolves
      that directly off the environment at schedule-registration time, not off a bound
      `@ConfigurationProperties` instance, so moving it would be cosmetic at best. Both records'
      compact constructors null-coalesce every field to the exact same default its old
      `@Value(...:default)` placeholder carried, including the nested `Stripe`/`Reconciliation`
      record itself (Spring Boot's relaxed binding otherwise leaves a nested constructor-bound
      record entirely `null` when none of its own sub-properties are set — the default `mock`
      gateway profile sets none of the `stripe.*` ones). `OrderReconciliationJobTest` switched from
      `ReflectionTestUtils.setField` to constructing a real `OrderJobProperties` (a record is
      implicitly `final`, so Mockito's mock maker here can't `@Mock` one — and there's no reason to,
      it's a plain, cheap value object).
    - **`CouponRedemptionServiceImpl`'s active-window check de-duplicated** — `resolve` and
      `listAvailable` each independently re-typed the identical `startAt`/`endAt` comparison
      semantics, with no shared code path to keep them from silently drifting apart. New private
      static `hasStarted`/`hasNotExpired` helpers, shared by both. The redemption-count checks and
      `minSubtotal` were deliberately **not** unified the same way: `resolve`'s single-coupon count
      query and `listAvailable`'s batched grouped-count query are intentionally different (unifying
      them would undo the N+1 fix above), and `minSubtotal` is a one-sided rule `listAvailable`
      deliberately skips — there's no shared logic to extract for either. All 35 existing
      `CouponRedemptionServiceImplTest` cases pass unchanged.
    - **New `orderstatus.RefundReconciliationJob` closes the "queued cancel loses the race to a
      gateway success" money gap** — previously documented (`PaymentProcessingOrderStatusHandler`'s
      own Javadoc) as deliberately unrecovered, since wiring a *synchronous* refund into that path
      would need `PaymentHandoffService#resolvePayment` to take on the identical
      "resolve-the-transition/gateway-call-outside-any-transaction/apply-the-result" restructuring
      `OrderServiceImpl#cancel` already got, which nothing had asked for. This job takes the
      simpler path instead: same poller shape as `OrderReservationExpiryJob`/
      `OrderReconciliationJob`, polling a new `PaymentRepository#findIdsByStatusAndOrderStatus`
      query for the one combination that can only ever mean an unrefunded capture — a `Payment` row
      `SUCCEEDED` on an order that reached `CANCELLED` (a normal cancel-with-refund already turns
      that row `REFUNDED` via `PaymentCancellationService#applyRefundResult`) — then calls
      `payment.PaymentGatewayPort#refund` outside any transaction before applying the result via
      that same `applyRefundResult`, a *different* bean's own `@Transactional` method. This also
      incidentally closes a second, related gap the same query happens to catch: if
      `OrderServiceImpl#cancel`'s own synchronous refund call itself fails (a genuine gateway
      outage), the `Payment` row was previously just left `SUCCEEDED` forever with no retry — now
      the next poll picks it up too. Safe to retry indefinitely: `StripePaymentGateway#refund`'s own
      idempotency key is deterministic (derived from `gatewayReference`), so a retried refund can
      never double-refund at the gateway, and a resolved row no longer matches the poll query.
      New `app.ecommerce.order.refund-reconciliation.poll-interval` (default `PT5M`) — deliberately
      not folded into `OrderJobProperties` above, for the identical "Spring's scheduler needs the
      raw placeholder" reason its two siblings weren't either. 4 new
      `RefundReconciliationJobTest` cases (mirroring `OrderReconciliationJobTest`'s own shape).
    - 20 new tests total across all six changes (16 + 4 — the `AddressMapper`/`OrderJobProperties`/
      eligibility-dedup changes added no new tests of their own, since they're pure refactors of
      already-covered behavior). 364 unit tests total (up from 344), verified via a real `mvn test`
      run (JDK 21).
  - **Follow-up: a marker interface plus a GoF Template Method base class for this module's
    `@Scheduled` reconciliation jobs, per request.** New `orderstatus.ReconciliationJob` — a bare,
    zero-method marker interface (same "Find Implementations" purpose as `infra`'s own
    `ApplicationEventHandler`/`Seeder`/`outbox.OutboxEventHandler`), implemented by all three of
    this module's poller jobs (`OrderReservationExpiryJob`, `OrderReconciliationJob`,
    `RefundReconciliationJob`). New `orderstatus.AbstractReconciliationJob implements
    ReconciliationJob` — Template Method (Behavioral): `OrderReconciliationJob` and
    `RefundReconciliationJob` had grown byte-identical in shape (a private `BATCH_SIZE = 50`
    constant, a poll-a-batch-of-ids loop, a per-id try/catch logging a warning and moving on) around
    otherwise-different domain logic; both now extend this base class instead, implementing only
    `pollBatch(int)` (their own repository query) and `reconcileOne(Integer)` (the actual per-id
    work, no try/catch of its own anymore — `AbstractReconciliationJob#reconcileBatch` catches and
    logs generically, `"{ClassName} failed for id={}: {}"`, in one place instead of two slightly
    differently-worded copies). Each subclass's own `@Scheduled` method is now a one-line call to
    the inherited `reconcileBatch()` — the annotation itself deliberately stays on the subclass,
    since each job's poll-interval is a distinct property key Spring's scheduler needs to resolve
    directly, not something a shared base class could carry. **`OrderReservationExpiryJob`
    deliberately implements `ReconciliationJob` directly instead of extending
    `AbstractReconciliationJob`** — its own per-id work must be genuinely `@Transactional` (it
    directly transitions an `Order`'s own status), so it's delegated to a separate `@Transactional`
    processor bean (`OrderReservationExpiryProcessor`) rather than an inline try/catch; forcing it
    through the same template would mean either losing that transactional boundary or duplicating
    it awkwardly around an abstract method that isn't itself proxied — the two-class split
    (interface for discoverability, abstract class for the two jobs whose shape genuinely matches)
    avoids over-abstracting the one job that doesn't fit. Pure refactor, no behavior change and no
    new tests needed — all three jobs' own constructors/field lists are unchanged, so every existing
    test (`OrderReconciliationJobTest`, `RefundReconciliationJobTest`,
    `OrderReservationExpiryProcessorTest`) passes unmodified. 364 unit tests total, unchanged,
    verified via a real `mvn test` run (JDK 21).
  - **Follow-up: `webhook.StripeWebhookService` now handles `payment_intent.canceled` too, reusing
    Stripe's own confirmation-limit retry cap instead of building a custom decline counter (a
    discussion prompted by "Option B: cap retries at N attempts, then finalize as FAILED" from the
    original decline-handling design).** Verified against Stripe's own docs before building
    anything: confirming a PaymentIntent has "a variable upper limit on how many times a
    PaymentIntent can be confirmed. After this limit is reached, any further calls... transition
    the PaymentIntent to the `canceled` state" — Stripe's own anti-card-testing posture, already
    enforced server-side, with no fixed/published number to duplicate or disagree with. This
    reactor was already halfway there without knowing it: `payment.StripePaymentGateway
    #resultFromIntent`'s own `checkStatus` poll already classified a `"canceled"` intent as a
    decline (its `default` switch arm), so `OrderReconciliationJob` would eventually catch this on
    its next poll tick regardless — the only real gap was the webhook not reacting to it
    immediately. **Fix**: `payment_intent.canceled` added to `HANDLED_EVENT_TYPES`;
    `applyPaymentIntentEvent`'s dispatch became a `switch` with a genuine third branch —
    `PaymentResult.declined(...)` for `canceled` (the exhausted PaymentIntent is over, not
    retryable), distinct from `payment_failed`'s own `attemptFailed(...)` (still retryable) — and
    `handleWebhook` now extracts `last_payment_error` for `canceled` too, so the final decline still
    carries a real, shopper-facing reason (whatever the last individual attempt's own decline was)
    instead of a generic message.
  - **Bug avoided before it shipped: this same webhook event also fires for our own explicit
    cancel.** `service.impl.OrderServiceImpl#cancel`'s own `payment.PaymentGatewayPort
    #cancelUnconfirmed` call (a shopper explicitly cancelling while a PaymentIntent is still
    unconfirmed) *also* transitions the intent to `canceled` and fires this identical webhook event
    — but that flow already resolves the `Payment` row itself, correctly, via
    `orderstatus.PaymentCancellationService#applyGatewayCancellation` (marking it `CANCELLED`, not
    `DECLINED`), synchronously within the same request. Naively finalizing every
    `payment_intent.canceled` event as a decline would have let this webhook silently flip an
    already-`CANCELLED` row back to `DECLINED` moments later. Stripe's own `cancellation_reason`
    field (`automatic` for Stripe-internal auto-cancels vs. a user-provided reason for an explicit
    API cancel) could in principle distinguish the two triggers, but this fix doesn't lean on that
    — unverified for this exact case, and a second, indirect signal to trust when a direct one is
    available. **Fix**: `applyPaymentIntentEvent` only treats `payment_intent.canceled` as a final
    decline when the `Payment` row is still `PENDING` — already-`CANCELLED` (or any other non-
    `PENDING` status) is a safe, logged no-op instead. This fully closes the common case; a narrow
    race remains if this webhook is delivered and processed in the brief window before
    `applyGatewayCancellation`'s own transaction commits — the order still correctly ends
    `CANCELLED` either way (`PaymentProcessingOrderStatusHandler#failPayment`'s own
    `cancelRequested`-wins rule takes over regardless of which path gets there first, since the
    shopper's own cancel request is what's driving this), but the `Payment` row could read
    `DECLINED` instead of `CANCELLED` in that one rare window — a known, accepted imprecision in the
    audit trail, not a functional bug; revisit only if that ever proves to matter in practice.
  - 2 new `StripeWebhookServiceTest` cases (the genuine-final-decline path, and the
    already-resolved-elsewhere no-op guard). 366 unit tests total (up from 364), verified via a real
    `mvn test` run (JDK 21).
  - **Follow-up: `OrderReconciliationJob` now closes the one real remaining gap in the Stripe
    payment flow — a charge attempt that crashes before ever reaching Stripe used to poll forever
    with no terminal exit.** Found during a dedicated "any other gaps left in the Stripe payment
    flow?" review, not the earlier general code-quality audit. `payment.StripePaymentGateway
    #checkStatus`'s own Javadoc had already documented the scenario (a `Payment` row with no
    `gatewayReference` at all, e.g. `payment.PaymentGatewayPort#charge` threw before Stripe ever
    created a `PaymentIntent`) but nothing ever resolved it: `resolvePayment`'s own `PENDING` branch
    is always a no-op, and even an explicit shopper cancel couldn't escape it either —
    `orderstatus.PaymentCancellationService#applyCancellation`'s own `gatewayReference != null`
    guard means a null one reports `gatewayCancellationNeeded() == false`, so the cancel just
    queues silently with no visible effect. **Fix**: a `PaymentOutcome#PENDING` result with a
    `null` `gatewayReference` is uniquely produced by exactly this "never reached the gateway" case
    — every other still-processing outcome (a real, live `PaymentIntent`) always carries a real
    one — so `OrderReconciliationJob#reconcileOne` can safely tell them apart. Once this job has
    already waited a full grace period and still sees no `gatewayReference`, there's nothing left
    to ever retrieve at Stripe, so it now finalizes with a synthetic `PaymentResult#declined`
    (`GATEWAY_ERROR` category) instead of polling forever — the shopper can then simply reorder,
    and if they'd already queued a cancel, `PaymentProcessingOrderStatusHandler#failPayment`'s own
    `cancelRequested`-wins rule correctly lands the order `CANCELLED` instead of `FAILED`, exactly
    as they asked. 2 new `OrderReconciliationJobTest` cases (the synthetic-decline finalization,
    and a regression guard proving a genuinely-still-processing order with a real `gatewayReference`
    is left untouched, not conflated with this fix). 368 unit tests total (up from 366), verified
    via a real `mvn test` run (JDK 21).
- **Phase 3 (US-4.2/4.3) — `Payment` persistence actually wired into the synchronous confirm/fail
  flow.** `orderstatus.PaymentHandoffService.startPaymentProcessing` now writes the `PENDING`
  `Payment` row (order, amount snapshotted from `Order.getTotal()`, denormalized idempotency key)
  in the exact same transaction as the order's own `PENDING -> PAYMENT_PROCESSING` transition —
  this is what `Payment`'s own Javadoc has promised since Phase 1, now actually true.
  `resolvePayment`'s signature changed from `(orderId, PaymentOutcome)` to
  `(orderId, PaymentResult)` — a `SUCCEEDED`/`DECLINED` result now also updates that same
  `Payment` row's `status`/`gatewayReference` (and, for a decline, `failureCategory`/
  `gatewayFailureMessage`) in the same transaction as the order's `CONFIRMED`/`FAILED` transition;
  a `PENDING` result (the reconciliation job's "still not resolved" case) leaves both the order and
  the `Payment` row untouched, exactly as before. **The `Payment` row's own `status` always
  reflects what the gateway actually decided, independent of the order's own final status** — a
  queued cancel racing a gateway success (`PaymentProcessingOrderStatusHandler.confirmPayment`)
  still records the payment as `SUCCEEDED` even though the order itself ends up `CANCELLED` with a
  restock, since the money really was captured; Phase 6 (refund on cancellation) is what will turn
  that `SUCCEEDED` row into a real `REFUNDED` one. A missing `Payment` row at resolve time throws a
  plain `IllegalStateException` (a genuine invariant violation, not a business error — rolls back
  the order transition alongside it rather than leaving a corrupted trail). The two existing
  callers (`service.impl.OrderServiceImpl#initiatePayment`, `orderstatus.OrderReconciliationJob`)
  needed only a one-line change each (pass the whole `PaymentResult` instead of unwrapping
  `.outcome()` first). `PaymentHandoffServiceTest`/`OrderServiceImplTest`/
  `OrderReconciliationJobTest` all updated for the new signature; `PaymentHandoffServiceTest`
  gained a new case for the missing-row invariant plus richer assertions on the persisted `Payment`
  row's fields for its existing cases. **296 unit tests total** (up from 295), verified via a real
  `mvn test` run (JDK 21). **Not built yet**: no `EcommerceErrorCode.PAYMENT_*` codes; the webhook
  path (Phase 5) will need its own way into `resolvePayment`-equivalent logic, not built here.
- **Phase 4 (US-4.4) — outbox publishing on a determined payment outcome, with a deliberate
  design deviation flagged and confirmed before building.** This module already has a precedent
  the *opposite* way: it chose **not** to publish an `ORDER_CREATED` outbox event back when
  checkout was built, specifically because nothing consumed it yet — an event with no registered
  handler just retries 5× and dies `FAILED` (`outbox.OutboxEventProcessor`'s own
  `IllegalStateException("No handler registered for eventType=...")`), which was judged worse than
  not publishing at all (see this file's own Epic 2/Epic 3 history). Asked whether payment outcomes
  should follow that same deferral or get a real (if lightweight) consumer instead — **chose to
  publish, with a placeholder consumer**, since US-4.4/US-4.6 name `PAYMENT_SUCCEEDED`/
  `PAYMENT_FAILED`/`PAYMENT_REFUNDED` as real acceptance criteria, not a speculative nice-to-have
  the way an order-creation event was.
  - `enums/OutboxAggregateType` gained `PAYMENT` (migration `202609020011__0.0.2__DKP-0049`,
    widening `CKC_OUTBOX_EVENT_AGGREGATE_TYPE` — Postgres has no `ALTER CHECK`, so drop-and-recreate,
    same shape as `DKP-0035`'s own `CKC_CUSTOMER_ORDER_STATUS` widening).
  - New `orderstatus/PaymentSucceededOutboxEventHandler`/`PaymentFailedOutboxEventHandler` — each
    with its own `EVENT_TYPE` constant + nested `Payload` record, this module's usual per-handler
    convention (see `service.impl.ProductChangedOutboxEventHandler`'s own Javadoc for why neither
    is shared across handlers). **Each `handle()` is a deliberate, documented placeholder** — same
    spirit as Epic 3's own (deleted) `payment.NoOpPaymentGatewayPort`: a structured audit log line,
    since this reactor has no real email/Slack/analytics integration to hand the event to yet.
    Replace the body with a real integration when one is actually needed; don't assume more exists
    than a log line.
  - New `orderstatus/PaymentRefundedOutboxEventHandler` — built alongside its two siblings for
    symmetry (all three of US-4.4's named events exist together); this phase left it with no
    publisher — Phase 6 (US-4.6) is what gave it one, see that section below.
  - `orderstatus.PaymentHandoffService#resolvePayment`'s private `applyResultToPayment` now
    publishes `PAYMENT_SUCCEEDED`/`PAYMENT_FAILED` (via new private `publishPaymentSucceeded`/
    `publishPaymentFailed` helpers, mirroring `ProductServiceImpl.publishProductChanged`'s own
    shape) right after saving the `Payment` row's new status — same transaction, addressing the
    dual-write problem this pattern exists for. `aggregateId` is the `Payment` row's own id (not
    the order's) — `OutboxAggregateType.PAYMENT` names `Payment` as the aggregate root. New
    `OutboxEventRepository` dependency on this bean.
  - `PaymentHandoffServiceTest` gained outbox-event assertions (event type/aggregate
    type/aggregate id/payload contents) to its existing `SUCCEEDED`/`DECLINED` cases, plus a
    `never()` assertion for the `PENDING` case and the missing-payment-row case; new
    `PaymentSucceededOutboxEventHandlerTest`/`PaymentFailedOutboxEventHandlerTest`/
    `PaymentRefundedOutboxEventHandlerTest` (each: `eventType()` returns the published constant, a
    `Payload` map round-trip, and `handle()` doesn't throw for a well-formed event — these handlers
    have no business logic beyond the log line, so nothing more to test). **305 unit tests total**
    (up from 296), verified via a real `mvn test` run (JDK 21).
  - **Not built yet**: no `EcommerceErrorCode.PAYMENT_*` codes; the webhook path (Phase 5) will
    need its own transactional write into this same outbox mechanism, not built here; the refund
    path (Phase 6) is what will finally give `PaymentRefundedOutboxEventHandler` a publisher.
- **Phase 5 (US-4.5) — Stripe webhook handling, confirmed shape realized: exposed directly on this
  service's own origin, bypassing `gateway` entirely.** New top-level `/webhooks/stripe` (not
  under `/api/v1/**` — this is Stripe's server calling us, never an end-user/admin client, and it
  carries no JWT). `security.SecurityConfig` gained a `permitAll()` rule for `/webhooks/**`, for
  the same reason `content-service`'s own `/internal/**` is `permitAll()` — nothing for Spring
  Security's JWT-based filter chain to verify here; unlike that path's shared-secret header
  (enforceable by a header-only `OncePerRequestFilter`), Stripe's own HMAC signature needs the raw
  request body too, so verification happens inside the handler itself instead. `gateway`'s own
  `GatewayRoutesConfig`/`CLAUDE.md` both gained a note that this path is deliberately never
  routed, mirroring `content-service`'s `/internal/content-items/**` precedent.
  - New `entity/StripeWebhookEvent` — the at-least-once-delivery dedup ledger US-4.5 calls for
    (`stripeEventId` unique). Deliberately its own small table, not folded into `Payment` — a
    single `Payment` row can legitimately receive more than one distinct Stripe event over its
    lifetime, so "one row per event id" and "one row per payment attempt" are different
    cardinalities. New `repository/StripeWebhookEventRepository` (`existsByStripeEventId`).
  - `Payment.gatewayReference` gained a partial unique index (`UX_PAYMENT_GATEWAY_REFERENCE`,
    `WHERE GATEWAY_REFERENCE IS NOT NULL`) — the webhook's own correlation key back to exactly one
    `Payment` row, since a webhook payload only ever names a PaymentIntent id, never this reactor's
    `orderId`/`paymentId`. New `PaymentRepository.findByGatewayReference`. Migration
    `202609020012__0.0.2__DKP-0050`.
  - New `payment/StripeFailureCategoryMapper` — the Stripe decline-code → `PaymentFailureCategory`
    translation **extracted out of `StripePaymentGateway`** (its Phase 2 home) into its own shared
    class, once the webhook path needed the exact same mapping for an async decline that
    `StripePaymentGateway.charge`'s synchronous path already had inline — both call sites must
    agree on one mapping, so this is now the single source of truth rather than two copies that
    could silently drift apart.
  - New `webhook/StripeWebhookService` — the whole mechanism lives in one `@Transactional`
    `handleWebhook(byte[] rawPayload, String signatureHeader)` method: verifies the signature (via
    `com.stripe.net.Webhook.constructEvent`), ignores any event type other than
    `payment_intent.succeeded`/`payment_intent.payment_failed`, then delegates to a package-private
    `applyPaymentIntentEvent(...)` taking plain Java primitives/enums (deliberately not Stripe's
    own `Event`/`PaymentIntent` types) that does the real dedup-check/correlate-by-
    `gatewayReference`/resolve work — this split is what makes the business logic unit-testable at
    all, since constructing a real, fully-functional Stripe `Event` (backed by its own
    `EventDataObjectDeserializer`) in a plain Mockito test is impractical; `handleWebhook` itself
    has no dedicated test, same "unverified at runtime, no local harness for a real external SDK"
    precedent `StripePaymentGateway` already set. **Atomicity is achieved by transaction
    propagation, not a manual multi-step orchestration**: `applyPaymentIntentEvent` calls
    `orderstatus.PaymentHandoffService#resolvePayment` — a *different* bean's own `@Transactional`
    method — which joins `handleWebhook`'s already-open transaction (Spring's default `REQUIRED`
    propagation) rather than starting a new one, so the dedup-ledger insert, the `Payment`/`Order`
    update, and the `PAYMENT_SUCCEEDED`/`PAYMENT_FAILED` outbox publish (Phase 4) all commit or
    roll back together — exactly US-4.5's "write the outbox event + update Payment/Order
    atomically" requirement, reusing Phase 3/4's transactional logic entirely rather than
    duplicating it. A `Payment`-row miss (no match for the webhook's own PaymentIntent id) is
    handled defensively: the dedup row is still recorded (so a redelivery doesn't repeat the
    warning forever), but nothing is resolved.
  - New `api/StripeWebhookApi`+`api/impl/StripeWebhookController` — `payload` is bound as
    `byte[]`, not `String`, specifically so no `HttpMessageConverter` re-encodes the exact bytes
    Stripe's HMAC signature was computed over before verification runs. The controller is a thin
    pass-through: catches `SignatureVerificationException` → `400`; any other failure propagates
    as a `5xx`, the correct outcome for a genuine unexpected error, since Stripe interprets a
    non-2xx response as "retry this delivery later."
  - New `app.ecommerce.payment.stripe.webhook-secret` (env var `STRIPE_WEBHOOK_SECRET`, no default)
    — the signing secret from this endpoint's own Stripe Dashboard/CLI registration.
    `docker-compose.apps.yml` gained a matching passthrough env var.
    `scripts/purge-seed-data.sql` gained `ecommerce.STRIPE_WEBHOOK_EVENT` to its `TRUNCATE` list
    (19 tables total now).
  - New `StripeWebhookServiceTest` (4 cases: already-processed event is a safe no-op; no matching
    `Payment` row records-but-doesn't-resolve; succeeded/failed each resolve with the right
    `PaymentResult` and record processed afterward, `Mockito.inOrder`-verified for the succeeded
    case). **309 unit tests total** (up from 305), verified via a real `mvn test` run (JDK 21); a
    targeted `mvn -pl ecommerce-service -am compile` also confirmed the webhook path's own new
    Stripe SDK usage (`Event`/`Webhook`/`SignatureVerificationException`) compiles against the
    real dependency.
  - **Not built yet**: `EcommerceErrorCode.PAYMENT_*` codes still don't exist; nothing has
    exercised this endpoint against a real Stripe test account/CLI yet (same unverified-at-runtime
    caveat as `StripePaymentGateway`'s own SDK calls).
- **Phase 6 (US-4.6) — refund on cancellation, scoped to this user story's own literal acceptance
  criterion.** `orderstatus.PaymentHandoffService` gained the identical durable-step/gateway-call/
  durable-step shape Phase 3 already established for charges, applied to refunds:
  `applyCancellation(orderId, callerUuid)` (transitions the order via
  `OrderStatusHandlerRegistry.cancel`, then reports whether a refund is owed — a new nested
  `CancellationResult(order, refundNeeded, paymentId, gatewayReference, amount)` record) and
  `applyRefundResult(paymentId, result)` (applies a `RefundResult`: `SUCCEEDED` →
  `Payment.status = REFUNDED` + `PAYMENT_REFUNDED` outbox publish — finally giving Phase 4's
  `PaymentRefundedOutboxEventHandler` its publisher; `FAILED`/`PENDING` just log, leaving the row
  `SUCCEEDED`). `service.impl.OrderServiceImpl#cancel` is no longer `@Transactional` (same reason
  `initiatePayment` isn't — see that method's own updated Javadoc): it calls `applyCancellation`,
  then, only if `refundNeeded()`, calls `payment.PaymentGatewayPort#refund` outside any
  transaction, then `applyRefundResult`.
  - **No new intermediate status/durable "refund in flight" marker was added, unlike
    `PAYMENT_PROCESSING`'s own precedent** — `StripePaymentGateway#refund`'s own idempotency key is
    deterministic (derived from `gatewayReference`, not a fresh key per call, see that class's own
    Phase 2 Javadoc), so retrying the whole cancel-then-refund operation after a crash can never
    double-refund at the gateway, unlike a charge attempt retried with a fresh key — this asymmetry
    is why the simpler shape is correct here, not a corner cut. If the refund call itself fails,
    `Payment` is simply left `SUCCEEDED` (the order is already durably `CANCELLED` from step one) —
    a known, undone-money gap with no automatic recovery built, since US-4.6 didn't ask for one.
  - **Deliberately does not cover the rarer race in `PaymentProcessingOrderStatusHandler
    #confirmPayment`** (a cancel queued while payment is still processing, that then loses the
    race to a gateway success moments later — that handler still only restocks, exactly as
    before this phase) — US-4.6's own acceptance criterion is a shopper cancelling an already-
    `CONFIRMED` order, not this narrower race; both handlers' own Javadoc were updated to say so
    precisely (no longer "deferred to Epic 4," since Epic 4's gateway integration now exists —
    it's just not wired to this one path). Revisit if that gap ever turns out to matter in
    practice — `orderstatus.PaymentHandoffService#resolvePayment` (that race's own caller) would
    need the identical restructuring `OrderServiceImpl#cancel` already got.
  - `OrderServiceImplTest.Cancel` rewritten around the new orchestration (no-refund-needed,
    refund-needed, and exception-propagation cases); its old direct ownership-check tests moved to
    new `PaymentHandoffServiceTest.ApplyCancellation`/`ApplyRefundResult` nested classes (mirroring
    `StartPaymentProcessing`/`ResolvePayment`'s own shape). Two pre-existing tests
    (`ConfirmedOrderStatusHandlerTest`/`PaymentProcessingOrderStatusHandlerTest`) needed their own
    hardcoded `OrderStatusHistory` reason-string assertions updated to match the corrected wording.
    **317 unit tests total** (up from 309), verified via a real `mvn test` run (JDK 21).
  - **Not built yet**: no automatic retry/reconciliation for a failed refund call; the
    confirmPayment-race case above.
- **Phase 7 (US-4.7) — user-facing failure reasons, a thin REST-exposure phase by design.** The
  actual Stripe-decline-code → `PaymentFailureCategory` mapping was already built in Phase 2
  (`payment.StripeFailureCategoryMapper`) — a deliberate consolidation flagged at the time, since
  translating a gateway's own vocabulary is squarely the Adapter's job. What was still missing was
  a real "clear but non-technical reason" a shopper actually reads, and a way to reach it over
  REST. `enums.PaymentFailureCategory` gained a constructor-supplied `shopperMessage` field per
  constant (`@Getter`, mirroring `common.exception.ErrorCode`'s own `getCode()`+`getMessage()`
  convention — server-owned copy, not left for the client to invent): a short, plain-language
  sentence per category (insufficient funds / card declined / gateway error), never the gateway's
  own raw string. `dto.OrderResponse` gained `paymentStatus`/`paymentFailureCategory`/
  `paymentFailureMessage`, resolved by a new best-effort live lookup in `mapper.OrderMapper#toResponse`
  (new `PaymentRepository` dependency on that mapper) — all three `null` until a payment attempt has
  actually started (a `PENDING` order that never called `POST /{id}/pay` has no `Payment` row at
  all), same "resolve nullable, doesn't always exist" shape `toOrderLineResponse`'s own variant
  lookup already uses. `paymentFailureMessage` is always `PaymentFailureCategory#getShopperMessage()`
  — **never** `Payment#getGatewayFailureMessage()` itself, which this reactor still never sends to
  a client anywhere. Reaches the shopper through the exact same three endpoints that already
  returned `OrderResponse` — `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, and, notably,
  `POST /api/v1/orders/{id}/pay`'s own synchronous response (so a shopper whose card is declined
  right at checkout sees the reason immediately, not just on a later order-detail visit) — no new
  endpoint needed. A pleasant side effect of the same live lookup: `paymentStatus` now also
  surfaces `REFUNDED` after Phase 6's cancellation flow runs, though showing that wasn't this
  phase's own goal. **No new unit tests** — `mapper.OrderMapper` has no dedicated test suite
  (matches this module's existing convention for its hand-written mappers, same as `CartMapper`/
  `CheckoutMapper`), and the new field population is a simple null-guarded field read with no
  branching logic worth isolating. 317 unit tests total, unchanged; verified via a real
  `mvn -pl ecommerce-service -am compile`+`test` run (JDK 21).
  - **Phase 8 (GUI) is now built too** — see `gui/CLAUDE.md`'s own note for
    `OrderDetailPage.tsx`/`OrderHistoryPage.tsx`'s wiring of these three fields. This closes out
    Epic 4 in full.

**Not built yet** (do not assume these exist): `ProductCategory` delete, combo-accurate attribute
filtering (the current filter checks "some variant has size M" and "some variant has color Blue"
independently, not "one variant with both together" — see `ProductSearchView`'s Javadoc). **Epic 3
(Order Lifecycle & Inventory) is now fully built, all 6 phases** — see its own section above.
**Epic 4 (Payments) is now fully built, all 8 phases** — see its own section just above; Epic 5
(reviews/recommendations) is not. Check
`docs/CHANGELOG.md`'s `[Unreleased]` entry and this file's own freshness before assuming more
exists than what's listed above. **Compiles cleanly** (verified via
a targeted `-pl ecommerce-service,gateway -am compile`/`test`; needs `JAVA_HOME` pointed at a JDK
21 install, see the `reference-jdk21-location` memory) but has **not** been run: the app hasn't
been booted against a real Postgres, so the Liquibase migrations against the new `ecommerce` schema
(including the `CUSTOMER_ORDER`/`ORDER_LINE`/`ORDER_STATUS_HISTORY` tables), Hibernate's
`ddl-auto: validate` check against it, the native SQL in `ProductSearchViewRepository.search`, the
new `ProductVariantRepository` conditional updates, the JWT verification path, and
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
  included in the created order's own lines, plus Epic 3 Phase 2's stock-reservation cases — see
  that epic's own section above; Epic 3 Phase 3 added a full `orderstatus/` test suite on top,
  Phase 4 extended it with the payment-handoff/reconciliation mechanism's own tests, Phase 5
  added `GetOrder`/`ListOrders` cases to `OrderServiceImplTest`, and Phase 6 added
  `OrderLifecycleIntegrationTest` — see that epic's own section; a post-Epic-3 follow-up added
  `ListAllOrders` for the admin fulfillment queue), and `CartServiceImplTest` (a post-Epic-2
  follow-up — this module's first test of `CartServiceImpl` itself, covering only the new
  `removeItems` bulk-delete path; `CheckoutServiceImplTest` gained the same follow-up's Phase 2
  `selectedVariantIds` cases — see above for both), and `OrderServiceImplTest$ListOrders` (the
  shopper-facing order-history status-tabs follow-up, above). A post-Epic-3 follow-up added 7 more
  cases to `ProductCategoryServiceImplTest` (`Create`/`Update` gained parent-assignment and
  cycle-rejection cases, and a new `ListTree` nested class covers the tree-building/sorting logic
  and the orphaned-child-becomes-root defensive case) for the `ProductCategory` hierarchy support
  above. A further follow-up added `ProductDescriptionSanitizerTest` (11 cases, including a
  verified-not-assumed Google-Docs-paste trace and the `<hr>`/`<pre>` coverage that shaped the
  `gui` TipTap toolbar's design) plus 2 more `ProductServiceImplTest` cases for the
  `Product.description` HTML-sanitization support above, a post-Phase-3 follow-up added
  `ProductDescriptionImageServiceImplTest` for the real-upload description-image support above,
  and a further follow-up added `ProductDescriptionSanitizerTest.stripsDataUriImageSourcesEntirely`
  (confirms a pasted-as-base64 image's `src` would be stripped entirely on save either way).
  165 tests, all passing, no Docker needed for any of
  them (this count was independently re-verified per test class in this session — treat it, not
  any earlier figure quoted elsewhere in this file's own history, as authoritative if the two ever
  disagree).
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
