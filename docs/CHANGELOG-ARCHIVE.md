# Changelog Archive

Full, unabridged history prior to `CHANGELOG.md`'s current `[Unreleased]` section — split out once
that file grew past ~3700 lines under a single ever-growing `[Unreleased]` heading that had never
actually been cut into a real release. `[0.0.1]` is the original monolith. `[0.0.2]` is everything
accumulated between that monolith and the full six-service microservices extraction
(`ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`,
`ai-service`), `gateway`'s routing/CORS consolidation, and the reactor-wide `@ComponentScan` fix.
`[0.0.3]` is everything that accumulated under `[Unreleased]` again afterward — almost entirely
`ecommerce-service`'s feature build-out (checkout shipping/coupon pricing strategies, payment
countdown + reconciliation) and its `gui` counterpart work — retroactively cut for the exact same
reason `[0.0.2]` was: the live file had grown past ~3700 lines under a single `[Unreleased]`
section again. Both retroactive cuts follow the same
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/) format as the live file. See
`docs/CHANGELOG.md` for anything more recent.

---

## [0.0.3] — 2026-09-07 — ecommerce-service feature build-out + gui parity

### Added (cont.)

- **`ecommerce-service`: new `shipping.ShippingFeeCalculator`/`FlatRateShippingFeeCalculator` —
  a GoF Strategy seam for checkout's shipping-fee pricing, per request.** Mirrors
  `payment.PaymentGatewayPort`'s own "interface today, swap the implementation later" shape:
  `CheckoutServiceImpl` now depends on `ShippingFeeCalculator.calculate(lines, subtotal)` instead
  of reading its own `flatShippingFee` field directly, so a future pricing rule
  (free-over-threshold, weight-tiered, a real carrier-rate API call) can be swapped in without
  touching checkout. `FlatRateShippingFeeCalculator` is the only implementation today — same fee
  regardless of cart contents, same `app.ecommerce.checkout.flat-shipping-fee` property, just moved
  onto its own class. No behavior change; `CheckoutServiceImplTest` updated to mock the new
  collaborator instead of `ReflectionTestUtils.setField`-ing the old private field. See
  `ecommerce-service/CLAUDE.md`'s checkout section for the full detail, including why
  `calculate` deliberately doesn't take a shipping address yet.
- **`ecommerce-service`: follow-up — swapped in `shipping.FreeOverThresholdShippingFeeCalculator`
  as the active `ShippingFeeCalculator` strategy, per request.** Free shipping once the cart
  subtotal reaches a new `app.ecommerce.checkout.free-shipping-threshold` property
  (`CHECKOUT_FREE_SHIPPING_THRESHOLD`, default `50.00`); otherwise the same flat fee
  `FlatRateShippingFeeCalculator` always charged (reusing the existing
  `app.ecommerce.checkout.flat-shipping-fee` property rather than adding a second one for the same
  number). `FlatRateShippingFeeCalculator` stays in the codebase as a reference implementation but
  lost its `@Component` — only one strategy may be an active Spring bean at a time, same
  "don't leave two ambiguous candidates wired in" rule `payment.NoOpPaymentGatewayPort`'s own
  Javadoc documents. New `FreeOverThresholdShippingFeeCalculatorTest` pins the at/above/
  below-threshold boundary. All 203 unit tests pass (up from 200).
- **`ecommerce-service`/`gui`: follow-up — checkout preview now shows a waived shipping fee
  explicitly, per request.** `ShippingFeeCalculator.calculate` now returns a new
  `shipping.ShippingFeeQuote(fee, originalFee)` record instead of a bare `BigDecimal` —
  `originalFee` is what would have been charged absent any promotional waiver, equal to `fee`
  whenever nothing was waived (`FlatRateShippingFeeCalculator` always reports the two equal).
  `CheckoutPreview`/`CheckoutPreviewResponse` gained a matching `originalShippingFee` field
  (via `CheckoutMapper`); `confirm` uses the actual `fee` for `Order.shippingFee`/`total` as
  before (at this point in the change, `originalFee` was not yet persisted anywhere on `Order` —
  see the next entry for the follow-up that reversed that). `gui`'s `CheckoutPage.tsx` Order
  Summary now shows the original fee struck through next to a "Free" label whenever it differs
  from the actual `shippingFee`, instead of a bare `$0.00`. New
  `CheckoutServiceImplTest`/`FreeOverThresholdShippingFeeCalculatorTest` cases pin the
  fee-vs-originalFee pass-through. All 204 backend unit tests pass (up from 203); verified via a
  clean `tsc --noEmit` and a successful `vite build` on the GUI side.
- **`ecommerce-service`/`gui`: follow-up — a placed order's own detail view now shows the same
  waived-shipping-fee treatment, per request.** New `Order.originalShippingFee` column
  (`ORIGINAL_SHIPPING_FEE`, migration `DKP-0040` — added nullable, backfilled from the existing
  `SHIPPING_FEE` column, then tightened to `NOT NULL`, the usual shape for a new required column
  on an already-populated table); `CheckoutServiceImpl.confirm` now persists
  `shippingQuote.originalFee()` onto it alongside the actual `shippingFee`. Mapped onto a new
  `OrderResponse.originalShippingFee` via `OrderMapper.toResponse`. `gui`'s `OrderDetailPage.tsx`
  Items section applies the identical struck-through-original-fee-plus-"Free"-label treatment
  `CheckoutPage.tsx`'s preview already uses, gated on `order.originalShippingFee >
  order.shippingFee`. New `CheckoutServiceImplTest` case
  (`persistsAWaivedFeeSeparatelyFromWhatItWouldHaveBeen`) pins that `confirm` persists both fields
  correctly. All 205 backend unit tests pass (up from 204); verified via a clean `tsc --noEmit`
  and a successful `vite build` on the GUI side.
- **`ecommerce-service`/`gateway`: new Coupon entity + admin CRUD — Phase 1 of the "ProductDiscount"
  feature (code-driven discounts targeting the cart subtotal or shipping fee, by percentage or
  fixed amount, with conditions), per request.** Scope confirmed before building: coupon-code
  entry only (no automatic/code-free promotions — that stays
  `shipping.FreeOverThresholdShippingFeeCalculator`'s own separate mechanism); at most 2 coupons
  per order, one per `CouponTarget` (`SUBTOTAL`/`SHIPPING_FEE`) rather than open-ended stacking;
  "full" eligibility conditions (active/date-range/min-subtotal now, product/category scoping +
  redemption limits in Phase 3). New `entity/Coupon` (one entity with two orthogonal enums —
  `target`/`type` — rather than four separate Strategy classes for the 2×2 combination; `code`
  normalized to uppercase before persisting and immutable after creation) and
  `entity/CouponRedemption` (the ledger `maxRedemptions`/`maxRedemptionsPerUser` will be enforced
  against once Phase 2 exists, real FKs to both `Coupon` and `Order`). Migration `DKP-0041`. New
  `EcommerceErrorCode.COUPON_*`, `CouponRepository`/`CouponRedemptionRepository`,
  `CouponSpecification`, `CouponCommands`/`CouponService`/`Impl`, `CouponMapper`,
  `dto/{CouponResponse,CreateCouponRequest,UpdateCouponRequest}`, and `CouponApi`+`Controller` at
  `/api/v1/admin/coupons` (CRUD only — no redemption endpoint yet). `gateway`'s
  `GatewayRoutesConfig` gained a matching `/api/v1/admin/coupons/**` route in the same change (the
  discipline established on Product Tags/AddressBook held). New `CouponServiceImplTest` — 220
  backend unit tests pass (up from 205). **Not built as part of this pass**: checkout
  integration/redemption (Phase 2), product/category eligibility scoping (Phase 3), and `gui` admin
  coupon management + checkout code-entry UI (Phase 4) — this is admin-only CRUD today, nothing
  wired into checkout yet. See `ecommerce-service/CLAUDE.md`'s own Coupon note for the full detail.
- **`ecommerce-service`: Coupon feature Phase 2 — checkout integration/redemption, per request.**
  `CheckoutService.preview`/`.confirm` both gained `subtotalCouponCode`/`shippingCouponCode`
  parameters (each optional, `null`/blank applies none — the type-level embodiment of "at most 2
  coupons, one per target" locked in Phase 1). New `service/CouponRedemptionService`/`Impl` —
  `resolve(code, target, ownerUuid, subtotal)` (normalizes the code, then checks
  active/target-match/date-range/min-subtotal/global-and-per-user redemption limits, in that
  order, each its own `EcommerceErrorCode`: `COUPON_INACTIVE`/`COUPON_TARGET_MISMATCH`/
  `COUPON_NOT_YET_ACTIVE`/`COUPON_EXPIRED`/`COUPON_MIN_SUBTOTAL_NOT_MET`/
  `COUPON_REDEMPTION_LIMIT_REACHED`/`COUPON_ALREADY_REDEEMED_BY_USER`), `calculateDiscount(coupon,
  baseAmount)` (percentage or fixed, clamped to never exceed `baseAmount`), and `redeem(coupon,
  order, ownerUuid, discountAmount)` (persists a `CouponRedemption` row). `resolve`/
  `calculateDiscount` are deliberately separate methods — `minSubtotal` is always checked against
  the cart subtotal regardless of target, but the discount itself is calculated against a
  target-specific base amount (subtotal for a `SUBTOTAL` coupon, the shipping fee for a
  `SHIPPING_FEE` one) — a single method conflating both would have to smuggle in a second base
  amount just for the eligibility check. `CheckoutServiceImpl`'s new private `resolveDiscounts`
  resolves both coupons (independently — either, neither, or both may be present) before building
  totals; `preview` never redeems (no `CouponRedemption` row, no limit consumed), `confirm` calls
  `redeem` for each non-null coupon only **after** `orderRepository.save` succeeds (verified via
  `Mockito.inOrder`, same discipline as the existing save-then-clear-cart ordering). New
  `Order.subtotalDiscountAmount`/`subtotalCouponCode`/`shippingCouponCode` columns (migration
  `DKP-0042`) — `subtotalDiscountAmount` needed its own column since `Order.subtotal` itself is
  never reduced (it stays the true pre-discount sum); the shipping discount needed no equivalent
  amount column, since the existing `shippingFee`/`originalShippingFee` pair (from the free-
  shipping-threshold follow-up above) already captures "actual charged vs. what it would have
  been" — a coupon-waived shipping fee reuses that same pair rather than adding a third,
  overlapping shipping-discount field. `CheckoutPreview`/`CheckoutPreviewResponse`/`OrderResponse`
  all gained matching fields via `CheckoutMapper`/`OrderMapper` (`CheckoutMapper.toConfirmResponse`
  deliberately not updated — the GUI navigates away from the confirm response with nothing reading
  it, same precedent `originalShippingFee` set originally). New `CouponRedemptionServiceImplTest`
  (eligibility/calculation/redemption cases) plus new `CheckoutServiceImplTest` cases (a subtotal
  coupon applied without touching shipping, a shipping coupon applied on top of the automatic
  free-over-threshold strategy, an ineligible coupon's rejection propagating through `preview`,
  and `confirm` persisting both coupon codes/amounts and redeeming only after saving) — 242 backend
  unit tests pass (up from 220). No `gateway` change needed — coupon codes travel as
  `CheckoutApi`'s own existing `preview`/`confirm` params/body fields, not a new endpoint.
  **Not built as part of this pass**: product/category eligibility scoping (Phase 3) and `gui`
  admin coupon management + checkout code-entry UI (Phase 4) — a shopper still has no way to type
  a coupon code into the actual storefront yet, only the backend now understands one.
- **`gui`: Coupon feature Phase 4 — admin coupon-management page + `CheckoutPage`'s code-entry UI,
  per request.** No backend change was needed — Phases 1–2 already exposed everything this pass
  needed. `types.ts` gained `Coupon`/`CouponTarget`/`CouponType`/`Create`+`UpdateCouponPayload`,
  plus `subtotalDiscountAmount` on `CheckoutPreview`/`Order` and `subtotalCouponCode`/
  `shippingCouponCode` on `Order`. New `pages/CouponListPage.tsx` + `components/
  CouponFormDialog.tsx` (mirroring `ProductTagListPage`'s admin-CRUD shape, with Active/
  Applies-To filters and a conditions summary column), wired into `AdminLayout`'s nav
  (`/admin/coupons`) and `App.tsx`'s route tree. `CheckoutPage.tsx`'s Order Summary gained a
  Coupons section — two independent code fields (one per `CouponTarget`), each Apply/Remove
  re-fetching the preview through a shared `loadPreview` helper; a failed code sets that field's
  own inline error rather than a page-level toast, and a new "Discount" row shows
  `subtotalDiscountAmount` when present. `handleSubmit` passes both applied codes into
  `checkoutApi.confirm` — `preview` itself never redeems. `OrderDetailPage.tsx` gained the same
  Discount row plus small `Chip`s showing any redeemed coupon codes, sourced from the now-persisted
  `Order` fields. **Also fixed a shipping-fee display bug this phase's partial-discount coupons
  exposed**, in both pages: the existing "struck-through original + Free" treatment assumed any
  waiver meant a $0 charge — true only before Phase 2 (when `FreeOverThresholdShippingFeeCalculator`'s
  all-or-nothing threshold was the only waiver source) — a `SHIPPING_FEE` coupon can discount only
  partially, so both pages now only show "Free" when the actual charge is genuinely zero, else the
  real discounted fee. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no
  Docker in this sandbox, so the admin CRUD, checkout Apply/Remove flow, and order-detail display
  are unverified in a real browser. This closes out all 4 phases of the Coupon feature except
  Phase 3 (product/category eligibility scoping), which remains deferred.
- **`ecommerce-service`: new `CouponSeeder` + `data/csv/coupons.csv` — 8 realistic sample coupons,
  per request, so the checkout code-entry UI and admin coupon list have something real to exercise
  without an admin manually creating one first.** Covers every eligibility branch
  `CouponRedemptionService.resolve` checks: `WELCOME10`/`SAVE5`/`VIP20` (all `SUBTOTAL` —
  percentage, min-subtotal-gated fixed amount, redemption-capped percentage), `FREESHIP`/
  `SHIPHALF` (both `SHIPPING_FEE` — one fully covers the flat fee, one only halves it, covering
  both branches of the `gui` shipping-display fix below), `SUMMER2026`/`BLACKFRIDAY2025` (an
  active and an already-expired date range), and `OLDPROMO` (`active=false`). Mirrors
  `ProductTagSeeder`'s shape (extends `CsvSeeder<Coupon>`, direct-repository persist, no
  `CouponService` involved), with `code` itself as the idempotency key — already the entity's own
  real natural key, normalized to uppercase before the existence check, unlike
  `ProductTagSeeder`'s/`ProductCategorySeeder`'s `name` stand-in. `EcommerceDataSeedingRunner` now
  runs it last (independent of every other seeder here). `scripts/purge-seed-data.sql` gained
  `ecommerce.COUPON`/`ecommerce.COUPON_REDEMPTION` to its `TRUNCATE` list so a re-seed isn't
  silently skipped by the natural-key idempotency check. No dedicated test — matches this
  reactor's existing convention that no seeder has its own unit test (exercised by actually
  booting the app with `app.seed.enabled=true`, not in isolation); 242 unit tests still pass
  unchanged, verified via a real `mvn test` run (JDK 21). Not run against a real database in this
  session, same caveat every seeder change in this reactor carries until the `docker compose`
  stack is actually started.
- **`ecommerce-service`/`gui`: Coupon feature follow-up — `maxDiscountAmount` (a cap on a single
  redemption's discount) and `description` (a shopper-facing summary), per request.**
  `maxDiscountAmount` closes a real gap: a `PERCENTAGE` coupon's discount was otherwise unbounded
  on a large-enough cart. The motivating example — "reduce 20%, maximum $20, for an order with
  subtotal > $100" — is now the seed data's `VIP20` coupon verbatim. New `Coupon.maxDiscountAmount`/
  `description` columns (migration `DKP-0043`, both nullable).
  `CouponRedemptionServiceImpl.calculateDiscount` applies the cap after the raw percentage/fixed
  calculation but before the existing base-amount clamp, uniformly to both `CouponType` values —
  not just `PERCENTAGE`. `description` is purely presentational, never read by
  `CouponRedemptionService` — it exists for a future `gui` dialog letting a shopper browse
  applicable coupons rather than requiring they already know a code (that dialog itself is not
  built yet; this is the data-model half only). `CouponCommands`/`Create`+`UpdateCouponRequest`/
  `CouponResponse`/`CouponController` all gained both fields; `CouponSeeder`/`coupons.csv` too,
  with every one of the 8 seed coupons now carrying a real description. New
  `CouponServiceImplTest`/`CouponRedemptionServiceImplTest` cases — 248 backend unit tests pass
  (up from 242). `gui`'s `CouponFormDialog.tsx` gained matching "Max discount amount"/"Description"
  fields; `CouponListPage.tsx`'s Code column now shows the description as a caption, and its
  Conditions column shows "Up to $X off" when a cap is set. Verified via a clean `tsc --noEmit`
  and a successful `vite build` on the GUI side, and a real `mvn test` run (JDK 21) on the backend.
- **`ecommerce-service`/`gui`: Coupon feature follow-up — `imageUrl`, a promo banner/icon for the
  future coupon-picker dialog, per request.** New `Coupon.imageUrl` column (migration `DKP-0044`,
  nullable `VARCHAR(500)`) — deliberately a permanent, unsigned URL, not presigned, mirroring
  `ProductDescriptionImageService`'s own choice for `Product.description`'s inline images: a
  `Coupon` has no "not-yet-published/deactivated" access-control concern, so there's no reason to
  pay a presigned URL's re-signing cost. New `CouponImageService`/`Impl` (delegates to `infra`'s
  `StorageService.uploadPublicImage`), `api/CouponImageApi`+`Controller` at
  `POST /api/v1/admin/coupons/images/upload` (a separate resource from `CouponApi`, nested under
  its path purely so `gateway`'s existing route already covers it), and `dto/CouponImageResponse`
  — all mirroring `ProductDescriptionImageService`/`Api`/`Controller`/`Response`'s exact shape.
  `Coupon`/`CouponCommands`/`Create`+`UpdateCouponRequest`/`CouponResponse`/`CouponController` all
  gained a matching `imageUrl` field. New `CouponImageServiceImplTest` plus a
  `CouponServiceImplTest.persistsImageUrl` case — 250 backend unit tests pass (up from 248). No
  `gateway` route change needed. Seeded coupons deliberately keep `imageUrl = null` for now — no
  real static asset to reference without generating placeholder banners. `gui`'s
  `ecommerceApi.uploadCouponImage` + `CouponFormDialog.tsx`'s new "Promo image" section (reusing
  the shared `components/Thumbnail.tsx`, with an upload-in-progress spinner overlay mirroring
  `ProfilePage.tsx`'s own avatar-upload treatment) + `CouponListPage.tsx`'s new leading thumbnail
  per row round out the GUI side. Verified via a clean `tsc --noEmit` and a successful `vite build`
  on the GUI side, and a real `mvn test` run (JDK 21) on the backend.
- **`ecommerce-service`/`gateway`/`gui`: the shopper-facing coupon picker dialog, per request —
  the payoff for the `description`/`imageUrl` fields built in earlier follow-ups.** New
  `CouponRedemptionService#listAvailable(target, ownerUuid)` lists every currently-redeemable
  coupon for a target (active, within date range, not yet exhausted globally or per-caller) —
  deliberately does not filter on `minSubtotal`, so a browsable list shows every offered coupon
  with its own condition rather than silently hiding ineligible ones. New
  `CouponRepository.findAllByTargetAndActiveTrueOrderByValueDesc`, `dto/AvailableCouponResponse`
  (leaner than admin's `CouponResponse`, plus a new `eligible` field computed per-request against
  the caller's live subtotal via a new hand-written `CouponMapper#toAvailableResponse`), and
  `api/CouponPickerApi`+`Controller` at `GET /api/v1/coupons/available` — a new top-level prefix,
  authenticated-shopper-facing (no admin gate), mirroring the `OrderApi`/`AdminOrderApi` split.
  `gateway`'s `GatewayRoutesConfig` gained a matching `/api/v1/coupons/**` route in the same
  change. New `CouponRedemptionServiceImplTest.ListAvailable` (8 cases) — 258 backend unit tests
  pass (up from 250). `gui`'s new `api/couponApi.ts` + `components/CouponPickerDialog.tsx` (a
  pure picker — selecting a coupon calls `onSelect(code)`, and `CheckoutPage.tsx`'s own existing
  `handleApplyCoupon` does the actual `checkoutApi.preview` revalidation, no second apply path)
  are wired in via new "Browse available coupons" buttons on `CheckoutPage.tsx`'s two coupon-entry
  rows, sharing one `couponPickerTarget` state slot for a single dialog instance. Verified via a
  clean `tsc --noEmit` and a successful `vite build` on the GUI side, and a real `mvn test` run
  (JDK 21) on the backend. This closes out the Coupon feature except Phase 3 (product/category
  eligibility scoping), which remains deferred.

### Changed (cont.)

- **`ecommerce-service`: `FlatRateShippingFeeCalculator` is the active `ShippingFeeCalculator` bean
  again, per request — fixing a real conflict with the Coupon feature's `SHIPPING_FEE` coupons.**
  Once a shopper's cart already qualified for `FreeOverThresholdShippingFeeCalculator`'s automatic
  free-shipping threshold (`shippingFee` already `0`), a `SHIPPING_FEE` coupon could still "apply"
  successfully — `CouponRedemptionServiceImpl.calculateDiscount` clamps to the base amount it's
  discounting off of, so the computed discount was silently `0` — yet `redeem()` still ran
  regardless, consuming a real, possibly limited-use `CouponRedemption` for zero actual benefit,
  with `gui` showing the exact same "Free" line before and after. Rather than add cross-mechanism
  guard logic, the fix was to stop running two independent shipping-discount mechanisms at once:
  coupons are now the only thing that ever discounts a shipping fee. Swapped via the usual
  `@Component` move (removed from `FreeOverThresholdShippingFeeCalculator`, added to
  `FlatRateShippingFeeCalculator` — both classes' own Javadoc updated with the full incident
  writeup); `application.yml`'s comment updated (`free-shipping-threshold` stays defined but
  unread unless that strategy is switched back in). `gui`'s `CheckoutPage.tsx`/`OrderDetailPage.tsx`
  had explanatory comments updated to stop citing the now-inactive automatic waiver as a possible
  discount source — their actual struck-through-fee display logic was already correct and needed
  no change, since it's driven by `originalShippingFee > shippingFee` regardless of which
  mechanism caused it. 258 backend unit tests still pass, verified via a real `mvn test` run
  (JDK 21); `gui` verified via a clean `tsc --noEmit` only.

- **`gui`: `AdminLayout.tsx`'s flat sidebar nav list replaced with a grouped parent/children
  structure, per request** (e.g. clicking "Ecommerce" drops down Product Categories/Products/
  Product Tags/Order Fulfillment/Coupons). New `NAV_STRUCTURE` groups every section by which
  backend module it fronts — Content (`content-service`), AI (`ai-service`), Ecommerce
  (`ecommerce-service`) — with Overview staying a standalone leaf. Expanded sidebar: each group is
  an accordion-style row (multiple groups can be open at once) wrapping an indented `Collapse` of
  its children, auto-expanding whichever group contains the current route on every navigation.
  Collapsed sidebar: a group's icon opens a flyout `Menu` instead, since there's no room to expand
  inline. Verified via a clean `tsc --noEmit` and a successful `vite build` only.

- **`gui`: fixed a layout-shift bug on `CartPage.tsx`'s multi-select header row, per report.** The
  "Delete Selected (N)" button was conditionally *rendered* only once at least one line was
  selected, so the row's own height jumped taller the instant the first item got selected (a MUI
  `Button` is taller than the plain Checkbox+label row beside it, and the row's flex height tracks
  its tallest child). Fixed by always mounting the button and toggling `visibility` instead of
  conditionally rendering it — `visibility: hidden` still reserves the button's layout box (so the
  row's height stays constant either way) while taking it out of the tab order; also `disabled`
  whenever nothing's selected. Verified via a clean `tsc --noEmit` and a successful `vite build`
  only.

- **`gui`: `CheckoutPage.tsx`'s two coupon-code fields (subtotal/shipping) collapsed into one
  shared code field inside `CouponPickerDialog.tsx` itself, per request** — "one input for coupon
  only," with the code field acting as a live search box filtering the dialog's own two sections
  (Free Shipping, shown first, then Discount — matching the Order Summary row order below), and
  coupon selection switched from a per-row Apply button to a `RadioGroup` per section (one coupon
  per `CouponTarget` — "restrict one coupon per type" is now a radio's own natural constraint,
  not just a backend rule). `CheckoutPage.tsx` no longer has any coupon-code field of its own at
  all — just the two applied-coupon `Chip`s (shipping first, then subtotal) and one "Add a
  coupon"/"Manage coupons" button that always opens the dialog. Selecting radios only updates
  local dialog state; a single bottom "Apply" action commits **both** slots in one combined
  `checkoutApi.preview` call (never a delta), and the dialog stays open with an inline error on
  failure rather than closing immediately, so a rejected selection doesn't force a reopen to try
  something else. The Order Summary's own Shipping row now renders before the subtotal Discount
  row too, matching the dialog's section order. Verified via a clean `tsc --noEmit` and a
  successful `vite build` only.

- **`gui`: `CouponPickerDialog.tsx`'s "No coupon" radio option removed from each section, per
  request.** Each `RadioGroup` now simply starts with nothing selected for a slot that has
  nothing applied yet, rather than a `NONE_OPTION` sentinel radio — by design, there is no longer
  a way to un-choose a coupon from inside the dialog once selected (plain radio semantics: picking
  a different option is the only way to change a selection). Clearing an already-applied coupon
  still works exactly as before, via that coupon's own `Chip`'s `onDelete` on `CheckoutPage.tsx`,
  outside the dialog. Verified via a clean `tsc --noEmit` and a successful `vite build` only.

- **`ecommerce-service`/`gui`: the coupon picker now sorts by what's actually best for this order,
  per request — not by a coupon's own declared `value`.** A `PERCENTAGE` coupon's raw `value`
  alone doesn't say how much money it actually saves (especially once `maxDiscountAmount` caps it,
  or against a `FIXED_AMOUNT` coupon at all). New `CouponRedemptionService.listAvailableRanked`
  computes each coupon's real `eligible` flag and `discountAmount` (via the existing
  `calculateDiscount`) and sorts eligible-first, then by `discountAmount` descending — an
  ineligible coupon always sorts after every eligible one regardless of size. Returns a new
  `CouponRedemptionService.RankedCoupon` record; `CouponPickerController` stays a thin
  pass-through (business logic lives in the service, per this reactor's own convention).
  `AvailableCouponResponse` gained `discountAmount`; `CouponPickerApi.listAvailable` gained an
  optional `shippingFee` param (the base amount `SHIPPING_FEE` coupons are compared against — the
  endpoint already had `subtotal` for `SUBTOTAL` ones). New `CouponRedemptionServiceImplTest
  .ListAvailableRanked` (3 cases) — 261 backend unit tests pass (up from 258). `gui`'s
  `AvailableCoupon` type gained `discountAmount` too, shown as a `"Save $X"` caption per eligible
  row; `couponApi.listAvailable`/`CouponPickerDialog.tsx` gained a matching `shippingFee`
  parameter/prop, with `CheckoutPage.tsx` passing `preview.originalShippingFee` (the pre-coupon
  baseline, not the possibly-already-discounted `preview.shippingFee`) so reopening the dialog
  always ranks shipping coupons consistently. No client-side sort — the dialog renders coupons in
  the order the response arrives. Verified via a clean `tsc --noEmit` and a successful `vite build`
  on the GUI side, and a real `mvn test` run (JDK 21) on the backend.

- **`ecommerce-service`/`gui`: a `phone` field on `Address`/`SavedAddress`/`Order`, per request.**
  A shipping contact number, threaded through both address paths — an existing AddressBook entry
  and a fresh, one-off address at checkout — the same way `fullName` already is.
  `entity/Address`/`entity/SavedAddress` both gained a `PHONE VARCHAR(30)` column, **nullable at
  the DB level** (new migration `DKP-0045`) since pre-existing rows have nothing to backfill it
  from, unlike every sibling address column (all `NOT NULL` from day one); required going forward
  regardless, purely at the application layer — `Create`/`UpdateSavedAddressRequest` both gained
  `@NotBlank`/`@Size(max = 30)`, and `CheckoutServiceImpl.resolveAddress`'s own imperative
  completeness check now requires it too for a fresh, ad-hoc checkout address. Threaded through
  every DTO/command/mapper in between (`AddressRequest`/`AddressResponse`/`SavedAddressResponse`,
  `CheckoutCommands.AddressInput`, `SavedAddressCommands.Create`/`.Update`,
  `OrderMapper.toAddressResponse`, both `CheckoutServiceImpl.toAddress(...)` overloads) — pure field
  threading, no new branching logic, so `CheckoutServiceImplTest`/`SavedAddressServiceImplTest` only
  needed updating for the new constructor arity (261 unit tests pass, unchanged). `gui`'s
  `Address`/`SavedAddress` types gained an optional `phone` (mirrors `line2`'s own precedent, since
  the type doubles as local form state that never actually holds `null`); `CreateSavedAddressPayload`/
  `UpdateSavedAddressPayload` gained a required one instead. `AddressFormDialog.tsx`/
  `CheckoutPage.tsx`'s own address form both gained a required "Phone Number" field;
  `AddressBookPage.tsx`/`OrderDetailPage.tsx`/`CheckoutPage.tsx`'s `formatSavedAddress` all show it
  conditionally, same as `line2`'s existing display idiom, since an order/address from before this
  field existed simply won't have one. Verified via a clean `tsc --noEmit`/`vite build` on the GUI
  side and a real `mvn test` run (JDK 21) on the backend.

- **`ecommerce-service`/`gui`: an `email` field on `Address`/`SavedAddress`/`Order` too, per
  request — the invoice/order-confirmation recipient, deliberately independent of the caller's
  Keycloak login email.** Prompted by a direct question about whether an order's invoice email
  needed its own column, given the app "normally sends invoice info to the user's email"; the
  initial recommendation was to resolve it from the JWT's own `email` claim instead (no schema
  change), but the user clarified a shopper's login email and the email they want an invoice sent
  to can legitimately differ — a real per-address contact detail, not an account attribute.
  Mirrors `phone`'s own treatment field-for-field: `entity/Address`/`entity/SavedAddress` both
  gained an `EMAIL VARCHAR(255)` column, **nullable at the DB level** (new migration `DKP-0046`,
  same no-backfill reasoning as `phone`'s own `DKP-0045`), required for every fresh write at the
  application layer instead — `Create`/`UpdateSavedAddressRequest` both gained
  `@NotBlank`/`@Email`/`@Size(max = 255)` (an actual format constraint, unlike `phone`), and
  `CheckoutServiceImpl.resolveAddress`'s own imperative completeness check now requires it too.
  Threaded through the same DTOs/commands/mappers `phone` was — pure field threading, so
  `CheckoutServiceImplTest`/`SavedAddressServiceImplTest` only needed the new constructor arity
  (261 unit tests pass, unchanged). `gui`'s `Address`/`SavedAddress` types gained an optional
  `email` (same reasoning as `phone`); `CreateSavedAddressPayload`/`UpdateSavedAddressPayload`
  gained a required one. `AddressFormDialog.tsx`/`CheckoutPage.tsx`'s own address form both gained
  a required "Email" field with a lightweight client-side regex check (the backend's own `@Email`
  is the real validation); `AddressBookPage.tsx`/`OrderDetailPage.tsx` show it conditionally, and
  `formatSavedAddress` now folds `phone`/`email` together into one contact segment. Verified via a
  clean `tsc --noEmit`/`vite build` on the GUI side and a real `mvn test` run (JDK 21) on the
  backend.

- **`gui`: new site-wide `app/Footer.tsx`, per request ("Add a footer for the user page").**
  Legal links (Privacy Policy/Terms of Service/Shipping Policy/Report a Violation), social links
  (Facebook/TikTok — a small inline `SvgIcon`, since no official TikTok glyph ships in
  `@mui/icons-material`), a few internal Quick Links (Shop/Cart/Your Orders/Your Account), a
  support email, and a copyright line. Every legal/social link is a literal `#` placeholder per
  request ("Use fake link at the moment"); only the Quick Links use real `react-router` `Link`s.
  Hidden on the same routes `NavBar.tsx` already hides itself on (`/admin`, `/chat`, `/messages`).
  Wired into `App.tsx` via the standard "sticky footer" flex shape
  (`display: flex, flexDirection: column, minHeight: 100vh` wrapping `NavBar`/`Routes`/`Footer`,
  `Routes` itself wrapped in a `flexGrow: 1` `Box`) so it sits at the bottom of the viewport on
  short pages without floating mid-page, confirmed safe against `ChatPage.tsx`/
  `MessagesPage.tsx`/`AdminLayout.tsx`'s own absolute `height: '100vh'` root layouts (none of which
  ever render `Footer` anyway). Verified via a clean `tsc --noEmit` and a successful `vite build`
  only — no Docker in this sandbox, so the actual sticky-footer behavior is unverified in a real
  browser.

- **`gui`: Order History/Detail moved into `AccountLayout`'s shell — `/account/orders`/
  `/account/orders/:id`, replacing their own former top-level `/orders`/`/orders/:id` routes, per
  request.** `AccountLayout.tsx`'s sidebar gained a third `Orders` entry alongside Profile/
  Addresses; `NavBar.tsx`'s own dedicated "Orders" button was removed in the same change (its
  "Account" button already covers `/account/**`, same treatment `Addresses` already had). Every
  internal link pointing at the old paths was repointed: `OrderHistoryPage.tsx`'s "View Details",
  `OrderDetailPage.tsx`'s "Back to Your Orders"/back arrow, `CheckoutPage.tsx`'s post-confirm
  redirect, and `Footer.tsx`'s "Your Orders" Quick Link. Both pages' own outer
  `width: '80%', mx: 'auto'` wrapper came off in favor of plain `p: 3` — the same fix
  `AddressBookPage.tsx` already needed for the identical reason (nested inside `AccountLayout`'s
  own already-`~80%`-wide column, a second `80%` compounds into a narrow, off-center block). No
  backward-compat redirect from the old URLs — unlike `/dashboard`'s own kept-as-redirect, nothing
  hardcodes the old `/orders` path the way several login-flow pages hardcode `/dashboard`. Verified
  via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the
  actual routing/nav-highlighting/layout behavior is unverified in a real browser.

- **`gui`: full `features/auth` folder analysis-and-cleanup pass, per request ("as we did with the
  ecommerce folder").** Two real bugs fixed: `ProfilePage.tsx` had a dead 401-handling branch that
  could never actually fire (`httpClient.ts` already intercepts every 401 on that endpoint before
  it reaches the generic error path); `Login.tsx`/`SignUp.tsx` had stale "Duck Chat" branding left
  over from before the app was renamed "Dev Knowledge Platform". Several duplicated blocks
  extracted into shared code: new `utils/keycloakConfig.ts` (Keycloak realm URL constants + a
  shared `rpInitiatedLogout` helper, each previously duplicated across 2-3 files), new
  `hooks/useOAuthCallback.ts` + `components/OAuthCallbackStatus.tsx` (the ~90%-identical
  `AuthCallback.tsx`/`AdminAuthCallback.tsx` flows), new `components/AuthCard.tsx`/
  `SocialLoginButtons.tsx`/`PasswordField.tsx` (duplicated across `Login.tsx`/`SignUp.tsx`/
  `AdminLogin.tsx`), and a new `@shared/utils/validation.ts#isValidEmail` (the same email regex was
  duplicated 4×, including 2 in `@ecommerce`'s `CheckoutPage.tsx`/`AddressFormDialog.tsx`, both
  switched over too). `ProfilePage.tsx` (already flagged elsewhere in this file as a God Component)
  shrank from ~520 to ~433 lines via two new hooks (`useCurrentUserProfile`/
  `useEmailVerificationPolling`) plus a `ProviderChip` component deduplicating markup that was
  repeated twice in that same file. `types.ts` gained literal-union types
  (`Role`/`UserProvider`/`UserStatus`) mirroring identity-service's own enums, tightening what used
  to be plain `string` fields. See `gui/CLAUDE.md`'s own detailed note for the full file-by-file
  breakdown. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in
  this sandbox, so the actual login/signup/callback/profile-edit flows are unverified in a real
  browser.

- **`ecommerce-service`: `ProductVariant.attributes` category-schema follow-up — a global
  `ProductAttribute` registry ("Option B"), assigned to categories with per-assignment `required`,
  per request.** Two designs were sketched and compared (a simpler per-category-only "Option A"
  vs. this shared-registry "Option B") before building either — B was chosen specifically so a
  concept like "Color" has one spelling and one shared value list everywhere it's assigned. New
  entities `ProductAttribute` (global, e.g. "color", cascade-owns its `ProductAttributeValue`
  vocabulary), `ProductAttributeValue`, and `ProductCategoryAttribute` (the many-to-many join to
  `ProductCategory`, cascade-owned by `ProductCategory.categoryAttributes`, mirroring
  `ProductTagAssignment`'s own explicit-join shape). `ProductCategoryService.create`/`.update`
  gained an `attributes` parameter (three-state semantics matching `ProductCommands.Update.tagIds`).
  **Enforcement is real, not advisory** — `ProductServiceImpl.validateAttributesAgainstCategory`
  runs in `create`/`addVariant`/`update` (re-validating existing variants on a category change),
  but a category with zero assignments stays exactly as free-form as before, so no existing
  category/product/seed data is affected. New `api/ProductAttributeApi`+`Controller` at
  `/api/v1/admin/product-attributes` (full CRUD); `ProductCategoryResponse` gained an `attributes`
  field (ids only, mirrors `ProductResponse.tagIds`'s own precedent). `gateway`'s
  `GatewayRoutesConfig` gained the matching new route. New migration `DKP-0047`. New
  `ProductAttributeServiceImplTest` plus new cases in `ProductCategoryServiceImplTest`/
  `ProductServiceImplTest` — **288 unit tests total** (up from 261), verified via a real `mvn test`
  run (JDK 21). Backend only this round, per explicit scope decision — no GUI yet; see
  `ecommerce-service/CLAUDE.md`'s own detailed note for the full breakdown.

- **`ecommerce-service`: sample seed data for `ProductAttribute`/`ProductCategoryAttribute`, per
  request.** New `service/seed/ProductAttributeSeeder` (`data/csv/product_attributes.csv` — 4
  attributes: `size`, `color`, `capacity`, `packSize`, each with a semicolon-joined value list) and
  `ProductCategoryAttributeSeeder` (`data/csv/product_category_attributes.csv` — assigns them to
  `Apparel`/`Drinkware`/`Stickers`; `Office`/`Accessories` deliberately left free-form, since their
  existing sample variants use a `size`-like key with a vocabulary that doesn't match `Apparel`'s,
  and `ProductAttribute.name` matches a variant's map key literally, so two categories can't share
  a key name with two different vocabularies). Wired into `EcommerceDataSeedingRunner` between tags
  and products — assignment must exist before `ProductSeeder` runs, since `ProductServiceImpl`'s
  category-schema enforcement applies to every variant seeded from that point on. Every value in
  the existing `product_variants.csv` for the three schema'd categories was verified against the
  new schema before this seed data was written (zero violations). See `ecommerce-service/CLAUDE.md`'s
  own `service/seed/` note for the full per-seeder breakdown.

- **`gui`: admin CRUD GUI for `ProductAttribute` ("Option B" global attribute registry), per
  request — completes the GUI side of the ProductAttribute feature (backend + seed data landed in
  the two entries above).** New `pages/ProductAttributeListPage.tsx` +
  `components/ProductAttributeFormDialog.tsx`, mirroring `ProductTagListPage.tsx`/
  `ProductTagFormDialog.tsx`'s shape plus a Values column (`Chip`s, sorted by `displayOrder`) and
  the form's own dynamic values-list editor (add via Enter/button, remove, reorder via the same
  `ArrowUpwardIcon`/`ArrowDownwardIcon` + array-swap `move()` convention `ImageThumbnailGrid.tsx`
  established) — the whole `values` list is always submitted as one array, matching the backend's
  own clear-and-rebuild `create`/`update` shape (list position becomes `displayOrder`
  server-side, never a caller-supplied field). Wired into `AdminLayout.tsx`'s nav (`TuneIcon`,
  `/admin/product-attributes`, right after Product Tags) and `App.tsx`'s admin route tree.
  `types.ts` gained `ProductAttribute`/`ProductAttributeValue`/`Create`+`UpdateProductAttributePayload`,
  and `ecommerceApi.ts` gained the matching `listProductAttributes`/`createProductAttribute`/
  `updateProductAttribute`/`deleteProductAttribute` methods + `ProductAttributeListParams`.
  - **`ProductCategoryFormDialog.tsx` gained the actual category↔attribute assignment UI** — an
    "Attributes" section below the existing Parent-category picker: fetches the full attribute
    registry once per dialog open (`listProductAttributes({ size: 200, ... })`, same "just fetch
    everything for a picker" convention `QuestionAnswerFormPage.tsx`'s own tag picker uses), seeds
    a local `assignments: CategoryAttributeAssignmentInput[]` from `category.attributes` (sorted by
    `displayOrder`) when editing, and renders each assigned row with a name lookup (cross-referenced
    by id, since `CategoryAttributeAssignmentResponse` is ids-only — mirrors `Product.tagIds`'s own
    "ids only" convention), a Required checkbox, reorder arrows, and a remove button, plus an "Add
    attribute" `Select` limited to attributes not yet assigned. **Always sends the full current
    `assignments` array on submit — never omits it** — unlike `UpdateProductCategoryPayload`'s
    three-state `null`/`[]`/non-empty semantics on the wire, this form is the one place that edits a
    category's schema at all, so there's no "admin didn't touch this section" ambiguity to preserve
    the way `ProductFormPage.tsx`'s tag picker also always sends its own full current set.
    `types.ts` gained `CategoryAttributeAssignment`/`CategoryAttributeAssignmentInput`, and
    `ProductCategory`/`Create`+`UpdateProductCategoryPayload` all gained an `attributes` field.
  - Verified via a clean `tsc --noEmit` (only the pre-existing `App.test.tsx`/`reportWebVitals.ts`/
    `MessageBubble.tsx`/`SessionSidebar.tsx` errors) and a successful `vite build` — no Docker in
    this sandbox, so the actual admin CRUD flow and the category-attribute assignment UI
    (reordering, required-toggle, add/remove) are unverified in a real browser.

- **`ecommerce-service`: bug fix — `ProductCategoryAttributeSeeder.seed()` threw
  `org.hibernate.LazyInitializationException: ... categoryAttributes ... no Session` the first
  time it actually ran, caught once the app was booted with `app.seed.enabled=true`.** Unlike
  `CsvSeeder`'s per-row `repository.save()` calls (each its own short-lived transaction via
  `SimpleJpaRepository`), this seeder fetches a `ProductCategory` via `findByNameIgnoreCase`, then
  reads its lazy `categoryAttributes` collection, then saves it back — all in one unit of work; with
  no surrounding transaction, the Hibernate session opened by `findByNameIgnoreCase` closed the
  instant that call returned, so the very next line's lazy-collection read had no session left to
  initialize against. Fixed by adding `@Transactional` to `seed()`, mirroring
  `ProductCategoryServiceImpl`'s own class-level annotation. Verified via a targeted
  `-pl ecommerce-service -am compile` (JDK 21) — not yet re-exercised against a real booted app in
  this session; see `ecommerce-service/CLAUDE.md`'s own `ProductCategoryAttributeSeeder` note.

- **`ecommerce-service`/`gui`: `ProductVariantEditor` adapted for two things, per request —
  editing an existing variant, and suggesting a variant's attribute rows from its product's
  category schema.** New backend endpoint `PUT /api/v1/admin/products/{id}/variants/{variantId}`
  (`ProductApi`/`ProductController.updateVariant`, reusing the existing `ProductVariantRequest`/
  `ProductVariantResponse` DTOs) plus `ProductService`/`ProductServiceImpl.updateVariant` — a
  full-replace update (SKU/price/stock/attributes), running the same validation `addVariant` does
  (SKU-conflict check only when the SKU actually changed, via new
  `ProductVariantRepository.existsBySkuAndIdNot`; the US-1.6 cross-variant attribute-key check now
  excludes the variant being edited from its own reference lookup; the category-schema
  enforcement runs unchanged) plus a new guard `addVariant` never needed —
  `PRODUCT_VARIANT_STOCK_BELOW_RESERVED` (`PRODUCT_VARIANT_005`) rejects lowering `stockQuantity`
  below the variant's own live `reservedQuantity`, instead of letting that surface later as a raw
  DB `CHECK` violation. 9 new `ProductServiceImplTest` cases — 297 unit tests total (up from 288),
  verified via a real `mvn test` run (JDK 21). No `gateway` route change needed.
  `gui`'s `ProductVariantEditor.tsx` gained a per-row Edit button opening `ProductVariantDialog.tsx`
  in edit mode (prefilled fields, "Save" instead of "Add"); `useDraftVariants` gained a matching
  `updateDraftVariant` for create-mode's local unsaved list; `ecommerceApi.ts` gained
  `updateVariant`. New `hooks/useCategoryAttributeSuggestions.ts` resolves the selected category's
  own attribute schema (ids only) into a ready-to-render `SuggestedAttribute[]` (name/required/
  controlled vocabulary) by cross-referencing the full category list and attribute registry, both
  fetched once. When the category has a schema, the dialog renders exactly those attributes as
  locked-key `Select`s restricted to each one's controlled vocabulary, in place of the free-form
  key/value editor — a deliberate 1:1 mirror of the backend's own all-or-nothing enforcement (once
  a category has *any* assigned attribute, any key outside that set is rejected), not just a
  convenience default; the free-form editor only reappears for a category with zero assigned
  attributes. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in
  this sandbox, so the actual edit/suggested-attribute flow is unverified in a real browser. See
  `ecommerce-service/CLAUDE.md`'s own `ProductVariantEditor` follow-up note for the full detail.

- **`ecommerce-service`: extended `ProductAttribute` category-schema coverage to all 5 sample
  categories, per request — `Office`/`Accessories` were the last two left deliberately free-form.**
  Three new narrow attributes in `data/csv/product_attributes.csv` — `matSize` (`Small;Large`),
  `sleeveSize` (`13in;15in`), `sockSize` (`S-M;L-XL`) — one per product whose own "size"-shaped
  concept was genuinely incompatible with the global `size` attribute (`XS`-`XXL`) or with each
  other, since `ProductAttribute.name` matches a variant's map key literally; renaming any of
  them into the shared `size` vocabulary would have misrepresented the actual product (a 13-inch
  laptop sleeve isn't meaningfully an "S"). `data/csv/product_variants.csv`'s own `attributes`
  cells for Segfault Desk Mat/Merge Conflict Laptop Sleeve/Compile Time Socks were updated to use
  these new keys instead of `size`; `data/csv/product_category_attributes.csv` gained `Office` =
  `color` + `matSize` and `Accessories` = `color` + `sleeveSize` + `sockSize` (all optional, since
  none applies to *every* product sharing that category). Verified via a standalone throwaway
  script replaying `ProductServiceImpl.validateAttributesAgainstCategory`'s exact rules — plus the
  separate US-1.6 "one key set per product" check — against the real CSV data: zero violations.
  See `ecommerce-service/CLAUDE.md`'s own `ProductCategoryAttributeSeeder` note for the full
  per-category breakdown.

- **`ecommerce-service`/`gui`: reversed category-schema enforcement to advisory-only, per explicit
  request — "the `product_category_attributes` is just a suggestion, don't force the user to
  follow it."** This directly reverses the earlier "Enforcement is real, not just advisory"
  decision — the actual catalog turned out to need attribute vocabularies/combinations this
  reactor's sample schema can't fully anticipate (e.g. a "Shirt" variant should be free to use
  `Size`/`Model` even though `Apparel` only suggests `size`/`color`).
  `ProductServiceImpl.validateAttributesAgainstCategory` was deleted outright, along with its
  call sites in `create`/`update`/`addVariant`/`updateVariant` and the now-unused
  `PRODUCT_VARIANT_ATTRIBUTE_NOT_ALLOWED_FOR_CATEGORY`/`PRODUCT_VARIANT_REQUIRED_ATTRIBUTE_MISSING`/
  `PRODUCT_VARIANT_ATTRIBUTE_VALUE_NOT_ALLOWED` error codes. `ProductCategoryAttribute`/
  `ProductAttribute` themselves are untouched — the data model and admin CRUD for both still exist
  exactly as before; only the *validation* of a variant's `attributes` against that schema is
  gone, and both entities' Javadoc now say so explicitly. The unrelated US-1.6 "every variant of
  one product shares one key set" checks are unaffected. Tests asserting the old rejection
  behavior were replaced with ones asserting the new tolerant behavior — 293 unit tests total
  (down from 297), verified via a real `mvn test` run (JDK 21).
  `gui`'s `ProductVariantDialog.tsx` dropped its "locked, suggestion-only" mode — the free-form
  key/value row editor is now always shown, with the category's suggested attributes surfaced as
  clickable quick-add `Chip`s above it (a `required` one is labeled "usually required," a hint
  only) and, for a row whose key matches a suggestion, a `freeSolo` `Autocomplete` offering that
  attribute's controlled vocabulary as options without restricting typed input. See
  `ecommerce-service/CLAUDE.md`'s own follow-up note (under the `ProductAttribute` design section)
  for the full detail on both sides.

- **`gui`: bug fix — an existing product variant's attribute key (and that row's own delete
  button) couldn't be edited or removed at all once a product had 2+ variants, reported directly
  ("I can not edit the Variant attribute (key) and delete the Variant").** Root cause:
  `ProductVariantEditor`'s `requiredAttributeKeys` (the keys this product's *other* variants use,
  meant to guide **adding** a new variant so it matches its siblings, US-1.6) was also being used
  to `disabled` the Key field/row delete button inside `ProductVariantDialog.tsx` while **editing**
  an existing variant — but every sibling variant always shares the exact same key set by that
  very invariant, so the check always matched and every row on any multi-variant product came back
  permanently locked. Fixed by dropping that disabling entirely (and the gate that hid the "Add
  attribute" button) — the check now only feeds `handleSubmit`'s fast, friendly pre-submit message,
  same as it always did; a genuine cross-variant inconsistency still gets rejected by the backend's
  own `PRODUCT_VARIANT_ATTRIBUTE_KEYS_INCONSISTENT`, surfaced via the normal `showError` toast.
  **Deleting the variant itself was not a bug** — the Remove button is disabled only when it's the
  product's last remaining variant (`PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT`), which is what was
  actually being observed. Verified via a clean `tsc --noEmit` and a successful `vite build` only.

- **`gui`: confirmation dialogs before deleting a product variant or a product image, plus a red
  delete icon for the variant one, per request.** `ProductVariantEditor.tsx`'s Remove button no
  longer calls `onRemove` directly — it opens the shared `@shared/components/ConfirmDialog` (the
  same component every other admin-list delete flow already uses) naming the variant's SKU;
  `onRemove`'s type widened to `(id) => void | Promise<void>` so the dialog can `await` it (create
  mode's removal is synchronous, edit mode's is a real backend call) and close only once it
  actually completes, with a loading spinner on Confirm meanwhile. Its `IconButton` also gained
  `color="error"` (red) — it had no explicit color before, unlike `ImageThumbnailGrid`'s own
  remove button (used by `ProductImageGallery.tsx`/`ProductImageStager.tsx`), which was already
  red. `ProductImageGallery.tsx` got the same confirm-dialog treatment: its `handleRemove` now
  returns a `Promise<boolean>` (was `Promise<void>`) so the confirm handler can close the dialog
  only on success, leaving it open on failure for a retry — matching
  `ProductAttributeListPage.tsx`'s established convention. Deliberately **not** applied to
  `ImageThumbnailGrid` itself, and so **not** applied to `ProductImageStager.tsx`'s own use of it
  (create-mode's not-yet-uploaded local image queue) — removing a queued file is a trivial,
  instantly-reversible local edit with no backend consequence, so a confirm dialog there would be
  unwarranted friction. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no
  Docker in this sandbox, so the actual confirm/cancel/retry flow is unverified in a real browser.

- **`ecommerce-service`/`gui`: a `Product` may now exist with zero variants, per explicit request
  ("A Product can exists without any Variant") — directly reverses US-1.6's original "always has
  at least one variant" rule.** `ProductServiceImpl.create` no longer rejects an empty/`null`
  variants list; `removeVariant` no longer rejects removing a product's last remaining one. The
  now-fully-unused `PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT` error code was deleted outright (a
  numbering gap now, not renumbered/reused). `CreateProductRequest.variants` lost its `@NotEmpty`;
  `ProductController.toVariantInputs` gained a null-guard (`variants` can now genuinely arrive as
  `null`). `Product`'s own Javadoc and both affected `ProductService` methods' Javadoc were
  rewritten to state the new rule. A variant-less product still simply never appears in
  browse/search — `ProductChangedOutboxEventHandler` already had a defensive empty-variants branch
  from when this rule was first built, so no read-side change was needed there. 3 tests replaced 2
  old rejection-asserting ones — 294 unit tests total (up from 293), verified via a real
  `mvn test` run (JDK 21).
  `gui`'s `ProductFormPage.tsx` no longer requires `draftVariants.length > 0` to create a product;
  `ProductVariantEditor.tsx`'s Remove button is never disabled to protect a "last variant" anymore,
  and its empty-state message became informational instead of a requirement. The public
  `ProductDetailPage.tsx` — unlike `ShopPage`'s browse/search grid, it resolves a product straight
  off `Product`/`active` with no variant-count filter, so a variant-less *active* product is still
  directly reachable by slug — needed a real fix: it used to render the literal text
  `"$Infinity – $-Infinity"` for the price (`Math.min`/`Math.max` over an empty array) and a
  silently-blank `VariantSelector` with a permanently-disabled, unexplained "Add to Cart." A new
  `hasVariants` gate now shows "This product isn't available for purchase yet — check back soon."
  instead, and `formatPriceRange` returns `null` (shown as "Price unavailable") rather than
  computing `Infinity`. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no
  Docker in this sandbox, so the actual zero-variant create/remove/storefront-detail flow is
  unverified in a real browser. See `ecommerce-service/CLAUDE.md`'s/`gui/CLAUDE.md`'s own follow-up
  notes for the full per-file detail.

- **`gui`: `ProductDescriptionEditor.tsx`'s outer wrapper (toolbar + content area) gained an
  explicit `bgcolor: 'background.paper'`, per request ("white in light mode").** Previously unset,
  so it just showed through whatever `Paper`/`Box` sat behind it. Used the theme token, not a
  literal `'#fff'`/`'white'` — this app has a real light/dark theme toggle
  (`app/theme.ts`/`shared/constants/colors.ts`), and `background.paper` resolves to `#ffffff` in
  light mode (satisfying the request exactly) while automatically becoming the correct dark
  surface color in dark mode, matching the same reasoning already used elsewhere in this app
  (`ProductDetailPage.tsx`'s own cards) for preferring theme tokens over hardcoded colors.
  Verified via a clean `tsc --noEmit` and a successful `vite build`.

### Fixed (cont.)

- **`gui`: `CartPage.tsx`'s quantity stepper sat visibly above the row's vertical center, reported
  directly ("may be due to the LowStock text hidden").** Confirmed: the always-rendered-but-
  invisible low-stock caption below the stepper (a prior fix for a *different*, width-jitter bug —
  see this same component's earlier note) gave the quantity column a taller flow height than every
  other single-line sibling in the row, so the outer row's `alignItems="center"` centered the whole
  two-line block instead of the stepper itself. Fixed by taking the caption out of flow entirely —
  conditionally rendered (no more `visibility: 'hidden'`/placeholder text) and `position: absolute`
  under a `position: relative` wrapper — so the column's flow height is now just the stepper's,
  centering it exactly like its siblings, with no live layout jump to guard against either (an
  absolutely positioned element was never part of the flow height to begin with). See
  `gui/CLAUDE.md`'s own note on this component for the full before/after reasoning.

- **`gui`: `ProductFormPage`'s Category `Select` used to render every category flat, with no
  indentation reflecting the hierarchy — caught by direct question, then fixed.** It fetched the
  flat-list endpoint and rendered a bare `MenuItem` per category, so a root category and a deeply
  nested one looked identical in the dropdown — the one place in the app not updated when
  `ProductCategory` gained hierarchy support, unlike `ProductCategoryFormDialog.tsx`'s own
  parent-picker and `ProductCategoryListPage.tsx`'s "Parent" column, both already correct. Now
  fetches `ecommerceApi.getProductCategoryTree` and renders it flattened + indented by depth, the
  same way that dialog's picker already did. The depth-flattening logic itself
  (previously a local `flattenTree` inside `ProductCategoryFormDialog.tsx`) moved out to a new
  shared `utils/categoryTree.ts` (`flattenCategoryTree`/`FlatCategoryOption`) once a second real
  call site needed the identical shape — `ProductCategoryFormDialog.tsx` still owns its own
  `excludeIds`-computing helpers (`getSubtreeIds`/`collectSubtreeIds`), specific to its own
  "can't be its own descendant's parent" concern, which `ProductFormPage`'s plain category picker
  doesn't have. Verified via a clean `tsc --noEmit` and a successful `vite build`.

### Changed (cont.)

- **`gui`: `ProductFormPage`'s main column reordered to Name → Variants → Images → Description,
  per request.** Originally proposed together with the two-column restructure below, but Images
  was deliberately deferred at the time — moving the create-mode "save first" placeholder up next
  to Name, before the description, would have read oddly. Asked again once
  `ProductImageStager` (previous entry) replaced that placeholder with a real, always-functional
  staging UI — with that gap closed, both could move up with no remaining downside. Variants/Images
  stay their own `Paper`s, just reordered within the main column's `Stack spacing={3}` instead of
  sitting full-width below both columns; `ProductDescriptionEditor` is now the last item in that
  stack instead of living inside the "Basic Info" card alongside Name. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

### Added (cont.)

- **`gui`: `ProductFormPage`'s create mode no longer requires "save the product first, then come
  back to add images," per request — closes a gap the user surfaced by asking why that flow
  existed.** Investigated first: `CreateProductRequest.images`/`ProductCommands.Create.images`
  already exist and are wired end-to-end on the backend, but `ProductServiceImpl.uploadImage`
  requires a real `productId` (to look the product up and to namespace the storage key under
  `products/{productId}/...`), and nothing else could turn a file into a `storageKey` without that
  lookup — so the GUI never had a way to populate `images[]` at create time, a genuine gap rather
  than a backend limitation. Considered a new backend staging-upload endpoint, then adopted the
  user's own simpler proposal instead, once it became clear it needs **zero backend changes**:
  `createProduct` already returns the new id, so the create form can just perform the image
  uploads itself, immediately after, using the exact endpoint edit mode already calls.
  - New `components/ProductImageStager.tsx` (+ `StagedImage` type) — a deliberately separate
    component from `ProductImageGallery.tsx`, not a shared/extended one: every handler on that
    component fires a real backend call the instant something happens (upload on pick, `DELETE` on
    remove, a 3-step scratch-sort-order dance on reorder to route around a real DB uniqueness
    constraint), none of which applies to files not yet attached to any product. Add/remove/reorder
    here are synchronous local-array edits with no network call — mirrors `ProductVariantEditor`'s
    existing draft/live split rather than retrofitting `ProductImageGallery`.
  - `ProductFormPage.handleSubmit`'s create branch uploads each staged file **sequentially** (not
    `Promise.all`, for deterministic `sortOrder` assignment and to avoid N simultaneous multipart
    uploads) right after `createProduct` resolves. Best-effort, not all-or-nothing — one bad file
    doesn't undo the already-created product or block the rest of the queue; the final success
    message reflects the real outcome, and the admin still lands on the real edit-mode gallery
    either way to retry anything that failed.
  - Preview thumbnails are local `URL.createObjectURL(file)` blob URLs, explicitly revoked in two
    places to avoid leaking them across this SPA's client-side navigation: once per file right
    after its own upload attempt, and via a ref-backed unmount-cleanup effect for the
    abandon-the-form case (Cancel, back button, navigating away without submitting).
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual create-with-images flow hasn't been exercised in a real browser.

### Added (cont.)

- **`ecommerce-service`: Product Tags — a many-to-many relationship (a product can have multiple
  tags, a tag can be attached to multiple products), Phase 1 (backend) of a request.** Mirrors
  `content-service`'s existing `Tag`/`ContentItemTag` pattern closely — researched and followed as
  the primary precedent rather than designed from scratch. Three scope decisions asked and
  confirmed before building: (1) `ProductTag` has no status/lifecycle field (just `name`/`slug`,
  matching `ProductCategory`'s own pre-hierarchy simplicity, unlike `content-service`'s `Tag`);
  (2) `ProductResponse.tagIds` exposes ids only, matching `QuestionAnswerResponse.tagIds`;
  (3) admin-only scope for this pass — no `ProductSearchView`/`ShopPage` wiring, deferred.
  - New `entity/ProductTag` + `entity/ProductTagAssignment` — the latter an **explicit join
    entity** (not `@ManyToMany`/`@JoinTable`), mirroring `content-service`'s `ContentItemTag`
    exactly, so the assignment row carries the same audit columns every entity in this reactor
    does. `Product.productTagAssignments` is cascade-owned (`ALL`, `orphanRemoval`), unlike
    `variants`/`images`. Migration `202609010001__0.0.2__DKP-0038__add_product_tag_tables.sql`.
  - New `EcommerceErrorCode.PRODUCT_TAG_*`, `ProductTagRepository`, `ProductTagAssignmentRepository`
    (`existsByProductTagId`, the delete in-use guard), `ProductTagSpecification`,
    `ProductTagService`/`Impl` (paginated list, unlike `ProductCategoryService`'s unpaginated one —
    free-form tags expected to proliferate more), `ProductTagMapper`, `dto/ProductTag*`, and
    `ProductTagApi`+`Controller` at `/api/v1/admin/product-tags`.
  - **Assignment doesn't live on `ProductTagService`** — it travels with
    `ProductCommands.Create`/`Update`'s new `tagIds: Set<Integer>`, mirroring
    `content-service`'s `QuestionAnswerServiceImpl.applyTagIds`/`QuestionAnswerCommands` split.
    New `ProductServiceImpl.applyTagIds` — same three-state update semantics (`null` = unchanged,
    empty = clear, non-empty = replace) as `QuestionAnswerCommands.Update`. `ProductMapper` gained
    a `resolveTagIds` `@AfterMapping` step. `ProductSpecification.withFilters` gained an optional
    `tagIds` overload (any-of match, `query.distinct(true)`); `ProductService`/`Api`/`Controller`
    `.list` all gained the matching parameter.
  - New `ProductTagServiceImplTest` + 4 new `ProductServiceImplTest` cases — **182 unit tests
    total** (up from 165), verified via a real `mvn test` run (JDK 21). Caught and fixed a real
    Maven staleness-check false negative along the way (`mvn compile` reported "up to date" after
    a record's constructor signature changed, hiding 12 real compile errors in a dependent test
    file until a forced `clean compile` surfaced them) and a `javac` overload-ambiguity gotcha
    (`verify(repo).delete(entity)` is ambiguous when the repository extends both `JpaRepository`
    and `JpaSpecificationExecutor`, since the latter gained its own `delete(Specification<T>)` in a
    recent Spring Data JPA release — fixed via an explicit `(JpaRepository<T, ID>)` cast).
  - **Not built in this pass, deliberately**: `ProductSearchView` tag data/`ShopPage` filter rail
    (public storefront), and the `gui` side (tag admin CRUD, `ProductFormPage`'s tag picker, admin
    product list tag filter) — Phase 2, see below.

- **`gui`: Product Tags, Phase 2 (admin GUI) of the request above — now built.** Mirrors
  `@content`'s `TagListPage.tsx`/`TagFormDialog.tsx` and `QuestionAnswerFormPage.tsx`'s
  Chip-toggle-cloud tag picker as the primary precedents, per the same 3 scope decisions Phase 1
  confirmed (no status field, ids-only `tagIds`, admin-only — no storefront wiring).
  - `types.ts` gained `ProductTag`/`CreateProductTagPayload`/`UpdateProductTagPayload`;
    `Product.tagIds: number[]`; `CreateProductPayload`/`UpdateProductPayload` both gained optional
    `tagIds?: number[]`.
  - `api/ecommerceApi.ts` gained `listProductTags`/`createProductTag`/`updateProductTag`/
    `deleteProductTag`. `ProductListParams.tagIds` needed a non-`buildQuery` code path in
    `listProducts` — `ProductApi.list`'s `Set<Integer> tagIds` binds from a **repeated** query
    param (`?tagIds=1&tagIds=2`), which the existing scalar-only `buildQuery` helper can't produce.
  - New `pages/ProductTagListPage.tsx` + `components/ProductTagFormDialog.tsx` (mirror
    `@content`'s Tag list/dialog, minus the Status filter/field entirely). Wired into
    `AdminLayout.tsx`'s nav (`SellIcon`, `/admin/product-tags`) and `App.tsx`'s admin routes.
  - `ProductFormPage.tsx`'s Organization sidebar gained a Tags `Paper` (Chip-toggle-cloud, same
    `allTags`/`selectedTagIds`/`toggleTag` shape as `QuestionAnswerFormPage.tsx`) — always sends the
    full current tag set on submit, never relying on the backend's "omit to leave unchanged"
    three-state semantics, since this form always has the complete picture.
  - `ProductListPage.tsx`'s filter bar gained a multi-select tag filter (`Select multiple` +
    `Checkbox`/`ListItemText`, any-of match against `ProductSpecification`'s own `IN`-based join).
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual admin tag CRUD, `ProductFormPage` picker, and list filter are unverified
    in a real browser. See `gui/CLAUDE.md`'s own Product Tags note for full detail.

- **`ecommerce-service`: seed data for `ProductTag`/`ProductTagAssignment`, a follow-up to the
  Product Tags feature above, per request.** New `service/seed/ProductTagSeeder` (mirrors
  `ProductCategorySeeder` — extends `infra`'s `CsvSeeder<ProductTag>`, `name` is the idempotency
  key, bypasses `ProductTagService` and persists directly since tag creation has no outbox
  event/read-model side effect) reads new `data/csv/product_tags.csv` (New Arrival, Best Seller,
  Limited Edition, Sale, Eco-Friendly, Staff Pick). No separate assignment seeder exists —
  `products.csv` gained an optional `tagNames` column (semicolon-joined, same inline convention
  `product_variants.csv`'s `attributes` cell uses); `ProductSeeder` resolves each name to a tag id
  via a new `ProductTagRepository.findByNameIgnoreCase` and feeds the result into the same
  `ProductCommands.Create.tagIds` path Phase 1 already wired up, replacing the previous hardcoded
  `Set.of()`. An unknown tag name fails loudly, matching `categoryName`'s own lookup; a blank cell
  is a normal untagged product. `EcommerceDataSeedingRunner` now runs
  `productTagSeeder.seed()` between categories and products. Verified via a targeted `mvn -pl
  ecommerce-service -am clean compile test` — all 182 existing tests still pass; not run against a
  real database in this session. See `ecommerce-service/CLAUDE.md`'s own Product Tags note for
  full detail.

- **`gui`: inline tag creation in `ProductFormPage.tsx`'s Tags picker, per request.** Asked directly
  whether "add/remove tag" meant toggling a tag on the product (already built — the Chip click) or
  managing the tag catalog itself; confirmed **create only** — renaming/deleting a `ProductTag`
  stays on `/admin/product-tags`, since inline delete would also need to handle the backend's
  `PRODUCT_TAG_IN_USE` guard.
  - **Follow-up, per request: creation is deferred until the product is actually saved, not fired
    the moment a name is typed.** The Tags `Paper` now has two sub-sections — "Existing tags" (the
    original Chip-toggle-cloud, unchanged) and a new "New tags" section: a queue input + each
    staged name as its own removable `Chip` (`onDelete`, `color="warning"`). New `stagedTagNames:
    string[]` holds plain not-yet-real names, kept fully separate from `allTags`/`selectedTagIds`
    so a discarded form (Cancel, navigate away) leaves zero trace in the tag catalog. Queuing a name
    is a pure local-state push, no network call — with two dedupe guards (a name matching an
    existing catalog tag selects that tag instead of queuing a doomed duplicate; a name matching an
    already-queued one is a no-op). New `resolveStagedTagIds()`, called once from `handleSubmit`
    right before the product create/update call, is the only place `createProductTag` is actually
    invoked — aborts the whole submit on the first failure (unlike `ProductImageStager`'s
    deliberately best-effort image-upload loop), since an incomplete tag set applied to the product
    would be more confusing than a failed save the admin can just retry.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only. See `gui/CLAUDE.md`'s
    own Product Tags note for full detail.

- **`ecommerce-service`: AddressBook, Phase 1 (backend) of a request — a shopper's own reusable,
  multi-address book with a designated default.** Reverses `Address`'s own original Epic 2 scope
  lock ("single inline address, no saved address book"). Three scope decisions confirmed before
  building: checkout wiring is in scope for this feature (not deferred to a later phase), each
  address gets an optional `label` (e.g. "Home"/"Work"), and checkout gets a "save this address for
  next time" quick-save checkbox.
  - New `entity/SavedAddress` — a full first-class entity (independent create/edit/delete/
    set-default lifecycle), deliberately separate from `Address` (still a plain `@Embeddable`
    frozen onto `Order` at purchase time). `ownerUuid` is a plain column, same "claims-only, no
    persisted row" shape `Order.ownerUuid` already uses. Migration `DKP-0039` — own `SAVED_ADDRESS_SEQ`,
    a plain btree index on `OWNER_UUID`, and a **partial unique index**
    (`WHERE IS_DEFAULT = TRUE`) enforcing "at most one default per owner" at the DB level,
    alongside `SavedAddressServiceImpl`'s own app-level unset-then-set dance.
  - New `EcommerceErrorCode.SAVED_ADDRESS_NOT_FOUND`/`CHECKOUT_ADDRESS_REQUIRED`,
    `SavedAddressRepository`, `SavedAddressCommands`, `SavedAddressService`/`Impl`,
    `SavedAddressMapper`, DTOs, and `SavedAddressApi`+`Controller` at `/api/v1/addresses` —
    **shopper-facing, never admin-gated** (no admin surface for this resource at all — an
    AddressBook entry has exactly one legitimate owner). The caller's first address is always
    auto-defaulted; deleting the current default auto-promotes the most-recently-created remaining
    one.
  - **Checkout integration**: an order's shipping address can now come from an existing AddressBook
    entry (`savedAddressId`) or a fresh one-off entry — `AddressRequest`'s fields are no longer
    `@NotBlank` (can't declaratively enforce "one or the other" anymore), validated imperatively
    instead in a new `CheckoutServiceImpl.resolveAddress`. New
    `CheckoutCommands.AddressSelection` replaces the bare `AddressInput` in
    `CheckoutService.confirm`'s signature. The "save for next time" quick-save is deliberately
    **best-effort** — wrapped in try/catch after the order is already durably saved, so a failure
    there can never roll back an order that already reserved real stock.
  - New `SavedAddressServiceImplTest` + 6 new `CheckoutServiceImplTest` cases — **200 unit tests
    total** (up from 182), verified via a real `mvn test` run.
  - **`gateway`'s `GatewayRoutesConfig` gained a matching `/api/v1/addresses/**` route in the same
    change** — applying the lesson learned on Product Tags's own missed route (see this file's
    Fixed entry below and `gateway/CLAUDE.md`).
  - **Not built in this pass**: the `gui` side (a "My Addresses" page, the `CheckoutPage` picker +
    quick-save checkbox) — Phase 2, see `ecommerce-service/CLAUDE.md`'s own AddressBook note for
    full detail.

- **`gui`: AddressBook, Phase 2 (GUI) of the request above — now built, including checkout wiring.**
  - `types.ts` gained `SavedAddress`/`CreateSavedAddressPayload`/`UpdateSavedAddressPayload` and a
    new `CheckoutAddressInput` (the two-shape `savedAddressId`-or-fresh-fields contract, mirroring
    `AddressRequest`). New `api/addressApi.ts`; `checkoutApi.confirm`'s `address` param changed
    type from `Address` to `CheckoutAddressInput`.
  - New `pages/AddressBookPage.tsx` + `components/AddressFormDialog.tsx` — never admin-gated.
    Each address is its own card (label/full name, "Default" chip, full address, star/edit/delete
    actions); the form dialog's "Set as default" checkbox only appears in create mode, mirroring
    the backend (`update()` never touches the default flag).
  - **`CheckoutPage.tsx`'s Shipping Address section gained a saved-address `RadioGroup` above the
    existing manual form** (only rendered when the caller has saved addresses), pre-selecting the
    default one, plus a trailing "Enter a new address" option. The manual form gained a "Save this
    address for future orders" checkbox + optional label field. `validate()` skips entirely when a
    saved address is selected.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only. See `gui/CLAUDE.md`'s
    own AddressBook note for full detail.

- **`gui`: new shared "Account" shell — `AddressBookPage` moved off its own top-level route into
  it, alongside `@auth`'s own profile page, per request.** Asked directly whether to merge
  Addresses into `Dashboard.tsx` outright; recommended and built a proper shell instead. New
  `app/account-shell/AccountLayout.tsx` — a deliberately simpler sidebar than `AdminLayout`'s own
  (no collapse toggle, since there are only two destinations today: Profile and Addresses), with
  `NavBar` staying visible above it (unlike `AdminLayout`, this area is still "part of the site").
  Lives directly under `app/`, not inside either feature, since its two destinations span two
  unrelated features (`@auth`, `@ecommerce`).
  - `@auth/pages/Dashboard.tsx` renamed to `ProfilePage.tsx` (`git mv`, component renamed to match)
    — content/logic untouched.
  - `/dashboard` kept as a redirect (`<Navigate to="/account/profile" replace />`), not removed —
    every existing call site (`AuthCallback.tsx`, `Login.tsx`, `SignUp.tsx`, `GuestRoute`'s default
    `redirect`, etc.) still navigates to this literal path, so none needed to change. New nested
    routes: `/account/profile`, `/account/addresses`, `/account` itself index-redirects to
    `/account/profile`.
  - `NavBar.tsx`'s "Dashboard" button became "Account" (navigates straight to `/account/profile`,
    highlights on `location.pathname.startsWith('/account')`); the separate "Addresses" button
    added earlier this session was removed — one nav entry now covers both via the sidebar.
  - Verified via a clean `tsc --noEmit` and a successful `vite build`; grepped the whole `gui/src`
    tree for every remaining `/dashboard` reference to confirm none was a missed spot.

- **`gui`: 4 follow-up fixes to `AccountLayout.tsx`, per request.**
  1. Fixed the sidebar overlapping `NavBar` — `variant="permanent"` still renders its `Paper`
     `position: fixed` by default (the same default `AdminLayout`'s own sidebar has, just invisible
     there since that shell hides `NavBar` entirely); overrode `.MuiDrawer-paper` to `position:
     static` instead, putting the sidebar back in normal flow below `NavBar`.
  2. Sidebar now shows the caller's avatar + username at the top, above the nav items — a new,
     `AccountLayout`-local `profileApi.getCurrentUser()` fetch, silent-fail (cosmetic only).
  3. Layout is now `width: '80%', mx: 'auto'` on the outer shell, split 20% sidebar / `flex: 1`
     content — replacing the original fixed-`220px` sidebar. `AddressBookPage.tsx`'s own outer
     `width: '80%'` had to come off in the same change (it was designed for sitting directly under
     `NavBar`; nested inside this already-80%-wide shell, a second `80%` compounded into a visibly
     narrow, off-center block) — now just `p: 3`, filling whatever column it's given.
  4. Sidebar `bgcolor: 'background.paper'`, not a literal white — same theme-token reasoning
     already established repeatedly this session, correct in both light and dark mode.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only. See `gui/CLAUDE.md`'s
    own AccountLayout follow-up note for full detail.

- **`gui`: fix #1 above wasn't actually fixed, plus a second symptom — reported directly, root-caused
  together.** The sidebar was still too close to `NavBar`, and its own rendered width visibly
  changed switching `/account/profile` ↔ `/account/addresses`. Both traced to the same cause: a
  nested-selector `sx` override (`'& .MuiDrawer-paper': { position: 'static' }`) fighting `Drawer`'s
  own baked-in `styled()` definition is unreliable — it can lose depending on emotion's
  style-injection order — so the override wasn't consistently winning, and a `fixed`-position
  element's `%` width resolves against the *viewport*, not the flex row, so any inconsistency in
  whether it applied showed up as width flicker between routes. **Real fix: dropped `Drawer`
  entirely for a plain `Paper`** — this shell never used any actual `Drawer` feature, so a plain
  `Paper` sidesteps the whole bug class by construction (no baked-in fixed positioning to fight).
  Also added `mt: 3`/`mb: 3` on the outer shell for real breathing room below `NavBar`. Verified via
  a clean `tsc --noEmit` and a successful `vite build` only.

- **`gui`: `AddressBookPage.tsx` restyled to match `CartPage`'s own lines-list box, per request.**
  Every address used to be its own separate bordered `Paper` card with the page's grey
  `background.default` visible in the gaps between them; now every address is a row inside **one**
  continuous `bgcolor: 'background.paper'` box, separated by `Stack divider={<Divider />}` instead
  of a gap — and the "My Addresses" headline + "Add Address" button now sit inside that same box
  too, rather than above/outside it. Verified via a clean `tsc --noEmit` and a successful
  `vite build` only.

- **`gui`: two more follow-up fixes, per request.**
  1. `AccountLayout.tsx`'s sidebar `Paper` was stretching to match the content column's height (the
     flex default, `alignItems: stretch`, since it was never set explicitly) — leaving a large empty
     area inside the sidebar's own border below its actual (short) content, reasonably described as
     "content not aligned with the sidebar." Fixed with one explicit `alignItems: 'flex-start'` on
     the outer row: both columns now size to their own content and start flush at the same top edge.
  2. Added a `Divider` in `AddressBookPage.tsx` between the "My Addresses" headline row and the
     address list/empty-state below it — previously just a plain margin with no visible separator.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only.

### Fixed (cont.)

- **`gateway`: `/api/v1/admin/product-tags/**` 404'd through Spring's static-resource handler
  (`NoResourceFoundException`, not an auth/5xx error) because `GatewayRoutesConfig`'s
  `ecommerceServiceRoutes()` never gained a route for it when the Product Tags feature shipped on
  `ecommerce-service`'s side.** Adding a new `@RequestMapping` on a standalone service is not
  enough by itself — a matching `route(path(...))` line must be added here in the same change, or
  the request never reaches that service at all; every other Product Tags path (`ProductTagApi`,
  `ecommerceApi.ts`, the admin GUI pages) was already correct, only this one routing registration
  was missed. Fixed by adding `.route(path("/api/v1/admin/product-tags/**"), http(baseUrl))` to
  that bean, alongside its sibling `/api/v1/admin/products/**`/`/api/v1/admin/product-categories/**`
  routes. `GatewayRoutesConfig`'s own class Javadoc now calls this failure mode out explicitly, as
  a caveat on its "confirmed via a full audit" claim.

- **`gui`: `ProductDescriptionEditor.tsx`'s content area no longer caps its own height, per
  request.** Removed `maxHeight: 400`/`overflowY: 'auto'` — a capped, internally-scrolling editor
  hid part of whatever an admin had written. Weighed against an alternative (reordering
  Variants/Images above the description) and rejected it explicitly: that wouldn't have touched
  the editor's own fixed-height scrollbox at all, so it wouldn't have fixed the complaint. The
  editor now grows to fit its content and lets the page scroll as a whole instead — one scrollbar,
  not a nested one — the same pattern real WYSIWYG editors use in a form (Shopify, Notion, Google
  Docs). `minHeight: 160` unchanged. Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

### Added (cont.)

- **`gui`: `ProductFormPage.tsx` restructured into a two-column layout, and
  `AdminLayout.tsx`'s sidebar made collapsible, per request — three named complaints: the
  description editor felt too narrow, the sidebar couldn't free up space, and there was unused
  space on the right doing nothing.** Presented as an explicit layout choice via a preview-based
  question (two-column Shopify-admin-style vs. just widening the single column) before building;
  two-column was chosen.
  - `ProductFormPage.tsx`: root container's `maxWidth: 900` removed entirely. New two-column row
    (`Box sx={{ display: 'flex', gap: 3 }}`) — **Basic Info** (`flex: '1 1 calc(68% - 12px)'`,
    `minWidth: 420`; Name + `ProductDescriptionEditor`) and a new, separate **Organization**
    column (`flex: '1 1 calc(32% - 12px)'`, `minWidth: 260`; just the Category `Select`, pulled
    out of the old combined "Basic Info" card). Same `calc()`-gap-compensation flex technique
    `ProductDetailPage.tsx`'s own two-column layout already established — the two subtractions
    sum to exactly the `gap: 3` (24px) between the columns, so they actually fill 100% width
    instead of always wrapping to two rows. Variants and the Image Gallery stay full-width below
    both columns, unchanged otherwise.
  - `AdminLayout.tsx`: sidebar `Drawer` (still `variant="permanent"`, unchanged) now toggles
    between `COLLAPSED_WIDTH` (64) and `EXPANDED_WIDTH` (220) via a new chevron `IconButton`;
    collapsed state persists to `localStorage` (`adminSidebarCollapsed`) across reloads. No
    matching change needed on the main-content side — a permanent (non-fixed-position) `Drawer`
    renders in normal document flow, so the main content's existing `flex: 1` already reflows to
    fill the freed space automatically. Nav labels/brand text/user-info text hide when collapsed,
    leaving icon-only rows each wrapped in a `Tooltip` so meaning isn't lost. Affects the shared
    shell for all 14 routes under `AdminLayout` (11 page components), not just `ProductFormPage`.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so neither the two-column reflow nor the sidebar collapse/expand/persisted-reload
    behavior has been exercised in a real browser yet.

- **`gui`: pasting or dragging-and-dropping an image file into the description editor now works
  too, per request.** Prompted by a direct question ("does it work if the user copy paste the
  image") — verified it didn't (TipTap has no built-in paste/drop-to-upload handling, and even a
  browser's base64 `data:`-URI paste fallback would have been stripped on save anyway — new
  `ProductDescriptionSanitizerTest.stripsDataUriImageSourcesEntirely` confirms `data:` isn't an
  allowed protocol under either `LINKS` or `IMAGES`, 165 unit tests total), then built it. New
  `useEditor`'s `editorProps.handlePaste`/`handleDrop` (plain ProseMirror hooks) detect an
  `image/*` file on the clipboard/drop event and upload it through the same
  `ecommerceApi.uploadDescriptionImage` path the toolbar button already uses, via a new shared
  `uploadAndInsertImageAt(view, pos, file)` helper — inserted via a raw ProseMirror
  `view.dispatch(tr.insert(...))`, since `handlePaste`/`handleDrop` only receive the low-level
  `view`, not the higher-level `editor` instance. Anything that isn't an image file (normal
  text/HTML paste, an internal drag-reorder within the editor) returns `false` and falls through
  to ProseMirror's unchanged default handling. Does not intercept an externally-hosted
  `<img src="https://...">` arriving as part of pasted HTML (left as-is, subject to the same
  protocol check as any other pasted link/image) — re-hosting an arbitrary external image would
  need a real fetch-and-reupload proxy, not built here. Verified via a clean `tsc --noEmit` and a
  successful `vite build` only — no Docker in this sandbox, so the actual paste/drop interaction
  hasn't been exercised in a real browser.

- **`infra`/`ecommerce-service`/`gui`: real image *upload* for the description editor's "Image"
  button (was URL-paste-only), per request — with a permanent-URL storage mechanism built
  specifically because a presigned one would have silently broken.** Worked through with the user
  first: a presigned URL (the mechanism every other image path in this reactor uses —
  `ProductImage`'s own gallery, `identity-service` avatars, `social-service` profile pictures/
  attachments) is time-limited and re-derived fresh on every read by whichever mapper owns it; an
  image embedded once into `Product.description`'s stored HTML is never re-read/re-derived that
  way, so a presigned URL baked into it would expire (default 60 min) and leave a permanently
  broken image. Fix is a genuinely public storage path, not a bigger re-resolution mechanism:
  - `infra`: new `StorageService.uploadPublicImage(keyPrefix, file)` — uploads under a fixed
    `description-images/` prefix (forced regardless of the caller's `keyPrefix`) and returns a
    permanent, unsigned URL. `StorageServiceImpl.ensurePublicReadPolicy` (called from the existing
    `@PostConstruct ensureBucketExists`, every startup — not just first bucket creation) grants
    anonymous `s3:GetObject` on that one prefix via MinIO's `setBucketPolicy`; every other object
    in the bucket is unaffected and stays presigned-only.
  - **Deliberately not applied to the product gallery itself** — discussed explicitly: a
    `ProductImage` can belong to a not-yet-published/deactivated product, so the presigned
    mechanism is real (if modest) access control worth keeping; making the gallery public too
    would make a deactivated product's photos reachable by link forever. A real, deliberate
    asymmetry, not an oversight.
  - `ecommerce-service`: new `service/ProductDescriptionImageService`/`Impl` — not a method on
    `ProductService`, since it touches zero `Product` state (no `productId`, no `ProductImage`
    row). New `POST /api/v1/admin/products/description-images/upload`
    (`ProductDescriptionImageApi`/`Controller`, `ProductDescriptionImageResponse`) — a separate
    resource from `ProductApi`'s own gallery upload, nested under the same URL prefix purely so
    `gateway`'s existing route already covers it. Needs no existing product — works in create mode
    too. New `ProductDescriptionImageServiceImplTest` — 164 unit tests total (up from 163).
  - `gui`: `ProductDescriptionEditor.tsx`'s "Image" toolbar button now triggers a hidden
    `<input type="file">` and uploads via new `ecommerceApi.uploadDescriptionImage`, replacing the
    old `window.prompt('Image URL')` flow entirely (not offered alongside it). New `uploadingImage`
    loading state + `showError` wiring (first error path this component had). Verified via a clean
    `tsc --noEmit` and a successful `vite build` — no Docker in this sandbox, so the actual upload
    flow (and the MinIO bucket-policy grant) hasn't been exercised end-to-end in a real browser.

- **`gui`: Phase 3 (final) of giving `Product.description` a real rich-content "article," per
  request — `ProductDetailPage.tsx`'s "Product Details" section, actually rendering it.** Renders
  `product.description` via `dangerouslySetInnerHTML`, but only after a client-side
  `DOMPurify.sanitize()` pass — defense in depth on top of `ProductDescriptionSanitizer`'s own
  on-write sanitization (Phase 1), never a substitute for it. New `dompurify` dependency
  (`^3.4.14`) — no `@types/dompurify` needed, confirmed `dompurify` ships its own types as of this
  version before adding a redundant stub package. The section is omitted entirely (not an empty
  card) when there's nothing to show — the "TipTap's empty document is `<p></p>`, not `""`" check
  `ProductFormPage.tsx`'s submit guard needed in Phase 2 was extracted from a local helper into a
  shared `utils/htmlContent.ts` (`hasVisibleHtmlContent`), since this read side needed the
  identical check. Rendered-content typography (`& p`/headings/`& ul`/`& blockquote`/`& table`/
  etc.) mirrors `ProductDescriptionEditor.tsx`'s own editing-surface styles, so a description looks
  the same while being written as it does once published. Verified via a clean `tsc --noEmit` and
  a successful `vite build` only — no Docker in this sandbox, so this hasn't been exercised in a
  real browser. **Closes out all 3 phases of this feature** — see the two entries below for
  Phases 1–2.

- **`gui`: Phase 2 of giving `Product.description` a real rich-content "article," per request —
  a TipTap WYSIWYG editor replacing `ProductFormPage.tsx`'s plain multiline `TextField`.** New
  `components/ProductDescriptionEditor.tsx`, new dependencies `@tiptap/react`,
  `@tiptap/starter-kit`, `@tiptap/extension-link`, `@tiptap/extension-image`,
  `@tiptap/extension-underline`, `@tiptap/pm`, `@tiptap/core` (all `^3.30.5`). Toolbar node/mark
  set is deliberately kept in sync with `ecommerce-service`'s `ProductDescriptionSanitizer`
  allowlist from Phase 1 — `StarterKit.configure({ horizontalRule: false, codeBlock: false })`
  disables the two nodes `ProductDescriptionSanitizerTest` confirmed the backend drops/degrades
  (`<hr>` entirely, `<pre><code>` down to a bare inline `<code>`); two new sanitizer test cases
  (`dropsHorizontalRuleAndDegradesCodeBlockToInlineCode`, `preservesHeadingsUnderlineAndBlockquote`)
  document that reference — 163 unit tests total in `ecommerce-service` now (up from 150).
  `ProductDescriptionEditor` is deliberately uncontrolled (`value` seeds `useEditor` once,
  `onChange` reports every edit afterward) — a plain controlled `value` prop would fight TipTap's
  own internal document on every keystroke and lose cursor position. `ProductFormPage.handleSubmit`
  gained a `hasVisibleDescriptionContent` helper — TipTap's "empty" document serializes as
  `"<p></p>"`, never `""`, so the previous `description.trim() || undefined` omit-if-empty check
  would have sent that markup to the backend instead of omitting the field. Bundle size grew
  ~1.7 MB → ~2.1 MB gzip (539 KB → 672 KB, ProseMirror is not small) — not addressed in this pass;
  code-splitting behind a dynamic `import()` is a flagged follow-up. Verified via a clean
  `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so this hasn't
  been exercised in a real browser yet.

- **`ecommerce-service`: Phase 1 of giving `Product.description` a real rich-content "article,"
  per request — server-side HTML sanitization.** Discussed Markdown (mirroring `content-service`'s
  `Article`/`QuestionAnswer`) vs. sanitized HTML + WYSIWYG editor vs. structured JSON blocks;
  landed on sanitized HTML (Option A) since the audience is an end shopper, not a developer, and
  needs layout Markdown structurally can't produce (inline images beside text, spec callouts) —
  the same shape real storefronts (Shopify et al.) use. New `service/ProductDescriptionSanitizer`
  (plain `@Component`, no interface — single implementation, nothing to swap) wraps the OWASP Java
  HTML Sanitizer, composing its `BLOCKS`/`FORMATTING`/`LINKS`/`IMAGES`/`TABLES` presets rather than
  a hand-written policy. `ProductServiceImpl.create`/`.update` sanitize before every persist —
  `Product.description` always holds already-safe HTML, so nothing downstream has to re-sanitize.
  Silent stripping, not a rejection — a WYSIWYG paste routinely carries markup outside the
  allowlist, and erroring on every stray tag would make the editor unusable. New Maven dependency
  `com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer` — version-managed in the
  root `pom.xml`'s `dependencyManagement` (this reactor's convention) but declared only in
  `ecommerce-service`'s own `pom.xml`, not `infra` (no second consumer yet — `infra`'s promotion
  bar is "needed by two feature modules"). `CreateProductRequest`/`UpdateProductRequest.description`
  gained `@Size(max = 50_000)`; no schema change (`Product.description` was already `TEXT`,
  unlimited). New `ProductDescriptionSanitizerTest` (8 cases) + 2 new `ProductServiceImplTest`
  cases (`@InjectMocks` needed a `@Spy`, not a `@Mock`, for this collaborator, so assertions see
  genuinely sanitized output) — 160 unit tests total (up from 150), verified via a real `mvn test`
  run (JDK 21) in this session. **Phase 1 only** — the `gui` WYSIWYG editor
  (`ProductDescriptionEditor.tsx`, TipTap) and `ProductDetailPage.tsx`'s still-stubbed "article"
  section (rendering `product.description` + a client-side DOMPurify pass) are Phases 2–3, not yet
  built.

- **`ecommerce-service`: `product_categories.csv` seed data adapted for `ProductCategory`'s new
  hierarchy support, per request.** New optional `parentName` column (blank for a root category);
  `ProductCategorySeeder.buildEntity` resolves it via
  `productCategoryRepository.findByNameIgnoreCase`, throwing if a row's `parentName` doesn't
  resolve to an already-seeded category — same "parent rows must appear before their children"
  ordering requirement `content-service`'s `CategorySeeder` already has (`CsvSeeder` persists each
  row in file order before moving to the next), just keyed by name instead of a `seedId` column
  (kept consistent with this seeder's existing name-based idempotency — no schema migration for
  this). Seed data itself: the 5 original leaf categories (`Apparel`/`Drinkware`/`Stickers`/
  `Office`/`Accessories`) now nest under 2 new root categories, `Wearables` (→ `Apparel`) and
  `Desk & Drinkware` (→ the other 4) — `products.csv` untouched, since products still reference the
  same 5 leaf category names. Verified via a targeted `mvn -pl ecommerce-service -am compile`.

- **`gui`: `ProductDetailPage.tsx`'s `Breadcrumbs` trail deepened to a real root→leaf ancestor
  chain, per request, now that `ProductCategory` has hierarchy support (previous entry).** New
  `utils/categoryPath.ts` (`buildCategoryPath`) walks a flat `ProductCategory[]`'s `parentId`
  chain client-side — no new endpoint; the existing `shopApi.listCategories()` (already used by
  `ShopPage`'s filter rail) already returns `parentId` now, so a `Map`+upward-walk is enough,
  with a `seen` guard making an unexpected cycle a no-op rather than an infinite loop (defense in
  depth only — the backend already rejects a cyclic `parentId` on write). Categories are fetched
  in a second, independent `useEffect` in parallel with the product fetch, with the failure case
  silently swallowed (no `showError`) — a cosmetic breadcrumb enhancement failing shouldn't surface
  a toast; it just falls back to the original single `product.categoryName` segment. Ancestor
  segments stay plain text, not links — unchanged reasoning from the original 3-segment version:
  `ShopPage`'s category filter is still id-driven with no URL-param support to pre-select it, so
  there's still no route an ancestor segment could correctly link to. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`ecommerce-service`/`gui`: `ProductCategory` gained parent/child hierarchy support, per
  request.** Self-referential adjacency list (`parent`/`children`, both `@ManyToOne`/`@OneToMany`
  on `ProductCategory` itself) — mirrors `content-service`'s own `Category` shape exactly, chosen
  over a materialized-path or closure-table representation for the same reason `content-service`
  picked adjacency-list originally: this taxonomy is shallow and read-light enough that the cheap
  parent-walk cycle check (`ProductCategoryServiceImpl.validateParentAssignment`) beats paying a
  write-time or schema-complexity cost for read patterns this taxonomy doesn't have. New migration
  `202608310001__0.0.2__DKP-0037__add_product_category_parent_id.sql` (nullable
  `PARENT_CATEGORY_ID` FK + index). `ProductCategoryService.create`/`.update` are now
  `parentId`-aware (cycle-guarded — rejects self-parent and any assignment reachable by walking
  the target's own ancestors); the interface gained `listTree()` (new `ProductCategoryTreeNode`
  record) returning every category grouped into root categories with nested `children`, each level
  sorted by name. New `EcommerceErrorCode.PRODUCT_CATEGORY_CYCLIC_PARENT`. `ProductCategoryResponse`/
  `CreateProductCategoryRequest`/`UpdateProductCategoryRequest` all carry/accept `parentId` now; new
  `ProductCategoryTreeNodeResponse` DTO backs a new `GET /api/v1/admin/product-categories/tree`
  endpoint (mirrors `content-service`'s `CategoryApi#tree`) — no `gateway` routing change needed,
  the existing `/api/v1/admin/product-categories/**` route already covers the new sub-path. 7 new
  `ProductCategoryServiceImplTest` cases (parent assignment, both cycle-rejection branches, tree
  building/sorting, and an orphaned-child-becomes-root defensive case), 150 unit tests total,
  verified via a targeted `mvn test` run against the real reactor build (JDK 21) in this session.
  `gui`'s `pages/ProductCategoryListPage.tsx`/`components/ProductCategoryFormDialog.tsx` were
  rebuilt to mirror `@content`'s hierarchical `CategoryListPage.tsx`/`CategoryFormDialog.tsx`
  instead of the flat `TagListPage.tsx`/`TagFormDialog.tsx` they used to follow — a "Parent" column
  (via a fetched-tree id→name map) and an indented parent-picker `Select` that excludes the
  category's own subtree. `types.ts`'s `ProductCategory` gained `parentId: number | null`; new
  `ProductCategoryTreeNode` type. Still no delete endpoint (unrelated, pre-existing gap) and the
  storefront (`ShopPage`'s category filter, `ProductDetailPage`'s 3-segment breadcrumb) was
  deliberately not rewired to the new hierarchy in this pass — both are natural follow-ups now that
  the data model supports them. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx` gained a `Breadcrumbs` trail (`Shop → {categoryName} →
  {product.name}`), replacing the old "Back to Shop" button, per request.** The 3-segment "flat"
  version, chosen over a full multi-level category-hierarchy breadcrumb after discussion — this
  app's `ProductCategory` is a flat taxonomy, a product only ever has one category, not a tree, so
  a deeper path isn't something the current data model can produce without a backend change.
  `Shop` links to `/shop`; the category segment is deliberately plain text, not a link —
  `Product`/`ProductSearchResult` only carry `categoryName`, never a `categoryId`, and
  `ShopPage`'s own category filter is id-driven with no URL-param support to pre-select it, so
  there's no route this segment could correctly link to. `@mui/material` core `Breadcrumbs`/`Link`
  — no new dependency. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderLineRow.tsx`'s clickable area now matches `CartPage`'s cart-line `Link` rule,
  found by the same cleanup audit as the entries below.** It used to wrap its entire info block
  (name + variant + quantity) in one `Link` — an inconsistency flagged as accidental drift from
  `CartPage`'s own rule (only thumbnail+name navigate, settled on after a bug report about its
  price/variant area being unintentionally clickable). Reconciled: only the thumbnail and the
  product name now link to `/shop/${productSlug}`; the variant/quantity lines below the name and
  the price stay non-clickable. Implemented as two separate `Link`s (thumbnail, then name) rather
  than one wrapping both, since this layout stacks the name above the variant/quantity lines
  instead of beside the thumbnail — a single `Link` can't cover both without also covering the
  lines that should stay non-clickable; both point at the same URL. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: unified the "image or fallback icon" thumbnail pattern into one shared
  `components/Thumbnail.tsx`, found by the same cleanup audit as `utils/format.ts` below.** It had
  been implemented four separate times (`CartPage.tsx`'s own `CartLineThumbnail`,
  `OrderLineRow.tsx`, `ProductCard.tsx`, each with slightly different sizing/fallback-icon-size/
  fade behavior). New component: `width`/`height` (default `64`), `borderRadius` (default `1`),
  `fallbackIconSize` (default `28`), and an opt-in `fade` prop (`CartPage`'s own on-load fade
  behavior, off by default) cover all three call sites' variations —
  `ProductCard.tsx`'s storefront-grid image now passes `width="100%" height={180} borderRadius={0}
  fallbackIconSize={40}` instead of keeping its own inline version. `ProductDetailPage.tsx`'s
  small gallery thumbnail strip is deliberately not folded in — a different concern (always-real
  image, click-to-select-active with a border highlight, no fallback-icon state), not a duplicate
  of this component. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: deduped `formatPrice`/variant-label-join, found by an explicit cleanup audit of
  `features/ecommerce` after a long run of small iterative tweaks.** New `utils/format.ts`
  (`formatPrice`, `formatVariantLabel`) replaces six copy-pasted verbatim `formatPrice(value:
  number): string` definitions (`CartPage.tsx`, `OrderLineRow.tsx`, `AdminOrderListPage.tsx`,
  `CheckoutPage.tsx`, `OrderDetailPage.tsx`, `OrderHistoryPage.tsx`) and two duplicated
  attribute-values-join one-liners (`CartPage.tsx`'s `variationLabel`, `OrderLineRow.tsx`'s
  `variantLabel`). `ProductCard.tsx`'s `formatPrice(min, max)` and `ProductDetailPage.tsx`'s
  `formatPriceRange` are deliberately untouched — both format a price *range*, a different shape,
  not just a naming coincidence. Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

- **`gui`: `CartPage.tsx`'s quantity stepper boxes now stay aligned across rows regardless of
  low-stock status — fixed width, the actual fix after two narrower attempts.** The quantity
  column's own `Stack` is shrink-to-fit by default (sized to its widest child); since the caption
  only rendered conditionally, a low-stock row's column was wider than a normal row's, and
  `alignItems="center"` centered the narrower stepper box within whatever width the column
  happened to be that row — shifting it left/right, row to row. First attempt: made the caption
  always render (`visibility: 'hidden'` with placeholder text when not low stock) to stabilize the
  column's height — insufficient, the actual complaint was horizontal. Second attempt:
  `alignItems="flex-start"` — reverted, since the column's *width* still varied by caption message
  length even with something always rendered, which could also nudge the outer row's own flex
  distribution among siblings. Landed on pinning the column to `sx={{ width: 160, flexShrink: 0 }}`
  instead, removing the width variability at its source; `alignItems="center"` and the
  always-rendered caption were kept, now safe since the box never resizes. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx`'s `Stepper` connector line color/positioning fixed via a proper
  custom `StepConnector`, per bug report.** Two problems: (1) the line's color came from MUI's own
  active/completed palette defaults, not the exact `text.disabled`/`primary.main` tokens
  `HappyPathStepIcon` colors itself with, so the two visibly drifted; (2) the line's default
  horizontal offset (`calc(±50% + 20px)`) is calibrated for MUI's own smaller default icon, so
  once the icon grew to 48px the line visibly reached in under its edge instead of stopping at it
  ("overlapping the step border"). New `OrderStatusConnector` (`styled(StepConnector)`, replacing
  the earlier plain `sx` overrides on `Stepper`) fixes both: targets
  `stepConnectorClasses.line`/`.active`/`.completed` directly with the identical
  `theme.palette.text.disabled`/`primary.main` tokens the icon uses, and widens the offset to
  `calc(±50% + 28px)` (past the 24px icon radius plus its 3px border, with a small gap to spare).
  Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx`'s step icon border and connector line are both thicker, per
  request** — the icon's `border` went from `2px` to `3px`, and the connector line between steps
  (`sx` on `.MuiStepConnector-line`, `borderTopWidth`) from MUI's default `1px` to `3px`, so the
  outline and the connecting line read as one consistent stroke weight. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx`'s step icons are now outlined, not filled, per request** —
  `HappyPathStepIcon` switched from a solid-color disc with a white icon to a `2px solid` border
  plus `bgcolor: 'background.paper'` circle, both colored by state (`error.main`/`primary.main`/
  `text.disabled`), with the icon itself carrying that same color instead of white. Verified via a
  clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx`'s `Stepper` connector line re-centered on the (now 48px) step
  icons, per bug report** — a fix. MUI's `StepConnector` defaults to a `top` offset calibrated for
  its own ~24px icon, so the line sat above center once the icon grew; overridden via
  `sx` on `.MuiStepConnector-alternativeLabel` (`top: 24`). Verified via a clean `tsc --noEmit`
  and a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx`'s order-status `Stepper` moved to its own section at the top of
  the page, made bigger, and given a per-step icon, per follow-up request.** Moved out of the
  "Order Timeline" `Paper` into its own "Order Status" `Paper` positioned right after the header
  row (was folded into the timeline card further down the page). New `ORDER_HAPPY_PATH_ICONS`
  (`utils/orderStatus.ts`, index-aligned with `ORDER_HAPPY_PATH`) —
  `PendingActionsIcon`/`PaymentIcon`/`CheckCircleIcon`/`LocalShippingIcon`/`Inventory2Icon` — fed
  into a new local `HappyPathStepIcon`, a custom `StepIconComponent` (the standard MUI pattern for
  a step icon that still reacts to `active`/`completed`/`error`) rendering a 48px filled circle
  (MUI's default is an unfilled ~24px numbered circle) with that step's own icon, swapped for a red
  `ErrorOutlineIcon` on the terminal/error step regardless of position. `StepLabel` text size and
  the section's own padding were both bumped up to match. Verified via a clean `tsc --noEmit` and
  a successful `vite build`.

- **`gui`: `OrderDetailPage.tsx` gained a horizontal `Stepper` for at-a-glance order status, per
  request** — `@mui/material` core (`Stepper`/`Step`/`StepLabel`), not `@mui/lab`, so no new
  dependency. Sits above the existing detailed status-history list (kept as-is, not replaced — the
  stepper is a summary, the list is still the source of exact timestamps/reasons). New
  `ORDER_HAPPY_PATH` (`utils/orderStatus.ts`) names the lifecycle's one linear path (`PENDING →
  PAYMENT_PROCESSING → CONFIRMED → SHIPPED → DELIVERED`); `CANCELLED`/`EXPIRED`/`FAILED` are
  terminal branches off that path, not steps on it, so they get no step slot of their own — for a
  terminal order, `activeStep` instead resolves (by scanning `statusHistory` backward for the
  transition into the current terminal status, then taking its `fromStatus`'s index) to whichever
  happy-path step the order was at when it terminated, and that step's `StepLabel` gets MUI's
  built-in `error` state (red icon/text) plus the actual terminal status as its caption, rather
  than inventing a "Cancelled" step. `DELIVERED` is marked `completed` by hand (MUI's default only
  auto-completes steps *before* `activeStep`, not the active one itself). Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`ecommerce-service` + `gui`: `OrderDetailPage.tsx` gets the same per-line treatment as
  `OrderHistoryPage.tsx`, plus a click-through to the product page, per request.** Backend:
  `OrderLineResponse` gained `productSlug`, resolved in the same live variant lookup
  `OrderMapper.toOrderLineResponse` already does for `attributes`/`primaryImageUrl` — `null` under
  the same since-deleted-variant condition. GUI: extracted `OrderLineRow` out of
  `OrderHistoryPage.tsx` into a new shared `components/orders/OrderLineRow.tsx` (both pages now
  render identical lines) — the thumbnail+info block links to `/shop/${productSlug}`, but not the
  whole row and not when `productSlug` is `null`, same "don't make the price clickable too" shape
  `CartPage`'s own cart-line `Link` fix already established (applied here from the start).
  `OrderDetailPage.tsx`'s Items section now renders via the shared component (was a plain
  "name × qty — price" row, `Divider`-separated between lines instead of none), and its
  Subtotal/Shipping/Total *values* (not their labels) are now `color="error.main"`, matching
  `CartPage`/`OrderHistoryPage`'s existing red-price convention. Backend: 143 unit tests total,
  all passing, no Docker needed, verified via a real `mvn test` run. GUI: verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderHistoryPage.tsx`'s prices are now `color="error.main"`, per request** — each
  `OrderLineRow`'s `lineTotal` and the card's own `Order.total`, matching `CartPage`'s existing
  red-price convention. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`ecommerce-service` + `gui`: `OrderHistoryPage.tsx` redesigned — order id hidden, every
  `OrderLine` rendered with product image/variant/quantity, `Order.total` shown, per request.**
  Backend: `OrderLineResponse` gained `attributes`/`primaryImageUrl`, resolved live by
  `OrderMapper.toOrderLineResponse` (`OrderLine` itself snapshots neither — only
  `sku`/`productName`/`unitPrice`/`quantity`); this required making `toOrderLineResponse`
  non-`static` (it now injects `ProductVariantRepository`/`StorageService`), so
  `CheckoutMapper` switched from calling it as a static method reference to constructor-injecting
  `OrderMapper` and calling the instance method instead. Both new fields are `null` when the
  variant's since been deleted (`productVariantId` isn't a real FK). No new endpoint, no
  migration. GUI: the numeric `Order #{id}` text is gone from the card (View Details still
  navigates by id, just doesn't display it); new `OrderLineRow` renders each line as
  `<64×64 thumbnail> <product name / "Variant: ..." / "Quantity: xN"> <lineTotal>`
  (`Divider`-separated between lines, same idiom `CartPage` uses), and `Order.total` now renders
  once, right-aligned, below the lines — previously this page only showed an aggregate
  "{itemCount} items · {total}" summary line with no per-line breakdown. `types.ts`'s `OrderLine`
  gained matching nullable `attributes`/`primaryImageUrl` fields. Backend: 143 unit tests total,
  all passing, no Docker needed, verified via a real `mvn test` run. GUI: verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `OrderHistoryPage.tsx`'s `Tabs` bar is now `bgcolor: 'background.paper'`, per
  follow-up request** — a vertical rule between each `Tab` label was tried first (`borderRight`
  on every `Tab` but the last), reverted immediately after ("my mistake"), replaced with a
  `background.paper` fill (`borderRadius: 1`) instead — the semantic paper token, not a hardcoded
  white, matching `ProductDetailPage`/`CartPage`'s own card treatment. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`ecommerce-service` + `gui`: shopper-facing order-history status tabs (`OrderHistoryPage`),
  per request** — grouped Shopee-style ("All | To Pay | Processing | Shipped | Delivered |
  Cancelled"), chosen over one tab per raw `OrderStatus` after asking. Backend: new
  `OrderSpecification.withOwnerAndStatuses(ownerUuid, Collection<OrderStatus>)` (always filters
  `ownerUuid`, plus an optional `status IN (...)`, since a grouped tab like "To Pay" maps to more
  than one raw status — the existing admin-only `withFilters(OrderStatus)` only does single-value
  equality); `OrderService.listOrders`/`OrderServiceImpl.listOrders` gained a
  `Collection<OrderStatus> statuses` parameter and now delegates through that Specification.
  **`OrderRepository.findByOwnerUuidOrderByIdDesc` deleted outright** once this left it with no
  callers — "most recent first" is now an explicit `Sort` on the `Pageable`
  (`OrderController.list` builds `PageRequest.of(page, size, Sort.by(DESC, "id"))`), same shape
  `AdminOrderController.list` already used for its own sort. `OrderApi.list` gained
  `@RequestParam(required = false) List<OrderStatus> statuses` (repeated query param). No
  `gateway` change needed. GUI: new `utils/orderStatus.ts` `ORDER_TAB_GROUPS`; `OrderHistoryPage.tsx`
  gained an MUI `Tabs` bar (resets to page 0 on tab change) and two distinct empty states — the
  existing full-page "No orders yet" takeover only for the `'all'` tab, an inline "No orders in
  this category" message (tabs bar still visible) for a filtered tab with zero matches.
  `orderApi.list` gained an optional `statuses?: OrderStatus[]` parameter. Backend: 143 unit tests
  total, all passing, no Docker needed, verified via a real `mvn test` run. GUI: verified via a
  clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s quantity stepper now also subtracts what's already in the
  shopper's own cart, per bug report** — a fix. It capped at the selected variant's own
  `stockQuantity - reservedQuantity` only; since add-to-cart never reserves stock (only checkout's
  `confirm` does — this app's own locked Epic 2 design), a shopper who'd already added, say, all
  14 in-stock units of a variant to their cart could still open this page and add more of the same
  variant. New `quantityAlreadyInCart` (`useCart().cart?.lines.find(...)?.quantity ?? 0`);
  `availableForSelectedVariant` is now `Math.max(0, stockQuantity - reservedQuantity -
  quantityAlreadyInCart)`. Purely a client-side UX improvement — the real oversell guard remains
  `CheckoutServiceImpl.confirm`'s atomic stock reservation; this cap only stops the shopper from
  *seeing* room to add more than what's really left. Doesn't account for other shoppers' carts
  (no such data exists client-side), same approximation `stockQuantity - reservedQuantity` always
  was. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s variation box lost its border/hover background, and its value text is
  now the same size as the product name, per request** — the box's `border: '1px solid',
  borderColor: 'divider'` and `'&:hover': { bgcolor: 'action.hover' }` are both removed (leaving
  just `borderRadius`/`cursor: 'pointer'`); `{variationLabel}`'s own `Typography` is now
  `variant="body1"` (was `"caption"`), matching the product name beside it. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s cart-line `Link` now covers only the thumbnail + product name, per
  request** — a fix. It used to also wrap the variation chip and unit price, making that whole
  middle stretch of the row (including the gaps between elements) clickable-to-navigate, which
  read as "the whole row is clickable." Those two now sit in a plain sibling `Box` (`display:
  'flex', gap: 2`) instead of nested inside the `Link` — same visual layout, but clicking the
  variation box or price no longer navigates to the product page. `handleVariationBoxClick`'s
  `preventDefault()`/`stopPropagation()` calls (needed only while the box sat inside the `Link`)
  were removed as dead code once it moved outside. Verified via a clean `tsc --noEmit` and a
  successful `vite build`.

- **`gui`: `CartPage.tsx`'s "Select all"/"N selected" text now aligns with each cart line's
  product image, per request** — a fix. The header's checkbox-to-text `Stack` was `spacing={1}`
  (8px), one size down from the 16px gap each `CartLineRow`'s own outer `Stack` (`spacing={2}`)
  puts between its `Checkbox` and the thumbnail `Link`, so the header text sat 8px left of where
  each line's image starts. Changed to `spacing={2}` to match. Verified via a clean `tsc --noEmit`
  and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s select-all/Delete-Selected header row now sits in its own card,
  separate from the lines-list card, per request** — a fix; both used to share one
  `bgcolor: 'background.paper'` box with no visual break between the header row and the first
  line. Split into two cards (`p: 2`/`p: 3` respectively, `mb: 2`/`3`) so the page's own grey
  background shows through the gap between them. Verified via a clean `tsc --noEmit` and a
  successful `vite build`.

- **`gui`: multi-select cart UI — Phase 3 (final) of the multi-select cart feature, per request.**
  `CartPage.tsx` gained a leading MUI `Checkbox` on every `CartLineRow` (available and unavailable
  lines alike), a header "select all" checkbox (checked/`indeterminate` as appropriate), and a
  "Delete Selected (N)" button that calls new `CartContext.removeItems` (a pass-through to new
  `cartApi.removeItems`, `POST /api/v1/cart/items/remove-batch`) and clears the selection on
  success. The "Proceed to Checkout" button becomes "Checkout Selected (N)" once at least one
  *available* selected line exists, and `navigate`s to `/checkout` with
  `state: { selectedVariantIds }`; with nothing selected it reads "Proceed to Checkout" and
  navigates with no state, checking out the whole cart exactly as before this feature existed.
  `CheckoutPage.tsx` reads that state (`useLocation().state?.selectedVariantIds`) and threads it
  through to both `checkoutApi.preview` (new optional param, sent as a repeated query string —
  `preview` is a `GET`) and `checkoutApi.confirm` (new optional param, sent in the request body).
  Selection state is component-local (`Set<number>` in `CartPage`), not persisted across reloads.
  Verified via a clean `tsc --noEmit` and a successful `vite build`. This closes out the
  multi-select cart feature (Phase 1: `ecommerce-service` bulk-remove primitive; Phase 2:
  `ecommerce-service` checkout `selectedVariantIds`; Phase 3: this) — see
  `ecommerce-service/CLAUDE.md`'s Epic 2 section and `gui/CLAUDE.md`'s `CartPage.tsx`/
  `CheckoutPage.tsx` notes for the full detail.

- **`ecommerce-service`: new bulk cart-item removal (`CartService.removeItems`,
  `POST /api/v1/cart/items/remove-batch`) — Phase 1 of a multi-select cart feature (bulk delete +
  checkout-selected-items-only), per request.** Researched first: the existing checkout `confirm`
  always operates on the caller's *entire* cart (no variant/line filter anywhere in
  `CheckoutServiceImpl`/`CheckoutApi`), and the only existing cart-removal path was single-`variantId`
  `DELETE /api/v1/cart/items/{variantId}` — no bulk endpoint existed. This phase adds the one
  missing primitive both halves of the feature need: removing an arbitrary set of variantIds from
  the cart in one call. `CartServiceImpl.removeItems` issues a single Redis `HDEL` across every
  requested field (one round trip, not N) and refreshes the cart's TTL the same way every other
  mutation does. New `dto/RemoveCartItemsRequest` (`@NotEmpty List<Integer> variantIds`).
  **Endpoint is `POST /items/remove-batch`, not `DELETE` with a body** — chosen over a
  `DELETE /api/v1/cart/items` with a request body after asking: not every HTTP client/proxy layer
  reliably forwards a body on `DELETE`, so a `POST` action endpoint avoids that class of bug for a
  small naming-purity trade-off. No `gateway` routing change needed — `/api/v1/cart/**` already
  covers the new sub-path. New `CartServiceImplTest` (this module's first test for
  `CartServiceImpl` at all — a pre-existing gap, not introduced by this change; only the new
  `removeItems` path is covered, existing untested methods weren't retroactively backfilled).
  138 unit tests total (up from 137), all passing, no Docker needed. **Phase 2 (extending
  checkout's `confirm`/`preview` with an optional `selectedVariantIds` filter) and the `gui`
  checkbox UI are follow-up work, not yet built** — see `ecommerce-service/CLAUDE.md`'s Epic 2
  section for the full plan.

- **`ecommerce-service`: checkout's `confirm`/`preview` now accept an optional
  `selectedVariantIds` — Phase 2 of the same multi-select cart feature, per request.**
  `CheckoutService.preview`/`.confirm` both gained a `List<Integer> selectedVariantIds` parameter
  (`null` = the whole cart, exactly the pre-existing behavior — fully backward compatible); a new
  private `filterBySelection` helper narrows the cart's lines down to just that set (or returns
  them unchanged when the param is `null`) before the existing availability/emptiness guards run,
  so "no selection" and "every line happens to be selected" both flow through identical downstream
  logic. **`confirm`'s final `cartService.clear(userUuid)` is now
  `cartService.removeItems(userUuid, orderedVariantIds)`** (Phase 1's bulk-remove primitive) — the
  actual point of this phase: anything excluded by `selectedVariantIds`, or dropped by `confirm`'s
  own final revalidation, now stays in the cart afterward instead of being wiped along with
  whatever was actually ordered. This changes behavior for the *no-selection* case too (a
  previously-dropped/unavailable line used to get cleared away for free by the whole-cart `clear()`
  — it no longer does), which was called out and accepted as part of this phase's plan.
  `AddressRequest` (the `confirm` request body) gained a `selectedVariantIds` field (bolt-on,
  same precedent as `CartLineResponse.availableQuantity`); `CheckoutApi.preview` (a `GET`, so no
  body to carry it) gained a `@RequestParam(required = false) List<Integer> selectedVariantIds`
  instead. A selection that matches nothing currently in the cart reuses the existing
  `CHECKOUT_CART_EMPTY` error code rather than adding a new one — same shopper-facing meaning
  either way ("nothing to check out"). `CheckoutServiceImplTest` gained 4 new cases (selection
  filters `preview`'s totals/lines correctly, a selection matching nothing rejects, `confirm`
  orders/removes only the selected lines leaving the rest in the cart, and the same
  matches-nothing rejection on `confirm`) plus updated every existing case's assertions from
  `verify(cartService).clear(...)` to `verify(cartService).removeItems(...)`.
  **`CartService.clear`/`CartServiceImpl.clear` deleted outright** — this phase left them with
  zero remaining callers in production code; not kept around for a hypothetical future "empty
  cart" action. 147 unit tests total (up from 138 — some renamed for the removeItems terminology,
  net +9), all passing, no Docker needed; verified via a real `mvn test` run. **The `gui` checkbox
  UI (per-row checkboxes, "select
  all," a "Delete Selected" button, and wiring the selection through to `checkoutApi.preview`/
  `.confirm`) is the remaining follow-up, not yet built** — see `ecommerce-service/CLAUDE.md`'s
  Epic 2 section.

- **`gui`: `CartPage.tsx`'s cart rows are now separated by a horizontal `Divider` instead of each
  having its own border box, per request** — the lines-list `Stack` now passes `divider={<Divider
  />}` (MUI's built-in separator-between-children prop); both `CartLineRow`'s available and
  unavailable-line variants dropped their own `border: '1px solid', borderColor: 'divider'` box and
  switched `p: 2` to `py: 2`, since there's no longer a box edge to pad against horizontally — the
  card around the whole list already provides that via its own `p: 3`. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s variation chip is now a fixed `width: 300` box with its border kept,
  per follow-up request** — the border was briefly removed (below), then reverted immediately
  after; separately, the chip itself (not just its outer reserved-slot `Box`, already fixed-width)
  was left unbounded/`inline-block`, so its own width still drifted with `variationLabel` length
  independent of the slot — now a fixed `width: 300` with `boxSizing: 'border-box'` so the
  `border`/padding don't push past that width; the outer reserved-slot `Box` widened to match
  (`width: 300`, was `240`). Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s `variationLabel` now shows just the attribute values, and its reserved
  column widened, per request** — was `Object.entries(...).map(([k, v]) => \`${k}: ${v}\`)`
  (e.g. `"Size: 15in, Color: Black"`), now `Object.values(line.attributes).join(' ')`
  (e.g. `"15in Black"`) — plainer now that "Variation:" already appears on its own line above it.
  The reserved column `Box` also widened from `width: 180` to `240`. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s variation chip is now two lines, per request** — "Variation:" label +
  `KeyboardArrowDownIcon` on the first line, `{variationLabel}` on its own line below (the chip's
  outer `Box` switched from `inline-flex` to `inline-block` to let the two lines stack). Verified
  via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s inline name/variation/unit-price row now uses fixed column widths, per
  follow-up request** — a fix. The first pass (below) used `flexShrink: 0` on the variation chip
  and unit price, which kept them from being squeezed but didn't stop their *position* drifting
  left/right per row depending on how long that row's own product name (or variation label)
  happened to be. Now: name is `width: 220` with `noWrap` (ellipsis-truncates), the variation chip
  sits inside its own `width: 180` `Box` (rendered even when empty, so the column is still
  reserved), unit price is `width: 90` — every row's chip and price now start at the same x
  position regardless of content length. Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

- **`gui`: `CartPage.tsx`'s product name/variation chip/unit price now sit inline in one row, per
  request** — was stacked vertically in their own `Box` under the thumbnail; now the page's wider
  `80%` container (below) leaves enough horizontal room to lay them out side by side instead. The
  name gets `noWrap` + `flexShrink: 1, minWidth: 0` to truncate with an ellipsis under space
  pressure; the variation chip and unit price both get `flexShrink: 0` to keep their own width.
  Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`, `CartPage.tsx`, `OrderHistoryPage.tsx`, and
  `OrderDetailPage.tsx`'s page containers are now `width: '80%'` instead of a fixed `maxWidth`
  (`1100`/`800`/`800`/`700` respectively), per request** — all four ecommerce pages now scale with
  viewport width rather than capping at a fixed pixel value. Verified via a clean `tsc --noEmit`
  and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s red now marks the line total instead of the per-unit price, per
  follow-up request** — tried `color="error.main"` on the per-unit "$X each" price first (matching
  `ProductDetailPage`'s red price treatment); moved to `color="text.primary"` for that price and
  `color="error.main"` on the row's right-aligned `lineTotal` instead, since that's the number this
  row actually charges. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s page-level Subtotal is now `color="error.main"` too, per request** —
  same reasoning as `lineTotal` above, applied one level up. Verified via a clean `tsc --noEmit`
  and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s lines list + divider + subtotal now sit in a `bgcolor:
  'background.paper'` card, matching `ProductDetailPage`'s own card, per request** — same
  `borderRadius: 2, p: 3`, same semantic-token reasoning (white in light mode; the right dark
  surface automatically if a dark theme is ever added, unlike a hardcoded `#fff`). The page title
  and the Continue Shopping/Checkout button row stay outside the card, mirroring how
  `ProductDetailPage` keeps its own "Back to Shop" button outside its card. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `CartPage.tsx`'s low-stock caption moved from under the unit-price line to below the
  quantity stepper box, per request** — a fix. The `−`/qty/`+` `Stack` is now wrapped in an outer
  column `Stack` so `utils/stock.ts`'s shared "Only N left in stock!" caption stacks directly
  beneath it instead of under the product name/price column. Verified via a clean `tsc --noEmit`
  and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s main image gained dot slide-position indicators, per
  request** — small circles overlaid bottom-center of the image, one per image, filled
  `primary.main` for the current `activeImageIndex` and `background.paper` otherwise; clicking one
  jumps straight to that image via the same `setActiveImageIndex` the thumbnail strip already
  calls. Guarded on `sortedImages.length > 1`, same as the arrows/thumbnails. Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s main image gained prev/next slide arrows, per request** —
  `ChevronLeftIcon`/`ChevronRightIcon` `IconButton`s overlaid on the image, calling a new
  `slideBy(delta)` helper that wraps `activeImageIndex` modulo the image count in either direction.
  Asked the user to pick between manual arrows, auto-advance, and swipe/drag before building —
  went with manual-only (arrows, no timer, no gesture handling) as the simplest fit for a product
  gallery, per the chosen answer. The existing thumbnail strip is unchanged and stays alongside the
  arrows (also asked, kept per the chosen answer) — both control the same `activeImageIndex` state,
  nothing removed. Both arrows guard on `sortedImages.length > 1`, same as the thumbnail strip.
  Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s quantity-row availability text now also shown before a
  variant is picked, per request** — a fix. Used to render only once a specific variant resolved;
  now always rendered, right next to the stepper: plain **"IN STOCK"**/"Out of stock" before a
  variant is resolved (any-variant-in-stock fallback, since per-variant stock is independent and
  nothing more specific is known yet), then once resolved, the existing "N items available" /
  "Only N left in stock!" / "Out of stock" three-way. (Briefly tried right-aligned via a
  `flexGrow: 1` spacer per an initial "on the right side" request, reverted to sitting next to the
  stepper per immediate follow-up.) Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

- **`gui`: `components/shop/VariantSelector.tsx`'s chips are now rectangles with a small corner
  radius instead of MUI's default fully-rounded pill, per request** — `sx={{ borderRadius: 1 }}` on
  each `Chip` (no dedicated shape prop exists on `Chip` itself). Applies to both `layout` modes
  (shared `chipsFor(key)`), so `ProductDetailPage` and `CartPage`'s inline variant switcher stay
  visually consistent. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s quantity number no longer sits visibly higher than the
  flanking `−`/`+` icon buttons, per request** — a fix; the quantity `TextField`
  (`variant="standard"`, underline disabled) has an asymmetric default input padding, which shifted
  the number up relative to the icon buttons' centered glyphs. `inputProps.style.padding: 0` on the
  raw `<input>` removes it. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s gallery+info row now sits in its own white card, per request**
  — it used to share the page's own grey `background.default`, with no visual separation. Wrapped
  in `bgcolor: 'background.paper'` (white in light mode; deliberately the semantic token, not a
  hardcoded `#fff`, so a future dark theme gets the correct dark surface color automatically)
  plus `borderRadius: 2, p: 3` so it reads as a card rather than a flat color change. Verified via
  a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s title/price weight toned down, per request** — the title
  (`h5`) and price (`h4`) had been `fontWeight={700}`/`{800}`, which read as too bold; both are now
  `fontWeight={400}`. (The price box background was also tried at `grey.50`/`grey.200` per a
  "lighter than `action.hover`" ask, but settled back on `action.hover`.) Verified via a clean
  `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s info panel gained a faked rating/sold-count/report row below
  the title, per request** — real ratings and sold counts don't exist yet (Epic 5's
  reviews/order-analytics backend), so this renders module-level constants
  (`FAKE_RATING = 4.9`, `FAKE_RATING_COUNT = 79`, `FAKE_SOLD_COUNT = 1000`, identical on every
  product) instead: an MUI `Rating` (`readOnly`, `precision={0.1}`) plus the numeric score, a
  vertical divider, "N Ratings", another divider, "N Sold", then a right-aligned `Report` button
  (`FlagOutlinedIcon`, `disabled` with a "Coming soon" tooltip — no report flow exists either,
  same treatment as the existing `Buy Now` button). Replace the three constants with real
  `product`-sourced values once that backend lands. Verified via a clean `tsc --noEmit` and a
  successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s price box is now full-width instead of sized to its text, per
  request** — a fix; was `display: 'inline-block'`, which shrank the gray highlight box down to
  the price text's own width. Dropped, since `Box`'s default `display: 'block'` already fills the
  info panel's width. Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s category chip and product description removed from the info
  panel, per request** — both are deferred to a future "article" section rendered below the info
  panel instead (not yet built). Panel content is now just title → price box → divider →
  `VariantSelector` → quantity row → button row. `Product.categoryName`/`.description` are unused
  on this page for now, no type changes. Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s two-column split widened from an even flex-basis to
  info-panel-favoring 55%/45% (info/image gallery), per request** — the info column (price box,
  row-layout variant picker, two large buttons) reads better with more horizontal room than an even
  split gave it. `flex: '1 1 400px'`/`'1 1 320px'` → `'1 1 calc(45% - 12.8px)'`/
  `'1 1 calc(55% - 19.2px)'`; both sides keep the same `minWidth: 320` floor so the parent's
  `flexWrap: 'wrap'` still stacks them on a narrow viewport, unchanged. **Fix, caught after the
  first pass shipped as plain percentages**: two flex-basis values summing to exactly 100% of the
  container, plus the parent's `gap: 4` (32px) on top of that, overflowed the line — flexbox decides
  line-wrapping from each item's pre-shrink hypothetical basis size, not its post-shrink size, so
  the extra 32px always forced both columns onto separate rows regardless of viewport width.
  Subtracting 12.8px/19.2px (summing to the full 32px gap) off the two sides restores the intended
  side-by-side layout; see `gui/CLAUDE.md`'s `ProductDetailPage.tsx` bullet for the full arithmetic.
  Verified via a clean `tsc --noEmit` and a successful `vite build`.

- **`gui`: `ProductDetailPage.tsx`'s info panel redesigned around a Shopee-style layout, per
  explicit request/reference — this app still has no purchased/named UI template, this is just the
  closest real-world reference point for this one page's arrangement.** New order top-to-bottom:
  category chip → title (`h5`) → price in its own gray (`action.hover`) box, `h4` (bigger than the
  title) in `error.main` red → description → divider → `VariantSelector` → a `Quantity`
  label/stepper/availability-text row → an `Add to Cart` + `Buy Now` button row (the category
  chip/description were removed from this panel in a later follow-up, above). The separate
  binary in-stock/out-of-stock chip that used to sit next to the price is gone — availability now
  lives entirely in the quantity row (plain "N items available", the existing shared
  `utils/stock.ts` low-stock warning, or "Out of stock"), shown only once a specific variant is
  resolved. `components/shop/VariantSelector.tsx` gained a `layout?: 'stacked' | 'row'` prop
  (default `'stacked'`, unchanged) so this page can opt into a label-left/chips-right row per
  attribute (`Size  M  L`) without disturbing `CartPage`'s own reuse of the same component in its
  narrow inline variant-switcher popover, which keeps the original stacked shape — both layouts
  share the same chip-rendering logic, just arranged differently. `Add to Cart` is now
  `size="large"` with extra padding (a fix — it read as too small to be the page's primary action);
  `Buy Now` sits next to it at the same size but `disabled` with a "Coming soon" tooltip, since no
  "skip the cart" checkout flow exists yet — rendered per the two-button reference layout rather
  than omitted, but not left looking like a working shortcut that silently does nothing. Verified
  via a clean `tsc --noEmit` and a successful `vite build`; no interactive browser testing was
  possible in this environment (no Docker to run the backend stack). See `gui/CLAUDE.md`'s
  `ProductDetailPage.tsx`/`VariantSelector.tsx` notes for the full detail.

- **`gui`: the admin order-fulfillment screen (US-3.7/3.8), on top of `ecommerce-service`'s new
  admin list-orders endpoint.** New `pages/AdminOrderListPage.tsx` (`/admin/orders`, nested under
  `AdminLayout`) mirrors `ProductListPage.tsx`'s admin `Table`/`TablePagination`/row-action-icon
  template near-verbatim — the admin audience, unlike `OrderHistoryPage`'s shopper-facing
  `Stack`-of-cards style. New `api/adminOrderApi.ts` (`list`/`ship`/`deliver`), a separate file from
  `orderApi.ts` mirroring the backend's own `AdminOrderApi`/`OrderApi` split. The status filter
  (reusing `utils/orderStatus.ts`'s shared labels) defaults to `CONFIRMED` — "ready to ship" is this
  queue's whole reason to exist — with `SHIPPED`/"All statuses" also selectable. Each row's
  Ship/Deliver `IconButton`s disable unless that row's own status actually allows the action,
  correct regardless of the current filter. Both actions route through the existing
  `@shared/components/ConfirmDialog` rather than firing immediately on click, since either could
  trigger a real customer-facing notification once a real payment/shipping integration exists.
  `AdminLayout.tsx` gained an "Order Fulfillment" sidebar entry. Verified via a clean `tsc --noEmit`
  and a successful `vite build`; no interactive browser testing was possible in this environment
  (no Docker to run the backend stack). See `gui/CLAUDE.md`'s Epic 3 note for the full detail.

- **`ecommerce-service`: admin order-fulfillment list (US-3.7/3.8), a post-Epic-3 follow-up added
  once the admin GUI needed a way to find orders to act on.** `AdminOrderApi.ship`/`.deliver` only
  ever took an order id — nothing let an admin *find* which orders needed shipping/delivering. New
  `GET /api/v1/admin/orders` (optional `?status=` filter — `CONFIRMED` for "ready to ship",
  `SHIPPED` for "ready to mark delivered" — sorted oldest-first, plain `id ASC`, no
  client-configurable sort: a FIFO fulfillment queue, not a general browser). `OrderRepository`
  gained `JpaSpecificationExecutor<Order>` and a new `repository/spec/OrderSpecification.
  withFilters(OrderStatus)` — this module's usual Specification-pattern convention for dynamic
  filtering, even for a single optional filter, same shape as
  `ProductCategorySpecification`/`ProductSpecification`. New `OrderService.listAllOrders(status,
  pageable)` — deliberately **not** ownership-checked (unlike `listOrders`, the shopper's
  own-orders query); admin-only, enforced at the REST layer. Reuses the existing
  `OrderMapper.toResponse`/`OrderResponse` — no new DTO needed. **No `gateway` change needed** —
  `/api/v1/admin/orders/**` (already routed since Phase 5) already covers the bare `GET`, the same
  way `/api/v1/admin/products/**` already covers `ProductApi.list`'s equivalent bare `GET`. New
  `OrderServiceImplTest.ListAllOrders` test (delegation only, same reasoning
  `ProductSpecification`'s own filtering logic is left untested at the unit level) — 1 new test
  method, 137 unit tests total (up from 136), all passing, no Docker needed; verified via a real
  `mvn test` run in this session. See `ecommerce-service/CLAUDE.md`'s Epic 3 section for the note.

- **`gui`: Order History & Detail (Epic 3's shopper-facing GUI, US-3.3/3.5/3.6) on top of
  `ecommerce-service`'s already-built backend.** Discussed with the user before building, same as
  the storefront's/Cart-and-Checkout's own template discussions — this app has no purchased/named
  UI template anywhere, so the choice was which *existing* page to structurally mirror: a dedicated
  `Stack`-of-`Paper`-cards list (`OrderHistoryPage`, chosen over a dense admin-style `Table` +
  `TablePagination`) and stacked-`Paper`-sections detail page (`OrderDetailPage`, structurally
  near-identical to `CheckoutPage`'s own Items/Shipping-To sections). New
  `@ecommerce/api/orderApi.ts` (`list`/`getById`/`cancel`/`pay`, mirroring `cartApi.ts`'s shape),
  `types.ts` gained `OrderStatus`/`OrderStatusHistoryEntry`/`Order` (the latter two reuse the
  existing `OrderLine`/`Address` types from Epic 2 as-is), and a new `utils/orderStatus.ts` shared
  by both pages (status labels/colors/cancellability). `OrderDetailPage`'s Order Timeline section is
  hand-built from `statusHistory` rather than pulling in `@mui/lab`'s `Timeline` component — no new
  dependency for a single use. **`CheckoutPage.tsx`'s successful `confirm` now navigates straight to
  `/orders/:id`** instead of its old inline `OrderConfirmationView` (deleted outright, along with
  the now-dead `CheckCircleOutlineIcon` import) — that view only ever existed because no "get order
  by id" endpoint existed when Epic 2 was built; the real page (with a working Pay Now button) is
  strictly better now that Epic 3 built one. New "Orders" `NavBar` entry next to Cart (no badge —
  unlike Cart's item count, there's no unambiguous "right" count to show). **Deliberately
  shopper-facing only this pass** — the admin ship/deliver screen (US-3.7/3.8) is a follow-up, since
  `AdminOrderApi` has no "list orders" endpoint yet to build a fulfillment queue against; that needs
  new backend work first, not just GUI work. Verified via a clean `tsc --noEmit` (only the
  pre-existing, already-documented `App.test.tsx`/`reportWebVitals.ts` dead code and two unrelated
  `@chat` unused-import warnings) and a successful `vite build`; the dev server boots without
  console errors, but no interactive browser testing was possible in this environment — no Docker
  available to run the backend stack, so the actual order-history → detail → pay/cancel
  click-through is still unverified by a human. See `gui/CLAUDE.md`'s Epic 3 note for the full
  detail.

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 6 of 6 (final) —
  cross-handler integration tests, closing out the epic.** Every test written across Phases 2–5
  isolates exactly one handler/processor/service at a time, with its collaborators mocked — none
  drove the real `orderstatus.OrderStatusHandlerRegistry`, wired with every real handler, through
  more than one transition in sequence. New `orderstatus/OrderLifecycleIntegrationTest` closes that
  gap: constructs the registry from real `PendingOrderStatusHandler`/
  `PaymentProcessingOrderStatusHandler`/`ConfirmedOrderStatusHandler`/`ShippedOrderStatusHandler`
  instances (mocking only `ProductVariantRepository`, the actual persistence boundary) and drives a
  single `Order` through 6 scenarios: the full happy path (`PENDING` → `PAYMENT_PROCESSING` →
  `CONFIRMED` → `SHIPPED` → `DELIVERED`, asserting exact inventory calls and the full history
  trail), cancel-before-payment (release), cancel-after-confirmation (restock), a queued cancel
  winning over a subsequent gateway success, a queued cancel winning over a subsequent gateway
  decline (the latter two span `PaymentProcessingOrderStatusHandler`'s own `cancel`/
  `confirmPayment`/`failPayment` methods together — no single-handler unit test can observe this
  interaction), and cancel genuinely blocked once `SHIPPED`. 6 new test methods, 136 unit tests
  total (up from 130), all passing, no Docker needed; verified via a real `mvn test` run in this
  session. Also a documentation consistency pass across every phase's own section of
  `ecommerce-service/CLAUDE.md`, which caught and fixed one real staleness:
  `orderstatus/OrderStatusTransitions`'s own Javadoc still said `PaymentProcessingOrderStatusHandler`
  didn't need `ProductVariantRepository`, true only through Phase 3 — Phase 4's
  `confirmPayment`/`failPayment` gave it that dependency, leaving `ShippedOrderStatusHandler` as the
  only handler without it. **Epic 3 is now fully built, all 6 phases** — see
  `ecommerce-service/CLAUDE.md`'s Epic 3 section for the complete phase-by-phase history.

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 5 of 6 — the REST
  surface.** New `repository/OrderRepository.findByOwnerUuidOrderByIdDesc`
  ("list my orders," US-3.5, fully-derived query with the sort baked into the method name) backed
  by a new `IDX_CUSTOMER_ORDER_OWNER_UUID` index (migration
  `202608300002__0.0.2__DKP-0036__add_customer_order_owner_uuid_index.sql`, deferred until this
  exact query needed it). `service/OrderService` gained `getOrder(orderId, callerUuid)` (US-3.5,
  same ownership-hiding shape as `cancel`) and `listOrders(callerUuid, pageable)`. New
  `dto/OrderStatusHistoryResponse`/`OrderResponse` (the latter reused for both list and detail
  endpoints, same convention `ProductResponse` already established). New `mapper/OrderMapper` —
  hand-written, not MapStruct; its `toOrderLineResponse`/`toAddressResponse` are `public static`
  and now the canonical home for that mapping — `CheckoutMapper`'s own private copies were deleted
  in favor of calling `OrderMapper`'s. `OrderMapper.toResponse` reads `Order.lines`/`statusHistory`
  (lazy collections), relying on Spring Boot's default `spring.jpa.open-in-view=true` (unchanged in
  this module) to keep the Hibernate session open through the controller layer — the same reliance
  `ProductMapper` already has for `Product.variants`/`images`. New `api/OrderApi`+`Controller` at
  `/api/v1/orders` (authenticated-only, no new `SecurityConfig` rule) — `GET` (list mine), `GET
  /{id}` (full status timeline), `POST /{id}/cancel`, `POST /{id}/pay` (`initiatePayment` — not in
  the originally-scoped plan, added since Phase 4 had already built the capability). New
  `api/AdminOrderApi`+`Controller` at `/api/v1/admin/orders` (`POST /{id}/ship`, `POST
  /{id}/deliver`) — a separate interface from `OrderApi`, mirroring the existing
  `ProductCategoryApi`/`PublicProductCategoryApi` split. `gateway`'s `GatewayRoutesConfig` gained
  `/api/v1/orders/**` and `/api/v1/admin/orders/**` routes. `OrderServiceImplTest` gained
  `GetOrder`/`ListOrders` nested test classes — 4 new test methods, 130 unit tests total (up from
  126), all passing, no Docker needed; verified via a real `mvn test` run in this session (both
  `ecommerce-service` and `gateway` also verified to compile together). No dedicated
  `OrderMapperTest`/controller tests, matching this module's existing convention. See
  `ecommerce-service/CLAUDE.md`'s Epic 3 section for the complete phase-by-phase history.

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 4 of 6 — US-3.3/3.4, payment
  handoff + reconciliation behind a stub gateway.** New `payment/` package (Epic 4's eventual home,
  seeded now since this phase needs a seam to call): `PaymentGatewayPort` — GoF **Adapter**
  (Structural) — `charge(idempotencyKey, amount)`/`checkStatus(idempotencyKey)`, both returning a
  new `PaymentOutcome` (`SUCCEEDED`/`DECLINED`/`PENDING`); `NoOpPaymentGatewayPort`, the only
  implementation today, always returns `SUCCEEDED` instantly (logs a warning each call) — delete it
  outright once Epic 4 adds a real adapter. `OrderStatusHandler` gained
  `startPaymentProcessing`/`confirmPayment`/`failPayment` (same default-rejects shape as Phase 3's
  methods). `PendingOrderStatusHandler.startPaymentProcessing` stamps `idempotencyKey` (the order's
  own id) and `paymentProcessingStartedAt`, transitions to `PAYMENT_PROCESSING`.
  `PaymentProcessingOrderStatusHandler.confirmPayment`/`failPayment` resolve the in-flight attempt
  (`confirmSale`/`release` respectively via a new `OrderStatusTransitions.confirmSaleForLines`
  helper) and both check `Order.getCancelRequested()` first — a queued cancel wins over whatever
  the gateway answered, ending the order `CANCELLED` either way (restocking if payment had actually
  succeeded, since money was captured and stock sold, if only for a moment; refund deferred to
  Epic 4, same as `ConfirmedOrderStatusHandler.cancel`). New `orderstatus/PaymentHandoffService` —
  the two independent `@Transactional` steps US-3.3 needs (`startPaymentProcessing` commits
  `PENDING -> PAYMENT_PROCESSING` **before** any gateway call; `resolvePayment` applies the verdict
  afterward in a second transaction) — deliberately two transactions, not one, so a crash between
  the gateway call and applying its result leaves the order durably `PAYMENT_PROCESSING` with its
  idempotency key intact rather than silently rolling back and risking a double charge on retry.
  New `orderstatus/OrderReconciliationJob` (US-3.4, `@Scheduled`) — polls a new
  `OrderRepository.findIdsByStatusAndPaymentProcessingStartedAtBefore` query for orders stuck in
  `PAYMENT_PROCESSING` past `app.ecommerce.order.reconciliation.grace-period` (default `PT2M`),
  calls `PaymentGatewayPort.checkStatus` for the ground truth (never assumes an outcome), applies
  it via `PaymentHandoffService.resolvePayment`; one poison order's exception is caught/logged
  per-order so it doesn't block the rest of the batch. `service/OrderServiceImpl` gained
  `initiatePayment(orderId, callerUuid)` — the orchestrator wiring the two `PaymentHandoffService`
  steps around the gateway call; deliberately **not** itself `@Transactional` (moved the
  class-level annotation from Phases 2–3 to individual methods for exactly this reason) so a
  gateway-call failure leaves the order `PAYMENT_PROCESSING` for `OrderReconciliationJob` to
  resolve later, rather than force-failing it here. 19 new test methods
  (`NoOpPaymentGatewayPortTest`, `PaymentHandoffServiceTest`, `OrderReconciliationJobTest`, plus new
  cases on the Phase 3 handler/registry/`OrderServiceImpl` tests) — 126 unit tests total (up from
  107), all passing, no Docker needed; verified via a real `mvn test` run in this session. See
  `ecommerce-service/CLAUDE.md`'s Epic 3 section for the remaining-phase plan (the REST surface,
  Phase 5).

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 3 of 6 — the GoF State-pattern
  skeleton, wired up for US-3.2/3.6/3.7/3.8.** New self-contained `orderstatus/` package (mirrors
  `outbox/`'s own shape): `OrderStatusHandler` is the Strategy interface (`status()` +
  `expire`/`cancel`/`ship`/`deliver`, each defaulting to a rejection via the new
  `EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION`, `ORDER_003`); `PendingOrderStatusHandler`
  (`expire`/`cancel`, both release-only), `PaymentProcessingOrderStatusHandler` (`cancel` only sets
  `Order.cancelRequested` — a gateway call is in flight, so it can't transition immediately),
  `ConfirmedOrderStatusHandler` (`cancel` restocks — stock here was already sold via
  `confirmSale`; `ship`), and `ShippedOrderStatusHandler` (`deliver` only — deliberately doesn't
  override `cancel`, so "blocked once shipped" falls out of the interface's own default rejection
  for free) each implement it. Deliberately **no handler class for the terminal statuses**
  (`EXPIRED`/`FAILED`/`CANCELLED`/`DELIVERED`) — `OrderStatusHandlerRegistry` (same registry shape
  as `outbox.OutboxEventDispatcher`) falls back to a handler with no overrides at all for any status
  with nothing registered, so "no handler" and "handler exists but doesn't support this action"
  reject identically. New static `OrderStatusTransitions` helper class (not a shared abstract base
  — `PaymentProcessingOrderStatusHandler` doesn't need `ProductVariantRepository` at all, and a base
  class would force it to accept one anyway) provides `releaseReservations`/`restockSoldLines`/
  `transitionTo` (the last one is also every handler's single point of writing an
  `OrderStatusHistory` row, US-3.5). New `ProductVariantRepository.restock` (mirrors
  `confirmSale`'s shape, in reverse). New `OrderReservationExpiryJob`/
  `OrderReservationExpiryProcessor` (US-3.2) — same poller/processor split as
  `outbox.OutboxRelay`/`OutboxEventProcessor` — poll a new `OrderRepository.
  findIdsByStatusAndDteCreationBefore` query every `app.ecommerce.order.expiry-check.poll-interval`
  (default `PT1M`) for `PENDING` orders older than `app.ecommerce.order.reservation-timeout`
  (default `PT15M`), re-checking each is still `PENDING` after loading (a defensive guard against a
  stale poll-batch id, not a distributed-concurrency mechanism — this reactor runs one instance per
  service today, so no `OutboxEventRepository.claim`-style atomic `UPDATE` was added here). New
  `service/OrderService`/`impl/OrderServiceImpl` — thin `cancel(orderId, callerUuid)`/
  `ship(orderId)`/`deliver(orderId)` wrappers (find-or-404 via new `EcommerceErrorCode.
  ORDER_NOT_FOUND`, `ORDER_002`, dispatch through the registry, save); `cancel` hides ownership the
  same way `ProductService.getActiveBySlug` hides a deactivated product's slug. No REST layer yet —
  that's Phase 5. 25 new test methods across one class per handler plus registry/expiry-job/
  `OrderServiceImpl` tests — 107 unit tests total (up from 82), all passing, no Docker needed. See
  `ecommerce-service/CLAUDE.md`'s Epic 3 section for the remaining-phase plan (US-3.3/3.4 + REST).

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 2 of 6 — US-3.1, stock
  reservation wired into checkout.** `CheckoutServiceImpl.confirm` now calls the Phase-1
  `ProductVariantRepository.reserve` for every available cart line **before** building the `Order`,
  inside the same `@Transactional` method checkout already runs in — an insufficient-stock line
  throws a new `EcommerceErrorCode.ORDER_INSUFFICIENT_STOCK` (`ORDER_001`, `409 CONFLICT`), and
  since nothing has committed yet, the surrounding transaction rolls back both the new order and
  every reservation already claimed by an earlier line in the same request. This is what makes
  "create the order and reserve stock atomically" (this epic's own locked decision — one local ACID
  transaction, not a saga) actually true. `confirm` also now appends the order's very first
  `OrderStatusHistory` row (`fromStatus = null`, `toStatus = PENDING`) — the first real writer of
  Phase 1's audit-trail entity. `CheckoutServiceImplTest` gained cases for the reservation
  succeeding (asserting call order `reserve` → `save` → `clear`, plus the new history row), a
  dropped line never being a reservation candidate, a single-line insufficient-stock rejection, and
  a multi-line request where an earlier line's reservation is claimed before a later line fails
  (documented as the real transaction's job to undo, not something a mocked-repository unit test
  can exercise) — 82 unit tests total (up from 80), all passing, no Docker needed. See
  `ecommerce-service/CLAUDE.md`'s Epic 3 section for the remaining-phase plan (US-3.2–3.8).

- **`ecommerce-service`: Epic 3 (Order Lifecycle & Inventory), Phase 1 of 6 — data-model foundation
  only.** This epic is being built in phases (see `ecommerce-service/CLAUDE.md`'s Epic 3 section);
  no US-3.1–3.8 service/API logic exists yet, only the schema and low-level repository primitives
  later phases will build on. `enums/OrderStatus` widened from just `PENDING` to the full 8-value
  state machine (`PENDING`/`PAYMENT_PROCESSING`/`CONFIRMED`/`EXPIRED`/`FAILED`/`CANCELLED`/
  `SHIPPED`/`DELIVERED`) in one pass, since the whole machine is already fully specified by this
  epic's user stories — unlike `OutboxAggregateType`'s incremental, one-per-epic growth.
  `entity/Order` gained `idempotencyKey` (US-3.3), `paymentProcessingStartedAt` (US-3.4),
  `cancelRequested` (US-3.6's queued-cancel-mid-payment flag), and a `statusHistory` collection
  (same `cascade = ALL`/`orphanRemoval`/`@OrderBy("id ASC")` shape as `lines`). New
  `entity/OrderStatusHistory` (table `ORDER_STATUS_HISTORY`) is US-3.5's audit-trail row shape
  (nullable `fromStatus`, `toStatus`, optional `reason`) — no dedicated repository yet, since
  `Order.statusHistory`'s cascade already covers writing to it the same way `OrderLine` doesn't
  need one either. `repository/ProductVariantRepository` gained `reserve`/`release`/`confirmSale`
  — atomic conditional `@Modifying` `UPDATE`s (re-checking `stockQuantity - reservedQuantity`
  against the requested amount in the same statement as the increment) rather than optimistic
  `@Version`-based locking, the same claim-style shape `OutboxEventRepository.claim` already
  established in this module — chosen so two concurrent checkouts racing the same variant can
  never both succeed past the available stock, without a separate read-then-write step a second
  transaction could interleave with. None of these three methods are called from anywhere yet
  (Phase 2 wires `reserve` into `CheckoutServiceImpl.confirm`). Migration
  `202608300001__0.0.2__DKP-0035__add_order_reservation_and_status_history.sql` widens
  `CKC_CUSTOMER_ORDER_STATUS` to all 8 values, adds the three new `CUSTOMER_ORDER` columns (with a
  partial unique index on `IDEMPOTENCY_KEY`) and a `(STATUS, DTE_CREATION)` index for later phases'
  scheduled-job poll queries, and creates `ORDER_STATUS_HISTORY`. See `ecommerce-service/CLAUDE.md`
  for the full detail and remaining-phase plan.

- **`CartLineResponse` gained `primaryImageUrl`** so the Cart page can show a product thumbnail
  per line, not just text. `CartMapper` (`ecommerce-service`) now injects `infra`'s
  `StorageService` (plain constructor injection — it's a hand-written mapper, not one of the
  MapStruct-generated abstract classes `ProductMapper`/`ProductSearchViewMapper` use the
  `@Autowired protected` field style for) and resolves each available line's product's minimum-
  `sortOrder` image into a presigned URL, same nullable "no images yet → null" shape
  `ProductSearchViewMapper` already established for the storefront grid. `gui`'s `CartLine` type
  and `CartPage.tsx` (a new 64×64 `CartLineThumbnail`, same `ImageNotSupportedIcon` fallback as
  `ProductCard.tsx`) picked it up to match. Verified via a targeted `ecommerce-service` compile +
  test run and a clean `gui` `tsc --noEmit`/`vite build`.

### Fixed

- **Product Detail and Cart pages had no upper bound on quantity, and never showed remaining
  stock — a shopper could pick (and previously could even successfully add to cart) far more than
  was actually available, only finding out at checkout confirm via `ORDER_INSUFFICIENT_STOCK`.**
  `ecommerce-service`: `CartLineResponse`/`CartMapper.toLineResponse` gained `availableQuantity`
  (`stockQuantity - reservedQuantity` for that line's variant; omitted like every other field
  beyond `variantId`/`quantity` when the line is unavailable) — `ProductVariantResponse` already
  exposed the raw columns for the product detail page, but the cart had no equivalent number at
  all, only a boolean `available`. `gui`: new `@ecommerce/utils/stock.ts`
  (`isLowStock`/`lowStockMessage`, `LOW_STOCK_THRESHOLD = 5`) shared by both pages so the
  threshold/wording can't drift between them. `ProductDetailPage.tsx`'s quantity stepper (`+`
  button and manual entry) now caps at the selected variant's own available count, and shows
  "Only N left in stock!" — inline in the same row as the stepper itself (moved there per
  follow-up feedback; the **Add to Cart**/"Log in to buy" button dropped to its own line below,
  `alignSelf: 'flex-start'` keeping it from stretching full-width) — once a specific variant is
  picked and low; deliberately not shown before a variant is selected, since different variants
  have independent stock. `CartPage.tsx`'s own `+`
  button caps the same way using the new `availableQuantity` field, with the same low-stock caption
  per line. Verified via a real `mvn test` run (137/137 still passing — no dedicated `CartMapperTest`
  exists, matching this module's existing convention of not unit-testing this hand-written mapper),
  a clean `tsc --noEmit`, and a successful `vite build`; no interactive browser testing was possible
  in this environment (no Docker to run the backend stack). See `ecommerce-service/CLAUDE.md`'s
  Cart section and `gui/CLAUDE.md`'s Product Detail/Cart notes for the full detail.

- **Keycloak: the admin login page showed a "Register" link and a "Continue with Google" button,
  both dead ends for the admin flow.** `AdminLogin.tsx` redirects to Keycloak's own bare hosted
  login page (no `kc_idp_hint`, unlike the regular login flow's Google button), which by default
  shows whatever the realm allows — registration and every enabled, non-hidden identity provider.
  Either path would just hit `adminAuthService.handleCallback`'s existing `role !== 'ADMIN'`
  rejection anyway. Fixed with two independent realm settings in
  `docker/keycloak/realm-export.json`: `registrationAllowed: false`, and `hideOnLoginPage: true` on
  the `google` identity provider. Neither affects the app's real non-admin flows — `SignUp.tsx`
  never reaches Keycloak's hosted registration page at all (calls `identity-service`'s own endpoint
  instead), and `Login.tsx`'s own Google button's `kc_idp_hint=google` redirects straight to Google
  without ever rendering the page `hideOnLoginPage` controls. **Keycloak's `--import-realm` only
  applies on a realm that doesn't already exist yet** — an already-running instance needs the same
  two toggles applied live via the Admin Console (Realm Settings → Login → User registration off;
  Identity Providers → google → Hide on login page on) for the change to take effect without a
  destructive `docker compose down -v`. See `docker/keycloak/README.md`'s new section for the full
  reasoning.

- **`gui`: `/admin/login` was visible to an already-logged-in non-admin user.** Every actual admin
  page was already correctly unreachable for them (`PrivateRoute requireRole="ADMIN"` bounces to
  `/admin/login`), but the login page itself had no equivalent guard — `AdminLogin.tsx`'s own
  redirect effect only handled "already authenticated as admin → `/admin/dashboard`," leaving a
  non-admin visitor looking at a normal, functional-looking Admin Login form. Fixed with an
  `else if (authService.isAuthenticated())` branch on that same effect, sending a non-admin to
  `/dashboard` instead. Deliberately not fixed by wrapping the route in the existing `GuestRoute`
  component — it only supports one fixed redirect target for any authenticated visitor, and this
  page's two "already signed in" destinations differ by role; a single target would either loop a
  non-admin (bounced from `/admin/dashboard` by `PrivateRoute` straight back to `/admin/login`) or
  send an admin to the wrong dashboard. Verified via a clean `tsc --noEmit` and a successful
  `vite build`.

- **`ecommerce-service`'s local (non-container) Redis connection failed with `NOAUTH`** —
  `application.yml`'s `spring.data.redis.password` defaulted to empty, but
  `docker-compose.infra.yml`'s `redis` service runs with `--requirepass password`; a run through
  `docker-compose.apps.yml` never hit this (that file already sets
  `REDIS_PASSWORD: ${REDIS_PASSWORD:-password}`), but starting the app directly (IDE/`mvnw`)
  against the same infra Redis did. Default changed to `${REDIS_PASSWORD:password}`, matching the
  containerized default. `ai-service`'s own Redis connection (its Bucket4j rate limiter) has the
  identical empty-default gap, not yet fixed — same remedy if it comes up there too.

### Added

- **`gui` built the Cart & Checkout GUI (Epic 2, US-2.1–2.7) on top of `ecommerce-service`'s
  already-built backend.** Discussed with the user before building, same as the storefront's own
  template discussion: a dedicated `/cart` page rather than a slide-out drawer (this app has no
  drawer precedent), and a single-page checkout with stacked sections rather than an MUI `Stepper`
  wizard (no wizard precedent either, and the backend's own `preview`/`confirm` two-call shape
  maps directly onto one page). New `@ecommerce/context/CartContext.tsx` (`CartProvider`/
  `useCart`, global cart state — same shape as `NotificationContext`/`StompConnectionContext`,
  provided in `App.tsx`), new `@ecommerce/api/{cartApi,checkoutApi}.ts`, new
  `@ecommerce/pages/{cart/CartPage,checkout/CheckoutPage}.tsx`, new `/cart`/`/checkout`
  `PrivateRoute`s in `App.tsx`, a new Cart icon+badge in `NavBar.tsx` (mirrors the existing
  Friends-request-count `Badge`), and `ProductDetailPage.tsx` finally gained its "Add to Cart"
  button (a quantity stepper + button, deliberately deferred until this epic existed — see
  `gui/CLAUDE.md`'s prior note). `Login.tsx`/`SignUp.tsx`/`AuthCallback.tsx` all call
  `useCart().refresh()` right after a successful login, since `CartProvider`'s own initial fetch
  runs once on mount and doesn't react to a later client-side login. Verified via `tsc --noEmit`
  (clean — the only pre-existing errors are the already-documented `App.test.tsx`/
  `reportWebVitals.ts`/two unrelated unused-import warnings) and a successful `vite build`; the
  dev server boots without console errors, but no interactive browser testing was possible in this
  environment — the actual add-to-cart → checkout click-through is still unverified by a human.

- **`ecommerce-service` completed Epic 2 (Cart & Checkout) — the checkout half (US-2.5–2.7) on top
  of the already-built cart half.** Two-step flow: `GET /api/v1/checkout/preview` revalidates the
  current cart (reusing `CartService.getCart`'s own existence/`active` check for US-2.7) and shows
  totals; `POST /api/v1/checkout/confirm` re-validates fresh — never trusting a client-cached
  preview — and creates an `Order`. New `entity/Address` (`@Embeddable` value object, embedded
  directly on `Order` — no standalone table, per this epic's locked "single inline address, no
  saved address book" decision), `entity/Order` (table `CUSTOMER_ORDER`, not `ORDER` — a reserved
  SQL keyword in PostgreSQL, same reason `social-service`'s `Group` maps to `MESSAGE_GROUP`) and
  `entity/OrderLine` (`productVariantId`/`sku`/`productName`/`unitPrice` all snapshotted at
  purchase time rather than FK'd live — `ProductServiceImpl.removeVariant` can hard-delete a
  variant outright, and an already-placed order must stay valid regardless), new
  `enums/OrderStatus` (only `PENDING` exists — this epic's own responsibility ends at order
  creation; Epic 3/4 add further values when they're built), new
  `repository/OrderRepository` (no "list my orders" query yet — out of this epic's scope), new
  `service/{CheckoutCommands,CheckoutPreview,CheckoutResult,CheckoutService,impl/CheckoutServiceImpl}`
  (flat shipping fee externalized via `app.ecommerce.checkout.flat-shipping-fee`, same `@Value`
  convention `CartServiceImpl`'s own `cartTtl` established), new `mapper/CheckoutMapper`
  (hand-written, reuses `CartMapper.toLineResponse` — extracted from `CartMapper.toResponse` for
  this reuse), new `dto/{AddressRequest,AddressResponse,OrderLineResponse,
  CheckoutPreviewResponse,CheckoutConfirmResponse}`, new `api/CheckoutApi`+`Controller`, two new
  `EcommerceErrorCode`s (`CHECKOUT_CART_EMPTY`/`CHECKOUT_NO_VALID_ITEMS`), a new Liquibase
  changeset (`DKP-0034`, `CUSTOMER_ORDER`/`ORDER_LINE`), a new `gateway` route
  (`/api/v1/checkout/**`), and a new `CheckoutServiceImplTest` (7 tests — both guards, the
  save-then-clear-cart ordering via `Mockito.inOrder`, and dropped-line reporting). Deliberately
  **not** built in this pass: an outbox event on order creation (nothing consumes it until Epic 3
  exists) and any order-listing endpoint. Verified via a full-reactor compile and test suite
  (excluding the two Docker-dependent tests) — 80 tests in `ecommerce-service` alone, up from 73.

- **`common.exception.Validator`** — a static guard-clause utility (`isTrue`/`isFalse`/`notNull`/
  `isNull`/`notFound`) collapsing the repeated `if (!condition) { throw new
  ApiException(errorCode, args); }` shape into one call, the same idiom as Spring's own
  `org.springframework.util.Assert`/Guava's `Preconditions`, but tied to this reactor's own
  `ErrorCode`-driven exceptions. `isTrue`/`isFalse`/`notNull`/`isNull` throw `BusinessException`;
  `notFound(Optional<T>, ErrorCode, Object...)` throws the more specific `ResourceNotFoundException`
  and returns the unwrapped value, replacing the
  `repo.findById(id).orElseThrow(() -> new ResourceNotFoundException(errorCode, id))` shape
  end-to-end. Every check is a **semantic upgrade, not a behavior change** — `GlobalExceptionHandler`
  handles the whole `ApiException` hierarchy identically via one `@ExceptionHandler`, so a call site
  that used to throw a bare `ApiException`/`BusinessException` directly now throws a strictly more
  specific subtype with the same HTTP status/message. **Deliberately named `Validator`, not
  `Assertions`/`Guard`**, despite `jakarta.validation.Validator` (Bean Validation's own interface,
  on every module's classpath via `spring-boot-starter-validation`) sharing the name — an accepted
  trade-off since the two are never imported in the same file today.
  Rolled out reactor-wide across every existing boolean-guard and `Optional`-`orElseThrow`
  `ResourceNotFoundException` call site that fit the shape (`content-service`, `ecommerce-service`,
  `identity-service`, `task-service`, `social-service`, `ai-service`, `infra`'s own
  `SlugServiceImpl`/`StorageServiceImpl`) — roughly 100 call sites across ~20 files. **Deliberately
  left unconverted**: guard clauses with a `log.error(...)` call between the condition and the
  throw (converting would silently drop the diagnostic log line), exception-translation inside a
  `catch` block (not a boolean-guard shape), and the two-plain-`String` `ResourceNotFoundException(
  resource, identifier)` constructor overload (`ai-service`'s `ContentServiceClientImpl`) — see
  `KeycloakAdminServiceImpl`'s three remaining manual throws and `GroupController`'s one for the
  `catch`-block/logging exceptions to this rollout. Verified via a full-reactor compile and the
  existing test suite (excluding the two Docker-dependent Testcontainers tests), no regressions.

### Changed

- **`CurrentUserIdArgumentResolver` consolidated into `infra.security`, out of four near-identical
  per-service copies** (`content-service`, `task-service`, `ai-service`, and — once Epic 2's cart
  gave it its first `@CurrentUserId` consumer — `ecommerce-service`). All four were byte-for-byte
  identical logic, differing only in Javadoc wording (each module's own `ownerUuid`/`authorUuid`/
  `userUuid` column vocabulary) — the same shape as this reactor's earlier
  `KeycloakRealmRoleConverter`/`JsonAuthenticationEntryPoint` consolidations. Each of the four
  modules' `@SpringBootApplication` entry points gained an explicit
  `@Import(CurrentUserIdArgumentResolver.class)` (this reactor's established "explicit imports
  over broad component scan" convention); each module's own `WebMvcConfig`/`ai-service`'s
  `ChatMvcConfig` still registers it locally via `addArgumentResolvers` — only the resolver class
  itself moved, not the registration step. **Not touched**: `social-service`'s own copy (resolves
  a genuinely different `Integer` local `SocialProfile` PK via a repository lookup, plus a
  STOMP-side `CurrentUserIdMessageArgumentResolver` counterpart this class has no equivalent for)
  and `identity-service`/`gateway` (neither ever had a copy). Verified via a full compile +
  the existing 73-test suite across all five touched modules (`infra` + the four consumers), no
  regressions.

### Added

- **`ecommerce-service` started Epic 2 (Cart & Checkout) — the cart half (US-2.1–2.4) is built;
  checkout (US-2.5–2.7) is not yet.** Showcases this epic's own locked pattern: Redis as a
  *primary* store, not a cache (see `docs/user-stories/02-cart-checkout.md`) — no Postgres table
  backs a cart at all. New `spring-boot-starter-data-redis` dependency, connecting to the same
  shared Redis instance `ai-service`'s Bucket4j rate limiter already uses (same property
  names/env vars — `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`), under this module's own `cart:`
  key prefix; no custom `RedisConfig` needed, since a plain autoconfigured `StringRedisTemplate`'s
  hash operations are exactly what a `variantId -> quantity` hash needs (unlike `ai-service`'s
  Bucket4j use case, which needs a raw Lettuce client). This is also this module's first use of
  `@CurrentUserId` — new `config/web/WebMvcConfig` registering `infra`'s shared
  `CurrentUserIdArgumentResolver` (see the "Changed" entry above — a local copy existed briefly
  before that consolidation), since Epic 1 never needed a per-caller identity at all. New
  `service/{Cart,CartLine,CartService,impl/CartServiceImpl}` (`addItem` increments via `HINCRBY`
  rather than read-then-write; `setQuantity`'s `0` branch removes the line and skips
  availability validation, since a shopper must always be able to remove an already-invalid line;
  the cart's 30-day TTL is refreshed on every mutation, never on a read — a deliberate,
  silent abandoned-cart cleanup, not a bug), a hand-written (not MapStruct) `mapper/CartMapper`
  (computes `subtotal`/`itemCount` across only the `available` lines — real aggregation logic
  MapStruct's per-field model doesn't fit), new `dto/{CartResponse,CartLineResponse,
  AddCartItemRequest,UpdateCartItemRequest}`, and `api/CartApi`+`Controller` at
  `/api/v1/cart` (authenticated-only — falls under the existing default
  `anyRequest().authenticated()` `SecurityConfig` rule, no new rule needed). `gateway`'s
  `GatewayRoutesConfig` gained a matching `/api/v1/cart/**` route — the app's first genuinely new
  top-level prefix (no shared-prefix disambiguation needed, unlike `/api/v1/public`/`/api/v1/admin`).
- **`gui` gained a public storefront (US-1.1–1.4) — the app's first genuinely public feature
  besides `/login`/`/signup`.** Every other feature in this app sits behind `PrivateRoute`;
  `/shop` (category rail, price/in-stock/attribute filters, paginated product grid) and
  `/shop/:slug` (image gallery, combo-accurate variant selector, no "Add to Cart" — Epic 2's
  cart/checkout isn't built yet, so there's nowhere for it to go) are plain, ungated routes,
  matching `ecommerce-service`'s own `permitAll` browse/search/detail API — browsing works
  identically logged in or out. `NavBar.tsx`'s new "Shop" button is the one nav entry rendered
  unconditionally, outside the auth-state branches every other button lives in. New
  `@ecommerce/api/shopApi.ts`, `pages/shop/{ShopPage,ProductDetailPage}.tsx`,
  `components/shop/{ProductCard,VariantSelector}.tsx`, and a `ProductSearchResult` type. Building
  this surfaced two real backend gaps, both fixed as part of this change (not scope creep — the
  storefront literally couldn't render without them):
  - **`ProductSearchResponse`/`ProductSearchViewMapper`** — the browse/search endpoint only ever
    returned a raw, private MinIO object key (`primaryImageStorageKey`), never a URL a browser
    could actually load. `ProductSearchViewMapper` is now an abstract class (same pattern as
    `ProductMapper`'s own earlier fix), resolving a presigned `primaryImageUrl` via
    `@AfterMapping`.
  - **New `PublicProductCategoryApi`/`Controller`** at `/api/v1/public/product-categories` — the
    storefront's category filter rail needs to list categories, but the only existing endpoint
    (`ProductCategoryApi.list`) sits under `/api/v1/admin/**` (`ROLE_ADMIN` required), unreachable
    for a logged-out or non-admin shopper. Delegates to the same
    `ProductCategoryService.list(null)` the admin controller already uses. `gateway`'s
    `GatewayRoutesConfig.ecommerceServiceRoutes()` gained a matching
    `/api/v1/public/product-categories/**` route.
  - **Known, page-scoped limitation, not a bug**: the storefront's attribute-facet filter options
    are built from whatever's on the currently-loaded page of results, not the whole category —
    there's no "every attribute value in this category" endpoint yet.
- **`ecommerce-service` gained a test suite covering Epic 1's 7 user stories — the first true
  unit-test suite in this reactor** (`social-service`'s is the only other test suite in this
  codebase, and it's Testcontainers integration tests for STOMP, not mocked unit tests). Plain
  JUnit 5 + Mockito + AssertJ unit tests for `ProductCategoryServiceImpl`, `ProductServiceImpl`
  (every US-1.6/1.7 validation branch), `ProductSearchServiceImpl` (its own logic only — blank-`q`
  handling, attribute-filter JSON construction, unsorted `Pageable`), `ProductChangedOutboxEventHandler`
  (the US-1.5 projection logic), and the generic outbox mechanism (`OutboxEventDispatcher`,
  `OutboxEventProcessor`) — 73 tests, all passing, no Docker required. Plus one deliberate
  exception: `ProductSearchViewRepositoryIT`, a real Postgres Testcontainers integration test for
  US-1.3/1.4's actual `tsvector`/`pg_trgm`/JSONB-containment native-SQL matching, which a mocked
  unit test structurally cannot verify — applies this module's own real Liquibase migration SQL
  directly via JDBC before running, so it exercises the exact production DDL. Confirmed to compile
  and to fail at exactly the expected point (no Docker daemon reachable in this session's sandbox);
  unverified beyond that until run somewhere Docker is available. New test-scope
  `org.testcontainers:junit-jupiter`/`:postgresql` dependencies added to `ecommerce-service/pom.xml`
  for it. See `ecommerce-service/CLAUDE.md`'s new test-suite note for the full class list and
  reasoning.
- **`ecommerce-service` gained a starter sample catalog** (a developer-swag theme — apparel,
  drinkware, stickers, office, accessories — 13 products, ~50 variants, 5 categories, placeholder
  images for 5 featured products), seeded via a new `service/seed/` package
  (`ProductCategorySeeder`, `ProductSeeder`, `ProductImageSeeder`, `EcommerceDataSeedingRunner`),
  gated by `app.seed.enabled` (`"true"` in `docker-compose.apps.yml`'s `ecommerce-service` block),
  same shape as `content-service`'s/`social-service`'s own seeders. `ProductSeeder` deliberately
  routes through `ProductService.create`/`deactivate` rather than a bare repository save, since
  that's what publishes `PRODUCT_CHANGED` and gets a seeded product into `ProductSearchView` at
  all; `ProductImageSeeder` generates placeholder JPEGs on the fly (`PlaceholderImageGenerator`,
  `java.awt`/`ImageIO` — no checked-in binary assets, since no real product photography exists for
  this sample catalog) and uploads them through the real `ProductService.uploadImage` path via a
  new `InMemoryMultipartFile` (a minimal byte-array-backed `MultipartFile`, since Spring's own
  `MockMultipartFile` is `spring-test`-scoped, not available to main source) — this seeder is that
  endpoint's first real exercise. Idempotency for this first pass is keyed by `name`/`sku`
  directly rather than a decoupled `seedId` column like `content-service`'s seeders use — a
  deliberate simplification for a small, fixed sample dataset, not long-lived content coexisting
  with user edits; see `ecommerce-service/CLAUDE.md`'s `service/seed/` note for the full reasoning
  and the tradeoff if this ever needs to support renaming a seeded row across re-seeds.
- **`ecommerce-service` gained a real image-upload endpoint for a product's gallery, plus a
  presigned URL on every `ProductImage` response — US-1.6's gallery was previously only reachable
  by already knowing a MinIO object key, with no way to actually get bytes into MinIO or see them
  rendered back.** `POST /api/v1/admin/products/{id}/images/upload` (multipart: `file` +
  `sortOrder`) is a new `ProductApi`/`ProductController`/`ProductService.uploadImage` path,
  validated by `infra`'s `StorageService.uploadImage` (image-only content type, ≤5 MB) and the
  same sort-order-conflict check `addImage` already had; the pre-existing raw-`storageKey`
  `addImage` endpoint is untouched. `ProductMapper` changed from a plain interface to an abstract
  class (mirroring `identity-service`'s `UserMapper`) so it can inject `StorageService` and resolve
  each `ProductImage.storageKey` into a time-limited presigned `url` field via `@AfterMapping` —
  the field the admin GUI actually renders as a thumbnail. `EcommerceServiceApplication` now
  imports `StorageProperties`/`StorageConfig`/`StorageServiceImpl` (the same trio
  `identity-service` imports for its own avatar upload), and gained its own `app.storage.*` block
  in `application.yml` — same MinIO instance/credentials as `identity-service`'s avatar upload,
  its own `product-images` bucket (auto-created on boot by `StorageServiceImpl`'s
  `@PostConstruct`, no manual provisioning needed). `docker-compose.apps.yml`'s `ecommerce-service`
  container now sets `MINIO_ENDPOINT` and waits on `minio` healthy, same as `identity-service`.
- **`gui` gained its first admin surface for `ecommerce-service`: Product Categories and Products,
  Epic 1's remaining two user stories (US-1.6/US-1.7) from the GUI side.** New `@ecommerce` feature
  folder (`types.ts`, `api/ecommerceApi.ts`, both mirroring `ecommerce-service`'s DTOs field-for-
  field) plus new path alias in `tsconfig.json`/`vite.config.ts`. `pages/ProductCategoryListPage.tsx`
  + `components/ProductCategoryFormDialog.tsx` mirror `@content`'s `TagListPage`/`TagFormDialog`
  pattern — a flat, unpaginated list (matching `ProductCategoryApi.list`'s own shape) with no
  delete action, since the backend exposes none. `pages/ProductListPage.tsx` +
  `pages/ProductFormPage.tsx` mirror `@content`'s `QuestionAnswerListPage`/`FormPage` pattern, with
  one deliberate divergence driven by the backend's own shape: variant/image mutation is
  independent of the basic-fields "Save" in edit mode (immediate API calls, matching
  `ProductApi`'s independently-mutable variant/image endpoints), but variants must be staged
  locally and submitted together with the initial `createProduct` call in create mode, since the
  backend requires ≥1 variant to create a product at all; images can't be added until a real
  `productId` exists, so the create form shows a "save first" notice and navigates straight to the
  new product's edit page on success. New `components/ProductVariantEditor.tsx` +
  `ProductVariantDialog.tsx` (a small key-value attribute editor that locks to a product's existing
  attribute-key set once it has ≥1 variant, enforcing US-1.6's consistent-keys rule client-side)
  and `components/ProductImageGallery.tsx` (upload via `postForm`, remove, and a real move-earlier/
  move-later reorder — implemented as a 3-step scratch-sort-order swap, since the backend rejects a
  sort order colliding with any other image and a naive 2-step swap would collide mid-flight).
  Wired into `AdminLayout.tsx`'s nav and `AdminDashboard.tsx`'s feature cards (both previously
  "Coming soon" placeholders, now `'ready'`).
- **Google/Facebook logins now also get a derived username instead of their raw email.**
  Keycloak's own "First Broker Login" flow assigns a federated identity's username as its email by
  default (a broker login has no separate username concept of its own) — the same starting point
  local password-based accounts had before an earlier `[Unreleased]` entry taught `createUser` to
  derive one instead. `KeycloakAdminService` gained `assignDerivedUsername(keycloakSubjectId,
  email)`, reusing that same `deriveUsernameBase`/`withSuffix` logic (and a new shared
  `applyUsername` helper `updateUsername` now uses too, rather than duplicating the
  fetch-representation/set-username/catch-409 shape). `UserServiceImpl.findOrCreateFromKeycloak`
  calls it — best-effort, log-and-continue on failure — only when JIT-provisioning a *brand-new*
  row whose incoming username still equals its email; never on every request, since a rename here
  changes what Keycloak reports for every later login too. **Reintroduces the same JWT
  claim-staleness problem the username-edit fix below already has**, but from a call site with no
  natural place to trigger a token refresh (`AuthCallback.tsx` only talks to Keycloak's token
  endpoint directly, never our own backend, so it has no way to know a rename happened at all).
  Closed in `gui`'s `Dashboard.tsx` instead — since `AuthCallback.tsx` always navigates to
  `/dashboard` first after login, `Dashboard.tsx`'s own mount effect now compares its just-fetched
  access token's `preferred_username` claim against the fetched profile's actual username, and calls
  `authService.refreshAccessToken()` on a mismatch — a general "does the claim match the server's
  view" check rather than a backend-supplied "was this new" flag, so it also covers any future
  server-side rename path the same way. See `identity-service/CLAUDE.md`'s
  `findOrCreateFromKeycloak` note and `gui/CLAUDE.md`'s matching note for the full mechanism.

### Fixed

- **Editing username on the Dashboard "Edit Profile" form silently reverted on the next request.**
  `UserServiceImpl.updateProfile` only ever wrote the local `identity.USER` row; Keycloak — the
  actual identity provider here — was never told, and `KeycloakJwtAuthenticationConverter` re-syncs
  `preferred_username` from the (unchanged) Keycloak record into that same row on every
  authenticated request, overwriting the edit almost immediately. Fixed by adding
  `KeycloakAdminService.updateUsername` (Admin REST API rename, mapping Keycloak's own 409 to
  `CommonErrorCode.USER_USERNAME_ALREADY_EXISTS`, alongside a new
  `IdentityErrorCode.KEYCLOAK_USER_UPDATE_FAILED` for any other failure) and having
  `UserServiceImpl.updateProfile` call it — after the existing local-uniqueness check, before the
  local save — whenever the submitted username actually differs from the current one. `gui`'s
  `Dashboard.tsx` also now calls `authService.refreshAccessToken()` right after a successful save
  when the username changed, since the currently-held access token's `preferred_username` claim was
  stamped at issuance and won't reflect the rename until a fresh token is issued — the same
  staleness the email-verification banner already works around, reused here for the same reason.
  **Known follow-up, not fixed here**: `social-service`'s own `SocialProfile.username` is
  independently JIT-synced from the same Keycloak claim (its own
  `KeycloakJwtAuthenticationConverter`), so friend search/public profiles there will keep showing
  the old username until that service's converter is revisited too, or at least until its holder
  obtains a fresh token.
  **Correction (same day)**: this fix's application code was correct but inert — Keycloak itself
  rejects any username change via the Admin REST API outright while the realm's `editUsernameAllowed`
  is `false` (the default this realm shipped with). See the "New accounts get a real username..."
  entry below for the full realm-config fix, which this update-username feature also depends on.

- **Logging out and logging back in via Google silently reused the previous Google account**,
  with no way to pick a different one. Not a bug in Keycloak brokering itself — RP-initiated
  logout correctly clears Keycloak's own SSO session, but Google keeps its own separate session
  cookie that logout never touches (by design — logging out of one broker-federated app shouldn't
  log you out of Google entirely). Since Google still saw an active session, it skipped its
  account-chooser screen and silently re-authenticated the same account. Fixed by setting
  `docker/keycloak/realm-export.json`'s `google` identity provider `config.prompt` to
  `select_account`, which makes Keycloak forward `&prompt=select_account` on every request to
  Google, forcing its account picker every time regardless of Google's own existing session.

### Added

- **New accounts get a real username instead of their full email address.**
  `KeycloakAdminServiceImpl.createUser` used to set Keycloak's `username` equal to the caller's
  `email` outright. It now derives one from the email's local part (e.g.
  `hoangtrungt111@gmail.com` → `hoangtrungt111`) via a new `deriveUsernameBase` (lowercased,
  non-`[a-z0-9_]` characters collapsed to `_`, capped at 30 chars — the same alphabet/length `gui`'s
  own username-edit validation already enforces, see this file's own "Fixed" section above) and
  disambiguates a collision with a numeric suffix (`withSuffix` — `hoangtrungt111`, `hoangtrungt1111`,
  `hoangtrungt1112`, ...), retried up to 50 times. Distinguishes a genuine username collision (retry)
  from an email collision (`IdentityErrorCode`/`EMAIL_ALREADY_EXISTS`, unchanged caller-facing
  behavior) by reading which field Keycloak's own 409 conflict body names. `loginWithEmailAllowed:
  true` (already set in `realm-export.json`, unchanged) means `gui`'s password-grant login — which
  always sends the caller's email as the grant's `username` — keeps working unaffected, since
  Keycloak resolves that value against either the username or email field when this flag is on.
  **Correction (same day) — this alone did nothing without a realm-config change too.**
  The assumption that `registrationEmailAsUsername` only governs Keycloak's own native
  self-registration form was wrong: Keycloak's own server source
  (`UsersResource.createUser()`/`UserResource.updateUserFromRep()`) unconditionally overrides
  *any* submitted `username` with `email` whenever `realm.isRegistrationEmailAsUsername()` is
  `true` — for every caller, including this module's own Admin REST API create/update calls, not
  just the native form. That's why a real registration still came back with `username == email`
  despite `createUser`'s new derivation logic running and returning `201 Created` — Keycloak
  silently substituted the email server-side after accepting the request. Separately, Keycloak
  also refuses any username *update* at all while `editUsernameAllowed` is `false` (the realm's
  prior default), which is what made the update-username fix above inert too. Both are now flipped
  in `realm-export.json` (`registrationEmailAsUsername: false`, `editUsernameAllowed: true`) — this
  only takes effect on a fresh Keycloak import (a realm already provisioned in a running instance
  needs the same two settings toggled by hand in Admin Console → Realm Settings → Login, or via a
  live `PUT /admin/realms/{realm}` call — see `docker/keycloak/README.md`'s import-once caveat).
  `registrationAllowed: true` (Keycloak's own native self-registration form, still reachable via its
  hosted login page) is unaffected by either flip and remains a separate, not-yet-addressed path.

- **Real email verification, Keycloak-native and non-blocking.** `identity-service`'s
  `KeycloakAdminServiceImpl.createUser` now creates accounts with `emailVerified: false` (was
  hardcoded `true` as a deliberate "pre-verified" scope choice) and triggers a real verification
  email via Keycloak's own Admin API `send-verify-email` action (`sendVerifyEmail` helper,
  best-effort — a mail-server hiccup logs a warning but never fails registration itself). A new
  `POST /api/v1/auth/resend-verification-email` (authenticated; `IdentityErrorCode.EMAIL_ALREADY_VERIFIED`
  if already verified, `VERIFICATION_EMAIL_SEND_FAILED` if Keycloak rejects the resend) backs a
  "Resend email" action. `KeycloakUserInfo` gained an `emailVerified` field (the JWT's own
  `email_verified` claim) so `UserServiceImpl.findOrCreateFromKeycloak`'s JIT-sync keeps the local
  `User.emailVerified` column in step with Keycloak's real record on every request — previously it
  was hardcoded `true` there too, meaning the local copy could never reflect reality regardless of
  what `createUser` set it to. `sendVerifyEmail` passes `client_id="gui"`/
  `redirect_uri=${app.keycloak-admin.frontend-url}/login?emailVerified=true` (a new
  `KeycloakAdminProperties` field, env `FRONTEND_URL` — already passed to this service's container
  in `docker-compose.apps.yml`, previously unread by anything) so the emailed link lands the user
  back in this app instead of Keycloak's default target — its own `account` client's generic "your
  account has been updated" page, a dead end from this app's perspective. `/login`, not
  `/dashboard`: handles a real edge case (logging out between registering and clicking the link
  would otherwise bounce `/dashboard` → `/login` anyway, with no feedback) in one redirect target —
  `GuestRoute` now forwards its query string when redirecting an already-authenticated caller on to
  `/dashboard`, so a still-logged-in user still lands there, just without losing
  `?emailVerified=true`. `Login.tsx`/`Dashboard.tsx` both show a one-time confirmation toast for
  that param via `useSearchParams`, then strip it so refreshing doesn't re-show it. Keycloak's
  initial "Confirm validity... Click here to proceed" step itself is a separate, hardcoded
  anti-prefetch guard on action-token links (not a config toggle) and stays as-is — removing it
  would need a custom Keycloak theme, judged not worth it here.

  **The realm's own `verifyEmail: true` flag turned out to make this blocking anyway, without
  either side ever setting a `requiredActions` list** — Keycloak's login pipeline auto-re-adds
  `VERIFY_EMAIL` as a required action at login time for *any* account with `emailVerified: false`
  whenever that realm flag is on, for every grant type including direct password grant (ROPC),
  surfacing as `resolve_required_actions` / "Account is not fully set up" and a flat login failure.
  Since this app triggers verification emails itself now (`sendVerifyEmail`) rather than relying on
  Keycloak's own self-registration flow (which is what that realm flag is actually for — unused
  here, registration goes through the custom Admin-API path instead), `docker/keycloak/realm-export.json`'s
  `verifyEmail` was flipped to `false`. Deliberately **non-blocking**, not gated behind a Keycloak
  `requiredActions: ["VERIFY_EMAIL"]`:
  `Login.tsx`'s direct password grant (ROPC) can't gracefully handle a pending required action —
  Keycloak rejects ROPC token issuance outright in that case, with no in-band way to complete it —
  so a blocking design would have locked every freshly-registered user out of password login
  entirely until they clicked the emailed link. Non-blocking sidesteps this: the account is usable
  immediately (no login-flow change needed), and `gui`'s `Dashboard.tsx` (its email-verification
  banner already existed, just dead-linked to the removed OTP flow) is the real UI — "Resend email"
  now calls the new endpoint instead of navigating to `/verify-otp`. `navigate`/`useNavigate` were
  dropped from `Dashboard.tsx` — that banner button was their only remaining call site.

  Verification status is a JWT claim, stamped at token-issuance time, so `Dashboard.tsx` has its
  own `useEffect` handling the stale-claim gap directly: while unverified, it calls
  `authService.refreshAccessToken()` (works on a still-valid, unexpired token — no need to wait for
  real expiry) and re-fetches `/api/v1/auth/user`, both immediately on mount and again on every
  `visibilitychange` back to `visible` — covering both the brand-new-tab case (see the
  `sendVerifyEmail` redirect entry below) and the already-open-tab-refocus case. Fully
  silent/best-effort; the listener detaches once verified. See `identity-service/CLAUDE.md`
  and `gui/CLAUDE.md` for the full detail.

### Fixed

- **`gui`'s silent-refresh-on-401 (`httpClient.ts`) was calling a dead pre-Keycloak-migration
  `identity-service` endpoint** (`/api/v1/auth/refresh`), so it always failed silently and every
  401 immediately cleared storage and hard-redirected to `/login` instead of attempting a real
  refresh. Replaced with a genuine Keycloak `refresh_token` grant: `httpClient.ts` (shared, no
  Keycloak knowledge of its own) now exposes `setTokenRefreshHandler` as an injection point;
  `authService.ts` implements the real `refreshAccessToken()` (owns the Keycloak URL/client
  constants already); `main.tsx` wires the two together once at startup. Since this app now
  issues tokens from two different Keycloak clients (`gui-password-login` for
  `loginWithPassword`, `gui` for the Authorization Code + PKCE / social-login flows) with nothing
  tracking which one a given session came from, `refreshAccessToken()` decodes the `azp`
  (authorized party) claim off the currently-stored access token instead — Keycloak always stamps
  it with the requesting `client_id`, and decoding a JWT payload works the same whether or not
  it's expired. See `gui/CLAUDE.md` for the full detail.

- **`gui`'s Google/Facebook login buttons (`authService.startOAuth`) called a dead
  `identity-service` endpoint** (`/api/v1/auth/oauth2/authorization/{provider}`) left over from
  before the Keycloak migration. Rewired as a third Authorization Code + PKCE flow against the
  same `gui` Keycloak client `adminAuthService.ts`/`AdminLogin.tsx` already use, with
  `kc_idp_hint=<provider>` appended so Keycloak's hosted login page redirects straight to the
  given identity provider instead of showing its own login form first. `AuthCallback.tsx`
  (`/auth/callback`) now does a real code-exchange via the new
  `authService.handleOAuthCallback`, mirroring `AdminAuthCallback.tsx` (same `hasRun` ref guard
  against StrictMode double-invoking the one-time-use code/verifier) minus the `ADMIN`-role gate.
  `authApi.ts`'s dead `exchangeStateToken` method (the old approach `AuthCallback.tsx` used to
  call) was removed outright. Keycloak itself still brokers the actual Google/Facebook OAuth
  dance — this app never talks to either provider directly. Google's identity provider in
  `docker/keycloak/realm-export.json` still needs a real Client ID/Secret and `enabled: true`
  before the button actually works end-to-end — see `docker/keycloak/README.md`. See
  `gui/CLAUDE.md` for the full detail.

- **`NavBar.tsx`'s `handleLogout` called `navigate('/login')` immediately after
  `authService.logout()`**, racing that function's own hard `window.location.href` redirect to
  Keycloak's RP-initiated logout endpoint (which itself redirects back to `/login` via
  `post_logout_redirect_uri`). Since the hard redirect doesn't halt JS execution synchronously,
  the extra client-side navigate rendered `/login` once via the SPA's still-mounted route change,
  then again for real once the browser actually came back from Keycloak — a confusing
  double-render on every logout. Removed the redundant `navigate('/login')` call.

- **`docker/keycloak/realm-export.json`'s `gui-password-login` client had an empty `redirectUris`
  list**, so Keycloak rejected `authService.logout()`'s `post_logout_redirect_uri` (no
  `post.logout.redirect.uris` attribute is set on any client in this realm, so Keycloak falls back
  to matching against the plain `redirectUris` list) and just showed its own generic "you are
  logged out" page instead of redirecting back to the app. Added
  `["http://localhost:3000/*", "http://localhost:3000/login"]`. Note for the Admin Console: this
  client has neither Standard nor Implicit flow enabled, so the "Valid redirect URIs" field (like
  "Web origins" below) may be hidden in the UI unless one of those flows is toggled on temporarily
  to reveal it, then back off after saving.

- **`common.exception.GlobalExceptionHandler` was never actually registered in any of the six
  standalone services — a reactor-wide gap, same root cause as the `infra` component-scan bugs
  documented below, just never hit for `common` until now.** Each service's
  `@SpringBootApplication` class sits in its own package (`com.ttg.devknowledgeplatform.identity`,
  `.content`, `.ecommerce`, `.task`, `.social`, `.ai`), a *sibling* of
  `com.ttg.devknowledgeplatform.common`, not a parent — default component scanning never reaches
  it, and no service `@Import`ed it explicitly. Surfaced by `identity-service`'s
  `KeycloakAdminServiceImpl` throwing `BusinessException(EMAIL_ALREADY_EXISTS)` on a duplicate
  registration: with no `@RestControllerAdvice` to catch it, the exception propagated uncaught,
  Spring's servlet container internally forwarded the failed request to `/error`, that forwarded
  request re-entered the service's own security filter chain as anonymous, `/error` isn't
  `permitAll`'d, so `infra`'s `JsonAuthenticationEntryPoint` fired and returned a generic 401
  `"Authentication required"` — completely masking the intended `409 EMAIL_ALREADY_EXISTS` (and,
  reactor-wide, masking every `BusinessException`/`ApiException`/bean-validation failure the same
  way, not just this one case). Fixed by adding `GlobalExceptionHandler.class` to the `@Import`
  list on all six `*ServiceApplication` classes (`IdentityServiceApplication`,
  `TaskServiceApplication`, `EcommerceServiceApplication`, `ContentServiceApplication`,
  `AiServiceApplication`, `SocialServiceApplication`) — `gateway` needs no equivalent, it has zero
  REST controllers to throw anything for `GlobalExceptionHandler` to catch.

- **`docker/keycloak/realm-export.json`'s `gui-password-login` and `identity-service-admin` client
  `description` fields exceeded Keycloak's `keycloak.CLIENT.DESCRIPTION varchar(255)` column**,
  failing Keycloak's own startup Liquibase migration (`value too long for type character varying(255)`)
  the first time a fresh import was attempted against an empty `keycloak` schema. Shortened both
  (fuller rationale already lives in `docker/keycloak/README.md`). Separately, `gui-password-login`
  was also missing a `webOrigins` entry — Keycloak only CORS-allows a client's own endpoints
  (including the token endpoint used by direct password grant) for origins listed there, so
  `authService.ts`'s direct browser call to Keycloak's token endpoint was blocked by CORS even
  though the grant itself succeeded server-side (`200 OK` but `net::ERR_FAILED` in the browser).
  Added `"webOrigins": ["http://localhost:3000"]` to that client.

- **`identity-service` never had its own `app.storage.*` (MinIO) configuration at all** — a genuine
  gap from this module's own extraction, surfaced for the first time only once the `@Import`
  refactor below made `infra`'s `StorageServiceImpl`/`StorageConfig` actually get constructed (every
  earlier component-scan bug in the list below had crashed the app before bean creation ever
  reached this far). `Failed to instantiate [io.minio.MinioClient]: ... endpoint must not be null`
  — `StorageProperties`' fields were all null since nothing ever bound `app.storage.*`. Added the
  same `app.storage.*` block (`${MINIO_ENDPOINT:http://localhost:9000}` etc., `minioadmin`/
  `minioadmin` matching `docker-compose.infra.yml`'s real MinIO credentials, `user-avatars` bucket)
  `social-service`/`gateway` already carry, to `identity-service/src/main/resources/application.yml`,
  plus `MINIO_ENDPOINT: http://minio:9000` (+ a `minio: condition: service_healthy` depends_on) to
  its `docker-compose.apps.yml` block, matching `social-service`'s existing entry. Local
  (non-Docker) runs need no `application-local.yml` change — the base file's `localhost:9000`
  default already covers it.

- **Three rounds of `infra` component-scan bugs, ending in a switch away from package scanning
  entirely to explicit `@Import`/`@EnableConfigurationProperties`.** Caught actually booting
  `task-service` for the first time — the first standalone service in this reactor's history to
  actually run against a real environment, which is exactly why none of these had been caught
  before:
  1. `ConflictingBeanDefinitionException` for `keycloakJwtAuthenticationConverter` — a stale
     compiled `.class` file for a since-deleted local converter, left in `target/classes` from
     before this module was fixed to use `infra`'s shared converter. Fixed with a targeted
     `./mvnw -pl task-service clean` (not a code change).
  2. `APPLICATION FAILED TO START` — `infra.config.thread.AsyncEventThreadPoolConfig` needed a bean
     of type `infra.config.thread.AsyncEventThreadPoolProperties` that couldn't be found. Root
     cause: `@ComponentScan` reaching `infra` picks up `AsyncEventThreadPoolConfig` itself (a plain
     `@Configuration`, instantiated regardless of whether the module dispatches any
     `@EventHandler`), but not `AsyncEventThreadPoolProperties` — a bare `@ConfigurationProperties`
     POJO with no `@Component` of its own (unlike `infra.config.storage.StorageProperties`, which
     does carry one) — which needs `@ConfigurationPropertiesScan`/`@EnableConfigurationProperties`
     specifically. First fix attempt (a *bare* `@ConfigurationPropertiesScan`, no arguments) was
     itself wrong and the identical error persisted: a bare annotation only scans the *declaring
     class's own package*, the same default-scope rule as a bare `@ComponentScan`, so it never
     reached the sibling `infra` package either — `content-service`'s/`ai-service`'s *existing*
     `@ConfigurationPropertiesScan` turned out to carry this identical bug despite predating this
     finding. Second attempt (explicit `basePackages` matching `@ComponentScan`) fixed it.
  3. **Root-cause discussion, not another bug:** why does a Maven dependency on `infra` not imply
     Spring picks up its beans? Because `@ComponentScan`/`@ConfigurationPropertiesScan` are
     package-rooted, independent of the classpath — a dependency only makes classes *loadable*,
     scanning is what makes Spring *look for* annotated ones. This — plus wanting `infra`
     component-scan bugs (three rounds, two classes of bug, six services) to stop being possible at
     all rather than merely fixed one-by-one — motivated moving off package scanning into `infra`
     entirely, in favor of each service's entry point naming the exact beans it uses:
     `@Import({...})` for `@Component`/`@Configuration` classes (`JacksonConfig`/
     `TraceContextFilter` everywhere; `KeycloakJwtAuthenticationConverter`+
     `KeycloakRealmRoleConverter` for `task-service`/`ecommerce-service`/`content-service`/
     `ai-service`; `KeycloakRealmRoleConverter` alone for `identity-service`/`social-service`'s own
     local converters; `StorageConfig`/`StorageProperties`/`StorageServiceImpl` for
     `identity-service`/`social-service`; `SlugServiceImpl` for `ecommerce-service`/
     `content-service`; `JsonAuthenticationEntryPoint` for `ai-service`; `AsyncEventThreadPoolConfig`
     for `ai-service`/`social-service` only — the two services that actually extend
     `AsyncEventHandler`) and `@EnableConfigurationProperties(AsyncEventThreadPoolProperties.class)`
     for the one bare-POJO properties class. A service keeps a bare, own-package-scoped
     `@ConfigurationPropertiesScan` only for its own local `@ConfigurationProperties` classes
     (`content-service`'s `InternalApiProperties`, `ai-service`'s dozen-plus `config/*`/
     `config/chat/*`) — never extended to reach `infra`. `gateway` needed no change at all — its own
     package is the parent of `infra`'s, so its scan always reached it "for free." Concrete side
     benefit: `AsyncEventThreadPoolConfig` (and its Micrometer instrumentation) is no longer
     instantiated in the four services that never dispatch an `@EventHandler`, where a package scan
     always would have regardless. Rewrote all six non-`gateway` entry-point classes
     (`TaskServiceApplication`, `EcommerceServiceApplication`, `IdentityServiceApplication`,
     `ContentServiceApplication`, `AiServiceApplication`, `SocialServiceApplication`), plus root
     `CLAUDE.md`'s, `infra/CLAUDE.md`'s, and each service's own `CLAUDE.md`'s narrative of this
     whole saga.

- **New `application-local.yml` for `task-service`, `identity-service`, `content-service`,
  `ai-service`, and `social-service`.** None of the five had any local-profile config at all — only
  a base `application.yml` with no `spring.datasource.*` and no fallback default the way
  `KEYCLOAK_ISSUER_URI` has one. Running any of them outside Docker (no `SPRING_DATASOURCE_URL`/etc.
  env vars set, since those are only wired in `docker-compose.apps.yml`'s `environment:` block)
  failed to start with "Failed to configure a DataSource: 'url' attribute is not specified" —
  confirmed for real on `task-service`; the other four share the identical gap and were fixed
  pre-emptively rather than waiting to hit the same error four more times. Each new file mirrors
  `ecommerce-service`'s existing `application-local.yml` (datasource pointed at
  `localhost:5432`/`dev-premier`, each module's own schema already set via
  `spring.jpa.properties.hibernate.default_schema` in the base file, looser JPA/logging for local
  dev, more actuator endpoints exposed) — run any of them with `-Dspring.profiles.active=local` per
  root `CLAUDE.md`, with `docker-compose.infra.yml` up so Postgres is reachable on `localhost:5432`.
  `ai-service`'s own Redis connection needed no equivalent override — its base `application.yml`
  already defaults `spring.data.redis.host`/`password` to `localhost`/empty.

- **Doc/reality mismatch: four services never had their own standalone `*-liquibase.yml` file.**
  `CLAUDE.md`, `docs/PROJECT_STRUCTURE.md`, and several `CLAUDE.md`/`pom.xml`/`application.yml`
  comments across `ecommerce-service`, `identity-service`, `content-service`, and `ai-service` all
  claimed each had its own dedicated single-service compose file (mirroring `task-service-liquibase.yml`/
  `social-service-liquibase.yml`), but only those latter two ever actually existed on disk — the
  other four services' migrations have only ever run via the consolidated `services-liquibase` job
  baked into `docker-compose.apps.yml`. Caught while debugging a real `run --rm services-liquibase`
  failure (`depends_on: postgres` unresolved because `docker-compose.apps.yml` was invoked without
  `docker-compose.infra.yml`, which is where `postgres` is actually defined). Corrected every
  reference to describe the real migration path instead of a nonexistent file — no compose/behavior
  change, docs-only.

### Added

- **`.env.example` at the repo root**, documenting every host-supplied variable
  `docker-compose.apps.yml` interpolates (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `FRONTEND_URL`,
  `REDIS_PASSWORD`, `INTERNAL_API_KEY`). Prompted by a real error: Compose interpolates every
  service's `environment:` block up front, before resolving which service you asked for, so
  `ai-service`'s mandatory `${OPENAI_API_KEY:?...}` blocked even an unrelated
  `docker-compose run --rm services-liquibase` call when the var wasn't set on the host. Copy to
  `.env` (now gitignored, alongside the tracked `.env.example` template) and Compose auto-loads it
  for every future invocation — no per-session `export` needed.

- **Distributed request tracing — `gateway/ROADMAP.md` backlog item #1** (correlation ID +
  structured access logging), built as [W3C Trace Context](https://www.w3.org/TR/trace-context/)
  `traceparent` propagation rather than a flat custom correlation-id header, since the marginal
  cost over a flat ID is small (one extra "mint a new span" step per hop) and it sets this reactor
  up for real tracing tooling (Jaeger/Zipkin/OpenTelemetry/Micrometer Tracing) later with zero
  rework, since they already speak this exact header format.
  - **New `infra.tracing.TraceContext`** — a record (`traceId`, `spanId`, `sampled`) implementing
    the `traceparent` grammar (`parse`/`fresh`/`withNewSpan`/`toHeaderValue`), no Spring dependency.
  - **New `infra.tracing.TraceContextFilter`** (`OncePerRequestFilter`, `@Component`) — binds a
    `TraceContext` to `MdcKeys.TRACE_ID`/new `MdcKeys.SPAN_ID` for every inbound request in whichever
    of this reactor's seven Spring Boot apps it runs in, rewrites the request's own `traceparent`
    header to carry this app's own span (so Gateway Server MVC's default header-forwarding proxy
    behavior carries it downstream automatically, no `GatewayRoutesConfig` change needed), and logs
    one structured access-log line (`method`/`path`/`status`/`tookMs`) per request. Auto-registered
    in all seven apps via the reactor-wide `@ComponentScan` fix — no per-app wiring needed. Carries
    `@Order(Ordered.HIGHEST_PRECEDENCE + 10)`, added after a review question caught that a filter
    bean with no `@Order` defaults to last place — *behind* every app's own Spring Security filter
    chain (auto-registered much earlier, at `HIGHEST_PRECEDENCE + 100`), which would have meant a
    401/403 short-circuiting inside Security before ever reaching this filter, losing the trace-id
    and access-log line for exactly the requests most worth tracing. `+10` rather than the bare
    extreme value, per a follow-up question — two filters sharing the exact same `@Order` have an
    undefined relative order, so this leaves headroom for a future "must run before Security"
    filter (e.g. `gateway/ROADMAP.md`'s rate-limiting item) to get its own distinct value instead.
  - **`gateway`'s `ChatStreamProxyController`** (the one route bypassing Gateway MVC's proxy DSL
    entirely, for SSE) now also forwards `traceparent` explicitly via an optional `@RequestHeader`,
    matching how it already forwards `Authorization`/`Content-Type`/`Accept`.
  - **New `ai-service.service.impl.TraceparentClientHttpRequestInterceptor`**, registered on
    `ContentServiceClientImpl`'s `RestClient.Builder` — stamps the current thread's MDC trace
    context onto every outbound call to `content-service`. **Known, deliberate gap:** falls back to
    a fresh, disconnected trace when this call runs on the async indexing pipeline's own worker
    thread, since `@Async`'s default executor doesn't propagate MDC — fixing that is a separate
    piece of work (an MDC-propagating `TaskDecorator` on that executor), not solved here.
  - **`logging.pattern.console` updated in all seven apps' own `application.yml`** to render
    `[traceId=%X{traceId:-}, spanId=%X{spanId:-}]` — an MDC value a log pattern never references is
    silently invisible, which had been true for the existing `AsyncEventHandler`'s own `TRACE_ID`
    binding this whole time, not just something this feature would have introduced. This property
    has to be duplicated across all seven `application.yml` files (a YAML value, not a Java bean —
    `infra`'s component scan can't centralize it the way it does everything else here).

### Changed

- **Root docker-compose files renamed to the standard Docker Compose convention.**
  `dev-knowledge-platform-docker-compose.yml` → `docker-compose.infra.yml` (Postgres/pgvector,
  Redis, MinIO, Mailpit, Keycloak) and `dev-knowledge-platform-apps-docker-compose.yml` →
  `docker-compose.apps.yml` (all seven standalone Spring Boot services). Pure rename, no service/
  behavior change — drops the redundant repo-name prefix in favor of Docker's own multi-file
  convention (`docker compose -f docker-compose.yml -f docker-compose.override.yml`). Updated every
  reference across `CLAUDE.md`, `docs/PROJECT_STRUCTURE.md`, `docs/AUTH_MICROSERVICES_NOTES.md`,
  `docker/keycloak/README.md`, and the `ai-service`/`gateway`/`content-service`/`ecommerce-service`
  module `CLAUDE.md` files. `docs/CHANGELOG-ARCHIVE.md` intentionally still refers to the old names
  — it's a historical record of entries as originally written, not rewritten retroactively.

- **Reactor version is now a single source of truth.** All ten `pom.xml` files (root + nine
  modules) previously each hardcoded the literal version string (`0.0.2-SNAPSHOT`), duplicated ten
  times. Switched to Maven's [CI-friendly version](https://maven.apache.org/maven-ci-friendly.html)
  pattern: the root `pom.xml` defines `<revision>0.0.2-SNAPSHOT</revision>` as a property and uses
  `<version>${revision}</version>` for itself; every child module's own `<parent><version>` now
  references that same `${revision}` placeholder instead of a literal. Bumping the version going
  forward means editing exactly one line (the `<revision>` property), not ten files. Verified with
  `./mvnw validate` (the lightweight POM-resolution phase, not a full build) — every module
  correctly resolved to `0.0.2-SNAPSHOT`. Only works because every module in this reactor is always
  built from the repo root via the reactor (`./mvnw ...`, with or without `-pl`), never as a
  standalone POM with no `relativePath` back to the parent — if that ever changes, this would need
  the `flatten-maven-plugin` to produce a resolved POM for whatever consumes the module standalone.

- **`KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` de-duplicated into `infra`.**
  All seven standalone services carried their own byte-for-byte-identical copy of
  `KeycloakRealmRoleConverter` (maps a JWT's `realm_access.roles` claim to `ROLE_*`
  `GrantedAuthority`s); five of them (`gateway`, `ecommerce-service`, `task-service`,
  `content-service`, `ai-service`) also carried an identical claims-only
  `KeycloakJwtAuthenticationConverter` (builds a `CustomOAuth2User` principal straight from the
  JWT's claims, zero database access). Both moved to new `infra.security.{KeycloakRealmRoleConverter,
  KeycloakJwtAuthenticationConverter}` classes, picked up automatically by every service's existing
  `@ComponentScan(basePackages = {"...own...", "com.ttg.devknowledgeplatform.infra"})` — the same
  mechanism that already centralized `JacksonConfig`. `identity-service` and `social-service` keep
  their own local `KeycloakJwtAuthenticationConverter` (both JIT-provision a real local row —
  `identity.USER`/`social.PROFILE` — which is genuine divergent logic, not duplication), but both
  now delegate to `infra`'s shared `KeycloakRealmRoleConverter` for the role-mapping half instead of
  keeping their own copy of that too. Required adding `spring-boot-starter-oauth2-resource-server`
  to `infra/pom.xml` — adds nothing new to any consumer's effective classpath, since every one of
  the seven services already declared that same starter itself. See `infra/CLAUDE.md`'s new
  `security/` entry and each affected service's own `CLAUDE.md` for the full detail.

- **`CurrentUserResolver` de-duplicated into `infra` too.** `task-service`, `content-service`, and
  `ai-service` each carried an identical claims-only `resolveXxxUuid(Principal)` helper (reads
  `CustomOAuth2User.getUserUuid()`, zero database access), differing only in method name
  (`resolveOwnerUuid`/`resolveAuthorUuid`/`resolveUserUuid`, matching each module's own column
  vocabulary). Moved into `infra.security.CurrentUserResolver` as one method, `resolveUserUuid`,
  with each of those three modules' own `CurrentUserIdArgumentResolver` calling it and assigning
  the result to whatever locally-named variable it needs. `social-service` (resolves a real local
  `SocialProfile` numeric PK via a repository lookup), `ecommerce-service` (never uses
  `@CurrentUserId` at all), and `identity-service` (resolves the caller via
  `@AuthenticationPrincipal` directly instead of this helper-class pattern) were left untouched —
  none of the three had a duplicate of this class to begin with.

- **`JsonAuthenticationEntryPoint` de-duplicated into `infra` too.** `gateway` and `ai-service` —
  the only two services with an explicit `.exceptionHandling().authenticationEntryPoint(...)`
  wired up — carried a byte-identical bean returning a JSON `401` body instead of Spring
  Security's default redirect/HTML response. Moved into `infra.security.JsonAuthenticationEntryPoint`
  with no other change needed (zero module-specific dependency, no method rename, no caller-side
  logic change). The other five services still don't wire this up at all and fall back to Spring
  Security's own default `401` behavior for a resource server.

- **JWT claim/authority magic strings extracted to constants.** `"typ"`, `"Bearer"`,
  `"realm_access"`, `"ROLE_ADMIN"` were re-typed as literal strings identically across
  `KeycloakRealmRoleConverter`, `infra`'s `KeycloakJwtAuthenticationConverter`, and
  `identity-service`'s/`social-service`'s own local converters; `"email"`/`"preferred_username"`/
  `"given_name"`/`"family_name"` likewise. New `infra.security.KeycloakJwtConstants` holds the
  first group (`TYPE_CLAIM`, `ACCESS_TOKEN_TYPE`, `REALM_ACCESS_CLAIM`, `ROLES_CLAIM`,
  `ROLE_PREFIX`) — the Keycloak-specific/project-specific pieces with no existing Spring constant.
  The standard OIDC claims in the second group now reference Spring Security's own
  `org.springframework.security.oauth2.core.oidc.StandardClaimNames` instead of a new
  project-specific constant, since the framework already names them (already on the classpath via
  `spring-boot-starter-oauth2-resource-server`, no new dependency needed). **`ROLE_ADMIN` was
  removed from `KeycloakJwtConstants` again shortly after** — "ADMIN" is a business-domain role
  name owned by `identity-service`'s own `UserRole` enum, not a generic mechanic like `ROLE_PREFIX`,
  and `infra` has zero dependency on any feature module so it can't reference that enum. That
  module's own converter now composes the check as `KeycloakJwtConstants.ROLE_PREFIX +
  UserRole.ADMIN.name()` instead of a bare duplicated literal.

### Fixed

- **Bean-name collision between `infra`'s shared `KeycloakJwtAuthenticationConverter` and
  `identity-service`'s/`social-service`'s own local converters of the same simple name.** Caught
  right after the `infra` consolidation above landed: Spring's default `@Component` bean-name
  generation uses only the simple class name, not the fully-qualified one, so
  `identity.security.KeycloakJwtAuthenticationConverter`/`social.security.KeycloakJwtAuthenticationConverter`
  registered under the identical default name as the shared `infra` bean
  (`keycloakJwtAuthenticationConverter`) the moment each module's own `@ComponentScan` reached
  `infra`'s package too — a `ConflictingBeanDefinitionException` at context startup for both
  services (`allow-bean-definition-overriding` isn't set anywhere in this reactor, so Spring
  Boot's default `false` applies — this would not have silently resolved itself). Fixed by giving
  both local converters an explicit, distinct `@Component` bean name
  (`identityKeycloakJwtAuthenticationConverter`/`socialKeycloakJwtAuthenticationConverter`).
  Injection needed no change — every consumer is typed to one specific class, so autowiring-by-type
  is unaffected by the bean name either way.

- **Admin login (`/admin/login`) now actually works, via direct Keycloak Authorization Code +
  PKCE — the regular `/login`/`/signup` flow is still broken and intentionally out of scope.**
  `AdminLogin.tsx`/`adminAuthService.ts` previously called `POST /api/v1/auth/login`, an endpoint
  `identity-service` dropped entirely once Keycloak took over the whole login lifecycle (see
  `identity-service/api/AuthApi.java`, which only has `GET /api/v1/auth/user` left) — this call
  always 404'd. Fixed by having `adminAuthService` redirect the browser directly to Keycloak's own
  hosted login page (`{keycloak}/realms/{realm}/protocol/openid-connect/auth`) using Authorization
  Code + PKCE (S256) — no gateway/identity-service involvement at all, matching the "gui" client's
  existing `publicClient: true`/`directAccessGrantsEnabled: false` config in
  `docker/keycloak/realm-export.json`, which needed no changes. New `adminAuthService.startLogin()`
  (hand-rolled PKCE via `features/auth/utils/pkce.ts` — `crypto.subtle` SHA-256 + base64url, no new
  dependency) generates a code_verifier/challenge/state, stashes verifier+state in
  `sessionStorage`, and redirects. New `AdminAuthCallback.tsx` (mounted at the new
  `/admin/auth/callback` route) receives Keycloak's redirect, and `adminAuthService.handleCallback`
  validates `state` (login-CSRF / authorization-code-injection guard), exchanges the code for
  tokens directly against Keycloak's token endpoint, decodes the returned access token's claims
  (`sub`/`preferred_username`/`email`/`realm_access.roles` — via new shared
  `shared/utils/jwt.ts#decodeJwtPayload`) since a raw Keycloak token response carries no custom
  `role` field the old backend response used to, and calls the existing
  `authService.storeTokens(...)` with the adapted shape — `PrivateRoute requireRole="ADMIN"` and
  the rest of the admin shell needed zero changes as a result. `adminAuthService.logout()` was
  fixed alongside login rather than left on its old dead fire-and-forget
  `POST /api/v1/auth/logout`: it now redirects to Keycloak's own end-session endpoint with an
  `id_token_hint` (the access token response's `id_token`, newly persisted via
  `AuthTokens.idToken`/`STORAGE_KEYS.idToken`/`authService.getIdToken()`), so the browser's actual
  Keycloak SSO session is terminated, not just this app's local tokens. New `VITE_KEYCLOAK_URL`/
  `VITE_KEYCLOAK_REALM` env vars (defaults matching `gateway/application.yml`'s own `issuer-uri`
  default) added to `vite-env.d.ts`. `Login.tsx`/`SignUp.tsx`/`VerifyOtp.tsx`/the existing
  state-token `AuthCallback.tsx`/`authService.startOAuth()` (Google/Facebook) are untouched and
  still call now-dead endpoints — see `gui/CLAUDE.md`'s updated known-gap note.

- **Regular user login (`Login.tsx`) now actually works too — via direct password grant (ROPC),
  deliberately a different grant type than admin's Authorization Code + PKCE above, for learning
  purposes.** `Login.tsx` previously called `authApi.login` → `POST /api/v1/auth/login`, the same
  dead `identity-service` endpoint the admin flow used to call — always 404'd. Fixed by adding
  `authService.loginWithPassword(email, password)`, which `POST`s Keycloak's token endpoint
  directly with `grant_type=password` against a **new, separate** Keycloak client
  (`gui-password-login` in `docker/keycloak/realm-export.json` — `publicClient: true`,
  `directAccessGrantsEnabled: true`, `standardFlowEnabled: false`, no redirect URIs), kept apart
  from the `gui` client so the admin PKCE flow's client never gains password-grant capability.
  `authApi.ts`'s dead `login` method was removed outright (no longer called from anywhere).
  The "Keycloak token response → `AuthTokens`" adaptation (decode access-token claims, derive
  `role`) was extracted out of `adminAuthService.ts` into new shared
  `features/auth/utils/keycloakClaims.ts#claimsToAuthTokens`, since both this flow and the admin
  one now need the identical step regardless of which grant type produced the tokens.
  `authService.logout()` was fixed alongside login the same way `adminAuthService.logout()` was —
  RP-initiated logout against Keycloak's end-session endpoint instead of the old dead
  `POST /api/v1/auth/logout`. `SignUp.tsx` (register would need a privileged server-side call to
  Keycloak's Admin REST API to create a user — ROPC can only authenticate an existing one),
  `VerifyOtp.tsx`, and both pages' Google/Facebook buttons remain untouched and still broken —
  separate, explicitly out-of-scope follow-ups.

- **User registration (`SignUp.tsx`) now actually works — via a real `identity-service` endpoint
  calling Keycloak's Admin REST API server-side.** Unlike login, registration can't be fixed
  client-side: Keycloak's token endpoint only authenticates an *existing* user, so creating one
  needs a privileged, confidential call — never something the browser can hold credentials for.
  New **`identity-service-admin`** Keycloak client (`docker/keycloak/realm-export.json`) —
  confidential, `serviceAccountsEnabled: true`, service account granted `manage-users` on the
  built-in `realm-management` client — kept entirely separate from `gui`/`gui-password-login`,
  since this one carries real administrative capability and must never be reachable from a
  browser. `identity-service` gained: `org.keycloak:keycloak-admin-client` dependency (version
  pinned to match the `quay.io/keycloak/keycloak:26.0` server image via new root `pom.xml`
  property `keycloak-admin-client.version`); `config/KeycloakAdminProperties`/`KeycloakAdminConfig`
  (a `Keycloak` admin-client bean authenticated via `client_credentials`); `dto/auth/RegisterRequest`/
  `RegisterResponse` (the latter matching `gui`'s existing `RegisterResponse` shape exactly);
  `exception/IdentityErrorCode` (`EMAIL_ALREADY_EXISTS`/`KEYCLOAK_USER_CREATE_FAILED`, this
  module's first `ErrorCode` enum); `security/service/KeycloakAdminService`(`Impl`) — builds a
  `UserRepresentation` (`username` = `email`, per the realm's `registrationEmailAsUsername: true`)
  and calls `keycloak.realm(...).users().create(...)`, mapping Keycloak's own 409 response to
  `EMAIL_ALREADY_EXISTS`. `AuthApi`/`AuthController` gained back a `register` endpoint (present
  before the Keycloak migration, deleted alongside it, now reintroduced with a different
  implementation — server-side Admin API call instead of local password hashing); `SecurityConfig`
  gained a `permitAll` rule scoped to `POST /api/v1/auth/register` specifically (every other
  endpoint on this module still requires a token — a brand-new user has none yet, which is the one
  exception). **Found and fixed during manual testing:** `gateway` enforces its own, separate
  security filter chain on every path it proxies — `identity-service`'s own `permitAll` rule for
  this path wasn't sufficient on its own, since `gateway`'s `SecurityConfig` (`security/SecurityConfig.java`)
  rejected the unauthenticated request with a `401` before it ever reached `identity-service`
  (confirmed via `identity-service`'s own access log showing nothing for this request at all).
  `gateway`'s `SecurityConfig` gained the identical `permitAll` carve-out for
  `POST /api/v1/auth/register`. This 401 also silently hijacked the frontend: `httpClient.ts`'s
  401-handler didn't exclude `/auth/register` from its "session expired, clear storage, redirect to
  `/login`" branch (only `/auth/refresh`/`/auth/login` were excluded) — fixed by adding
  `/auth/register` to that exclusion too, so any future failure on this endpoint surfaces as a
  normal error in `SignUp.tsx` instead of a confusing silent redirect. **Scope decision:** new accounts are created already `enabled`/`emailVerified: true`
  rather than gated on Keycloak's real "Verify Email" required action — `SignUp.tsx` calls the new
  endpoint, then `authService.loginWithPassword(email, password)` (the same ROPC call `Login.tsx`
  already uses) to log the user in immediately, rather than identity-service also performing a
  login grant on the new user's behalf. `VerifyOtp.tsx` remains unreferenced and untouched either
  way. `authApi.ts`'s `register` return type changed from `AuthTokens` to `RegisterResponse` to
  match.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 1 — the crash-safe `Payment` data model
  (US-4.2), per `docs/user-stories/04-payments.md`.** New `entity/Payment` — one payment attempt
  per `Order`, written with a new `PaymentStatus.PENDING` **before**
  `payment.PaymentGatewayPort#charge` is ever called, inside the same transaction as Epic 3's own
  `orderstatus.PaymentHandoffService#startPaymentProcessing` order transition — exactly what Epic
  3's reconciliation job (US-3.4) already queries against on a crash, now with a real row behind
  it instead of just `Order.idempotencyKey` alone. Real `@ManyToOne` FK to `Order` (same
  reasoning as `CouponRedemption`'s own FK — an `Order` row is permanent, with no delete path
  anywhere in this module); `idempotencyKey` is denormalized from `Order.idempotencyKey` (unique)
  so a future webhook handler can find this row without loading its `Order` too.
  `gatewayReference`/`failureCategory`/`gatewayFailureMessage` are all nullable and added now but
  stay unpopulated until later phases (a real gateway adapter's own charge/PaymentIntent id;
  US-4.7's decline-category mapping) — the full row shape was added in one pass, the same way
  every one of Epic 3's own `Order` columns was, since Epic 4's own user stories already specify
  it end to end. New `enums/PaymentStatus` (`PENDING`/`SUCCEEDED`/`DECLINED`/`REFUNDED` —
  deliberately not the same enum as `payment.PaymentOutcome` despite the vocabulary overlap, see
  its own Javadoc) and `enums/PaymentFailureCategory` (`INSUFFICIENT_FUNDS`/`CARD_DECLINED`/
  `GATEWAY_ERROR`). New `repository/PaymentRepository` — `findByOrderId` (correct today since
  this module's one-shot charge flow never retries a declined charge; not schema-enforced, since a
  future retry flow would legitimately need more than one row per order) and
  `findByIdempotencyKey` (the future webhook-correlation lookup); neither has a caller yet. New
  migration `202609020010__0.0.2__DKP-0048__add_payment_table.sql` — `PAYMENT` table/sequence, FK
  onto `CUSTOMER_ORDER`, a unique constraint on `IDEMPOTENCY_KEY`, `CHECK`s on `STATUS`/
  `FAILURE_CATEGORY`/`AMOUNT > 0`, and an index on `CUSTOMER_ORDER_ID`.
  `scripts/purge-seed-data.sql` gained `ecommerce.PAYMENT` to its `TRUNCATE` list (18 tables total
  now) — same "real activity, no seeder of its own" reasoning as `SAVED_ADDRESS`/
  `COUPON_REDEMPTION`. **Not built in this phase**: no `PaymentGatewayPort.refund`, no
  `MockPaymentGateway`/`StripePaymentGateway`, nothing writes/reads a `Payment` row from any
  service/handler code yet, no `EcommerceErrorCode.PAYMENT_*` codes. See
  `ecommerce-service/CLAUDE.md`'s new Epic 4 section for the full detail and the confirmed shape
  for later phases (Stripe webhook exposed directly on this service's own origin, bypassing
  `gateway`; real `stripe-java` SDK against Stripe's test-mode API, not a structural-only adapter).

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 2 — the gateway abstraction, a GoF Strategy pair
  (`MockPaymentGateway`/`StripePaymentGateway`) behind Epic 3's existing `PaymentGatewayPort`
  Adapter seam (US-4.1).** `PaymentGatewayPort` gained `refund(gatewayReference, amount)` —
  deliberately keyed by the gateway's own charge/PaymentIntent id, not this module's internal
  `paymentId` (the gateway itself has no notion of our numeric PK). `charge`/`checkStatus` now
  return a new `payment.PaymentResult` record (`outcome`/`gatewayReference`/`failureCategory`/
  `gatewayFailureMessage`) instead of a bare `PaymentOutcome` — widens Epic 3's original return
  type with what Phase 3 will need to persist onto `Payment`, without yet touching persistence
  itself; the three existing callers (`OrderServiceImpl#initiatePayment`,
  `OrderReconciliationJob`, and `PaymentHandoffService#resolvePayment`, whose own signature is
  unchanged) were updated mechanically to unwrap `.outcome()`. New `payment.RefundOutcome`/
  `RefundResult` mirror `PaymentOutcome`/`PaymentResult` for the refund vocabulary (deliberately a
  separate enum/record — a refund failure isn't a charge decline). New
  `payment.PaymentGatewayException` (unchecked) — thrown only for a genuine gateway/network/API
  error, never for a card decline, so a transient outage during `initiatePayment` leaves the order
  safely `PAYMENT_PROCESSING` for reconciliation rather than being force-failed.
  `payment.MockPaymentGateway` is the new default active bean
  (`app.ecommerce.payment.gateway=mock`) — deterministically declines one magic amount (`13.13`)
  instead of Epic 3's old `NoOpPaymentGatewayPort`'s unconditional success; that placeholder class
  (and its test) were deleted outright, superseded by this class and `payment.StripePaymentGateway`
  — a real adapter making genuine `stripe-java` SDK calls against Stripe's test-mode API
  (`app.ecommerce.payment.gateway=stripe` + a real `STRIPE_SECRET_KEY`). No real checkout UI
  collects a card anywhere in this reactor yet, so a configurable
  `app.ecommerce.payment.stripe.test-payment-method-id` (default `pm_card_visa`, one of Stripe's
  own built-in test-mode PaymentMethod ids) stands in for one. A synchronous decline surfaces as a
  thrown `CardException`, translated via a new `categorize(StripeError)` helper (Stripe's own
  `decline_code`/`code` → `PaymentFailureCategory`) — a deliberate consolidation of most of
  US-4.7's mapping work into this phase, since it's squarely the Adapter's own translation duty;
  the later Phase 7 is now mostly just exposing the already-populated category over REST.
  **`checkStatus` has no native Stripe endpoint to call** — it replays the exact same `charge`
  request under the same `Idempotency-Key` header, and Stripe returns its original cached response
  instead of repeating the operation — the one concrete reason this class depends on
  `PaymentRepository` (to recover the original `amount` from the `Payment` row Phase 1 wrote).
  New `stripe-java` Maven dependency (version-managed in the root `pom.xml`, declared only in
  `ecommerce-service`'s own `pom.xml`, same "no second consumer yet" precedent as
  `owasp-java-html-sanitizer`); `docker-compose.apps.yml` gained matching `PAYMENT_GATEWAY`/
  `STRIPE_SECRET_KEY` passthrough env vars (both default to the safe no-credentials-needed mock
  path). New `MockPaymentGatewayTest`; `StripePaymentGateway` has no dedicated unit test (a real
  external SDK/API with no local test harness, left unverified-at-runtime like this module's other
  Docker-dependent code). **295 unit tests total** (up from 293), verified via a real `mvn test`
  run (JDK 21); a targeted `mvn -pl ecommerce-service -am compile` also confirmed the new SDK usage
  compiles against the real dependency, catching one real API mismatch along the way
  (`StripeError.getPaymentIntent()` returns a `PaymentIntent` object, not a bare `String` id). See
  `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full detail.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 3 — `Payment` persistence wired into the
  synchronous charge-confirmation flow (US-4.2/4.3).**
  `orderstatus.PaymentHandoffService.startPaymentProcessing` now writes the `PENDING` `Payment`
  row (order, amount snapshotted from `Order.getTotal()`, denormalized idempotency key) in the
  same transaction as the order's own `PENDING -> PAYMENT_PROCESSING` transition — fulfilling what
  `Payment`'s own Phase 1 Javadoc promised. `resolvePayment`'s signature changed from
  `(orderId, PaymentOutcome)` to `(orderId, PaymentResult)`: a `SUCCEEDED`/`DECLINED` result now
  also updates that same `Payment` row's `status`/`gatewayReference` (and, for a decline,
  `failureCategory`/`gatewayFailureMessage`) in the same transaction as the order's
  `CONFIRMED`/`FAILED` transition; a `PENDING` result still leaves both the order and the row
  untouched. The `Payment` row's own `status` always reflects what the gateway actually decided,
  independent of the order's own final status — a queued cancel racing a gateway success still
  records `SUCCEEDED` (the money really was captured), even though the order itself ends up
  `CANCELLED` with a restock; Phase 6 (refund on cancellation) is what will turn that row into a
  real `REFUNDED` one. A missing `Payment` row at resolve time throws a plain
  `IllegalStateException` — a genuine invariant violation, not a business error, rolling back the
  order transition alongside it rather than leaving a corrupted trail. Both existing callers
  (`OrderServiceImpl#initiatePayment`, `OrderReconciliationJob`) needed only a one-line change each
  to pass the whole `PaymentResult` instead of unwrapping `.outcome()` first.
  `PaymentHandoffServiceTest`/`OrderServiceImplTest`/`OrderReconciliationJobTest` updated for the
  new signature; `PaymentHandoffServiceTest` gained a new missing-row-invariant case plus richer
  assertions on the persisted `Payment` row's fields. **296 unit tests total** (up from 295),
  verified via a real `mvn test` run (JDK 21). Not built yet: `EcommerceErrorCode.PAYMENT_*` codes;
  the webhook path (Phase 5). See `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full
  detail.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 4 — outbox publishing on a determined payment
  outcome (US-4.4), with a deliberate design deviation flagged and confirmed before building.**
  This module already had a precedent the *opposite* way: it chose not to publish an
  `ORDER_CREATED` outbox event when checkout was first built, specifically because nothing
  consumed it yet (an event with no registered handler just retries 5× and dies `FAILED`). Asked
  whether payment outcomes should follow that same deferral or get a real, if lightweight,
  consumer instead — chose to publish, with a placeholder consumer, since US-4.4/US-4.6 name
  `PAYMENT_SUCCEEDED`/`PAYMENT_FAILED`/`PAYMENT_REFUNDED` as real acceptance criteria, not a
  speculative nice-to-have. `enums.OutboxAggregateType` gained `PAYMENT` (migration `DKP-0049`,
  widening `CKC_OUTBOX_EVENT_AGGREGATE_TYPE`). New `orderstatus.PaymentSucceededOutboxEventHandler`/
  `PaymentFailedOutboxEventHandler`/`PaymentRefundedOutboxEventHandler` — each a deliberate,
  documented placeholder consumer (a structured audit log line, no real email/Slack/analytics
  integration exists yet, same spirit as Epic 3's own deleted `NoOpPaymentGatewayPort`);
  `PaymentRefundedOutboxEventHandler` has no publisher yet (Phase 6's job).
  `orderstatus.PaymentHandoffService#resolvePayment` now publishes `PAYMENT_SUCCEEDED`/
  `PAYMENT_FAILED` in the same transaction as the `Payment` row's own status update (`aggregateId`
  is the `Payment` row's own id, not the order's). New tests for the outbox-publish behavior and
  the three new handlers. **305 unit tests total** (up from 296), verified via a real `mvn test`
  run (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full detail.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 5 — Stripe webhook handling (US-4.5), realizing
  the confirmed shape from earlier phases: exposed directly on this service's own origin, never
  proxied by `gateway`.** New top-level `POST /webhooks/stripe` (not under `/api/v1/**` — Stripe's
  own server calls it, never an end-user/admin client, carrying no JWT); `security.SecurityConfig`
  gained a `permitAll()` rule for `/webhooks/**` for the same reason `content-service`'s own
  `/internal/**` is, though verification itself happens inside the handler (Stripe's HMAC
  signature needs the raw request body, not just a header, so a header-only filter like
  `InternalApiKeyFilter` doesn't fit). `gateway`'s own `GatewayRoutesConfig`/`CLAUDE.md` and root
  `CLAUDE.md`'s Routing section all gained a note that this path is deliberately never routed.
  New `entity.StripeWebhookEvent` — the at-least-once-delivery dedup ledger (`stripeEventId`
  unique), its own small table since a single `Payment` can legitimately receive more than one
  distinct Stripe event over its lifetime. `Payment.gatewayReference` gained a partial unique
  index (`WHERE ... IS NOT NULL`) — the webhook's own correlation key back to exactly one row; new
  `PaymentRepository.findByGatewayReference`. Migration `DKP-0050`. New
  `payment.StripeFailureCategoryMapper` — the Stripe decline-code mapping **extracted out of**
  `StripePaymentGateway` (its Phase 2 home) once the webhook needed the identical translation for
  an async decline, so both call sites now share one source of truth. New
  `webhook.StripeWebhookService` — one `@Transactional handleWebhook` method verifies the
  signature then delegates to a package-private `applyPaymentIntentEvent(...)` taking plain Java
  primitives/enums (not Stripe's own SDK types) for the actual dedup/correlate/resolve logic, which
  is what makes it unit-testable; atomicity comes from Spring's transaction propagation —
  `applyPaymentIntentEvent` calls `orderstatus.PaymentHandoffService#resolvePayment` (a different
  bean's own `@Transactional` method), which joins the already-open transaction rather than
  starting a new one, so the dedup insert, the `Payment`/`Order` update, and the outbox publish
  (Phase 4) all commit or roll back together — reusing Phase 3/4's transactional logic entirely
  rather than duplicating it. New `api.StripeWebhookApi`/`StripeWebhookController` — `payload`
  bound as `byte[]`, not `String`, so no message converter re-encodes the bytes before signature
  verification. New `app.ecommerce.payment.stripe.webhook-secret`
  (env var `STRIPE_WEBHOOK_SECRET`); `docker-compose.apps.yml` gained a matching passthrough.
  `scripts/purge-seed-data.sql` gained `ecommerce.STRIPE_WEBHOOK_EVENT` (19 tables total now). New
  `StripeWebhookServiceTest` (4 cases). **309 unit tests total** (up from 305), verified via a real
  `mvn test` run (JDK 21); a targeted `mvn -pl ecommerce-service -am compile` confirmed the new
  Stripe SDK usage (`Event`/`Webhook`/`SignatureVerificationException`) compiles against the real
  dependency. See `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full detail.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 6 — refund on cancellation (US-4.6), scoped to
  this user story's own literal acceptance criterion.** `orderstatus.PaymentHandoffService` gained
  the identical durable-step/gateway-call/durable-step shape Phase 3 already established for
  charges, applied to refunds: `applyCancellation(orderId, callerUuid)` (transitions the order,
  then reports whether a refund is owed via a new nested `CancellationResult` record) and
  `applyRefundResult(paymentId, result)` (`SUCCEEDED` → `Payment.status = REFUNDED` +
  `PAYMENT_REFUNDED` outbox publish — finally giving Phase 4's `PaymentRefundedOutboxEventHandler`
  its publisher; `FAILED`/`PENDING` just log, leaving the row `SUCCEEDED`).
  `service.impl.OrderServiceImpl#cancel` is no longer `@Transactional` (same reason
  `initiatePayment` isn't): it calls `applyCancellation`, then, only if a refund is owed, calls
  `payment.PaymentGatewayPort#refund` outside any transaction, then `applyRefundResult`. **No new
  intermediate status/durable "refund in flight" marker was added** — `StripePaymentGateway
  #refund`'s own idempotency key is deterministic (derived from `gatewayReference`, not a fresh
  key per call), so retrying the whole operation after a crash can never double-refund at the
  gateway, unlike a charge retried with a fresh key — this asymmetry is why the simpler shape is
  correct here, not a corner cut. **Deliberately does not cover the rarer race in
  `PaymentProcessingOrderStatusHandler#confirmPayment`** (a cancel queued while payment is still
  processing, that then loses the race to a gateway success moments later) — US-4.6's own
  acceptance criterion is a shopper cancelling an already-`CONFIRMED` order, not this narrower
  race; both handlers' own Javadoc/`OrderStatusHistory` reason strings were corrected to say so
  precisely instead of the stale "deferred to Epic 4" wording (Epic 4's gateway integration exists
  now — it's just not wired to this one path). `OrderServiceImplTest.Cancel` rewritten around the
  new orchestration; new `PaymentHandoffServiceTest.ApplyCancellation`/`ApplyRefundResult` nested
  classes. **317 unit tests total** (up from 309), verified via a real `mvn test` run (JDK 21). See
  `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full detail.

### Added (cont.)

- **`ecommerce-service`: Epic 4 (Payments) Phase 7 — user-facing failure reasons (US-4.7), a thin
  REST-exposure phase since the actual decline-code mapping was already built in Phase 2.**
  `enums.PaymentFailureCategory` gained a constructor-supplied `shopperMessage` field per constant
  (mirroring `common.exception.ErrorCode`'s own `getCode()`+`getMessage()` convention) — a short,
  server-owned, non-technical sentence per category, never the gateway's own raw decline string.
  `dto.OrderResponse` gained `paymentStatus`/`paymentFailureCategory`/`paymentFailureMessage`,
  resolved by a new best-effort live lookup in `mapper.OrderMapper#toResponse` (a new
  `PaymentRepository` dependency on that mapper) — all three `null` until a payment attempt has
  actually started. Reaches the shopper through the same three endpoints that already returned
  `OrderResponse` (`GET /api/v1/orders`, `GET /api/v1/orders/{id}`, and notably
  `POST /api/v1/orders/{id}/pay`'s own synchronous response, so a declined card is explained
  immediately at checkout) — no new endpoint needed. A pleasant side effect: `paymentStatus` also
  now surfaces `REFUNDED` after Phase 6's cancellation flow. No new unit tests —
  `mapper.OrderMapper` has no dedicated test suite (matches this module's existing convention for
  its hand-written mappers), and the new logic is a simple null-guarded field read. 317 unit tests
  total, unchanged; verified via a real `mvn -pl ecommerce-service -am compile`+`test` run (JDK
  21). See `ecommerce-service/CLAUDE.md`'s Epic 4 section for the full detail.

### Added (cont.)

- **`gui`: Epic 4 (Payments) Phase 8 — payment status/failure-reason wiring, closing out Payments
  in full (all 8 phases now built).** `types.ts` gained `PaymentStatus`/`PaymentFailureCategory`
  unions and three matching nullable fields on `Order`. `OrderDetailPage.tsx`'s `handlePay` FAILED
  branch now shows the real `paymentFailureMessage` instead of a hardcoded generic string. Since a
  decline (or a refund) can also arrive asynchronously — outside this page's own `handlePay` call
  entirely, via the Stripe webhook or the reconciliation job — a toast alone isn't enough: a
  persistent `Alert severity="error"` now renders whenever `status === 'FAILED' &&
  paymentFailureMessage`, and a `severity="success"` one whenever `paymentStatus === 'REFUNDED'`
  (Epic 4 Phase 6's own outcome, previously invisible anywhere in the GUI since `order.status`
  alone stays `CANCELLED` either way). `OrderHistoryPage.tsx`'s own order cards get a compact,
  one-line version of the same failure reason for the identical reason. Verified via a clean
  `tsc --noEmit` and a successful `vite build`; no Docker in this sandbox, so the actual banners
  are unverified in a real browser. See `gui/CLAUDE.md`'s own note for the full detail.

### Added (cont.)

- **`gui`: Epic 4 Phase 8's admin half — a small payment-status `Chip` on `AdminOrderListPage.tsx`,
  per request, after confirming scope first.** Deliberately not the full `Alert` treatment the
  shopper-facing pages got — this table defaults to `CONFIRMED` (already-paid) orders and has no
  per-order admin detail page, so the payment fields only help while triaging non-`CONFIRMED` rows.
  An extra `Chip` renders only when `paymentStatus` carries information the existing order-status
  chip doesn't already: `DECLINED` (a `Tooltip` carrying `paymentFailureMessage` — the order chip
  already says "Payment Failed", so this just adds the *why*) and `REFUNDED` (genuinely new, since
  `order.status` stays `Cancelled` either way, tooltipped with the refunded amount).
  `SUCCEEDED`/`PENDING` render nothing extra. Verified via a clean `tsc --noEmit` and a successful
  `vite build`. See `gui/CLAUDE.md`'s own note for the full detail.

### Added (cont.)

- **Epic 4 Phase 8 follow-up (per request) — real card collection via Stripe Elements, replacing
  `StripePaymentGateway`'s hardcoded test PaymentMethod.** Chosen over a Stripe Checkout
  hosted-redirect alternative after a pros/cons comparison: it slots into this reactor's existing
  synchronous charge shape (collect a card, POST it, get a resolved `Order` back) without
  restructuring `OrderDetailPage`'s "Pay Now" flow into a redirect/return round trip.
  - **`ecommerce-service`:** `payment.PaymentGatewayPort#charge` gained a `paymentMethodId`
    parameter (the real gateway PaymentMethod id, e.g. Stripe's `pm_...`), threaded through
    `orderstatus.PaymentHandoffService#startPaymentProcessing`/`service.OrderService#initiatePayment`.
    New `dto.PayOrderRequest` — an optional `@RequestBody` on `OrderApi#pay`, so existing
    curl/Postman-driven testing of that endpoint keeps working unchanged. `MockPaymentGateway`
    ignores the new parameter; `StripePaymentGateway#charge` uses it when present, falling back to
    `test-payment-method-id` when absent. New `entity.Payment#paymentMethodId` column
    (`PAYMENT_METHOD_ID`, nullable — migration
    `202609030001__0.0.2__DKP-0051__add_payment_payment_method_id.sql`), read back by
    `StripePaymentGateway#checkStatus` so its idempotent-replay reproduces the exact original
    request, payment method included.
  - **New `GET /api/v1/public/payment-config`** (`api.PaymentConfigApi`/`api.impl
    .PaymentConfigController`, `dto.PaymentConfigResponse`) — permit-all, reports the active
    `PaymentGatewayPort` strategy and, only when it's `stripe`, the matching publishable key (new
    `app.ecommerce.payment.stripe.publishable-key`/`STRIPE_PUBLISHABLE_KEY` property) — a single
    server-side source of truth for whether `gui`'s checkout should render Stripe Elements.
    `gateway/routing/GatewayRoutesConfig` gained a matching `/api/v1/public/payment-config` route
    in the same change.
  - **`gui`:** new `@stripe/stripe-js`/`@stripe/react-stripe-js` dependencies. New
    `api/paymentConfigApi.ts` (fronts the endpoint above) and
    `components/orders/PaymentMethodDialog.tsx` — a `CardElement`-based dialog (not the newer
    `PaymentElement` deferred-payment flow, which needs an upfront `clientSecret`/`mode`/`currency`
    this reactor's synchronous charge shape doesn't have) that hands a `pm_...` id back to
    `OrderDetailPage.tsx`. `OrderDetailPage.tsx` fetches the payment config once on mount and opens
    this dialog instead of paying directly whenever `gateway === 'stripe'`; `orderApi.pay` gained
    an optional `paymentMethodId` parameter, posted only when supplied — a mock-gateway `pay()`
    call is unchanged from before this follow-up.
  - **Not built, explicitly flagged rather than silently cut**: full 3D Secure/SCA handling
    (Stripe's `requires_action` status still maps to `PaymentOutcome.PENDING`/reconciliation, never
    an interactive client-side challenge) — none of this reactor's test PaymentMethod ids trigger
    it, so it doesn't block local testing.
  - **319 unit tests total** (up from 317), verified via a targeted
    `-pl ecommerce-service,gateway -am compile`+`test` run (JDK 21) and, for `gui`, a clean
    `tsc --noEmit` + successful `vite build` — no Docker in this sandbox, so the actual Elements
    card form is unverified in a real browser. See `ecommerce-service/CLAUDE.md`'s and
    `gui/CLAUDE.md`'s own notes for full detail.

### Added (cont.)

- **`gui`: ecommerce-feature style-consistency audit + cleanup, per request.** A full read-through
  of every page/component under `features/ecommerce/` (pages + components, not api/hooks/utils)
  found several genuine same-role-rendered-differently inconsistencies — a full-page loading
  spinner duplicated byte-for-byte across 7 pages, two competing "boxed panel" visual languages
  (bordered `Paper variant="outlined"` vs. a borderless `Box` with `bgcolor: 'background.paper'`
  and a differently-rounded corner) with no semantic rule distinguishing them, a dialog
  primary-submit button duplicated across all 6 admin CRUD dialogs plus 4 more near-duplicates with
  a drifting, undocumented spinner size (16/18/20/24px), and a centered empty/"not found" state
  rendered at three different completeness levels for the same role. Four new shared components
  extracted to fix the pure-duplication findings:
  - **`shared/components/FullPageLoader.tsx`** — the centered 50vh spinner; replaces the identical
    inline block in `AddressBookPage`/`ProductFormPage`/`CartPage`/`CheckoutPage`/
    `OrderDetailPage`/`OrderHistoryPage`/`ProductDetailPage`.
  - **`shared/components/EmptyState.tsx`** — icon/title/description/action, for an empty list or a
    "not found" detail page alike; replaces the ad hoc versions in the same pages above, and closes
    the completeness gap on `OrderDetailPage`'s ("Order not found") and `ProductDetailPage`'s
    ("Product not found") own states, which previously rendered without the icon/wrapper treatment
    every other instance already had.
  - **`shared/components/SubmitButton.tsx`** — the "contained button that swaps its label for a
    spinner while saving" pattern; `spinnerSize` now derives from `size` (24 for a `"large"` CTA,
    16 otherwise) instead of being picked ad hoc per call site. Wired into all 6 admin CRUD dialogs
    (`AddressFormDialog`/`CouponFormDialog`/`ProductAttributeFormDialog`/
    `ProductCategoryFormDialog`/`ProductTagFormDialog`/`ProductVariantDialog`),
    `CouponPickerDialog`'s Apply button, `PaymentElementForm`'s Pay button, `CheckoutPage`'s
    "Place Order & Pay" submit, `OrderDetailPage`'s "Pay Now", and `ProductFormPage`'s Save/Create.
  - **`features/ecommerce/components/common/SectionPanel.tsx`** — the shared bordered-panel
    wrapper (`Paper variant="outlined"` plus an optional title/action header row), standardizing on
    the bordered look as the one convention (already the theme's own default `Paper` styling and
    already the majority usage) over the borderless variant. Applied to `AddressBookPage`'s list
    panel, `CartPage`'s two panels, `ProductDetailPage`'s gallery/info and description panels
    (fixing the actual bordered-vs-borderless inconsistency), plus — as a zero-visual-change dedup
    — `ProductFormPage`'s Basic Info/Organization/Tags panels, `ShopPage`'s Categories/Price/
    Attributes sidebar panels, `ProductImageGallery`, `ProductVariantEditor`, and
    `ProductImageStager` (all of which already used the identical bordered-panel-plus-title
    markup). **Deliberately left untouched**: `CheckoutPage`/`OrderDetailPage`'s own `Paper`
    panels, which use a different title convention (`subtitle1`/600 vs. this component's
    `subtitle2`/700) — folding those in would have silently changed their heading weight/size as a
    side effect of a "pure dedup" pass, so that split stays a separate, still-open style decision
    rather than one resolved implicitly here.
  - A handful of lower-priority/judgment-call findings from the same audit were deliberately **not**
    acted on in this pass (need a product decision, not just deduplication): `ShopPage`'s
    `maxWidth: 1400` page-container width vs. Cart/Checkout/ProductDetail's `width: '80%'`
    (resolved in a follow-up, below); `ShopPage`'s `h4` page title vs. every other page's `h5`; the
    page-level secondary/"Cancel" button's `outlined` (`ProductFormPage`) vs. plain-text
    (`CartPage`'s "Continue Shopping") variant; `ProductDetailPage`'s three large CTAs overriding
    the theme's own `sizeLarge` default padding; and the CRUD-dialog vs. picker-dialog close-button
    (✕) split. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker
    in this sandbox, so the actual visual result (in particular the border/radius change on the
    three migrated borderless panels) is unverified in a real browser.

### Added (cont.)

- **`gui`: ecommerce style-audit follow-up — unified the four shopper-facing top-level pages
  (`ShopPage`, `CartPage`, `CheckoutPage`, `ProductDetailPage`) onto one page-width convention, per
  request.** Three of the four used plain `width: '80%'` (unbounded growth on very wide monitors —
  80% of a 3840px screen is ~3072px, stretching form fields/line-item rows/gallery+info panels
  uncomfortably wide); `ShopPage` alone used a flat `maxWidth: 1400` (no fluid behavior on narrower
  screens). Landed on a hybrid rather than picking one side: `width: '80%'` capped at
  `maxWidth: 1400` — behaves identically to today's plain `80%` on anything under ~1750px wide, and
  only engages the cap on genuinely wide monitors. New
  `features/ecommerce/components/common/WideContentContainer.tsx` (children + optional `sx`
  override, always includes the page's own `p: 3`) — not app-wide, since this "wide storefront
  page" role is specific to these four shopper-facing pages; the three `AccountLayout`-nested pages
  (`AddressBookPage`/`OrderHistoryPage`/`OrderDetailPage`) are explicitly out of scope (that layout
  already applies its own width cap). `ShopPage`'s own page title (`h4` vs. every other page's
  `h5`) was a separate, still-undecided finding from the original audit at the time — resolved in
  a further follow-up, below. Verified via a clean `tsc --noEmit` and a successful `vite build`
  only — no Docker in this sandbox, so the actual visual result on a wide monitor is unverified in
  a real browser.

### Changed (cont.)

- **`gui`: ecommerce style-audit — remaining judgment-call findings resolved one at a time, per
  request.**
  - **`ShopPage.tsx`'s page title changed from `h4` to `h5`** — matches every other ecommerce
    page's page-title convention exactly; no other ecommerce page used `h4` for this role.
  - **`CartPage.tsx`'s "Continue Shopping" button changed from the plain text variant to
    `variant="outlined"`**, matching `ProductFormPage.tsx`'s own page-level "Cancel" button — both
    are a page-level secondary action sitting next to a contained primary button, and an outline
    reads more clearly as an equally-weighted action at that prominence than plain text (which is
    reserved for a *dialog*-level Cancel in this app's own convention).
  - **`ProductDetailPage.tsx`'s three large CTAs ("Add to Cart"/"Buy Now"/"Log in to buy") had
    their custom `sx={{ px: 4, py: 1.25, fontSize: '1rem', fontWeight: 600 }}` override removed
    outright** — reverted to the plain theme `sizeLarge` default (`padding: '7px 18px'`,
    `fontWeight: 600` is already the theme's own global `MuiButton` default), matching every other
    `size="large"` button in the app (`CheckoutPage`'s "Place Order", `OrderDetailPage`'s
    "Pay Now"). No visual intent lost — the override's only non-default value was the padding.
  - **The CRUD-dialog vs. picker-dialog close-button (✕) split was confirmed as the intended
    convention, not a bug — no code change.** The 6 admin CRUD dialogs (short forms, Cancel always
    visible without scrolling) rely solely on their `DialogActions` Cancel button; `
    CouponPickerDialog`/`PaymentDialog` (longer/scrollable content) additionally carry a `CloseIcon`
    in their `DialogTitle`. Documented explicitly in `gui/CLAUDE.md` so this reads as a deliberate
    rule going forward, not an accidental omission on 6 dialogs.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual visual result is unverified in a real browser.

### Added (cont.)

- **`gui`: style-consistency audit extended to the 7 remaining feature folders** (`ai`, `auth`,
  `chat`, `content`, `friends`, `messaging`, `tasks`), per request — checking both each folder's
  own internal consistency and direct adoption of the shared components already built for
  `@ecommerce` (`FullPageLoader`, `EmptyState`, `SubmitButton`). Good news first: most of these
  folders had already independently converged on the same conventions `@ecommerce` just
  standardized on (h5 page titles, `Paper variant="outlined"`, the short-form-dialog-gets-no-✕
  rule, identical dialog field/filter-row spacing) — nothing to fix there. Concrete findings acted
  on this pass (all direct wiring of `@shared/components/SubmitButton.tsx`/`FullPageLoader.tsx`,
  no new components needed):
  - **`FullPageLoader`** → `content/pages/QuestionAnswerFormPage.tsx` (byte-identical match).
  - **`SubmitButton`** → `content/components/CategoryFormDialog.tsx`,
    `content/components/TagFormDialog.tsx`, `content/pages/QuestionAnswerFormPage.tsx`,
    `tasks/components/ProjectFormDialog.tsx` (all byte-identical to the pattern already extracted
    from `@ecommerce`'s own CRUD dialogs), plus `auth/pages/Login.tsx`/`SignUp.tsx` (with
    `size="large"`, spinner size 24 falls out of `SubmitButton`'s own size-driven default
    automatically) and `auth/pages/ProfilePage.tsx`'s "Save" button.
  - **`SubmitButton` gained a new optional `startIcon` prop** to support `ProfilePage.tsx`'s "Save"
    button, which keeps its `SaveIcon` visible next to the spinner while saving (MUI's `startIcon`
    is a separate slot from `children`, so this couldn't be expressed via `label` alone) — passed
    straight through to the underlying `Button` unconditionally.
  - **`auth/pages/AdminLogin.tsx`'s "Sign In with Keycloak" button gained `size="large"`** while
    being wired onto `SubmitButton` — it already used a 24px spinner and `fullWidth`, identical to
    `Login`/`SignUp`'s own "primary sign-in CTA" role, but was missing the `size="large"` prop
    those two have; this was a drift, not a deliberate choice, so it's now fixed as part of the
    same wiring pass rather than preserved as-is.
  - **New findings not acted on in this pass** (need a further decision, same as `@ecommerce`'s own
    deferred items): promoting `@ecommerce`'s `TableStatusRow.tsx` to `gui/src/shared/components/`
    so `@content`'s 3 list pages (which duplicate its exact loading/empty `TableRow` pattern
    verbatim) can adopt it; a new shared component for the "centered spinner"/"centered empty
    message" pair duplicated identically 5× in `@friends` and near-identically in
    `@messaging/ConversationList.tsx`; the same hardcoded `rgba(0,0,0,0.45)` overlay color now
    found independently in both `@ecommerce/CouponFormDialog.tsx` and `@auth/ProfilePage.tsx`;
    `@ai/PipelineMetricsPage.tsx`'s `maxWidth: 1000` vs. `@ai/EmbeddingsPage.tsx`'s plain `p:3` for
    the same admin-page role; and `fontWeight="bold"` (string) vs. `fontWeight={700}` (number) on
    `@auth`'s 3 gate-page titles (cosmetic-only, renders identically). See `gui/CLAUDE.md`'s own
    note for the full per-file detail.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual visual result is unverified in a real browser.

### Changed (cont.)

- **`gui`: `TableStatusRow.tsx` promoted from `@ecommerce/components/` to
  `gui/src/shared/components/`, and `@content`'s 3 list pages wired onto it, per request.** The
  component itself is unchanged (no prop changes needed — it was already fully generic); only its
  location and import path moved (`@ecommerce`'s 6 existing importers —
  `ProductCategoryListPage`/`ProductTagListPage`/`ProductListPage`/`ProductAttributeListPage`/
  `CouponListPage`/`AdminOrderListPage` — updated from `'../components/TableStatusRow'` to
  `'@shared/components/TableStatusRow'`). `content/pages/CategoryListPage.tsx`/`TagListPage.tsx`/
  `QuestionAnswerListPage.tsx` each had their own hand-rolled loading-row/empty-row `<TableRow>`
  pair — character-for-character identical to what `TableStatusRow` already renders — replaced with
  the shared component; each dropped its own now-unused `CircularProgress` import as a result.
  **New code adding an admin list table anywhere in this app should use `@shared/components/
  TableStatusRow`, not hand-roll this pair again.** Verified via a clean `tsc --noEmit` and a
  successful `vite build` only — no Docker in this sandbox, so the actual visual result is
  unverified in a real browser.

### Added (cont.)

- **`gui`: new `shared/components/SectionStatus.tsx`, the `Box`-based sibling of `TableStatusRow`,
  per request.** Same "centered spinner while loading, centered message while empty, `null`
  otherwise" contract, for a plain list/section rather than a `<TableBody>` — extracted after the
  exact `Box sx={{py:6,textAlign:'center'}}` + `CircularProgress`/`Typography` pair turned up
  identically duplicated across `@friends`'s 5 list components
  (`UserSearch`/`FriendsList`/`BlockedUsersList`/`FriendRequestsOutgoing`/`FriendRequestsIncoming`)
  and near-identically in `@messaging/components/ConversationList.tsx` (`py:4`/`spinnerSize:24`
  instead of the majority's `py:6`/`28`, now expressed as overridable props with those exact
  defaults rather than copy-pasted values). Each of the 5 `@friends` files dropped its own
  now-unused `Box`-for-this-purpose/`CircularProgress`/`Typography` imports (`Box` itself is still
  imported in 4 of the 5 — still used elsewhere for an unrelated divider/layout role).
  **`UserSearch.tsx`/`FriendsList.tsx` needed care, not a mechanical swap**: both have a
  `loading && x.length === 0` first-branch condition (so a quiet background refetch with existing
  items keeps showing the list instead of flashing a spinner) rather than a plain `loading` check —
  collapsing their outer branch condition down to the same `visible.length === 0`/`x.length === 0`
  shape the other 3 files' simpler `if (loading && …) … if (x.length === 0) …` pair already reduces
  to (by absorption: `(loading && empty) || empty` is just `empty`) preserves this exactly; a naive
  `loading || isEmpty` outer condition (tried first) would have hidden the list during every
  background refetch, which was caught and fixed before verifying, not shipped and found later.
  **`ConversationList.tsx`'s empty-message `Typography` picked up `SectionStatus`'s own default
  (no explicit `variant`, i.e. `body1`)** — it previously set `variant="body2"` explicitly, smaller
  than every one of `@friends`'s 5 equivalents (which never set a variant, so already rendered at
  the default), so this is a deliberate additional normalization onto the majority's own size, not
  an accidental regression. **New code needing a centered loading/empty state outside a table
  anywhere in this app should use `@shared/components/SectionStatus`.** Verified via a clean
  `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the actual
  visual result is unverified in a real browser.

### Added (cont.)

- **`gui`: new `shared/components/UploadingOverlay.tsx`, per request.** Fixes the same hardcoded
  `bgcolor: 'rgba(0,0,0,0.45)'` overlay-tint literal found independently in
  `@ecommerce/CouponFormDialog.tsx` (a rectangular coupon-image thumbnail) and
  `@auth/pages/ProfilePage.tsx` (a circular avatar) — now `bgcolor: (theme) =>
  alpha(theme.palette.common.black, 0.45)`, theme-driven rather than a magic literal. The new
  component takes the overlay's content as `children` (a `CircularProgress` for both files'
  "uploading" state, a `PhotoCameraIcon` for `ProfilePage`'s separate hover-only "click to change
  photo" hint) plus `borderRadius` (`1` default for a rectangle, `'50%'` for `ProfilePage`'s
  circular avatar) and an optional `revealOnHover` flag (opacity `0`→`1` on hover, only used by
  `ProfilePage`'s hover-hint overlay — its "uploading" overlay and `CouponFormDialog`'s own overlay
  both stay always-visible, `revealOnHover`'s default). **New code needing a dark, centered-content
  overlay over an image/avatar mid-upload should use `@shared/components/UploadingOverlay`.**
  Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
  sandbox, so the actual visual result is unverified in a real browser.

### Changed (cont.)

- **`gui`: `@ai/pages/PipelineMetricsPage.tsx`'s `maxWidth: 1000` vs. `@ai/pages/EmbeddingsPage.tsx`'s
  plain `p:3` (no width cap) confirmed as intentional, not a bug — no code change, per request.**
  Checked each page's actual content shape before deciding: `EmbeddingsPage` is a table-based admin
  list, matching the "admin table page = plain `p:3`, no cap" convention used everywhere else in
  this app; `PipelineMetricsPage` is a KPI-dashboard page (a `Grid` of `KpiCard`s, 3-column at
  `md`) — a genuinely different page shape, where the width cap keeps its stat cards from
  stretching sparse and wide on an ultra-wide monitor. Documented in `gui/CLAUDE.md` so this
  doesn't get "fixed" into false consistency later.

### Changed (cont.)

- **`gui`: `fontWeight="bold"` (string) normalized to `fontWeight={700}` (number) on `@auth`'s 3
  gate-page titles (`Login.tsx`/`SignUp.tsx`/`AdminLogin.tsx`), per request.** Purely cosmetic —
  both render identically — but now matches the `fontWeight={700}` convention every other
  page-title `Typography` in this app already uses. This closes out every finding from the
  7-folder style-consistency audit (`ai`/`auth`/`chat`/`content`/`friends`/`messaging`/`tasks`).
  Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
  sandbox, so the actual visual result is unverified in a real browser.

### Changed (cont.)

- **`gui`: style-consistency audit extended to `app/` and `shared/` (the two folders outside the 8
  feature folders already covered), per request — 4 findings, all fixed.**
  - **`app/admin-shell/AdminDashboard.tsx`'s feature-card `Paper`s gained `variant="outlined"`** —
    the only `Paper` usage in the entire app (across every feature folder plus `app/`/`shared/`)
    that omitted it.
  - **`AdminDashboard.tsx`'s `maxWidth: 800` unified to `maxWidth: 1000`, matching
    `@ai/pages/PipelineMetricsPage.tsx`** — both are dashboard-shaped admin pages (a `Stack` of
    feature cards vs. a `Grid` of KPI cards) that had landed on two different arbitrary width caps
    with no shared rationale; 1000 was chosen since `PipelineMetricsPage`'s 3-column KPI grid
    already uses the extra room.
  - **`app/NavBar.tsx`'s 6 near-identical authenticated-only nav buttons (Account/Chat/Messages/
    Friends/Tasks/Cart) extracted into a local `NavButton` helper** (plus the public Shop button,
    via an added `sx` override prop for its one-off `mr: 0.5`) — `color="inherit" size="small"`, a
    `startIcon`, and an `action.selected` tint while active, previously copy-pasted 7×.
  - **Fixed a real (if currently harmless) bug while extracting `NavButton`**: the "is this route
    active" check was inconsistent — Friends/Tasks/Cart used an exact-match `isActive` helper
    (`pathname === path`), while Account/Chat/Messages inlined `location.pathname.startsWith(path)`
    directly. `NavButton` forces one shared answer; `isActive` was redefined to `startsWith` (the
    already-correct behavior for Account, which needs to stay highlighted across `/account/profile`,
    `/account/addresses`, etc.) and now backs all 7 buttons. Login/Logout deliberately stayed plain
    `Button`s, not migrated onto `NavButton` — neither has an "active route" concept to highlight.
  - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual visual result is unverified in a real browser.

### Fixed (cont.)

- **`ecommerce-service`: an explicit "Cancel Order" click during the Stripe Elements payment phase
  left the order stuck `PAYMENT_PROCESSING` forever, same root cause as the webhook-correlation fix
  above but a distinct symptom — reported directly after that fix.** `PaymentProcessingOrderStatusHandler
  .cancel` only ever queues `Order.cancelRequested` for a `PAYMENT_PROCESSING` order, on the
  (once-correct) assumption a gateway call was already in flight and would resolve momentarily —
  no longer true once client-side confirmation (Option A) can leave a charge unconfirmed
  indefinitely while the shopper just looks at the card form, and nothing ever consulted the queued
  flag for a `PENDING` outcome anyway. Fixed by actively cancelling the still-unconfirmed Stripe
  PaymentIntent at the gateway: new `payment.PaymentGatewayPort#cancelUnconfirmed`, a new
  `enums.PaymentStatus.CANCELLED` (migration `DKP-0051`, deliberately distinct from `DECLINED` so
  the GUI never shows a misleading "payment declined" reason on a shopper-initiated cancel), and a
  new `orderstatus.PaymentHandoffService#applyGatewayCancellation` durable step wired into
  `service.impl.OrderServiceImpl#cancel`. A cheaper, purely-local alternative (just check
  `cancelRequested` on the next `PENDING` poll, no gateway call) was considered and rejected — it
  would leave Stripe still willing to honor the PaymentIntent, risking a real charge captured
  against an order this reactor had already marked `CANCELLED`. 328 unit tests total (up from 320),
  verified via a real `mvn test` run (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2
  follow-up section for the full incident writeup.
- **`ecommerce-service`: Stripe `IdempotencyException` after a local dev database purge — the
  idempotency key was the order's own recyclable primary key, found immediately after the fix
  above by actually running `stripe listen` against a freshly-purged database.**
  `PendingOrderStatusHandler.startPaymentProcessing` stamped `String.valueOf(order.getId())` as the
  Stripe idempotency key; `scripts/purge-seed-data.sql`'s `TRUNCATE ... RESTART IDENTITY` resets
  `CUSTOMER_ORDER_SEQ` back to 1, so a new order can land on an id a *different*, earlier order (a
  different total) already used as its own key. Stripe's own idempotency cache lives server-side
  for 24 hours regardless of what happens in this app's own database, so it still remembered the
  old key/amount pair and correctly refused the new, differently-priced request:
  `IdempotencyException: Keys for idempotent requests can only be used with the same parameters
  they were first used with`. Fixed by generating a random `UUID` instead — confirmed via a
  full-module grep that nothing anywhere parses this column back into a number, and that both
  `Order.idempotencyKey`/`Payment.idempotencyKey` (`VARCHAR(64)`) fit a 36-character UUID with no
  migration needed. Does not change the actual double-charge protection (which lives entirely in
  `PaymentHandoffService.startPaymentProcessing`'s own re-entrancy check, unaffected by this) — only
  *cross-order* key collisions after a database reset. Two tests asserting the old numeric-derived
  value were updated to assert a well-formed UUID instead; 328 unit tests total, unchanged,
  verified via a real `mvn test` run (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2
  follow-up section for the full incident writeup.
- **`ecommerce-service`: a card decline permanently blocked an order from ever succeeding
  afterward, even via Stripe's own supported retry-with-a-different-card flow on the same
  `PaymentIntent`.** Declining a test card finalized the order to `FAILED` (a terminal status);
  switching to a working card on the same `PaymentElement` form then succeeded at Stripe, but the
  webhook's `payment_intent.succeeded` delivery tried to `confirmPayment` an order already
  `FAILED` — a status with no registered transition — and got rejected with
  `ORDER_003: Cannot confirmPayment an order in status FAILED` (`409`, and Stripe kept retrying the
  same failing delivery). Root cause: `StripeWebhookService` treated every
  `payment_intent.payment_failed` event as a final decline, but that event actually means the
  `PaymentIntent` dropped back to `"requires_payment_method"` — still open for another attempt with
  a different card, exactly what happened here. Fixed by introducing
  `PaymentResult.attemptFailed` — a `PENDING` result (not `DECLINED`) carrying the decline detail
  for display without finalizing the order; `PaymentHandoffService.applyResultToPayment`'s
  `PENDING`/`SUCCEEDED` branches updated to record/clear that detail correctly across retries.
  `PaymentOutcome.DECLINED` itself is unchanged and still finalizes to `FAILED` where that's
  actually correct (`MockPaymentGateway`'s one-shot decline, a genuinely `"canceled"` intent found
  by reconciliation). 330 unit tests total (up from 328), verified via a real `mvn test` run
  (JDK 21). Backend only in this pass — see `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2
  follow-up section for the full incident writeup, and the entry directly below for the matching
  `gui` follow-up.
- **`gui`: the payment-decline banner on `OrderDetailPage.tsx`/`OrderHistoryPage.tsx` widened to
  also show for a still-retryable decline, per request — the frontend half of the backend fix
  directly above.** Both banners used to gate on `order.status === 'FAILED'`, which stopped
  showing anything the moment that backend fix meant most declines leave the order at
  `PAYMENT_PROCESSING` instead. Fixed by dropping that condition entirely — the banner now shows
  whenever `order.paymentFailureMessage` is present, since that field's own nullability (cleared
  the moment a later attempt succeeds) already carries all the information needed. Verified via a
  clean `tsc --noEmit` and a successful `vite build` — no Docker in this sandbox, so the actual
  now-visible-while-`PAYMENT_PROCESSING` banner is unverified in a real browser. See
  `gui/CLAUDE.md`'s own follow-up note for the full detail.
- **`ecommerce-service`: a full module-wide code-quality audit was requested (duplication, God
  classes, N+1 queries, missing abstractions, test-coverage gaps) — two of the audit's high-priority
  findings were fixed immediately; the rest are documented in `ecommerce-service/CLAUDE.md` for a
  later pass.**
  - **Fix #1: `OrderServiceImpl#cancel` could surface a raw error for a Cancel Order click that had
    already succeeded, if it lost a race to a concurrent resolution of the same order** (a
    double-click, or the Stripe webhook/`OrderReconciliationJob` resolving the order at nearly the
    same moment) — `cancel()`'s own follow-up steps deliberately run outside any single transaction,
    so two resolutions of the same order could be mid-flight at once; whichever committed last hit
    `ORDER_INVALID_STATUS_TRANSITION` or an optimistic-lock conflict and propagated it as a real
    error, even though the order had already reached exactly the outcome the shopper asked for.
    Fixed by catching both, re-fetching the order, and returning it as a normal success whenever it
    genuinely reached `CANCELLED` — any other rejection (a genuinely invalid request) still
    propagates unchanged.
  - **Fix #2: a genuine Stripe gateway/network outage during `charge`/`refund`/`cancelUnconfirmed`
    propagated as a raw, unmapped `500`** — `EcommerceErrorCode` had zero `PAYMENT_*` codes despite
    Epic 4 being fully built. Fixed with a new `PAYMENT_GATEWAY_UNAVAILABLE` code (`503`, mirroring
    `CommonErrorCode.SERVER_EXTERNAL_SERVICE_ERROR`'s own shape) and a shared
    `OrderServiceImpl#callGatewayOrFail` helper translating the exception at every direct gateway
    call site, without touching the existing crash-safety guarantee (the order is already durably
    `PAYMENT_PROCESSING` by the time this translation runs).
  - 6 new `OrderServiceImplTest` cases. 336 unit tests total (up from 330), verified via a real
    `mvn test` run (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section
    for the full incident writeup and the audit's remaining, not-yet-actioned findings.
- **`ecommerce-service`: the audit's three N+1 query findings fixed next, per request.**
  - `CartServiceImpl.getCart` (the app's single hottest read path) used to cost up to `2N` queries
    for an `N`-line cart — one `findById` per line plus a lazy `product` load per line. New
    `ProductVariantRepository#findAllByIdWithProduct` (one `JOIN FETCH` query, any cart size) fixes
    it; 5 new `CartServiceImplTest.GetCart` cases (this method had zero coverage before).
  - `entity.Product`'s `variants`/`images`/`productTagAssignments` were mapped unconditionally on
    every row of the paginated admin product list, up to 60 extra lazy-load queries for a 20-row
    page. Fixed with `@BatchSize(size = 20)` on all three collections — Hibernate now batches every
    pending same-type collection into one `IN (...)` query instead of one per product.
  - `CouponRedemptionServiceImpl.listAvailable` (called on every coupon-picker open) used to query
    a redemption count once per candidate coupon. New grouped
    `countGroupedByCouponId`/`countGroupedByCouponIdForOwner` queries fix it, while preserving the
    original "zero limits configured → zero count queries" short-circuit exactly; 3 new test cases.
  - 8 new tests total. 344 unit tests total (up from 336), verified via a real `mvn test` run
    (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section for the full
    detail.
- **`ecommerce-service`: the audit's `PaymentHandoffService` God-class finding fixed next, per
  request — split into `orderstatus.PaymentHandoffService` (charge lifecycle:
  `startPaymentProcessing`/`resolvePayment`) and new `orderstatus.PaymentCancellationService`
  (cancellation/refund lifecycle: `applyCancellation`/`applyGatewayCancellation`/
  `applyRefundResult`, plus its own `CancellationResult` record).** The ~430-line original spanned
  three lifecycles that only share one seam — `applyGatewayCancellation`'s `ALREADY_RESOLVED`
  branch delegates straight into `resolvePayment`'s own `cancelRequested`-aware handling —
  so `PaymentCancellationService` takes a one-directional dependency on `PaymentHandoffService` for
  that one call only; no dependency runs the other way. Each of the three `OutboxEvent`-publish
  helper methods the original class held turned out to be used by exactly one of the two
  post-split lifecycles, so no shared "outbox publisher" component was needed — each just moved
  with its own call site. `OrderServiceImpl` now injects both services; `StripeWebhookService`/
  `OrderReconciliationJob` are untouched (both only ever called `resolvePayment`, which stayed put).
  `PaymentHandoffServiceTest` now covers only the charge lifecycle; new
  `PaymentCancellationServiceTest` covers the rest, mocking `PaymentHandoffService` for the
  `ALREADY_RESOLVED` delegation. Same 25 test methods as before, just split across two files — 344
  unit tests total, unchanged, verified via a real `mvn test` run (JDK 21). See
  `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section for the full detail.
- **`ecommerce-service`: the audit's remaining findings fixed next, per request ("go ahead with all
  of them") — six real changes plus one investigated-and-left-alone.**
  - Investigated the duplicated `PaymentSucceededOutboxEventHandler.Payload`/
    `PaymentRefundedOutboxEventHandler.Payload` records and left them alone — a reactor-wide
    convention (`ProductChangedOutboxEventHandler`'s own Javadoc) deliberately keeps event payloads
    per-handler to avoid a shared DTO becoming a cross-epic contention point; these two just
    happen to match today, which isn't a real bug to fix.
  - New `StripeFailureCategoryMapperTest` (16 cases) — this Stripe decline-code mapping had zero
    coverage before.
  - New `util.NameNormalizer` — deduplicates a byte-identical `normalizeName` from
    `ProductCategoryServiceImpl`/`ProductTagServiceImpl`/`ProductAttributeServiceImpl`.
  - New `infra.service.seed.CsvReader` — deduplicates a byte-identical `readCsv` from
    `ProductSeeder`/`ProductCategoryAttributeSeeder` (and now backs `CsvSeeder.seed()` itself too).
  - New `mapper.AddressMapper` (MapStruct) — replaces two hand-written `toAddress` copies in
    `CheckoutServiceImpl` with the module's own MapStruct convention.
  - New `config.PaymentProperties`/`config.OrderJobProperties` — consolidate seven separate
    `@Value`-injected fields (Stripe config across three classes, two order-job durations) into two
    constructor-bound `@ConfigurationProperties` records.
  - `CouponRedemptionServiceImpl.resolve`/`listAvailable`'s duplicated active-window check
    (`startAt`/`endAt` comparison) now shares private `hasStarted`/`hasNotExpired` helpers.
  - New `orderstatus.RefundReconciliationJob` closes the "queued cancel loses the race to a gateway
    success" money gap — polls a new `PaymentRepository#findIdsByStatusAndOrderStatus` query for a
    `Payment` left `SUCCEEDED` on a `CANCELLED` order and applies the missed refund asynchronously;
    also incidentally catches a synchronous refund call that itself failed during
    `OrderServiceImpl#cancel`. 4 new `RefundReconciliationJobTest` cases.
  - 20 new tests total. 364 unit tests total (up from 344), verified via a real `mvn test` run
    (JDK 21). See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section for the full
    per-change detail.
- **`ecommerce-service`: a marker interface plus a GoF Template Method base class for the
  `@Scheduled` reconciliation jobs, per request.** New `orderstatus.ReconciliationJob` (bare marker,
  same "Find Implementations" purpose as `infra`'s `ApplicationEventHandler`/`Seeder`), implemented
  by `OrderReservationExpiryJob`/`OrderReconciliationJob`/`RefundReconciliationJob`. New
  `orderstatus.AbstractReconciliationJob` (Template Method) — `OrderReconciliationJob`/
  `RefundReconciliationJob` had grown byte-identical in shape (poll a batch of ids, try/catch-and-log
  per id) around different domain logic; both now extend it, implementing only `pollBatch`/
  `reconcileOne`. `OrderReservationExpiryJob` implements the marker directly instead — its own
  per-id work is delegated to a separate `@Transactional` processor bean, which doesn't fit the
  template. Pure refactor, no behavior change; 364 unit tests total, unchanged, verified via a real
  `mvn test` run (JDK 21).
- **`ecommerce-service`: `webhook.StripeWebhookService` now handles `payment_intent.canceled`,
  reusing Stripe's own confirmation-limit retry cap instead of building a custom decline counter.**
  Confirming a PaymentIntent has "a variable upper limit on how many times a PaymentIntent can be
  confirmed. After this limit is reached, any further calls... transition the PaymentIntent to the
  `canceled` state" (Stripe's own docs) — Stripe's own anti-card-testing posture, already enforced
  server-side, with no fixed number to duplicate. `applyPaymentIntentEvent`'s dispatch gained a
  genuine third branch: `canceled` → `PaymentResult.declined(...)` (the exhausted PaymentIntent is
  over), distinct from `payment_failed`'s own `attemptFailed(...)` (still retryable). Guards against
  a real collision: `OrderServiceImpl#cancel`'s own explicit `cancelUnconfirmed` call also fires
  this identical event as a side effect of its own synchronous cancel (already resolved via
  `PaymentCancellationService#applyGatewayCancellation`, marking the row `CANCELLED` not
  `DECLINED`) — `applyPaymentIntentEvent` only treats `canceled` as a final decline when the
  `Payment` row is still `PENDING`, a safe no-op otherwise. 2 new `StripeWebhookServiceTest` cases.
  366 unit tests total (up from 364), verified via a real `mvn test` run (JDK 21). See
  `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section for the full detail, including
  the one narrow, accepted race this doesn't fully close.
- **`ecommerce-service`: `OrderReconciliationJob` closes the one real remaining gap in the Stripe
  payment flow — a charge attempt that crashes before ever reaching Stripe used to poll forever
  with no terminal exit.** A `Payment` row with no `gatewayReference` at all (e.g.
  `PaymentGatewayPort#charge` threw before Stripe ever created a `PaymentIntent`) previously left
  the order stuck `PAYMENT_PROCESSING` forever — `resolvePayment`'s own `PENDING` branch is always
  a no-op, and even an explicit shopper cancel couldn't escape it (`PaymentCancellationService
  #applyCancellation`'s own `gatewayReference != null` guard silently queues the cancel with no
  effect). A `PENDING` result with a `null` `gatewayReference` is uniquely produced by exactly this
  case, so `OrderReconciliationJob#reconcileOne` now finalizes it with a synthetic
  `PaymentResult#declined` (`GATEWAY_ERROR`) once past the grace period, instead of polling
  forever. 2 new `OrderReconciliationJobTest` cases. 368 unit tests total (up from 366), verified
  via a real `mvn test` run (JDK 21).
- **`ecommerce-service`: three more sanity-checked edge cases fixed, per request, found during a
  dedicated review of the Stripe payment flow's remaining risk surface.** A fourth candidate
  (`toSmallestCurrencyUnit`'s rounding) turned out to be a non-issue on closer inspection — no
  change made there.
  - `PaymentHandoffService#applyResultToPayment`'s `PENDING` branch now only applies failure detail
    while the `Payment` row is still genuinely `PENDING` — guards against Stripe's own
    out-of-order webhook delivery reintroducing a stale decline reason onto an already-`SUCCEEDED`
    payment (the order itself was never at risk; this was cosmetic but user-visible).
  - `PaymentCancellationService#applyRefundResult`'s `SUCCEEDED` branch is now idempotent against a
    row that's already `REFUNDED` — closes the common case of `RefundReconciliationJob`'s own poll
    racing `OrderServiceImpl#cancel`'s synchronous refund for the same payment (both gateway calls
    were already money-safe via Stripe's own idempotency key; this stops the duplicate
    `PAYMENT_REFUNDED` outbox event).
  - `CheckoutServiceImpl#confirm` now claims a short-lived per-user Redis lock
    (`checkout-lock:{userUuid}`, 15s TTL, never explicitly released) before doing anything else,
    rejecting a concurrent second call for the same caller — closes a real double-charge risk from
    a double-click/network-retry/back-button resubmit, which US-3.1's own atomic stock reservation
    never protected against (that only guards two *different* shoppers racing the same stock). New
    `EcommerceErrorCode.CHECKOUT_ALREADY_IN_PROGRESS` (`CHECKOUT_004`, `409`).
  - 3 new tests. 371 unit tests total (up from 368), verified via a real `mvn test` run (JDK 21).
  See `ecommerce-service/CLAUDE.md`'s Epic 4 Phase 2 follow-up section for the full per-fix detail.
- **`ecommerce-service`: `OrderServiceImpl#initiatePayment`'s own re-entrant call now checks live
  gateway status instead of replaying `charge()`** — the backend half of a "Continue Payment" GUI
  feature (not yet built), found while designing it. A shopper reloading the checkout page (or a
  future "Continue Payment" click) while already `PAYMENT_PROCESSING` used to unconditionally
  replay `charge()`, but Stripe's own idempotent replay returns the frozen response from the
  *original* `create()` call, never a live re-fetch — a real bug: a shopper who'd already paid on
  another tab, or whose intent Stripe had already auto-canceled, would see a stale "still needs
  payment" snapshot. Now checks the order's own status first: first time still calls `charge()`;
  re-entrant calls `checkStatus()` instead (the same live retrieve `OrderReconciliationJob` already
  uses), returning a fresh, trustworthy `client_secret` if still genuinely open, or finalizing the
  order via `resolvePayment` if it already resolved. 2 new `OrderServiceImplTest` cases. 373 unit
  tests total (up from 371), verified via a real `mvn test` run (JDK 21).
- **`ecommerce-service`: `initiatePayment` now also tolerates losing a race to a concurrent
  reservation expiry, the same shape as `cancel`'s own concurrent-resolution tolerance.** A
  shopper resuming payment on a still-`PENDING` order can race `OrderReservationExpiryJob`'s own
  sweep of that same order right at the edge of the reservation timeout — the loser previously
  surfaced either a raw `ObjectOptimisticLockingFailureException` or a generic
  `ORDER_INVALID_STATUS_TRANSITION` naming an internal method name. New
  `EcommerceErrorCode.ORDER_RESERVATION_EXPIRED` (`ORDER_004`, `409`); `initiatePayment` now
  catches both exception shapes and surfaces this clean code only when the order genuinely reached
  `EXPIRED`, otherwise rethrows unchanged. 3 new `OrderServiceImplTest` cases. 376 unit tests total
  (up from 373), verified via a real `mvn test` run (JDK 21).
- **`gui`: `OrderDetailPage.tsx` gained a "Continue Payment" button — the GUI half of the backend
  work above.** A `PAYMENT_PROCESSING` order (not already cancel-requested) previously had no way
  to resume payment at all — only a `PENDING` order showed "Pay Now". New `canContinuePayment`
  reuses the exact same `handlePay`/`orderApi.pay`/`PaymentDialog` flow "Pay Now" already uses,
  just with a different label, since the backend's `initiatePayment` is the same endpoint for a
  first attempt or a resume. `handlePay`'s catch block now also refetches the order, so a rejected
  call (e.g. the new `ORDER_RESERVATION_EXPIRED`) updates the stale status chip/action buttons
  instead of leaving them stuck. Verified via a clean `tsc --noEmit` and a successful `vite build`
  only — no Docker in this sandbox, so the actual click-through is unverified in a real browser.
- **`ecommerce-service`: an order abandoned mid-payment (never confirmed, declined, or explicitly
  cancelled) now auto-expires, folded into `OrderReconciliationJob` rather than a new poller.** A
  second, much longer threshold (`reconciliation.abandonmentTimeout`, default `PT45M` —
  `ORDER_ABANDONMENT_TIMEOUT`) is checked only when `checkStatus` still reports a genuinely
  still-open PaymentIntent (`PENDING` with a real `gatewayReference`); once stuck that long, the
  job actively cancels it at the gateway and finalizes via a new
  `PaymentCancellationService#applyAbandonmentExpiry` — the same durable-step shape
  `OrderServiceImpl#cancel` already uses for an explicit click. Lands the order on `EXPIRED` (new
  `PaymentProcessingOrderStatusHandler#expire`), not `CANCELLED`/`FAILED` — `CANCELLED` means "the
  shopper asked," `FAILED` means "the gateway declined," neither of which is true here; `EXPIRED`
  already means "the system gave up because nobody finished in time." The `Payment` row itself
  still lands on the already-existing `CANCELLED` status either way — no new enum value, no
  migration. 9 new tests. 385 unit tests total (up from 376), verified via a real `mvn test` run
  (JDK 21).
- **`ecommerce-service`/`gui`: a live "time left to pay" countdown, backed by a new on-demand
  reconcile endpoint that lets the countdown reaching zero trigger the auto-expire logic right now
  instead of waiting for the scheduled job's next poll tick.** `OrderReconciliationJob.reconcileOne`'s
  own logic was extracted wholesale into a new `PaymentReconciliationService.reconcileNow`, so the
  scheduled poll and the new `POST /api/v1/orders/{id}/reconcile` endpoint are provably the same
  code. `OrderResponse` gained a computed, nullable `paymentExpiresAt` (never the raw
  `abandonmentTimeout` duration — every client reflects a config change automatically). A gateway
  failure during the on-demand call never fabricates a local `CANCELLED`/`EXPIRED` outcome — same
  "never fabricate an outcome the gateway didn't give" rule every other gateway-touching flow in
  this module already follows. 5 new tests (net); 390 unit tests total (up from 385). GUI: new
  shared `useCountdown`/`MessageDialog` (both deliberately reusable beyond this feature, per
  request) plus a `usePaymentCountdown` hook and `PaymentCountdown` chip, wired into `CheckoutPage`'s
  payment phase, `OrderDetailPage`, and `OrderHistoryPage` (per-row). Verified via a real
  `mvn test` run and a clean `tsc --noEmit`/`vite build` — no Docker in this sandbox, so the actual
  live countdown/reconcile/dialog flow is unverified in a real browser.

## [0.0.2] — 2026-08-11 — start of the microservices break-up

### Added

- **`gateway` now relays `/api/v1/chat/stream` (SSE chat) by hand — CORS is fully consolidated at
  `gateway` with zero remaining exceptions.** Follow-on to the previous CORS-consolidation pass,
  which left one deliberate carve-out (`ai-service`'s own `CorsConfig`, narrowed to just this one
  endpoint, since Spring Cloud Gateway Server MVC's usual `RouterFunction` routing has real,
  documented problems proxying Server-Sent Events). Asked directly whether to just delete that
  carve-out or actually eliminate the reason it existed — chose the latter.
  - **New: `routing/ChatStreamProxyController`** — a plain `@RestController` (not the Gateway MVC
    DSL) that proxies just this one path by hand: read a chunk from `ai-service`, write it to the
    browser, flush, repeat. Uses the JDK's own `HttpClient` rather than Spring's `RestClient` —
    deliberately, not by default — because it needs the upstream response's status code available
    *before* committing to stream the body, and `RestClient.exchange()` scopes the response/body
    to one callback, which doesn't fit that shape. `HttpClient.send(...,
    BodyHandlers.ofInputStream())` returns as soon as headers arrive, exposing status code and a
    lazily-readable body as two independent fields. Forwards `Authorization`/`Content-Type`/
    `Accept` verbatim, same as every other proxied path — `ai-service` still verifies the JWT
    itself regardless.
  - **New: `routing/StreamingProxyAsyncConfig`** (renamed from the original `ChatStreamAsyncConfig`
    — see below) — `StreamingResponseBody`'s own Javadoc recommends a dedicated `TaskExecutor` over
    Spring MVC's default (unbounded, one thread per request), so this wires a small bounded
    `ThreadPoolTaskExecutor` (`streamRelayExecutor`) plus a 60-second async timeout that must stay
    in sync with `ai-service`'s own `SseStreamTemplate.SSE_TIMEOUT_MS` — same "one value duplicated
    with an explanatory comment, since there's no Maven dependency to share the real constant
    through" pattern already used elsewhere in this reactor for exactly this kind of cross-module
    constant.
  - **Renamed `ChatStreamAsyncConfig`/`chatStreamExecutor` → `StreamingProxyAsyncConfig`/
    `streamRelayExecutor`** shortly after landing, once it became clear the original names were
    already narrower than what they actually govern: `configureAsyncSupport`'s `setTaskExecutor`
    sets *the one* default async executor for this whole Spring MVC context, so any future second
    `StreamingResponseBody`-based endpoint in `gateway` would start using this same bean
    automatically, regardless of its name — a chat-specific name would have quietly become
    inaccurate the moment that happened, rather than describing the mechanism it actually governs.
    Thread name prefix changed to match (`chat-stream-proxy-` → `stream-relay-`).
  - **`ai-service`'s `CorsConfig` deleted outright** (not just narrowed further) — and its
    `SecurityConfig` dropped the `.cors(...)` wiring and `CorsConfigurationSource` dependency
    entirely. Every request this module receives now comes from `gateway` (a server-to-server
    call via the new controller above), never a browser directly, and CORS is a browser-enforced
    mechanism a server-to-server call never triggers in the first place.
  - **GUI**: `chatApi.ts`'s `streamChat` points back at `gateway` (`VITE_BACKEND_URL`) instead of
    calling `ai-service` directly — the `VITE_AI_SERVICE_URL` env var introduced in the previous
    CORS pass is removed again, since nothing needs it anymore. `vite-env.d.ts` updated to match
    (back down to two origins: `gateway` for everything HTTP, `social-service` for the
    WebSocket/STOMP connection, which still isn't proxied — Gateway Server MVC and this new
    controller both only handle plain HTTP, not a protocol upgrade).
  - **Verified against real behavior, not just a successful compile — including a live bug the
    test itself surfaced.** Booted `gateway` locally (Postgres was reachable) and POSTed to the
    new endpoint: first attempt hit a `NullPointerException` inside the JDK `HttpClient` builder,
    traced to a deliberately-relaxed test-only header (`required = false`, to probe without a real
    JWT) — confirmed this is unreachable in the shipped code, where the header stays
    `required = true` (the default) and Spring guarantees non-null before the method body ever
    runs. Retested with a non-`Bearer`-prefixed `Authorization` value (avoids triggering `gateway`'s
    own JWT validation while still giving the controller a non-null header) and confirmed via the
    logged `java.net.ConnectException` at the exact `httpClient.send()` line that the proxy
    correctly attempts to reach `ai-service`'s configured base URL. Both temporary test-only
    changes (`SecurityConfig`'s `anyRequest().permitAll()`, the header's `required = false`)
    reverted immediately after, confirmed via a clean `git diff` on both files before the final
    build. Full reactor build green; GUI `tsc --noEmit` showed zero new errors.
  - **Docs.** Root `CLAUDE.md` (Architecture → Routing section), `gateway/CLAUDE.md` (`routing/`
    and `security/` bullets), `ai-service/CLAUDE.md` (`CorsConfig`/`SecurityConfig`),
    `gui/CLAUDE.md` (back to two origins), `docs/PROJECT_STRUCTURE.md` (`gateway`'s and
    `ai-service`'s tree entries).

- **CORS consolidated at `gateway` — the natural next step after routing landed.** `gateway`'s
  `CorsConfig` is now the sole CORS source of truth for everything actually proxied through it;
  `ai-service`'s own copy (the only other one that ever existed — the other five services never
  had one) is narrowed to a single deliberate exception rather than removed outright.
  - **Investigated before touching anything, and found the plan needed adjusting twice.** First:
    the GUI's `httpClient.ts`/`authService.ts`/`adminAuthService.ts`/`chatApi.ts`/`socket.ts` all
    hardcoded the same stale fallback, `http://localhost:8081` (`ecommerce-service`'s port, not
    `gateway`'s `8080`) — a single-origin-for-everything assumption left over from before the
    six-way service split. Second: Spring Cloud Gateway Server MVC has real, documented upstream
    problems proxying Server-Sent Events (connection leaks, broken chunked streaming) — meaning
    `/api/v1/chat/stream`, already one of the 23 routes added when routing first landed, was never
    actually safe to proxy. That route is narrowed to `/api/v1/chat/sessions/**` (plain REST —
    session listing/history only); the SSE endpoint itself is excluded.
  - **`ai-service`'s `CorsConfig` narrowed from `/**` to `/api/v1/chat/stream`** — the one endpoint
    the GUI still calls directly instead of through `gateway`, for the SSE reason above. Every
    other endpoint this module owns needs no CORS config here anymore.
  - **GUI now has three distinct backend-origin constants, not one.** `VITE_BACKEND_URL` (default
    `http://localhost:8080`, `gateway`) covers `@shared/api/httpClient.ts` and almost every
    feature's REST calls. Two new env vars carve out the two things `gateway` doesn't proxy:
    `VITE_AI_SERVICE_URL` (default `http://localhost:8086`) for `@chat/api/chatApi.ts`'s
    `streamChat` only — `listSessions`/`getSessionHistory` in the same file are unaffected, they
    go through the shared `httpClient` normally. `VITE_SOCIAL_SERVICE_URL` (default
    `http://localhost:8084`) for `@messaging/api/socket.ts`'s STOMP connection — WebSocket
    upgrades were never routed through `gateway` at all (Gateway Server MVC only proxies plain
    HTTP), so this one was never a CORS matter in the first place, just a base-URL correction.
    `vite-env.d.ts` updated to declare both new env vars.
  - **Found, documented, deliberately not fixed**: `@auth/services/authService.ts`'s `startOAuth`/
    `logout` and `@auth/api/authApi.ts`'s `login` (used by `adminAuthService`) all call
    `identity-service` endpoints that no longer exist (`/api/v1/auth/oauth2/authorization/**`,
    `POST /api/v1/auth/logout`, `POST /api/v1/auth/login`) — pre-Keycloak-migration leftovers;
    `identity-service`'s `AuthApi` only has `GET /api/v1/auth/user` left. `logout`'s
    fire-and-forget `.catch()` masks the failure so client-side logout still works in practice, but
    `startOAuth`/admin `login` would genuinely 404. Flagged in `gui/CLAUDE.md` as a known gap — a
    real, separate piece of work (migrating admin login to go through Keycloak properly), out of
    scope for this pass.
  - **Verified, not just compiled.** Full reactor `./mvnw` build (gateway + ai-service) green;
    `npx tsc --noEmit` on the GUI showed zero new errors (only pre-existing ones — the documented
    CRA-era `App.test.tsx`/`reportWebVitals.ts` leftovers and two unrelated minor issues, none in
    any of the five edited files); `npm run build` succeeded.
  - **Docs.** Root `CLAUDE.md` (Architecture → Routing section — the `/api/v1/chat/stream`
    exclusion, the WebSocket carve-out, CORS-consolidated status), `gateway/CLAUDE.md` (`routing/`
    and `security/` bullets), `ai-service/CLAUDE.md` (`CorsConfig`'s narrowed scope), `gui/CLAUDE.md`
    (the three-origin split, the dead-endpoint gap), `docs/PROJECT_STRUCTURE.md` (`gateway`'s and
    `ai-service`'s tree entries).

- **`gateway` is now the single entry point for external clients — proxies HTTP traffic to all six
  standalone services via Spring Cloud Gateway Server MVC.** First step of turning `gateway` into a
  real API gateway rather than just a JWT-verification shell; routing is the enabling capability
  everything else (CORS consolidation, rate limiting, timeouts, retry, circuit breaker) would build
  on top of, none of which are built yet.
  - **New module: `routing/`** — `GatewayRoutesConfig` (one `RouterFunction<ServerResponse>`
    `@Bean` per backend service, `GatewayRouterFunctions.route` + `GatewayRequestPredicates.path` +
    `HandlerFunctions.http`, 23 routes total) and `GatewayServicesProperties` (`app.services.*`,
    each service's base URL — `localhost:<port>` by default, Compose DNS names in
    `application-docker.yml`, same convention `ai-service`'s own `ContentServiceClientProperties`
    already established for its one HTTP dependency). Paths forwarded unchanged — every backend
    already expects exactly the path the GUI called it with directly, before this gateway existed.
  - **Routing table built from a full audit of every `@RequestMapping` in the reactor, not assumed
    from prefixes.** Three top-level prefixes are shared by more than one service and only
    disambiguate one segment deeper: `/api/v1/users` (`identity-service` owns `/me/**`,
    `social-service` owns `/public/**`/`/search`), `/api/v1/public` (`content-service` owns
    `/question-answers/**`/`/articles/**`, `ecommerce-service` owns `/products/**`), and
    `/api/v1/admin` (`ecommerce-service`/`content-service`/`ai-service` each own a distinct
    resource segment that never collides with the others'). `content-service`'s
    `/internal/content-items/**` deliberately excluded — service-to-service traffic
    (`ai-service` → `content-service` directly, gated by `X-Internal-Api-Key`, not a JWT), never
    meant for external clients.
  - **Dependency correction mid-implementation**: the first attempt used
    `spring-cloud-starter-gateway-server-webmvc` (the current/renamed artifact), which doesn't
    exist in the `2023.0.3` (Leyton) release train this reactor's Spring Boot 3.2.5 needs — that
    longer artifact name only exists starting Gateway 4.3.0, a later, incompatible release train.
    Corrected to `spring-cloud-starter-gateway-mvc`, the artifact name Gateway 4.1.x (2023.0.x's
    version) actually shipped this feature under. `spring-cloud-dependencies:2023.0.3` imported at
    the root `pom.xml`, same BOM-import convention `langchain4j-bom` already established there for
    its own sole consumer (`ai-service`).
  - **Verified against real behavior, not just a successful compile.** Postgres was reachable in
    this session, so `gateway` could boot fully under its `local` profile; a temporary
    `anyRequest().permitAll()` override (reverted immediately after, never committed) let live
    requests reach the routing layer without a real Keycloak JWT. Every sampled path resolved to
    the correct backend host:port with the correct HTTP method preserved (`/api/v1/tasks` →
    `task-service:8083`, `/api/v1/users/me` `PUT` → `identity-service:8082`,
    `/api/v1/users/search` → `social-service:8084` — confirming the trickiest collision split
    resolves correctly, not just compiles), each confirmed via the actual
    `ResourceAccessException`/target URL logged when the intentionally-absent backend refused the
    connection — not merely an HTTP status code, which wouldn't have distinguished a matched route
    from an unmatched one (both return non-2xx either way). A genuinely unmapped path fell through
    to Spring's normal no-handler behavior (`NoResourceFoundException`) rather than being caught by
    any route, confirming no accidental catch-all.
  - **Not built yet, by design**: load balancing and service discovery — there is exactly one
    instance of each service at a fixed address in this deployment, so both would solve a problem
    that doesn't exist here today. CORS consolidation (each service still has its own `CorsConfig`
    even though the GUI could now go through `gateway` for all of them), rate limiting, timeouts,
    retry, and circuit breaker are all deferred to later, incremental passes.
  - **Docs.** Root `CLAUDE.md` (Module Structure table, dependency-order paragraph, Long-term
    direction, new Architecture → Routing section), `gateway/CLAUDE.md` (new intro paragraph, new
    `routing/` bullet, fixed a stale "not-yet-built proxy layer" reference in the Rules section),
    `docs/PROJECT_STRUCTURE.md` (`gateway` section's intro, tree, and closing summary).

### Changed

- **Consolidated `dev-knowledge-platform-apps-docker-compose.yml`'s six separate one-shot Liquibase
  runners into one `services-liquibase` container.** Previously each standalone service with its
  own changelog (`ecommerce-service`/`identity-service`/`task-service`/`social-service`/
  `content-service`/`ai-service`) got its own inline compose service (`ecommerce-liquibase`,
  `identity-liquibase`, etc.), each a separate `liquibase/liquibase:4.29` container running one
  `liquibase ... update` invocation. Replaced with a single `services-liquibase` service —
  `entrypoint: ["sh", "-c"]` running a shell loop over all six service names, each mounted at its
  own `/changelog/<service>` path (six volume mounts on the one container) and invoked with its own
  `--search-path`/`--changelog-file`, same `--url`/`--username`/`--password`/`--driver` all six
  always shared anyway (one Postgres instance, one `dev-premier` database — only the schema differs,
  and that's embedded in each changelog's own SQL, not the JDBC URL). All six app containers'
  `depends_on` updated to point at `services-liquibase` instead of their own dedicated runner.
  Running the six sequentially inside one container (rather than six containers with no
  dependency relationship between them) is a deliberate improvement, not just a leaner compose
  file: all six changelogs already shared one Liquibase `DATABASECHANGELOG`/`DATABASECHANGELOGLOCK`
  tracking pair (same database, no per-service schema override on the tracking tables), so the old
  shape let six containers race to start in parallel and potentially contend for that lock —
  Liquibase's own wait/retry handled it, but nondeterministically. One container running them one
  at a time removes that contention entirely. The six standalone single-service `*-liquibase.yml`
  files at the repo root (for running one service's migration outside the combined apps-compose
  flow) are untouched — this consolidation is scoped to the combined file only.
  `docs/PROJECT_STRUCTURE.md`'s Deployment section updated to match; `docs/CHANGELOG.md`'s own
  historical entries describing the old six-service shape are left as-is, since they're a record of
  what was true at the time, not a live description.

- **`gateway`'s entire Liquibase changelog tree deleted outright — this app has zero migration
  story left at all, not just zero live tables.** Follow-on to the `product.USER`/schema-drop work
  above: once `DKP-0033` dropped every table `product` schema ever held, the whole
  `database/sql/` tree under it (every already-run historical changeset from the six embedded-module
  extractions, plus `DKP-0033` itself) had nothing left to justify keeping around, even as frozen
  history — every table it ever described has a fresh-snapshot equivalent in its owning standalone
  service's own changelog now, or (for `USER`) was retired with nothing replacing it here at all.
  One real blocker surfaced mid-change and was resolved before deleting anything:
  - **`DKP-0024` (`CREATE SCHEMA IF NOT EXISTS keycloak`) was not dead weight** — it's the only
    mechanism in this project that creates the `keycloak` Postgres schema before Keycloak's own
    internal migration runs (which assumes the schema already exists and fails otherwise). Deleting
    the changelog tree without replacing this would have broken Keycloak's ability to boot against
    a fresh Postgres volume. **Moved to `docker/postgres/init.sql` instead** — that script already
    runs automatically via `docker-entrypoint-initdb.d` before Postgres reports healthy, and every
    service's `depends_on` already waits on that healthcheck, so no new compose wiring was needed;
    a bespoke one-shot compose service (an alternative considered and rejected) would have
    duplicated a mechanism that already existed. Same practical one-time-only semantics as the old
    changeset: `initdb.d` scripts only run against an empty/fresh data volume, but a schema persists
    in the volume once created either way.
  - **`liquibase-core` removed from `gateway/pom.xml`**, along with the `<build><resources>` block
    that existed solely to expose changelog XML/SQL files under `src/main/java` on the classpath
    (Maven's default `src/main/resources` inclusion still applies without it — nothing else in this
    module ever needed the custom block). `spring.liquibase.*` removed from both
    `application.yml` (was already `enabled: false` there, plus a now-dangling `change-log` pointing
    at the deleted file) and `application-docker.yml` (was `enabled: true` — this is the profile
    that would have actually broken on next boot without the fix above).
  - **Compose wiring retired to match.** `dev-knowledge-platform-liquibase.yml` (the standalone
    one-shot runner that existed solely to apply this changelog outside the combined apps-compose
    flow) deleted outright. `dkp-liquibase` service block removed from
    `dev-knowledge-platform-apps-docker-compose.yml`, and `gateway`'s own container there dropped
    its `depends_on: dkp-liquibase` (kept `depends_on: minio`). The six other services' own
    `*-liquibase` runners/compose files are unaffected — each still migrates its own schema exactly
    as before.
  - **Docs.** Root `CLAUDE.md` (Module Structure table, Long-term direction, Build & Run Commands,
    Migrations — Liquibase bullet), `gateway/CLAUDE.md` (intro, `security/` bullet, full
    `database/sql/` rewrite), `docs/PROJECT_STRUCTURE.md` (module tree, `gateway` section, Database
    section, Deployment section), `dev-knowledge-platform-docker-compose.yml`'s own Keycloak schema
    comment, `dev-knowledge-platform-apps-docker-compose.yml`'s header comment. Full reactor build
    verified green after the code/config changes, before any doc updates began — same discipline as
    the `product.USER` drop above.

- **`gateway` retired its own local `User` persistence entirely — `identity-service` is now the
  sole system-of-record for user identity in this reactor, and `product` schema was dropped down
  to zero live tables.** Prompted by a simple observation: `gateway` JIT-provisioned a row into
  `product.USER` on every request, but nothing ever read it back — it has had zero REST controllers
  since well before this change, and the authorization decision (`ROLE_ADMIN` or not) was always
  read straight off the JWT's own `realm_access.roles` claim, never the row. Confirmed via grep
  before touching anything: the only class that ever read the row back, `CurrentUserResolver`, had
  zero real callers left (its two consumers had already moved to other modules in earlier
  extractions). Landed in order:
  - **`KeycloakJwtAuthenticationConverter` rewritten to be claims-based**, mirroring
    `ecommerce-service`'s/`task-service`'s/`content-service`'s/`ai-service`'s converters —
    `jwt.getSubject()` stands in for `userUuid` now, with zero database read or write. Dropped its
    `UserRepository` field, `@Transactional`, and the whole `findOrCreateUser` find-or-create block.
  - **`CurrentUserResolver` deleted outright** (confirmed dead via grep — zero callers).
  - **`UserSeeder`/`DataSeedingRunner` deleted outright**, along with `data/csv/users.csv` and the
    standalone `data/init-admin-user.sql` script (never wired into any seeding mechanism, and
    inserted directly into `product.USER`) — nothing left to seed once the table is gone.
    `app.seed.*` removed from `application.yml`/`application-docker.yml`.
  - **`User`/`UserRepository`/`UserProvider`/`UserRole`/`UserStatus` moved from `common` to
    `identity-service` outright**, not re-exported — `identity-service` became the sole consumer
    once `gateway` dropped its own copy (confirmed via a reactor-wide grep of every real import,
    not Javadoc mentions). `identity-service`'s `IdentityServiceApplication` dropped its
    `@EntityScan`/`@EnableJpaRepositories` overrides as a direct result — default Spring Boot
    component scanning now covers `identity.entity`/`identity.repository` without help, since
    they're no longer off in `common`. `User.@Table` still deliberately carries no hardcoded
    schema, same convention as every entity in this reactor — `identity-service`'s own
    `hibernate.default_schema: identity` resolves it to `identity.USER` at runtime.
  - **`DKP-0033`: a new Liquibase changeset dropping all 23 tables the `product` schema ever
    held, in one statement.** Started narrower — just `product.USER` — but widened once it became
    clear every other table in that schema was itself already-orphaned history from the six
    embedded-module extractions (content/task/social/ai), each with a fresh snapshot already living
    in its own schema; there was nothing left in `product` for anything to read or write.
    Discovered along the way: 14 live FK constraints from 10 of those other tables
    (`CHAT_SESSION`/`FRIEND_REQUEST`/`FRIENDSHIP`/`USER_BLOCK`/`GROUP_MEMBER`/`DM_THREAD`/
    `DM_MESSAGE`/`CHANNEL_MESSAGE`/`PROJECT`/`TASK`) still pointed at `product.USER(USER_ID)` — a
    plain `DROP TABLE` would have failed with a "still referenced" error. Once the scope widened to
    "drop everything," `CASCADE` became the right tool (unlike the narrower single-table draft,
    where naming each constraint explicitly was preferred over `CASCADE`'s blast-radius risk) —
    every possible dependent was already inside the same drop list, confirmed via a reactor-wide
    grep for `REFERENCES product.` finding nothing outside it, in this schema or any other
    service's. One table (`INTERVIEW_QUESTION`, `DKP-0002`) had been renamed to `QUESTION_ANSWER`
    partway through this reactor's history (`DKP-0014`) — the drop list uses the live name. Every
    sequence (`ALTER SEQUENCE ... OWNED BY`, declared in each table's own creating changeset) drops
    automatically alongside its table — no separate `DROP SEQUENCE` needed. The `product` schema
    container itself is left in place, just empty, in case a future orchestration endpoint ever
    needs it.
  - **Docs.** Root `CLAUDE.md` (Module Structure table, dependency-order paragraph, Long-term
    direction, Security section, Database Conventions, Liquibase bullet), `common/CLAUDE.md`
    (`entity`/`enums`/`repository` bullets, plus an unrelated pre-existing doc-drift bug on the
    `@CurrentUserId` bullet caught and fixed while in the area — it still claimed the argument
    resolvers lived in `gateway`, but those moved out to `ai-service`/`social-service`/
    `content-service`/`task-service` in earlier extractions), `gateway/CLAUDE.md` (full rewrite of
    the intro, `security/`, `service/`, `database/sql/` bullets, and the "Current-user resolution"
    section), `identity-service/CLAUDE.md` (new `entity`/`repository`/`enums` bullets, updated
    `IdentityServiceApplication`/"types imported from `common`"/"never hardcode a schema"/"deleted
    outright" sections), `docs/PROJECT_STRUCTURE.md` (`common`, `identity-service`, `gateway`,
    Database, and Deployment sections — the `gateway` section also had pre-existing drift from an
    earlier, uncaught session predating this one: it still described `categories.csv`/`tags.csv`/
    `question-answers/*.md` living under `gateway`'s own resources, when those had already moved to
    `content-service` in an earlier extraction). Full reactor build (`./mvnw clean package`, all 9
    modules) verified green after every code change above, before any doc updates began.

- **`gateway` post-extraction cleanup** — a pass over `gateway` looking for leftovers from the
  six-module extraction now that it's the natural stopping point (see below). `pom.xml` dropped
  three dependencies confirmed dead by grep against this app's own 10 remaining source files (no
  `@Mapper`, no OpenAPI annotation, no `commons-pool2`/pooling usage): `mapstruct`,
  `springdoc-openapi-starter-webmvc-ui`, `org.apache.commons:commons-pool2`. `io.minio:minio` was
  checked the same way and kept — `infra`'s `StorageServiceImpl`/`StorageConfig` (`@Service`/
  `@Configuration` under `com.ttg.devknowledgeplatform.infra.*`) are component-scanned into this
  app's context regardless of whether this app's own code calls `StorageService`, so `MinioClient`
  must stay on the classpath for that bean to construct at startup — removing it would break boot,
  not just remove dead code. `pom.xml`'s `<description>` also updated — it referenced "the one
  remaining cross-module REST orchestration endpoint" and "STOMP transport wiring" as if they
  existed today; neither does (zero REST controllers, no WebSocket/STOMP transport, both already
  documented as such in `gateway/CLAUDE.md`). Deleted three empty leftover package directories
  (`config/{cache,thread,web}`) whose classes were deleted during the `ai-service` extraction —
  git doesn't track empty directories, so they never surfaced in a diff. Fixed stale Javadoc on
  `UserSeeder`: it claimed `gateway` "still needs to seed its own `product.USER` table for the
  remaining embedded modules (`content-service`/`ai-service`/`social-service`/`task-service`) to
  reference" — no longer true post-extraction, since none of those standalone services have an FK/
  join on this table (claims-based `authorUuid`/`userUuid`/`ownerUuid` columns instead, or, for
  `social-service`, its own independently-seeded `SocialProfile` copy of this same demo data via
  its own `SocialProfileSeeder`). `docs/PROJECT_STRUCTURE.md`'s `gateway` section had the same
  drift one level up — it described `DataSeedingRunner` as still running seeders in order
  "category → tag → questionAnswer → user," which would be a compile error today (`gateway` has no
  Maven dependency on `content-service` to import those seeders from); corrected to reflect that it
  only runs `UserSeeder`. Also reconciled a long-stale git index: ~330 paths (nearly all
  under the pre-rename `api/` module, plus a few one-off renamed files) were staged as "new file"
  while already absent from the working tree — unstaged via `git restore --staged`, no working-tree
  changes. Separately, `gui/.gitignore` gained `/dist` (Vite's actual build output directory; the
  existing `/build` entry is a leftover Create React App convention) — 7 build artifacts were about
  to be committed for the first time.

- **`ai-service` extracted into a standalone Spring Boot application — the sixth and final module
  pulled out of the monolith, following the
  `ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service`
  precedent. After this extraction, `gateway` has **zero embedded feature modules remaining**,
  closing out the microservices-extraction-plan project. Unlike `content-service`'s own extraction,
  this one needed no HTTP rewrite of its own beforehand — `ai-service` already had zero Maven
  dependency on `content-service` going into this extraction (severed during `content-service`'s own
  extraction, not this one) — so the work here was giving `ai-service` its own app
  shell/security/schema and relocating the runtime infrastructure its controllers/services actually
  use out of `gateway`, plus a claims-based rename of two entities. Landed in order:
  - **`ChatSession.userId: Integer` → `userUuid: String`, `PipelineMetrics.userId: Integer` →
    `userUuid: String` — claims-based, mirroring `task-service`'s `ownerUuid`/`content-service`'s
    `authorUuid`.** Confirmed via an explicit `AskUserQuestion` before applying it (claims-based was
    the recommended option and the one chosen) — a chat session or a pipeline-metrics row only ever
    needs "is this the caller's own session/row," never another user's profile data, so there's no
    reason to justify a persisted `User` copy just for this module. Every method that took/returned
    `Integer userId` in the chat+pipeline-metrics feature was renamed throughout:
    `ChatSessionRepository` (`findByIdAndUserId` → `findByIdAndUserUuid`,
    `findSessionSummariesByUserId` → `findSessionSummariesByUserUuid`), `ChatSessionService`/`Impl`,
    `RagQueryService`/`Impl`, `RagPipelineContext`, `PipelineCompletedEventListener`,
    `ChatApi`/`ChatController`. Columns renamed the same way (`USER_ID` → `USER_UUID`) in the new
    schema/changelog (below) — no FK to any `USER` table either before or after, since these are
    append-only/session rows that must survive a user's deletion from whichever app JIT-provisioned
    them.
  - **Own `ai` schema + Liquibase changelog.** New `database/sql/ai-service.xml` +
    `202608080001__0.0.2__DKP-0032__add_ai_service_tables.sql` (renamed from a `0.0.1` version
    segment on 2026-08-11, alongside every other standalone service's own changelog — see root
    `CLAUDE.md`'s Liquibase naming note) — a fresh snapshot (not a replay of
    `gateway`'s incremental history) of `CONTENT_EMBEDDING`/`CHAT_SESSION`/`CHAT_MESSAGE`/
    `SYS_PARAM`/`PIPELINE_METRICS` into the new `ai` schema, same convention every other standalone
    service's changelog already follows. All entities (`ChatSession`, `PipelineMetrics`,
    `ChatMessage`, `SysParam`) had their `@Table(schema = "product")` hardcodes removed (same bug
    class as the `common.entity.User`/`task-service`/`social-service`/`content-service` incidents —
    `ContentEmbedding` never had one). Two native-SQL schema literals also needed fixing on top of
    that sweep, since hand-written native queries don't go through Hibernate's schema resolution at
    all: `ContentEmbeddingRepository`'s three native queries
    (`product.content_embedding` → `ai.content_embedding`) and `PipelineMetricsRepository`'s native
    aggregate query (`product.PIPELINE_METRICS` → `ai.PIPELINE_METRICS`).
  - **Standalone app shell.** New `AiServiceApplication` (`@SpringBootApplication` +
    `@ConfigurationPropertiesScan` + `@EnableAsync`, no `@EntityScan`/`@EnableJpaRepositories`), own
    `security/` package mirroring `content-service`'s/`task-service`'s exactly
    (`KeycloakRealmRoleConverter`, `KeycloakJwtAuthenticationConverter` building `CustomOAuth2User`
    straight from JWT claims with zero DB access, `CurrentUserResolver`, `SecurityConfig` —
    `@EnableWebSecurity` + `@EnableMethodSecurity`, needed here since `IngestionApi`'s
    `@PreAuthorize("hasRole('ADMIN')")` is the only method left in the whole reactor that needs it —
    rules: `/actuator/**` permitAll, `/api/v1/admin/**` hasRole(ADMIN), everything else
    authenticated, `CorsConfig`, `JsonAuthenticationEntryPoint`), own `config/web/
    CurrentUserIdArgumentResolver` (registered via the existing `ChatMvcConfig`'s new
    `addArgumentResolvers` override, alongside its pre-existing `addInterceptors` registration of
    `ChatRateLimitInterceptor`), own `application.yml` (port `8086`, `ai` schema). `pom.xml` gained
    `spring-boot-starter-oauth2-resource-server`, `postgresql` (runtime),
    `spring-boot-starter-actuator`, `spring-boot-maven-plugin` (+ resources block for XML/SQL);
    `spring-boot-starter-oauth2-client` un-optionaled.
  - **`sseStreamExecutor`/`bucket4jRedisConnection` beans relocated from `gateway`, verbatim.** New
    `config/thread/{ThreadPoolConfig,ThreadPoolProperties}` (`sseStreamExecutor`, 10 core / 50 max /
    queue 100) moved here unchanged from `gateway`'s now-deleted classes of the same name — no
    behavioral change, `ChatController`/`SseStreamTemplate` already lived here. New
    `config/RedisConfig` carries only the `bucket4jRedisConnection` bean (a raw Lettuce
    `StatefulRedisConnection` for `ChatRateLimiter`'s binary bucket state), moved from `gateway`'s
    `RedisCacheConfig`.
  - **Dead-code deletion: `gateway`'s `RedisCacheConfig` was only half load-bearing.** A
    reactor-wide grep for `@Cacheable`/`@CacheEvict`/`@CachePut` found **zero real usages anywhere in
    the whole codebase** — `RedisCacheConfig`'s `@EnableCaching` + `cacheManager`/
    `baseRedisCacheConfiguration` beans (backed by `infra`'s `CacheTtlProperties`/`CacheNames`) had
    been wired up and shipped but never actually consumed by a single Spring-managed cache
    annotation, same class of finding as `ai-service`'s own `SearchDocument` dead-entity finding from
    the `content-service` extraction. Deleted outright rather than moved: `gateway`'s
    `RedisCacheConfig` itself, plus `infra`'s now-fully-orphaned `CacheTtlProperties`/`CacheNames`.
    Also deleted as part of the same cleanup, now unreachable once `gateway` has no REST controllers
    or SSE endpoints of its own left at all: `gateway`'s `config/web/WebMvcConfig`,
    `config/web/CurrentUserIdArgumentResolver`, `config/thread/ThreadPoolConfig`,
    `config/thread/ThreadPoolProperties`.
  - **`gateway` cleanup.** `pom.xml` lost the `ai-service` internal Maven dependency (same
    explanatory comment-block pattern used for the other five removed dependencies),
    `spring-boot-starter-data-redis`, `spring-boot-starter-cache`, `bucket4j-redis`
    (`commons-pool2`/`io.minio:minio` kept — a deliberate, low-risk judgment call). `application.yml`
    lost the entire Redis block, `app.ai.*`, `app.threads.*`, `app.chat.*`, `app.content-service.*`,
    and a dead top-level `cache: default-ttl` block (kept: `app.frontend.url`, `app.storage.*`,
    `app.seed.*`); `application-local.yml` lost a dead `spring.data.redis.password` override;
    `application-docker.yml` lost dead `spring.data.redis.*` and `app.ai.embedding-model.api-key`
    overrides. `SecurityConfig` lost its `@EnableMethodSecurity` import/annotation — now dead here
    (`gateway` has zero `@PreAuthorize`-annotated methods left), declared instead by `ai-service`'s
    own new `SecurityConfig`. `Dockerfile` lost the `COPY ai-service ai-service` line; comment
    updated to reflect zero embedded modules remaining — only `common`+`infra`+`gateway` sources
    copied now, every other module's `pom.xml` alone still needed for Maven's reactor `<modules>`
    parse. **Net result: `gateway` now depends on nothing but `common`+`infra`** — zero embedded
    feature modules, zero REST controllers, zero DTOs/mappers of its own; purely JWT
    verification/JIT-provisioning of `product.USER`, Liquibase migrations for that table plus every
    departed module's frozen historical changesets, and `UserSeeder`. `gateway`'s original ai-table
    Liquibase changesets (`DKP-0005`/`DKP-0006`/`DKP-0007`/`DKP-0008`/`DKP-0010`/`DKP-0011`/
    `DKP-0012`) are now frozen/orphaned history, same treatment as its old content/task/social
    changesets.
  - **Docker/compose wiring.** New `ai-service/Dockerfile` (multi-stage, port `8086`, mirrors
    `content-service/Dockerfile` exactly — build context is repo root, copies `common`+`infra`+
    `ai-service` sources plus every other module's `pom.xml` alone) and `ai-service-liquibase.yml`
    (standalone migration-runner compose file, mirrors `content-service-liquibase.yml`). Added to
    `dev-knowledge-platform-apps-docker-compose.yml`: an `ai-liquibase` runner + full `ai-service`
    container block (port `8086`; env vars `SPRING_DATASOURCE_*`, `KEYCLOAK_ISSUER_URI`,
    `FRONTEND_URL`, `REDIS_HOST: redis`, `REDIS_PASSWORD`, `OPENAI_API_KEY` (required, `:?` syntax),
    `ANTHROPIC_API_KEY` (optional), `CONTENT_SERVICE_BASE_URL: http://content-service:8085`,
    `INTERNAL_API_KEY`; depends on `ai-liquibase`+`redis`+`content-service`). `gateway`'s own
    container block simplified — no longer needs `OPENAI_API_KEY`/`CONTENT_SERVICE_BASE_URL`/
    `INTERNAL_API_KEY` or `redis`/`content-service` `depends_on`, just
    `SPRING_PROFILES_ACTIVE`/`KEYCLOAK_ISSUER_URI`/`FRONTEND_URL`, depending on
    `dkp-liquibase`+`minio`. The apps-compose file's header comment already describes "seven
    independently-runnable Spring Boot processes... gateway now has zero embedded feature modules."
  - **Docs.** Root `CLAUDE.md` (module table, dependency-order paragraph, standalone-services
    paragraph, Option-C reasoning paragraph, Long-term-direction, Build & Run Commands, Security
    section, Database Conventions, Liquibase bullet), `docs/PROJECT_STRUCTURE.md` (module tree,
    dependency paragraphs, rewrote `## ai-service` to standalone shape, rewrote `## gateway` removing
    all now-deleted `config/` classes, Database section, Deployment section),
    `ai-service/CLAUDE.md` (full rewrite to standalone shape, dead-code findings, `userUuid` rename),
    `gateway/CLAUDE.md` (full rewrite removing all "ai-service still embedded" language). Not yet
    run/booted against a real Postgres/Keycloak in this session — same unverified-at-runtime caveat
    every standalone extraction in this repo has carried at this stage.

- **`content-service` extraction started — the fifth module targeted for standalone-service
  extraction, and architecturally the hardest of the five so far, since `ai-service`'s coupling to
  it is real, deep, and bidirectional (direct repository access, real FKs, a write-back on
  `ContentItem.qualityScore`) rather than a removable leftover like the previous four modules'
  `common.entity.User` couplings. Plan: real HTTP calls from `ai-service` to `content-service`
  (chosen over reviving the old JPA coupling some other way) — the first genuine inter-service
  network call in this reactor. Landed so far, in order:
  - **Step 1 — `ContentType`/`ContentStatus`/`QuestionDifficulty` moved from `content-service` to
    `common`.** `ai-service` uses all three on its own public REST contracts
    (`ChatRequest.sourceTypes`, `RagFilter`, `PublicContentApi`) and internal indexing filters, not
    just as `content-service`-internal plumbing — once `content-service` stops being a Maven
    dependency, a plain cross-service Java import breaks. 33 files across both modules updated to
    import from `common.enums` instead. `TagStatus` stayed in `content-service` — confirmed via
    grep it has no consumer outside that module.
  - **Step 2 — `content-service`'s internal indexing API built.** New `/internal/content-items/**`
    surface (`InternalContentApi`/`Controller`, `InternalContentService`/`Impl`,
    `InternalContentItemMapper`, `dto/internal/*`, `ContentItemSpecification`) that `ai-service`'s
    indexing layer will call over HTTP once its own rewrite lands (a later step) — built ahead of
    that rewrite so the contract exists first. Gated by a shared-secret header
    (`InternalApiKeyFilter`, `app.internal-api.key`/`INTERNAL_API_KEY`) rather than an end-user JWT;
    `gateway`'s `SecurityConfig` marks `/internal/**` `permitAll()` so the filter (not Spring
    Security) enforces it. New `ContentErrorCode.CONTENT_ITEM_NOT_FOUND` for the generic
    `ContentItem` lookup (previously only reachable via `ai-service`'s own unscoped
    `ResourceNotFoundException` throw, which had no `ErrorCode` at all). See
    `content-service/CLAUDE.md` for the full endpoint list and the DTO-shape rationale.
  - **Step 3 (standalone app shell) merged into a later step, not built yet.** Adding
    `ContentServiceApplication`/`SecurityConfig`/`KeycloakJwtAuthenticationConverter` now, while
    `gateway` still has a Maven dependency on `content-service`, would give `gateway`'s own
    component scan a second, conflicting `SecurityFilterChain` before `content-service` ever runs
    standalone — `task-service`/`social-service` never hit this because their app-class + security
    + Maven-dependency-removal all landed in one atomic step. Deferred to land together with the
    Maven-dependency removal instead.
  - **Step 4 — `ai-service`'s indexing pipeline rewritten to call step 2's HTTP API instead of
    injecting `content-service`'s repositories.** `ContentIndexingServiceImpl`/
    `EmbeddingIndexServiceImpl` now go through a new `ContentServiceClient` (Spring `RestClient`)
    instead of `ContentItemRepository`/`QuestionAnswerRepository`/`ArticleRepository` — the first
    genuine inter-service HTTP call in this reactor (today it's a loopback call to the same
    process; only `app.content-service.base-url` changes once `content-service` is truly
    standalone). `ContentEmbedding.contentItem` (a `@ManyToOne` FK) became a plain
    `contentItemId: Integer` column; `ContentIngestionService.ingest(...)` takes
    `Integer contentItemId, ContentType sourceType` instead of a live `ContentItem`.
    `EmbeddingIndexServiceImpl`'s `indexed` filter — previously a JPA Criteria `EXISTS` subquery
    joining `ContentEmbedding` against `ContentItem` — can't survive the split as one query; it now
    reads the embedded-id set from its own database first, then asks `content-service`'s API to
    intersect (`ids=`) or exclude (`excludeIds=`, a new filter added to the internal API
    specifically for this) that set as part of its own paginated query, keeping pagination correct
    without an in-memory scan. `PublicContentApi`/`PublicContentController` and
    `ContentPublishedEventListener` were **not** touched — they still use `content-service`'s
    services/repositories directly and move in a later step, so `ai-service`'s Maven dependency on
    `content-service` stays for now.
  - **Bonus fix found while touching `ContentEmbedding`: `ai-service`'s `SearchDocument` entity was
    dead code.** Zero consumers anywhere in the reactor (no repository, no service, no controller)
    and mapped to a `SEARCH_DOCUMENT` table no Liquibase changeset ever created — with
    `hibernate.ddl-auto: validate` active, this would have failed application startup the moment
    anything forced Hibernate to validate it. Deleted outright rather than converted.
  - **Step 5 — `PublicContentApi`/`PublicContentController` moved back into `content-service`; the
    `ai-service` → `content-service` Maven dependency is now fully removed.** These two classes
    only ever fronted `content-service`'s own `ArticleService`/`QuestionAnswerService`/mappers/
    `ContentItemRepository` — nothing ai-service-specific — so leaving them in `ai-service` was
    drift from before `content-service` owned its own REST layer at all. Combined with step 4's
    HTTP rewrite, `ai-service` no longer references a single `content-service` class; its `pom.xml`
    dependency on `content-service` was deleted in the same pass. `content-service` and `ai-service`
    are now two parallel siblings with **no** dependency relationship in either direction — both
    depend only on `common`+`infra` — updating the root `CLAUDE.md` dependency-order description
    that had stood since `ai-service`'s original build (`common` ← `infra` ← `content-service` ←
    `ai-service`). Also deleted in this step: `ai-service`'s dead `ContentPublishedEventListener`
    (its event never had a publisher wired up, and an in-process Spring event can't cross a service
    boundary once the two modules are actually separate deployables) — a dangling Javadoc
    `{@link ContentPublishedEventListener}` in `PipelineCompletedEventListener` was fixed in the
    same pass.
  - **Step 6 — `content-service` given its own `content` schema + Liquibase changelog tree,
    dormant for now.** New `database/sql/content-service.xml` + `DKP-0031__add_content_service_tables.sql`
    — a fresh snapshot of `CATEGORY`/`TAG`/`CONTENT_ITEM`/`CONTENT_ITEM_TAG`/`QUESTION_ANSWER`/
    `ARTICLE`'s final shape as of `gateway`'s own `DKP-0018`, not a replay of that tree's
    incremental history (same convention `task-service`'s `DKP-0028` and `social-service`'s
    `DKP-0029`/`DKP-0030` already followed). Not live yet — no standalone
    `content-service-liquibase.yml` compose file exists to run it (that's step 8), so `gateway`'s
    own changelog tree stays authoritative for now; new schema changes still go there until this
    module's own tree is actually wired up. **Bonus fix in the same pass**: all 6 of this module's
    entities (`Category`/`Tag`/`ContentItem`/`ContentItemTag`/`QuestionAnswer`/`Article`) still
    hardcoded `@Table(schema = "product")` — the same bug class as the `common.entity.User`/
    `task-service`/`social-service` incidents (an explicit `@Table(schema=...)` always wins over
    `hibernate.default_schema`). Fixed to resolve via `hibernate.default_schema` instead, before
    this module ever ran against the new `content` schema — no behavior change today (still
    resolves to `gateway`'s `product`), but unblocks a future standalone `application.yml` setting
    `hibernate.default_schema: content` from actually taking effect.
  - **Step 7 — `content-service` given a full standalone app shell (deferred from step 3), and
    `gateway`'s Maven dependency finally removed.** New `ContentServiceApplication`
    (`@ConfigurationPropertiesScan`, no `@EntityScan`/`@EnableJpaRepositories`), `security/`
    (`SecurityConfig` mirroring `gateway`'s old three-way public/internal/admin rule set for these
    same paths, `KeycloakRealmRoleConverter`, `KeycloakJwtAuthenticationConverter`,
    `CurrentUserResolver`), `config/web/` (`CurrentUserIdArgumentResolver`, `WebMvcConfig`), own
    `application.yml` (port `8085`, `content` schema), and `pom.xml` additions (security/
    oauth2-resource-server/postgres/actuator/validation/spring-boot-maven-plugin; oauth2-client
    changed from `optional=true` to a real runtime dependency) — same shape `task-service`'s/
    `ecommerce-service`'s own shells already established.
    - **`ContentItem.authorId: Integer` → `authorUuid: String`, claims-based, mirroring
      `task-service`'s `ownerUuid`.** The old field was populated via a one-off
      `common.UserRepository.findByEmail(...).map(User::getId)` lookup, write-once, never read
      back — exactly the "Option C" shape (see the `project-microservices-extraction-plan`
      memory) that needs zero persisted `User` copy. `ArticleController`/`QuestionAnswerController`
      now take `@CurrentUserId String authorUuid` directly instead of
      `@AuthenticationPrincipal CustomOAuth2User` + a `UserRepository` lookup.
      `DKP-0031` (never executed against any real database) was edited in place to carry
      `AUTHOR_UUID` from the start, mirroring `task-service`'s own `ownerId`→`ownerUuid`
      correction, rather than adding a follow-up `ALTER` changeset.
    - **`gateway`'s `DataSeedingRunner` narrowed to `UserSeeder` only** — `CategorySeeder`/
      `TagSeeder`/`QuestionAnswerSeeder` moved into a new `content-service`-owned
      `DataSeedingRunner`, and their seed data files (`data/csv/categories.csv`, `data/csv/tags.csv`,
      `data/question-answers/*.md`, 100 files) physically moved from `gateway/src/main/resources/`
      into `content-service/src/main/resources/`, since `gateway`'s classpath no longer includes
      this module's jar.
    - **`gateway`'s own `SecurityConfig` lost its now-dead `/api/v1/public/**` and `/internal/**`
      `permitAll()` rules** (both were `content-service`-only concerns) and its `pom.xml`/
      `Dockerfile` lost the `content-service` dependency/`COPY` line — the latter mirrors the exact
      stale-`COPY`-line bug class already caught once during `social-service`'s own extraction
      (that time it was a leftover `task-service` line). `gateway`'s
      `app.content-service.base-url` (read by `ai-service`'s `ContentServiceClient`, still embedded
      here) now points at a genuinely separate process (`http://localhost:8085` locally) instead of
      a same-process loopback; `app.internal-api.key` was removed from `gateway`'s own
      `application.yml` entirely, since `InternalApiKeyFilter`/`InternalApiProperties` no longer run
      in this process at all.
    - `content-service` and `gateway` are now full standalone siblings with no dependency
      relationship, same status `content-service` and `ai-service` already reached in step 5 —
      updated root `CLAUDE.md`'s Module Structure table, Security section, and Database Conventions
      section accordingly (`content-service` is now listed as a sixth standalone service alongside
      `ecommerce-service`/`identity-service`/`task-service`/`social-service`; `gateway`'s own
      `product.CATEGORY`/`TAG`/`CONTENT_ITEM`/etc. changesets are now frozen, orphaned history, same
      status the old `PROJECT`/`TASK`/friend-graph changesets already had).
  - **Step 8 — Docker/compose wiring.** New `content-service/Dockerfile` (port `8085`, mirrors
    `task-service`'s exactly), `content-service-liquibase.yml` (standalone migration-runner compose
    file), and a `content-liquibase`+`content-service` pair added to
    `dev-knowledge-platform-apps-docker-compose.yml`. `gateway`'s own container gained
    `CONTENT_SERVICE_BASE_URL: http://content-service:8085` and a shared `INTERNAL_API_KEY` env var
    (both default to a dev-only placeholder, override in production) — the first app container in
    this compose file with a genuine runtime dependency on another standalone service's Compose
    service name, since `ai-service`'s `ContentServiceClient` (still embedded in `gateway`) now
    reaches `content-service` as a real separate container instead of a same-process loopback. Not
    yet run against a real Postgres/Keycloak in this session — same unverified-at-runtime caveat
    every standalone extraction has carried at this stage.
  - Remaining step (final consolidated docs pass) not started yet — tracked in-session, not yet
    reflected elsewhere in this file.

- **`social-service` extracted into a standalone Spring Boot application — the fourth module pulled
  out of the monolith, following the `ecommerce-service`/`identity-service`/`task-service`
  precedent. Unlike the previous three, this one had to bring real-time transport with it, not just
  REST, and made a deliberate architectural choice the previous three didn't: no shared entity with
  `common.entity.User` at all.**
  - **No coupling to `common.entity.User`, by design, not accident.** Discussed directly with the
    user: this module genuinely needs a real local identity row (friend search, group membership,
    DM threads all search/list/join across *other* users), ruling out `task-service`'s
    claims-based-only approach — but reusing the shared `common.entity.User` entity (`gateway`'s/
    `identity-service`'s pattern) was explicitly rejected in favor of a new, module-local
    `entity.SocialProfile` (table `social.PROFILE`) carrying only the columns this module's code
    actually reads/writes (verified by grepping real usages): `profileUuid`/`keycloakSubjectId`/
    `email` for JIT-provisioning, `username`/`firstName`/`lastName`/`profilePicture`/`status` for
    search/display, `seedId` for seeding. No `password`, OAuth `provider`, `role`, `emailVerified`,
    or `enabled` — this module has no auth-lifecycle concern and nothing reads those fields.
    `role`/`provider`/`emailVerified` were also trimmed out of the public `UserInfoResponse` API
    shape (previously riding along via MapStruct's automatic same-name field mapping from `User`) —
    confirmed safe first via a `gui` grep: the only real caller of `getPublicProfile`
    (`MessagesPage.tsx`, fetching a DM recipient's header) reads only `username`/`firstName`/
    `lastName`/`profilePicture`/`status`; `role`/`provider`/`emailVerified` are only ever read off
    the *logged-in user's own* account dashboard, an entirely separate `identity-service` endpoint.
  - **Every relationship in this module's entity graph repointed at `SocialProfile`:**
    `FriendRequest.requester`/`addressee`, `Friendship.user1`/`user2`, `UserBlock.blocker`/
    `blocked`, `GroupMember.user`, `DmThread.user1`/`user2`, `DmMessage.sender`,
    `ChannelMessage.sender` — all 7 entities, plus every repository, `UserSpecification`,
    `FriendMapper`/`MessagingMapper`, `UserController`, `FriendServiceImpl`/`GroupServiceImpl`/
    `DmServiceImpl`/`DmMessagingController`, and all three friend-graph/chat seeders
    (`FriendGraphSeeder`/`UserBlockSeeder`/`DmThreadSeeder`), replacing every `common.entity.User`/
    `common.repository.UserRepository` reference with this module's own `SocialProfile`/
    `SocialProfileRepository`.
  - **Bonus fix found while touching these entities: all 9 (`FriendRequest`, `Friendship`,
    `UserBlock`, `Group`, `GroupMember`, `Channel`, `DmThread`, `DmMessage`, `ChannelMessage`) still
    hardcoded `@Table(schema = "product")`** — a stale leftover from before the extraction. Per this
    exact incident's precedent for `common.entity.User` (see the Database Conventions entry
    elsewhere in this file), an explicit `@Table(schema=...)` always wins over
    `hibernate.default_schema`, so this module's Hibernate would have validated/queried against
    `product.*` instead of its own `social.*` tables. Fixed to `schema = "social"` alongside the
    owner-field rework, before this ever ran against a real database.
  - **Full WebSocket/STOMP transport relocated from `gateway`, not just REST** — `security/
    WebSocketConfig`, `StompAuthChannelInterceptor`, and `config/web/CurrentUserIdMessageArgumentResolver`
    all moved here verbatim (adjusted to call this module's own `KeycloakJwtAuthenticationConverter`/
    `SocialProfileRepository` instead of `gateway`'s), since chat's WebSocket endpoint was
    `gateway`'s only STOMP use case — `gateway`'s own copies of all three were deleted outright, and
    its now-unused `spring-boot-starter-websocket` dependency was removed from its `pom.xml` too.
    `gateway`'s `SecurityConfig` lost its now-dead `/ws/**` and `/api/v1/users/public/**`
    `permitAll` rules (both routes no longer exist in `gateway`'s own context).
  - **Seed data duplicated, not shared** — `social-service` has no access to `gateway`'s
    resources/classpath at runtime anymore, so a new `SocialProfileSeeder` (+ this module's own copy
    of `data/csv/users.csv`, same `id`/seed-key values as `gateway`'s) seeds the same 20 demo
    accounts independently into `social.PROFILE`, keeping `FriendGraphSeeder`/`UserBlockSeeder`/
    `DmThreadSeeder` working unchanged. This module's own new `service.seed.DataSeedingRunner`
    orchestrates all four in order; `gateway`'s `DataSeedingRunner` no longer references any of the
    three friend-graph/chat seeders (it stopped compiling the moment the Maven dependency dropped) —
    narrowed to category/tag/question-answer/user only. `data/csv/friend-requests.csv`/
    `user-blocks.csv` deleted from `gateway`'s resources (moved to `social-service`'s own, alongside
    its own copy of `users.csv`) since nothing in `gateway` reads them anymore.
  - **New Liquibase changelog tree** (`social-service/.../database/sql/social-service.xml` +
    `2026/0.0.2/*.sql`), applied via a new standalone `social-service-liquibase.yml` docker-compose
    file: `DKP-0029` adds `social.PROFILE`; `DKP-0030` adds a fresh snapshot of the friend-graph +
    chat tables (final shape of `gateway`'s old `DKP-0015`/`DKP-0019`), with every FK repointed at
    `social.PROFILE` instead of `product.USER`. `gateway`'s old `DKP-0015`/`DKP-0019` changesets are
    untouched (frozen, already-run history) but now describe an orphaned table set `gateway`'s own
    Spring context no longer maps any entity to.
  - **Dropped `gateway`'s Maven dependency on `social-service`** (`gateway/pom.xml`) — the reactor
    now splits into five independent Maven-dependency clusters: `gateway` (content/ai-service only
    now), `ecommerce-service`, `identity-service`, `task-service`, `social-service` (the latter four
    depending only on `common`+`infra`).
  - **Test suite relocated in full, not deferred** — `AbstractStompIntegrationTest`/
    `DmMessagingStompIntegrationTest` (`gateway`'s only tests) moved to `social-service`'s own
    `src/test`, now booting `SocialServiceApplication` instead of `gateway`'s context. Dropped the
    Redis Testcontainer this suite used to need — `social-service` has no Redis-backed bean of its
    own (unlike `gateway`, which needed it for `RedisCacheConfig`/`ChatRateLimiter`). Testcontainers
    (`junit-jupiter`, `postgresql`) and `dasniko:testcontainers-keycloak` test dependencies moved
    from `gateway/pom.xml` to `social-service/pom.xml` alongside the tests; `gateway` now has no
    test suite of its own.
  - **Docker/compose wiring:** `social-service/Dockerfile` (port `8084`, mirrors `identity-service`'s),
    standalone `social-service-liquibase.yml` at the repo root, a new `social-liquibase` +
    `social-service` pair in `dev-knowledge-platform-apps-docker-compose.yml` (same `dev-premier`
    database, own `social` schema, `MINIO_ENDPOINT`/`APP_SEED_ENABLED` env vars — the only one of
    the four standalone extractions so far that needs a real MinIO connection, for avatar/attachment
    presigned URLs). Added the missing `app.storage.*` MinIO configuration block to
    `social-service/application.yml`, mirroring `gateway`'s.
  - **Bonus fix found while editing `gateway/Dockerfile`: a stale `COPY task-service task-service`
    line left over from `task-service`'s own extraction** (that module dropped its Maven dependency
    on `gateway` earlier in this same body of work, but its `Dockerfile` `COPY` line was missed at
    the time). Removed alongside the new `social-service` removal.
  - **Verification:** full reactor `./mvnw clean test-compile` passed after every step through the
    Docker/compose wiring. The final documentation-only pass could not be re-verified against a
    fresh build — the local environment's Maven/JVM processes became unresponsive partway through
    (orphaned processes from earlier long-running builds in this session, unrelated to any source
    change) — but no source, build, or resource file was touched after the last successful
    `test-compile`, only Markdown documentation.

- **Follow-up correction to the `task-service` extraction below: this module never actually needed
  a persisted `User` row either — same "Option C" shape as the `ecommerce-service` correction
  above, for a different concrete reason.** Prompted by the question "why do we now have three
  copies of `USER` (`product`/`identity`/`task`) for one Keycloak identity" — the answer this time
  wasn't "no entity has a user FK at all" (that's `ecommerce-service`'s case), it was "the FK exists,
  but every use of it only ever compares two UUIDs." `Project.owner`/`Task.owner` are real
  relationships in the domain sense, but `ProjectServiceImpl`/`TaskServiceImpl` never *join* through
  them for anything beyond "does this row's owner match the caller" — no listing/searching other
  users, no displaying another user's username/avatar. That specific check is exactly as well
  answered by comparing the row's owner column to the verified JWT's own `sub` claim, with zero
  database access, as it is by loading a `User` entity and comparing numeric PKs.
  - **Reworked:** `Project.owner`/`Task.owner` (`@ManyToOne User`, `OWNER_ID` FK) became
    `Project.ownerUuid`/`Task.ownerUuid` (plain `String`, `OWNER_UUID` column, indexed, no FK).
    `ProjectRepository.findByOwner(User, Pageable)` → `findByOwnerUuid(String, Pageable)`;
    `TaskSpecification.withFilters`'s `ownerId: Integer` param → `ownerUuid: String`, predicate
    changed from `root.get("owner").get("id")` to `root.get("ownerUuid")`. Every
    `ProjectService`/`TaskService` method's `Integer ownerId` param became `String ownerUuid`;
    `ProjectServiceImpl`/`TaskServiceImpl` lost their `UserRepository` dependency and
    `resolveUser`/`CommonErrorCode.USER_NOT_FOUND` handling entirely — there's no user row left to
    resolve. `ProjectApi`/`TaskApi`'s `@CurrentUserId Integer userId` params became
    `@CurrentUserId String ownerUuid` throughout, propagated through both controllers.
  - **Rewritten:** `task-service`'s `KeycloakJwtAuthenticationConverter` no longer touches
    `UserRepository` — it builds `CustomOAuth2User` directly from `jwt.getSubject()`/
    `getClaimAsString(...)`, no persistence, no `@Transactional`, mirroring
    `ecommerce-service`'s converter of the same name instead of `gateway`'s/`identity-service`'s.
    `CurrentUserResolver.resolveUserId(Principal, UserRepository)` → `resolveOwnerUuid(Principal)`,
    reading the principal's UUID directly with no repository parameter at all.
    `CurrentUserIdArgumentResolver` lost its `UserRepository` dependency and now matches
    `String`-typed `@CurrentUserId` parameters instead of `Integer`-typed ones.
  - **Reverted:** `TaskServiceApplication`'s `@EntityScan`/`@EnableJpaRepositories` widening onto
    `common.entity`/`common.repository` (this module touches neither anymore) — now matches
    `EcommerceServiceApplication`'s shape exactly (no annotations at all, default scanning covers
    this module's own `entity`/`repository` packages).
  - **Liquibase collapsed back to one changeset:** deleted the standalone `DKP-0028` (`task.USER`
    table) migration entirely; the `DKP-0029` `task.PROJECT`/`task.TASK` migration became the sole
    `DKP-0028` in this module's tree, with `OWNER_UUID` (`VARCHAR(36)`, indexed, no FK) replacing
    `OWNER_ID` — no `task.USER` table exists at all now. Nothing had been committed or run against a
    real database yet, so this was a clean rewrite, not a follow-on drop migration.
  - **Bonus fix found while touching these entities: `Project`/`Task` still hardcoded
    `@Table(schema = "product")`** — a stale leftover from before the extraction. Per this exact
    incident's precedent for `common.entity.User` (see the Database Conventions entry below), an
    explicit `@Table(schema=...)` always wins over `hibernate.default_schema`, so `task-service`'s
    Hibernate would have validated/queried against `product.PROJECT`/`product.TASK` instead of its
    own `task.PROJECT`/`task.TASK` — the exact isolation-breaking bug the `User`-schema incident was
    about, just on a different pair of entities. Fixed to `schema = "task"` alongside the owner-field
    rework, before this ever ran against a real database.
  - **Scope note:** `gateway`'s own `product.USER` persistence (needed by `Friendship`/
    `FriendRequest`/`GroupMember`/`DmThread` and other real relational/listing needs in
    `social-service`) and `identity-service`'s `identity.USER` are both unaffected and unchanged —
    this correction applies only to `task-service`, which never had `social-service`-style needs to
    begin with.
  - **Verification:** full reactor `./mvnw clean compile` passes.

- **`task-service` extracted into a standalone Spring Boot application — the third module pulled
  out of the monolith, following the `ecommerce-service`/`identity-service` precedent.** Unlike
  `identity-service`'s extraction, this one needed no in-process call-site rewrite anywhere:
  `gateway` never called into `task-service`'s Java classes directly — `ProjectApi`/`TaskApi` were
  already this module's own REST layer, just riding on `gateway`'s Spring context via the Maven
  dependency — so dropping that dependency was a pure `pom.xml` edit, no behavior change to fix up.
  - **`task-service`'s own standalone app shell:** `TaskServiceApplication` entry point
    (`@EntityScan`/`@EnableJpaRepositories` covering both this module's own `entity`/`repository`
    packages and `common.entity`/`common.repository` — this module has a real relational need for
    `common.entity.User`, unlike `ecommerce-service`, since `Project.owner`/`Task.owner` are genuine
    FKs), its own `SecurityConfig` (everything requires auth except `/actuator/**`, no public/
    admin-only surface — single-user personal task tracker), a `KeycloakRealmRoleConverter`
    duplicated from `gateway`'s/`identity-service`'s/`ecommerce-service`'s, and a
    `KeycloakJwtAuthenticationConverter` that inlines the JIT-provisioning logic directly (like
    `gateway`'s/`ecommerce-service`'s, not delegated like `identity-service`'s, since this module
    has no other in-process service to delegate to). Ported `CurrentUserResolver`/
    `CurrentUserIdArgumentResolver`/`WebMvcConfig` from `gateway` (duplicated, not shared — no STOMP
    transport here, so no message-argument-resolver counterpart is needed). Added what a standalone
    resource server needs to `task-service/pom.xml` (`spring-boot-starter-security`,
    `oauth2-resource-server`, `oauth2-client` — needed for `CustomOAuth2User`'s `OAuth2User`
    supertype even though no controller here takes `@AuthenticationPrincipal` directly, `postgres`,
    `actuator`, `spring-boot-maven-plugin`, plus the same `src/main/java`-resource-inclusion `build`
    block `identity-service`/`ecommerce-service` use for their own bundled Liquibase changelogs).
  - **This module now persists its own `task.USER` row, same reasoning as `identity-service` (not
    `ecommerce-service`'s "Option C").** `Project.owner`/`Task.owner` are genuine `@ManyToOne User`
    foreign keys and `CurrentUserResolver` looks up the caller's numeric PK from it — a bare JWT
    claim can't satisfy that the way it does for `ecommerce-service`.
  - **New Liquibase changelog tree** (`task-service/.../database/sql/task-service.xml` +
    `2026/0.0.2/*.sql`), applied via a new standalone `task-service-liquibase.yml` docker-compose
    file at the repo root: `DKP-0028` adds this module's own `task.USER` table (a fresh snapshot of
    `gateway`'s `product.USER` as of `DKP-0025`, not a replay of that migration history — same
    approach `identity-service`'s `DKP-0026` took), `DKP-0029` adds `task.PROJECT`/`task.TASK` as a
    fresh snapshot of the *final* shape those tables reached in `gateway`'s tree (post-`DKP-0022` —
    no `CONTENT_ITEM_ID` column at all, `PARENT_TASK_ID` present from the start), not a replay of
    that tree's own `DKP-0020`→`DKP-0021`→`DKP-0022` incremental history. `gateway`'s old
    `DKP-0020`/`DKP-0021`/`DKP-0022` changesets are untouched (frozen, already-run history, per this
    repo's never-edit-an-executed-changeset convention) but now describe an orphaned
    `product.PROJECT`/`product.TASK` pair `gateway`'s own Spring context no longer maps any entity
    to.
  - **Dropped `gateway`'s Maven dependency on `task-service`** (`gateway/pom.xml`) — the reactor now
    splits into four independent Maven-dependency clusters: `gateway` (still pulling in content/ai/
    social-service), `ecommerce-service`, `identity-service`, and `task-service` (the latter three
    depending only on `common`+`infra`).
  - **Docker/compose wiring:** `task-service/Dockerfile` (port `8083`, mirrors `identity-service`'s),
    standalone `task-service-liquibase.yml` at the repo root, and a new `task-liquibase` +
    `task-service` pair in `dev-knowledge-platform-apps-docker-compose.yml` (same `dev-premier`
    database, own `task` schema, same env-var-only datasource config convention as
    `ecommerce-service`/`identity-service` — no base-profile `spring.datasource` block).
  - **Verification:** full reactor `./mvnw compile` passes at every step (app shell, gateway
    dependency removal, Liquibase resource packaging). Still not booted against real Postgres —
    same unverified-at-runtime caveat as `ecommerce-service`/`identity-service`.

- **Follow-up correction to the `identity-service` extraction below: `ecommerce-service` doesn't
  need a persisted `User` row at all, and the fix applied to give it one was reverted.** Prompted
  by the question "why do we have both `ecommerce.USER` and `identity.USER`" — the answer surfaced
  that `ecommerce-service` has no entity with a foreign key onto a user, so the only thing its
  `KeycloakJwtAuthenticationConverter` ever needed was "who is the caller, and are they admin,"
  both fully answerable from the verified JWT's claims (`sub` standing in for `userUuid`) with zero
  DB access. This is "Option C" from a broader options discussion (vs. "Option A," a synchronous
  call to `identity-service`'s API, and "Option B," event-driven read-model replication via the
  outbox pattern this module already uses for `ProductSearchView` — reserved for if/when a future
  feature, e.g. Epic 5's reviews, needs to *display* another user's info, not needed today).
  - **Reverted:** the `DKP-0027__add_ecommerce_user_table.sql` migration (deleted), `common.entity.User`/`common.repository.UserRepository`
    dependency, and the `@EntityScan`/`@EnableJpaRepositories` annotations on
    `EcommerceServiceApplication` added for the `identity-service` extraction below.
  - **Rewritten:** `ecommerce-service`'s `KeycloakJwtAuthenticationConverter` no longer touches
    `UserRepository` — it builds `CustomOAuth2User` directly from `jwt.getSubject()`/`getClaimAsString(...)`,
    no persistence, no `@Transactional`.
  - **Still true, unchanged:** `gateway` and `identity-service` both still persist their own
    independent local `User` copy (`product.USER`/`identity.USER`) — they have real relational
    needs (`Task.owner`, `FriendRequest`/`Friendship`, `identity-service`'s own profile-mutation
    endpoints) that a bare JWT claim can't satisfy. `ecommerce-service` was the one case where the
    original "give every deployable its own `User` copy" fix was actually unnecessary.
  - **Verification:** full reactor `./mvnw compile` passes. Still not booted against real Postgres.

- **`identity-service` extracted into a standalone Spring Boot application — the second module
  pulled out of the monolith, following the `ecommerce-service` precedent.** Done step-by-step,
  each step compiled and verified before the next (see the `project-microservices-extraction-plan`
  memory for the full sequencing):
  - **`gateway` and `ecommerce-service` no longer call `identity-service`'s `UserService` in
    process.** Each now has its own `KeycloakJwtAuthenticationConverter` that JIT-provisions/
    refreshes its own local `User` row directly via `common`'s `UserRepository` (deliberately
    duplicated, not shared) — `gateway`'s copy is new; `ecommerce-service` already had one.
  - **`social-service`'s `UserApi` (`getPublicProfile`/`search`) no longer depends on
    `identity-service`.** Added a local `UserInfoResponse` DTO and a `toUserInfo` mapping method on
    `social-service`'s own `FriendMapper`; the base lookup now goes through `common`'s
    `UserRepository` directly. Removed the `identity-service` Maven dependency from
    `social-service/pom.xml` — surfaced a real gap: `spring-boot-starter-security` and
    `spring-boot-starter-oauth2-client` (`optional=true`) had been arriving transitively via that
    dependency and needed adding directly, for `@AuthenticationPrincipal CustomOAuth2User`.
  - **`UserSeeder` relocated from `identity-service` to `gateway`** (same package as
    `DataSeedingRunner`) — pure move, no logic change; it only ever wrote via `common`'s
    `UserRepository`. `identity-service` needs no seed data of its own: a seeded demo account has
    no real Keycloak identity, so its own `identity.USER` table only ever fills via
    JIT-provisioning on an actual login.
  - **Major fix, found mid-extraction: `common.entity.User`'s `@Table` hardcoded
    `schema = "product"`.** An explicit `@Table(schema=...)` always wins over an app's own
    `hibernate.default_schema` property, so every deployable using this entity — including
    `ecommerce-service`, already shipped — was silently writing `User` rows into the monolith's
    `product.USER` table regardless of its own intended schema, defeating per-service-per-schema for
    the one entity every service needs. Removed the hardcode so each app's own
    `hibernate.default_schema` (`product`/`ecommerce`/`identity`) resolves it at runtime. Added the
    now-required physical `USER` table migration for both `identity-service` (`DKP-0026`, new
    `identity` schema) and, as a direct consequence, `ecommerce-service` (`DKP-0027`, fixing its own
    latent isolation gap that this bug had been masking).
  - **Second bug found while writing this changelog entry: neither `IdentityServiceApplication` nor
    `EcommerceServiceApplication` had `@EntityScan`/`@EnableJpaRepositories`.** Spring Boot's default
    JPA scanning is scoped to the main class's own package tree, but `common.entity.User`/
    `common.repository.UserRepository` live outside both (`com.ttg.devknowledgeplatform.common.*`) —
    meaning `UserRepository` would never become a bean in either app, and
    `KeycloakJwtAuthenticationConverter` (which both apps inject it into) would fail at startup.
    This was a pre-existing bug in `ecommerce-service` (never caught since it's never been booted
    against real Postgres) that got replicated into `identity-service`'s new shell by copying the
    same pattern; fixed in both.
  - **`identity-service`'s own standalone app shell:** `IdentityServiceApplication` entry point, its
    own `SecurityConfig` (everything requires auth except `/actuator/**`), a
    `KeycloakRealmRoleConverter` duplicated from `gateway`/`ecommerce-service`, and a
    `KeycloakJwtAuthenticationConverter` that — unlike the other two — still delegates to its own
    in-process `UserService.findOrCreateFromKeycloak`, since both live in the same app now. Cleaned
    up `identity-service/pom.xml`'s three dead dependency groups left over from the Keycloak
    migration (JJWT, Redis-for-blacklist, mail-for-OTP) and added what a standalone resource server
    needs (`oauth2-resource-server`, `postgres`, `actuator`, `spring-boot-maven-plugin`).
  - **Dropped `gateway`'s Maven dependency on `identity-service`** (`gateway/pom.xml`) — the reactor
    now splits into three independent Maven-dependency clusters: `gateway` (still pulling in
    content/ai/task/social-service), `ecommerce-service`, and `identity-service` (the latter two
    depending only on `common`+`infra`).
  - **Docker/compose wiring:** `identity-service/Dockerfile` (port `8082`, mirrors
    `ecommerce-service`'s), standalone `identity-service-liquibase.yml` at the repo root, and a new
    `identity-liquibase` + `identity-service` pair in
    `dev-knowledge-platform-apps-docker-compose.yml` (same `dev-premier` database, own `identity`
    schema). Also removed a now-stale `COPY identity-service identity-service` line from
    `gateway/Dockerfile` — gateway's `-am` build hasn't needed those sources since the Maven
    dependency was dropped, only the `pom.xml` (for reactor parsing).
  - **Not yet built:** the `gateway`-side HTTP proxy to `identity-service` — until that exists, its
    two endpoints (`GET /api/v1/auth/user`, `PUT/POST /api/v1/users/me*`) are only reachable directly
    on its own port, same limitation `ecommerce-service` already has.
  - **Not yet verified:** none of `identity-service`'s or `ecommerce-service`'s Docker/schema wiring
    has actually been run against real Postgres/Keycloak in this pass — full reactor compiles
    (`./mvnw compile` across all 9 modules) but a real `docker compose up` may still surface issues
    the compiler can't catch (Liquibase migration correctness, `ddl-auto: validate`, the
    `@EntityScan`/schema fixes actually resolving as designed).
  - **Known pre-existing gap, parked, not fixed in this pass:** `gateway`'s `UserSeeder` (post-move)
    injects a `PasswordEncoder` bean that no longer exists anywhere in the reactor —
    `PasswordEncoderConfig` was deleted outright during the Keycloak migration (see `identity-service`
    entries below) but this dependency was never removed. Local dev seeding
    (`app.seed.enabled=true`) may already fail at runtime with an unsatisfied-dependency error.

- **Docker deployment scaffolding for the two independently-runnable Spring Boot processes in this
  repo, `gateway` and `ecommerce-service`** — first concrete step of a longer-term move toward true
  microservices (each embedded module eventually extracted to its own deployable, following the
  `ecommerce-service` precedent). This pass is scoped to deployment scaffolding only.
  - New `gateway/Dockerfile` and `ecommerce-service/Dockerfile` — multi-stage (`maven:3.9.9-eclipse-temurin-21`
    build stage using `mvn -pl <module> -am package` against the reactor, `eclipse-temurin:21-jre-jammy`
    runtime stage, non-root user). Build context is the repo root in both cases, since the Maven
    reactor build needs sibling-module sources.
  - New root-level `dev-knowledge-platform-apps-docker-compose.yml` — brings up both app containers
    plus one-shot Liquibase migration runners (`dkp-liquibase`, `ecommerce-liquibase`), wired via
    `depends_on`/`service_completed_successfully`/`service_healthy` to the existing infra compose
    file's `postgres`/`redis`/`minio`/`keycloak` containers. **Must be run combined with
    `dev-knowledge-platform-docker-compose.yml` via two `-f` flags in one command** — service-name
    DNS only resolves when both files share one Compose project:
    `docker compose -f dev-knowledge-platform-docker-compose.yml -f dev-knowledge-platform-apps-docker-compose.yml up -d --build`.
  - New root-level `.dockerignore`.
  - **No changes to `common`, `infra`, or the root `pom.xml`** — all three already function as the
    shared-foundation modules a microservices split needs (proven by `ecommerce-service` already
    depending on `common`+`infra` as ordinary library jars with zero Maven dependency on `gateway`);
    extracting `gateway`'s Redis-cache config into `infra` now would have no real second consumer
    yet, so it stays deferred. **No source changes inside `ecommerce-service` either** — its
    container datasource config is supplied via plain `SPRING_DATASOURCE_*` environment variables
    in the new compose file (Spring Boot's relaxed binding), not a new `application-docker.yml`.
  - **Out of scope for this pass:** Kafka/RabbitMQ or any other messaging, a `kubernetes/`
    directory, and splitting any of `content-service`/`ai-service`/`task-service`/`social-service`/
    `identity-service` out of `gateway` — all deferred to later, separate phases.
  - **Not verified:** this is the first time `ecommerce-service` boots against a real Postgres/
    Keycloak at all (per its own `CLAUDE.md`, its Liquibase migration, `ddl-auto: validate` check,
    and JWT verification path were previously unexercised at runtime) — a first `docker compose up`
    may surface a pre-existing runtime issue unrelated to this Docker work itself.

- **Phase 3 of the Keycloak migration: `ecommerce-service` becomes a real OAuth2 resource server,
  the same as `gateway` in Phase 2 — completing the switch across both apps that verify tokens.**
  - `pom.xml`: `spring-boot-starter-oauth2-resource-server` added; the old JJWT dependencies
    (`jjwt-api`/`jjwt-impl`/`jjwt-jackson`) removed — nothing in this module builds/parses a JWT by
    hand anymore.
  - New `KeycloakRealmRoleConverter`/`KeycloakJwtAuthenticationConverter` — **deliberately
    duplicated** from `gateway`'s classes of the same name, not shared via a Maven dependency, the
    same standalone-deployability precedent the now-deleted `JwtVerifier` itself set. The
    JIT-provisioning half talks to `common.repository.UserRepository` directly (this module already
    depends on `common`, and has no dependency on `gateway`/`identity-service` to route through
    instead).
  - **Deleted:** `security/JwtVerifier.java` (manual RSA public-key loading + JJWT verification) and
    `security/JwtAuthenticationFilter.java` — both replaced by `SecurityConfig`'s
    `.oauth2ResourceServer(jwt -> jwt.jwtAuthenticationConverter(...))` wiring, the same shape
    `gateway` uses.
  - `application.yml`: `jwt.public-key-location` removed; added
    `spring.security.oauth2.resourceserver.jwt.issuer-uri` — the same realm URL as `gateway`'s,
    proving both services trust Keycloak independently with zero shared Java code.
  - **`infra`'s `RsaKeyUtils` and both checked-in dev RSA key pair PEM files deleted too** — Phase 3
    was the last remaining consumer (`JwtVerifier`); a reactor-wide grep after deleting it confirmed
    zero remaining references anywhere. Originally slated for a later "Phase 5 cleanup" pass in the
    migration plan, but since it became unambiguously, fully dead as a direct consequence of this
    phase's own deletions (not a separate judgment call), removing it now rather than leaving known
    dead code sitting in the tree.
  - **Verification:** `./mvnw clean compile` and `./mvnw test-compile` both pass across the whole
    reactor. Not verified: actually booting `ecommerce-service` against the real Keycloak realm and
    confirming a `gateway`-issued token also authenticates its endpoints on `:8081` — needs a live
    run (no Docker in the environment this was authored in, same limitation as Phases 1–2).
  - **Not yet done:** the `gui` rework onto `react-oidc-context` (Phase 4), and the existing-user/
    seeded-demo-account Keycloak linkage (Phase 5, now much smaller in scope since the `RsaKeyUtils`
    cleanup already landed here).

- **Keycloak added to local dev infra — Phase 1 of the Keycloak identity-provider migration
  (application code unchanged so far).** Full plan: `docs/CHANGELOG.md` history aside, the
  rationale is that RS256 (previous entry) partly obsoletes itself once every service can point at
  Keycloak's `issuer-uri` for automatic JWKS rotation instead of manually managing an RSA key pair,
  and Keycloak's hosted login/registration/password-reset/Google+Facebook-brokering replaces the
  custom `OAuth2LoginSuccessHandler`/state-token/OTP-email flow. `identity-service` staying a
  Maven module (not becoming a standalone deployable) was evaluated and rejected — `common.entity.User`
  is referenced by NOT-NULL FKs from 8+ entities across `social-service`/`task-service` plus a
  hot-path join in `GroupServiceImpl.isChannelMember`, making that extraction a much larger,
  separate project not justified by this migration.
  - New `keycloak` service in `dev-knowledge-platform-docker-compose.yml`
    (`quay.io/keycloak/keycloak:26.0`, `start-dev --import-realm`, port `8180`), backed by the
    existing `postgres` container under a new `keycloak` schema, same "one instance, per-component
    schema" convention as `product`/`ecommerce`. Unlike those two, Keycloak's own Liquibase
    migration assumes its schema already exists rather than creating it — bootstrapped by a new
    `gateway` changeset (`DKP-0024`, `create_keycloak_schema.sql`) instead, reusing this project's
    existing "Liquibase creates schemas" mechanism rather than a bespoke docker-compose init
    service (tried and reverted — caught via a real `docker compose up` failure,
    `schema "keycloak" does not exist`, since `docker-entrypoint-initdb.d/init.sql` only runs
    against a genuinely fresh Postgres volume and doesn't help anyone who already had one). Because
    Liquibase migrations are a separate manual step in this project's workflow, `keycloak` may fail
    to boot on a first-ever `up` before that migration has run — given `restart: on-failure`, it
    recovers automatically on its own next restart attempt once the schema exists.
  - New `docker/keycloak/realm-export.json` (+ `docker/keycloak/README.md` explaining it) — realm
    `dev-knowledge-platform`, `USER`/`ADMIN` realm roles, a public `gui` SPA client
    (Authorization Code + PKCE, no direct grants), a `diagnostic-cli` client (direct grants enabled,
    dev/test-only, not used by any application code), Google/Facebook configured as **disabled**
    identity providers with placeholder credentials (real OAuth app secrets can't be committed —
    filling them in and enabling is a manual step, documented in the README, and each provider's
    own app registration needs its redirect URI updated to Keycloak's broker callback, which is
    external to this repo and can't be automated), user self-registration + email verification +
    password reset enabled, SMTP pointed at the existing `mailpit` container so verification emails
    stay visible in its UI like the old OTP emails did.
  - **Verified live**: realm imports cleanly (after two real hiccups, both fixed and left in the
    checked-in config — a `CLIENT.DESCRIPTION` column-length overflow on `diagnostic-cli`'s
    description, and a composite-role import-ordering trap from hand-declaring
    `default-roles-dev-knowledge-platform` with `offline_access`/`uma_authorization` before
    Keycloak auto-creates them; fixed by dropping both from the composite, since neither feature is
    used here); the discovery document resolves; a password-grant token fetch via `diagnostic-cli`
    for `kc-smoke-test@devknowledge.local`/`smoke-test-password` returns a real RS256 JWT with
    correct `iss`/`realm_access.roles`/`email` claims.

- **Phase 2 of the Keycloak migration: `gateway` becomes a real OAuth2 resource server, JIT
  local-`User` provisioning from Keycloak JWT claims, and deletion of the entire custom
  JWT/login/registration/OTP-email stack this replaces.**
  - New `USER.KEYCLOAK_SUBJECT_ID` column (Liquibase `DKP-0025`) — the join key linking a local
    `User` row to the Keycloak account authenticating as it. A new column, not a reuse of
    `PROVIDER`/`PROVIDER_ID` (now inert historical columns) — see the migration's own comment for
    the full reasoning.
  - `identity-service`'s `UserService` gained `findOrCreateFromKeycloak(KeycloakUserInfo)` —
    resolves by `keycloakSubjectId` first, falls back to `findByEmail` (links a pre-existing local
    row on its owner's first Keycloak login), else creates a new row; only writes when a field
    actually differs from the token's claims, not unconditionally on every request.
  - New `gateway` classes `KeycloakRealmRoleConverter` (maps Keycloak's `realm_access.roles` claim
    to `ROLE_*` `GrantedAuthority`s — Spring's default converter only reads a flat `scope` claim)
    and `KeycloakJwtAuthenticationConverter` (the JIT-provisioning glue, builds the same
    `CustomOAuth2User` principal shape the old filter did, so every `@CurrentUserId`/
    `@AuthenticationPrincipal CustomOAuth2User` call site elsewhere in the reactor is unaffected;
    also rejects a Keycloak refresh token presented as a bearer token, via its `typ` claim, mirroring
    the old refresh-vs-access-token rule). Shared by both the REST filter chain and STOMP `CONNECT`
    authentication (`StompAuthChannelInterceptor`, now decoding via an injected `JwtDecoder` instead
    of the deleted `JwtTokenProvider`) — one JIT-provisioning code path, not two.
  - `SecurityConfig`: `.oauth2Login(...)` + the custom `JwtAuthenticationFilter` replaced by
    `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`, finally putting `gateway`'s long-declared
    (previously unused) `spring-boot-starter-oauth2-resource-server` dependency to work. `jwt.*`
    and `spring.security.oauth2.client.*` config removed from all three `application*.yml`
    profiles; added `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
  - **Deleted outright** (all superseded by Keycloak): `identity-service`'s `JwtTokenProvider`,
    `security.jwt.{TokenClaims,AccessTokenClaims,RefreshTokenClaims}`, `CustomOAuth2UserService`,
    `CustomOidcUserService`, `OAuth2LoginSuccessHandler`, `StateTokenService`/`impl`,
    `RefreshTokenBlacklistService`/`impl`, `OtpService`/`EmailService`/`impl` (the whole OTP-email
    flow), `PasswordEncoderConfig`, every `dto/auth/*` type, `dto/RegisterRequest`, and
    `dto/{OAuth2UserInfo,GoogleOAuth2UserInfo,FacebookOAuth2UserInfo,OAuth2UserInfoFactory}`;
    `gateway`'s `JwtAuthenticationFilter`. `identity-service`'s `OAuth2Api`/`OAuth2Controller`
    renamed to `AuthApi`/`AuthController`, trimmed to just `GET /api/v1/auth/user` (the only
    endpoint Keycloak doesn't otherwise cover — it returns this app's own profile/avatar shape).
    `spring-boot-starter-mail` dropped from `gateway`'s `pom.xml` (its only consumer, the OTP
    emailer, is gone — Keycloak now sends verification/reset email straight to `mailpit`, per the
    realm config in the entry above) along with the now-dead `spring.mail.*`/`app.mail.*` config
    and `cache.ttl.state-tokens`. `spring-boot-starter-oauth2-client` stays — `CustomOAuth2User`
    (`common`) implements its `OAuth2User` interface, a real structural dependency independent of
    the login-flow feature that used to justify it.
  - **`gateway`'s STOMP integration tests reworked onto a real Keycloak Testcontainer**
    (`com.github.dasniko:testcontainers-keycloak`, transitively brings
    `org.keycloak:keycloak-admin-client`) — `AbstractStompIntegrationTest` now imports a dedicated,
    minimal test realm (`keycloak/test-realm-export.json`, separate from the dev realm export) and
    `persistUser()` provisions a matching Keycloak user per call via the admin client, linked by
    `keycloakSubjectId`, so tests exercise the JIT converter's realistic find-path rather than its
    create-path. `accessTokenFor`/`refreshTokenFor` now fetch real tokens via a Resource Owner
    Password grant against a test-only client (the real `gui` client disables that grant).
    `DmMessagingStompIntegrationTest`'s expired-token test can no longer hand-sign a token (this
    app holds no private key anymore) — it now fetches a real token from a second test client whose
    access-token lifespan is configured to 1 second, and waits for it to genuinely expire.
    `application-test.yml`'s OAuth2-client stub block (only present to satisfy
    `OAuth2ClientAutoConfiguration`) is gone.
  - **Not yet done** at the time this entry was written (since landed — see the Phase 3 entry
    above): `ecommerce-service`'s equivalent resource-server switch, and deleting `infra`'s
    `RsaKeyUtils`/the checked-in dev RSA key pair. Still pending: the `gui` rework onto
    `react-oidc-context` (Phase 4).
  - **`gui`'s existing login/register/OTP UI now calls endpoints that no longer exist**
    (`/api/v1/auth/{login,register,verify-otp,resend-otp,exchange-state,refresh,logout}` are all
    deleted) — this is expected and intentional until Phase 4 (frontend rework) lands, not a
    regression to fix here.
  - **Verification**: `./mvnw -pl common,infra,identity-service,gateway -am clean compile` and
    `-am test-compile` both pass; the new Keycloak Testcontainer dependency resolves and its static
    initializer runs correctly (confirmed it gets as far as attempting to pull the Keycloak image
    before failing) — but actually running `DmMessagingStompIntegrationTest` end-to-end was not
    possible in the environment this was authored in (no Docker daemon/CLI available there). Run
    `./mvnw -pl gateway -am test -Dtest=DmMessagingStompIntegrationTest` somewhere with Docker
    before relying on this.

### Changed

- **JWT signing switched from HS512 (shared secret) to RS256 (RSA key pair).** Motivated by
  `ecommerce-service`'s standalone extraction: with HS512, every verifying service holds the same
  secret that signs, so a compromise of the newer, less-trusted `ecommerce-service` process could
  have been used to forge tokens for any user platform-wide. With RS256, `identity-service` alone
  holds the private key and signs; every verifier (`gateway`'s `JwtAuthenticationFilter`/
  `StompAuthChannelInterceptor` via `JwtTokenProvider`, `ecommerce-service`'s `JwtVerifier`) only
  ever needs the public key, so a verifier compromise can no longer be used to mint new tokens.
  - New `infra/security/RsaKeyUtils` — parses PKCS#8/X.509 PEM key material from a Spring
    `Resource` into `PrivateKey`/`PublicKey`. Lives in `infra` (not either JWT-handling module)
    because `identity-service` (needs both keys) and `ecommerce-service` (needs only the public
    key) are independent siblings that can't depend on each other — same reasoning as this
    module's `StorageService`/`CacheNames`.
  - `identity-service`'s `JwtTokenProvider` now loads both keys once at startup (`@PostConstruct`)
    instead of re-deriving an HMAC key from `jwt.secret` on every call, and signs/verifies with
    `Jwts.SIG.RS256`.
  - `ecommerce-service`'s `JwtVerifier` now loads only the public key and verifies with it — this
    service never holds, and never needs, the private key.
  - Config: `jwt.secret` replaced by `jwt.private-key-location` (`gateway` only — the deployable
    `identity-service` actually runs in) and `jwt.public-key-location` (`gateway` and
    `ecommerce-service` both). Dev defaults point at a checked-in, dev-only RSA key pair
    (`identity-service/src/main/resources/jwt/dev-jwt-private.pem`, `dev-jwt-public.pem`, copied
    into `ecommerce-service/src/main/resources/jwt/` too since that module can't reach the first
    module's classpath resources); `application-docker.yml` has no default, same convention as the
    old `JWT_SECRET` env var it replaces — both `JWT_PRIVATE_KEY_LOCATION`/
    `JWT_PUBLIC_KEY_LOCATION` must be supplied for any real deployment, pointing at a real,
    non-committed key pair.
  - `gateway`'s `DmMessagingStompIntegrationTest.buildExpiredAccessToken` (the one place outside
    `JwtTokenProvider` that manually signs a token, to produce an already-expired one for a
    rejection test) updated to sign with the RS256 private key instead of the old HMAC secret.

### Added

- **New `ecommerce-service` module: Epic 1 (Catalog & Search) entities.** Study-project
  e-commerce vertical slice, scoped in full first via user stories
  (`docs/user-stories/README.md` + `01-catalog-search.md` through
  `05-reviews-recommendations.md`) before any code — only Epic 1 has code so far.
  - New Maven module `ecommerce-service` (`com.ttg.devknowledgeplatform.ecommerce.*`), depending
    only on `common`+`infra`; registered in the root `pom.xml` (`<modules>` +
    `dependencyManagement`) and added as a `gateway` dependency so its entities are on the
    classpath Hibernate validates against (`spring.jpa.hibernate.ddl-auto: validate`).
  - `entity/`: `ProductCategory` (flat taxonomy — named to avoid colliding with
    `content-service`'s `Category` in the shared `product` schema), `Product`,
    `ProductImage` (ordered gallery via `infra`'s `StorageService`), `ProductVariant`
    (`attributes` as JSONB `Map<String,String>`, same `@JdbcTypeCode(SqlTypes.JSON)` approach as
    `ai-service`'s `ContentEmbedding.metadata`; `stockQuantity`/`reservedQuantity` two-column
    reservation model with a DB CHECK constraint), `ProductSearchView` (the CQRS read model for
    browse/search/filter — no writer yet, that's the projection relay, a future step), and
    `OutboxEvent` (shared transactional-outbox table every future epic will reuse; `status`
    (new `enums.OutboxEventStatus`: `PENDING`/`PROCESSING`/`PROCESSED`/`FAILED`) plus
    `attemptCount`/`lastError` are the relay's claim/dispatch/diagnose signals — a plain
    processed/unprocessed timestamp alone can't distinguish "not yet picked up" from "picked up
    and permanently failing," or protect against two relay instances double-dispatching the same
    row. `aggregateType` is a second new enum, `enums.OutboxAggregateType` (DB `CHECK`-backed,
    only `PRODUCT` today); `eventType` deliberately stays a plain string — one Java field can only
    be backed by one enum type, and every future epic keeps adding its own event types to this
    same shared table, so forcing them all into one ever-growing enum (and widening a `CHECK`
    every time) would fight the table's whole purpose. `aggregateType`'s set, by contrast, grows
    roughly once per epic, cheap enough for an enum + `CHECK`).
  - Liquibase migration `202608040001__0.0.2__DKP-0023__add_ecommerce_catalog_tables.sql`
    (renamed from a `0.0.1` version segment on 2026-08-11, alongside every other standalone
    service's own changelog — see root `CLAUDE.md`'s Liquibase naming note):
    `PRODUCT_CATEGORY`/`PRODUCT`/`PRODUCT_IMAGE`/`PRODUCT_VARIANT`/`PRODUCT_SEARCH_VIEW`/
    `OUTBOX_EVENT` (incl. `STATUS`/`ATTEMPT_COUNT`/`LAST_ERROR`/`AGGREGATE_TYPE` check), the
    `pg_trgm` extension, a
    DB-generated `tsvector` column (`GENERATED ALWAYS AS (to_tsvector('english', SEARCH_TEXT))
    STORED` — the two-argument form is immutable, unlike the one-argument form, which is what
    makes it legal in a generated column), and GIN indexes for full-text/trigram/JSONB-containment
    search. Edited in place rather than added as a new changeset — confirmed via `git status` that
    this changeset had never been committed or applied anywhere, so Liquibase's "never modify an
    already-run changeset" rule didn't yet apply.
  - **Minimal admin vertical slice on top of the entities**: `exception/EcommerceErrorCode`;
    `repository/{ProductCategoryRepository,ProductRepository,ProductVariantRepository,
    ProductImageRepository}` + `repository/spec/{ProductCategorySpecification,ProductSpecification}`;
    `service/{ProductCategoryService,ProductService}` (+`impl/`) returning entities, never this
    module's own DTOs, plus `service/ProductCommands` (create/update input records, mirroring
    `content-service`'s `QuestionAnswerCommands`) for the multi-field/nested-list `Product` create
    input; `mapper/{ProductCategoryMapper,ProductMapper}`; `dto/*`;
    `api/{ProductCategoryApi,ProductApi}` + `api/impl/*Controller`
    (`/api/v1/admin/product-categories`, `/api/v1/admin/products` — both admin-gated automatically
    via `gateway`'s existing `/api/v1/admin/**` rule, no method-level `@PreAuthorize` needed).
    `ProductServiceImpl.create` validates at-least-one-variant, no duplicate SKU/sort-order within
    the same request, no SKU conflicting with an existing variant, and consistent attribute keys
    across a product's variants (US-1.6) — all as `ApiException`s raised before any DB write.
    `Product` gained lazy, no-cascade `variants`/`images` collections (mirroring
    `content-service`'s `Category.children`); after saving each child via its own repository, the
    code also appends it to the parent's in-memory collection rather than relying on a later lazy
    re-fetch, since Hibernate can already treat a brand-new parent's collection as
    "initialized empty" at persist time.
  - **Outbox relay + PRODUCT_CHANGED projector, giving `ProductSearchView` its first writer.**
    New `outbox/` package: `OutboxEventHandler` (Strategy interface — `eventType()` +
    `handle(OutboxEvent)`), `OutboxEventDispatcher` (builds a `Map<String, OutboxEventHandler>`
    from every handler bean Spring finds), `OutboxEventProcessor` (claims one event via a new
    atomic `OutboxEventRepository.claim(id, from, to)` conditional `UPDATE`, dispatches it,
    `@Transactional`), `OutboxRelay` (the `@Scheduled` poller,
    `app.ecommerce.outbox.relay.poll-interval` / default `PT5S`, following `ai-service`'s
    `CorpusStatisticsServiceImpl` convention for configurable intervals — no new
    `@EnableScheduling` needed, already active app-wide via `ai-service`'s `AiServiceConfig`).
    `OutboxEventProcessor` is deliberately its own bean rather than a second method on
    `OutboxRelay`, to avoid Spring's `@Transactional` self-invocation proxy pitfall (a bean
    calling its own `@Transactional` method via `this.foo()` bypasses the proxy and silently runs
    with no transaction at all).
    New `service/impl/ProductChangedOutboxEventHandler`: re-derives the whole `ProductSearchView`
    row from current `Product`/`ProductVariant` state (never trusts anything in the event payload
    beyond the id) — computes `minPrice`/`maxPrice`/`inStock` from variants and
    `availableAttributes` as the distinct values per attribute key across all variants.
    **Deactivating (or a missing) product deletes its `ProductSearchView` row rather than
    updating it** — the read model has no `active` column of its own, and US-1.7 says a
    deactivated product must "disappear from browse/search," so a missing row *is* the
    not-visible state. `ProductServiceImpl` now publishes a `PRODUCT_CHANGED` `OutboxEvent` (via
    a new private `publishProductChanged` helper) after every create/update/deactivate, in the
    same transaction as the underlying `Product` write.
  - **Public browse/search endpoint** (US-1.1, US-1.3, US-1.4): new
    `repository/ProductSearchViewRepository` with a single native `search()` query handling every
    optional filter (category, keyword, price range, in-stock-only) via the
    `(:param IS NULL OR ...)` idiom rather than dynamically-built SQL — keyword matching combines
    `tsvector` exact-token search (`@@`) with `pg_trgm` similarity (typo tolerance `tsvector`
    alone misses), ranked by `ts_rank` when a keyword is given; the price filter is a
    variant-range overlap check, not exact match. The `SELECT` list is spelled out explicitly
    (never `SELECT *`), since `SEARCH_VECTOR` is a DB-only generated column with no Java field and
    would otherwise break the entity-result mapping. New `service/ProductSearchService`(`Impl`),
    `mapper/ProductSearchViewMapper`, `dto/ProductSearchResponse`,
    `api/ProductSearchApi`+`api/impl/ProductSearchController` at `/api/v1/public/products` (under
    `gateway`'s existing public permit-all rule).
  - **New direction**: this module will eventually be extracted into its own standalone Spring
    Boot application (own DB, own JWT validation, `gateway`-proxied) as a dedicated
    microservices-study exercise, once Epic 1 is further along — see the
    `project-ecommerce-service-module` memory for the full sequencing. Not started yet.
  - **Verified 2026-08-04: full reactor compiles cleanly** (`./mvnw -pl gateway -am compile`,
    `BUILD SUCCESS`) — needed `JAVA_HOME` pointed at a JDK 21 install (`~/.jdks/ms-21.0.10` on
    this machine; the shell's default resolves to Java 8, matching the JDK note already in root
    `CLAUDE.md`). Compilation only, though — the app hasn't been booted against a real Postgres,
    so the Liquibase migration, Hibernate's `ddl-auto: validate` check, and the native SQL in
    `ProductSearchViewRepository.search` remain unverified at runtime.
  - Not yet built (**superseded — all four closed, see the entry below**): variant/image
    add-remove-reorder endpoints, a public product-detail endpoint (US-1.2), full attribute-value
    filtering. Still not built: `ProductCategory` delete, and Epics 2–5.

- **Closed Epic 1's four remaining gaps against `docs/user-stories/01-catalog-search.md`** — all 7
  of that epic's user stories now have working code.
  - **US-1.1** (gallery image in results): `ProductSearchView`/`ProductSearchResponse` gained
    `primaryImageStorageKey` (nullable — a product can momentarily have zero images); the
    migration got a matching column. `ProductChangedOutboxEventHandler` computes it from the
    product's first `ProductImage` by `sortOrder`.
  - **US-1.2** (public product detail): new `ProductService.getActiveBySlug` (treats a deactivated
    product's slug the same as nonexistent — never confirms past existence to a public,
    unauthenticated caller) + new `ProductRepository.findBySlug`; new
    `GET /api/v1/public/products/{slug}` on `ProductSearchApi`/`Controller`, reusing the existing
    `ProductResponse`/`ProductMapper` (already includes variants + gallery).
  - **US-1.4** (attribute-value filtering): `ProductSearchApi.search` gained a catch-all
    `Map<String,String> allParams` — any query param not in the reserved set (`page`, `size`,
    `categoryId`, `q`, `minPrice`, `maxPrice`, `inStockOnly`) is treated as an attribute filter
    (e.g. `?size=M&color=Blue`). `ProductSearchServiceImpl` combines every filter into **one** JSON
    object (via the injected `ObjectMapper`) and passes it as a single bind parameter;
    `ProductSearchViewRepository.search` compares it against `AVAILABLE_ATTRIBUTES` via JSONB
    containment (`@>`) — Postgres's `@>` on a JSON object recursively ANDs across every key on the
    right-hand side, so one containment check correctly implements "AND across attribute keys,
    any listed value per key" without building SQL dynamically per filter.
  - **US-1.6** (independent variant/image mutation): `ProductService` gained
    `addVariant`/`removeVariant` (rejects removing a product's last variant —
    `PRODUCT_REQUIRES_AT_LEAST_ONE_VARIANT` reused from create-time) and
    `addImage`/`removeImage`/`updateImageSortOrder` (a product *can* end up with zero images, no
    equivalent floor). New endpoints on `ProductApi`: `POST`/`DELETE .../variants/{variantId}`,
    `POST`/`DELETE`/`PATCH .../images/{imageId}`; new `UpdateProductImageSortOrderRequest` DTO. New
    `EcommerceErrorCode` entries: `PRODUCT_VARIANT_NOT_FOUND`/`_BELONGS_TO_ANOTHER_PRODUCT`,
    `PRODUCT_IMAGE_NOT_FOUND`/`_BELONGS_TO_ANOTHER_PRODUCT`/`_SORT_ORDER_CONFLICT`. A new variant
    is checked against the product's *existing* variants' attribute-key set (not just other
    variants in the same request, which is what the create-time check already covered).
  - **Bug caught while touching `ProductSearchViewRepository` for US-1.4**: its native query's
    `FROM`/`countQuery` still hardcoded the pre-extraction `product.PRODUCT_SEARCH_VIEW` schema
    prefix — the entity-extraction schema rename only touched `@Table(schema=...)` Java
    annotations, never this plain SQL string, so it silently survived that whole pass. Fixed to
    `ecommerce.PRODUCT_SEARCH_VIEW`. Any *other* native/raw-SQL schema references in this module
    would have the same blind spot if the schema ever moves again — worth grep-checking by hand,
    not just trusting a find/replace over Java annotations.
  - Still not built: `ProductCategory` delete, combo-accurate attribute filtering (checks "some
    variant has size M" and "some variant has color Blue" independently, not "one variant with
    both"), and Epics 2–5. Compiles clean (full reactor); still not run against a real Postgres.

- **`ecommerce-service` extracted into its own standalone Spring Boot application** — a dedicated
  microservices-study exercise (the user's explicit reason for this whole module), done once the
  Epic 1 vertical slice above was substantial enough to be worth pulling out. Full sequencing:
  `project-ecommerce-service-module` memory.
  - New `EcommerceServiceApplication` (`@SpringBootApplication` + `@EnableScheduling` — the
    outbox relay needed its own now that this app doesn't include `ai-service`, which used to
    enable scheduling app-wide via its own `AiServiceConfig`). Deliberately sits at
    `com.ttg.devknowledgeplatform.ecommerce` rather than the shared root package `gateway`'s main
    class uses — default Spring Boot component/entity/repository scanning covers only the main
    class's own package tree, so `common.entity.User`/`common.repository.UserRepository` (no
    matching table in this service's own database) are correctly never scanned, with zero
    `@EntityScan` configuration needed. `common.entity.AbstractEntity` needs no special handling
    either, despite living outside this package tree — it's a `@MappedSuperclass`, resolved via
    ordinary Java inheritance the moment Hibernate processes a real `@Entity` subclass, not
    something requiring its own scan entry.
  - **Own database**: all 6 entities moved from the monolith's shared `product` schema to a new
    `ecommerce` schema (`@Table(schema = ...)` on each, `CREATE SCHEMA IF NOT EXISTS ecommerce`
    added to the migration). The migration itself moved from `gateway`'s changelog tree to a new
    one under this module (`database/sql/ecommerce-service.xml` + `2026/0.0.2/*.sql`), applied via
    a new standalone `ecommerce-service-liquibase.yml` docker-compose file (mirroring
    `dev-knowledge-platform-liquibase.yml`'s shape) rather than `gateway`'s. Connects to the same
    Postgres instance/database as the monolith for now (`dev-premier`) — a separate schema/DB, not
    yet a fully separate Postgres container.
  - **Own JWT verification**: new `security/{JwtVerifier,JwtAuthenticationFilter,SecurityConfig}`.
    `JwtVerifier` verifies (never issues) HS512 tokens directly via `io.jsonwebtoken`, using the
    same shared `jwt.secret` and claim shape (`sub`=email, `userUuid`, `username`, `role`)
    `identity-service`'s `JwtTokenProvider` already issues — deliberately **not** a Maven
    dependency on `identity-service`'s classes, since a genuinely separate service couldn't import
    another service's Java types either; this is the more faithful lesson over reusing
    `JwtTokenProvider` directly. `SecurityConfig` mirrors `gateway`'s `/api/v1/admin/**`
    (`hasRole('ADMIN')`) / `/api/v1/public/**` (permit-all) rule shape, minus the OAuth2 login flow
    this service never handles.
  - **`gateway`'s Maven dependency on `ecommerce-service` removed** (`gateway/pom.xml`) — this
    service's beans no longer belong on `gateway`'s classpath at all. `gateway`-side HTTP
    proxying to this service is **not built yet**; until then it's only reachable on its own
    port (`8081`).
  - Root `CLAUDE.md` and `docs/PROJECT_STRUCTURE.md` updated: `ecommerce-service` is called out
    as outside the monolith's dependency graph, and Epic 5's originally-planned
    `ecommerce-service` → `ai-service` Maven dependency is flagged as needing rethinking — `ai-
    service` still only runs inside the monolith, so that dependency shape (which works for
    `ai-service` → `content-service`, both still inside the same deployable) doesn't transfer to a
    standalone `ecommerce-service` without a real network call instead.
  - New reference memory: JDK 21 location on this machine (`~/.jdks/ms-21.0.10`) for running
    `./mvnw`, since the shell's default `JAVA_HOME` resolves to Java 8.
  - Still unverified at runtime: the app hasn't been booted against a real Postgres, so the new
    schema's migration, the native SQL search query, and the new JWT verification path are all
    unconfirmed beyond compiling.

- **`gui`: Task/Project management screens, fronting `task-service`'s `ProjectApi`/`TaskApi`.** New
  feature folder `gui/src/features/tasks/` (`types.ts`, `api/taskApi.ts`,
  `components/{ProjectFormDialog,TaskFormDialog}.tsx`, `pages/TasksPage.tsx`), new `@tasks` path
  alias (added to both `tsconfig.json` and `vite.config.ts`), new `/tasks` route (`App.tsx`) and
  NavBar entry — all a top-level `PrivateRoute`, not nested under `/admin`, since every
  `Project`/`Task` is owned by the caller rather than being admin-gated content.
  - `TasksPage` was reworked from the original MUI-Tabs single-hub shape into a 3-pane
    Todoist/Asana-style layout: left `TasksSidebar` (Today / This week smart filters + a Projects
    list with inline create/edit/archive via `ProjectFormDialog`), a middle column
    (`TaskQuickAdd` + either the default Overdue/Today/Upcoming/Completed sectioned view or, when a
    sidebar filter is active, a flat list — `utils/taskBuckets.ts` owns the client-side bucketing
    and the `dueAfter`/`dueBefore` range math for the Today/This-week filters), and an
    always-reserved right `TaskDetailPanel` (task fields + inline subtask list/add, empty-state
    placeholder when nothing is selected). `components/{TaskList,ProjectList}.tsx` (the old
    Tabs-era table views) were deleted — fully superseded, no remaining importers.
  - `TaskRow.tsx` was simplified alongside this: it used to double as both a recursive top-level
    tree node (expand/collapse + inline subtask fetch/render) and a leaf row — a mixed-responsibility
    smell. Since the main list no longer renders top-level rows through it (that's
    `TasksPage`/`TaskDetailPanel`'s job now), the recursive/expand branch was dead code and was
    removed; `TaskRow` is now a single leaf row reused both by the main list (via an optional
    `onSelect` prop) and by `TaskDetailPanel`'s subtask list — see further down this entry for its
    final checkbox/title/due-chip/three-dot-menu shape, which went through several iterations.
  - `TaskController`'s `ALLOWED_SORT_FIELDS` only permits `id`/`dteCreation`, so "soonest due date
    first" ordering for the dashboard/list is done client-side in `TasksPage.tsx` after fetching
    (tasks with no due date sort last) rather than via an unsupported `sortBy=dueDate` param.
  - `TasksPage.tsx` owns the one `taskApi.listProjects` fetch for the whole page and passes the
    result down to `TasksSidebar`/`TaskDetailPanel`/`TaskRow`/dialogs — `TasksSidebar` no longer
    fetches its own separate copy (it used to, doubling the real `/api/v1/projects` load on every
    page open independently of React `StrictMode`'s expected dev-mode double-invoke). Also fixed:
    the default "all" view rendered nothing (no empty-state message) when a user genuinely has zero
    tasks, since the Overdue/Today/Upcoming/Completed section map just returns null for every
    empty bucket with no fallback.
  - `fetchTasks`/`TaskDetailPanel`'s `fetchSubtasks` used to unconditionally set their loading flag
    on every call, so any mutation (checkbox toggle, status change, edit, delete, quick-add, subtask
    add) blanked the whole list/subtasks section to a spinner and back — visible as a full-page
    "blink" on every action. Both now take an optional `{ showSpinner }` — `true` only for the
    filter-driven/task-selection-driven initial load, `false` for the quiet background refetch every
    mutation callback uses instead, which just swaps the data in once ready with no spinner. The
    selected-task resync effect in `TasksPage.tsx` was also switched from reference equality to a
    content (`JSON.stringify`) comparison, since a fresh fetch always returns new object instances
    even when nothing about that particular task changed — reference equality was replacing
    `selectedTask` on every refetch regardless, which re-triggered `TaskDetailPanel`'s subtask fetch
    needlessly.
  - No content-service linking in this pass — `Task.contentItemId` is never set from the GUI yet
    (no content-item picker). No project delete in the GUI either, matching the backend: `ProjectApi`
    only exposes archive, not delete.
  - Status went through two shapes before landing on its final one: a plain `<Select>` first
    (looked inconsistent next to the priority/due-date chips), then its own colored `Chip`+`Menu`
    (matching the priority picker in `TaskQuickAdd.tsx`) — both since superseded by the three-dot
    menu described below. `TaskStatus.canTransitionTo` on the backend is still deliberately
    permissive (any status to any other), so no transition-guarding UI was ever added — every
    status stays selectable from any current status.
  - Priority no longer has its own `Chip` in `TaskRow.tsx` — it's now encoded as the done-checkbox's
    color (gray/blue/orange/red for Low/Medium/High/Urgent, via MUI `Checkbox`'s `icon` prop set to
    `CheckBoxOutlineBlankIcon`), with a `Tooltip` spelling out the label on hover. Removing the chip
    was safe: it was non-interactive display-only in the row (priority is still changed via the
    full edit dialog), and freed up horizontal space in an already-dense row. Once a task is marked
    Done, the checkbox always fills in a neutral `success.main` green `CheckBoxIcon` regardless of
    that task's priority (via `checkedIcon`) — a red checkmark on a completed Urgent task read as
    contradictory ("still urgent?") next to the strikethrough "done" treatment. Uses the square
    `CheckBoxOutlineBlank`/`CheckBox` icon pair (MUI `Checkbox`'s own default look), not a round
    radio-button-style icon, to read unambiguously as a checkbox rather than a single-select control.
  - The default "all" view's Completed section renders at `opacity: 0.6` (`TasksPage.tsx`) so it
    visually recedes behind Overdue/Today/Upcoming — still fully interactive (checkbox/status/
    edit/delete unaffected by `opacity`), just de-emphasized now that it's no longer the day's
    actionable work.
  - `TaskRow.tsx`'s final shape: checkbox (priority-colored) / title / due-date chip / a single
    "⋯" (`MoreHorizIcon` — horizontal, not `MoreVertIcon`) button. The status `Chip` and the
    standalone Edit button are both gone —
    replaced by a menu opened from "⋯" whose content is a single `Box` with two inline icon rows
    (a "Priority" label + all 4 `FlagIcon`s in a `Stack direction="row"`, a "Status" label + all 3
    status icons the same way — clicking any icon applies it immediately, current value highlighted
    via `bgcolor: 'action.selected'` on a `borderRadius: 1` rounded square, not the `IconButton`'s
    own default circular shape — a rounded square reads more clearly as "this one's active" among a
    row of same-sized icons than a circle does) plus a **Delete** `MenuItem` below a `Divider`
    (still behind `ConfirmDialog`, unchanged). An earlier version used click-to-drill-down submenus (Priority ▸ /
    Status ▸ swapping the menu's content via a `menuView` state) instead of showing both inline —
    replaced because seeing every option (and the current one) in one glance beat a
    click-then-choose flow for something this low-stakes. The "⋯" button itself is hidden
    (`opacity: 0`) until the row is hovered or something inside it has focus — the row `Stack`'s
    `sx` carries `'&:hover .task-row-more, &:focus-within .task-row-more': { opacity: 1 }`, and the
    button's own `opacity` also forces `1` while its menu is open (`menuAnchor` truthy) so it
    doesn't visually vanish out from under an open menu the moment the mouse leaves the row.
    `opacity` rather than `display`/`visibility: hidden` deliberately keeps it focusable/tabbable
    for keyboard users even before hover.
  - `TaskRow.tsx` takes a `selected` prop — `true` gives the row a persistent `action.selected`
    background (stays darkened even when the mouse isn't over it), `false` (the default) means
    plain hover feedback via `action.hover` instead; hovering a `selected` row keeps
    `action.selected` rather than switching to the lighter hover tint, so the "this is the open
    task" signal doesn't flicker as the mouse moves over it. `TasksPage.tsx` passes
    `selected={selectedTask?.id === task.id}` at both `TaskRow` call sites (the bucketed and flat
    lists) so the row backing the currently-open `TaskDetailPanel` stays visually distinct. Not
    wired up in `TaskDetailPanel`'s own subtask-row usage — subtasks have no independent "open"
    state to reflect. The row gained `px: 1, mx: -1, borderRadius: 1` alongside this so the
    hover/selected tint reads as a rounded highlight rather than a hard-edged rectangle touching
    the list's own padding.
  - Fixed along the way: the "⋯" button's `Tooltip` had lost its `title="More options"` prop
    (down to a bare `<Tooltip>`, which doesn't compile — `title` is a required prop) during an
    editor/linter pass between edits; restored.
  - The click-to-open-detail handler moved from the title `Typography` to the row `Stack` itself
    (`onClick={onSelect ? () => onSelect(task) : undefined}`), so clicking anywhere in the row
    (empty space, the due-date chip, around the checkbox) opens `TaskDetailPanel`, not just the
    title text. Both the `Checkbox` and the "⋯" `IconButton` call `e.stopPropagation()` in their
    own `onClick` first, since a native click on either still bubbles up through the DOM to the
    row's handler (unlike the `Menu`/`ConfirmDialog` contents, which render into a portal outside
    the row's DOM subtree and never bubble there regardless) — without it, checking a task done or
    opening the "⋯" menu would also fire `onSelect` on every click. No `cursor: 'pointer'` on the
    row despite it being clickable — tried, then explicitly removed; the row's own hover
    background (`action.hover`/`action.selected`) is the intended hover affordance instead. The
    `Tooltip`s that used to wrap the checkbox (`"Priority: X"`) and the "⋯" button
    (`"More options"`) were removed too — both `Checkbox`/`IconButton` render unwrapped now; the
    priority/status submenu items' own per-icon `Tooltip`s (label-only, e.g. `"Low"`/`"To do"`)
    are untouched.
  - Row height now matches `TaskQuickAdd.tsx`'s "+ Add a task…" `TextField` (`size="small"`, real
    height ~37px) instead of being taller — the row `Stack` went from `py: 0.75` (a fixed ~12px of
    vertical padding stacked on top of its tallest child) to `minHeight: 37, py: 0`, and the
    `Checkbox` (whose own default small-size touch target is 38px — taller than the target 37px on
    its own, so the `Stack`'s `minHeight` alone wasn't the binding constraint) got `sx={{ p: '8px'
    }}` to bring its rendered height down to line up. Measured via real `boundingBox()` calls in a
    live browser, not guessed from theme spacing math.
  - The "⋯" menu's three inline-icon rows are ordered **Date, Priority, Status** (Date was added
    last, after Priority/Status, but reordered to lead — it's the row most often changed quickly).
  - Added a third inline-icon row to the "⋯" menu, **Date**, mirroring Priority/Status's shape:
    `WbSunnyIcon`/`WbTwilightIcon`/`CalendarViewWeekIcon` for Today/Tomorrow/This week (a fourth,
    `CalendarMonthIcon` "Custom", toggles a native `<TextField type="date">` inline below the icon
    row instead of applying immediately — picking a date there calls the same handler and closes
    the menu). `datePresetValue()` computes each preset as local midnight (`startOfToday()` for
    Today/+1 day for Tomorrow, `endOfWeek()` from `utils/taskBuckets.ts` with its time reset to
    `00:00:00` for This week) — reusing `taskBuckets.ts`'s existing date math rather than
    duplicating the "which day is the coming Sunday" logic a second time. The currently-matching
    preset (compared by calendar day via `toDateString()`, not exact instant equality) gets the
    same `action.selected` highlight treatment as the current priority/status; if the due date is
    set but doesn't match any of the three presets, **Custom** gets the highlight instead. Setting
    a date goes through a new `handleDueDateSelect`, structurally identical to
    `handlePrioritySelect` (`taskApi.updateTask` with every other field resent unchanged — same
    "no dedicated single-field endpoint" constraint). `toDateInputValue` (ISO → `YYYY-MM-DD` via
    `.slice(0, 10)`) is copied from `TaskFormDialog.tsx` rather than extracted to a shared util —
    it's a one-line function, not worth a cross-file import for.
  - Swapped the Date row's Today/Tomorrow/This-week icons once more, this time for legibility:
    `TodayIcon` (a calendar page) → `WbSunnyIcon` (an actual sun) for Today; `NavigateNextIcon`
    (a plain chevron, no date meaning at all on its own) → `WbTwilightIcon` (sun-on-horizon, reads
    as "sunrise/next day") for Tomorrow; `DateRangeIcon` (a from→to range-picker glyph) →
    `CalendarViewWeekIcon` (a calendar rendered as week-columns) for This week — closer to "a week
    of days" than a range-select icon, though MUI has no literal "calendar +7" glyph. All three
    already existed in the installed `@mui/icons-material` version — checked
    `node_modules/@mui/icons-material/` directly rather than assuming.
  - **`TaskQuickAdd.tsx` reworked**: the always-visible due-date `<TextField type="date">`, the
    priority `Chip`, and the "Add" `Button` are all gone. The title field is now the only visible
    control, with two `IconButton`s as `InputProps.endAdornment`: a `CalendarMonthIcon` (opens the
    same Today/Tomorrow/This-week-presets-plus-Custom-date-input popover as `TaskRow`'s "⋯" Date
    row — colors `primary.main` once a date is picked, `Tooltip` shows the picked date) and an
    `ExpandMoreIcon` — the same chevron `TasksPage.tsx`'s bucket-header expand/collapse toggle
    uses, deliberately, instead of `ArrowDropDownIcon` (tried first), so the two "opens more stuff
    below" affordances in this feature look identical rather than using two different downward-
    arrow glyphs — (opens a Priority-only menu, colored by the pending priority the same way
    `TaskRow`'s checkbox ring is). The Priority menu's content is the same inline-icon-row format
    as `TaskRow`'s "⋯" Priority section (a "Priority" caption label above a `Stack direction="row"`
    of `FlagIcon` `IconButton`s, current value highlighted via `bgcolor: 'action.selected'`) rather
    than a vertical `MenuItem` list — tried the `MenuItem` list first, replaced for the same
    "this app's task menus all look the same" consistency reason. Both just set local
    `dueDate`/`priority` state — no API call
    until the task is actually created, unlike `TaskRow`'s versions of these same widgets which
    mutate an existing task immediately. Submitting is Enter-only now (`onKeyDown`, no `Button`),
    and the field re-focuses itself (`inputRef.current?.focus()` in the `finally` block) so
    consecutive tasks can be typed without touching the mouse.
  - **Real bug caught while building the above**: the title `TextField` had `disabled={saving}`,
    which meant `inputRef.current?.focus()` (called synchronously in the same `finally` block
    right after `setSaving(false)`) ran before React had committed the re-render that actually
    re-enabled the input — so it was still disabled in the DOM at the exact moment `.focus()` ran,
    and `.focus()` on a disabled element is a silent no-op. Fixed by dropping `disabled={saving}`
    entirely rather than deferring the focus call (e.g. `setTimeout(…, 0)`) — the create request is
    fast and local, and `handleSubmit`'s own `if (!trimmed || saving) return` guard already
    prevents a double-submit; there was nothing the `disabled` prop was actually protecting.
  - `DATE_PRESETS`/`DATE_PRESET_LABEL`/`datePresetValue()` moved from `TaskRow.tsx` into
    `utils/taskBuckets.ts` (a plain `.ts` file — no JSX) once `TaskQuickAdd` needed the same
    date-preset math `TaskRow` already had — keeps "which day is the coming Sunday" defined once.
    (Superseded a few commits later, see below: the whole Date/Priority/Status/Delete menu itself
    — including its `DATE_PRESET_ICON` JSX map — moved into a new shared `TaskOptionsMenu.tsx`, so
    `TaskRow.tsx`/`TaskQuickAdd.tsx` no longer each keep their own copy of that map either.)
  - **New `components/TaskOptionsMenu.tsx`**: the entire "⋯" menu body (Date/Priority/Status inline
    icon rows + the Delete `MenuItem`) extracted out of `TaskRow.tsx` into its own component, fully
    controlled via props — `priority`/`onPriorityChange` (always required), and three independently
    optional pairs: `dueDate`/`onDueDateChange`, `status`/`onStatusChange`, `onDelete`. Omitting a
    pair hides that section entirely (checked via the callback prop's presence, e.g.
    `Boolean(onStatusChange && status)` — not a separate `showStatus` boolean) rather than rendering
    it disabled. `TaskRow.tsx` passes all four (backed by `taskApi.updateTask`/`changeTaskStatus`
    calls that fire immediately). `TaskQuickAdd.tsx`'s "more options" menu (previously its own
    inline `Priority`-only `Menu`) now renders the same component passing only
    `priority`/`onPriorityChange={setPriority}` — no `dueDate`/`status`/`onDelete`, so only the
    Priority row renders; its separate Date `Menu` (the calendar-icon popover) is untouched, since
    `TaskOptionsMenu` only replaces what used to be the `moreMenuAnchor` `Menu`. The component is
    otherwise identical to `TaskRow`'s previous inline version — same "which preset matches the
    current due date" highlight logic, same "resend every field" `updateTask`-based mutation
    pattern (now the *caller's* job via the `onXChange` props, not this component's) — just with
    one implementation instead of two files each keeping their own copy of the Date/Priority/Status
    JSX and constants (`PRIORITIES`, `PRIORITY_LABEL`, `STATUS_ICON`, `DATE_PRESET_ICON`, etc.).
  - Cleaned up a leftover from before the `TaskOptionsMenu` extraction: `TaskQuickAdd.tsx`'s "more
    options" trigger `IconButton` (the `ExpandMoreIcon`) was still wrapped in its own
    `Tooltip title={\`Priority: ${priorityLabel(priority)}\`}` — redundant now that
    `TaskOptionsMenu`'s own flag icons each carry their own per-priority `Tooltip`, and the same
    kind of tooltip was already removed from `TaskRow`'s checkbox/"⋯" button for the same reason.
    Removed the `Tooltip` wrapper (renders unwrapped now, matching `TaskRow`'s pattern) and the
    now-dead `priorityLabel` helper that only that `Tooltip` was calling. The calendar icon's own
    `Tooltip` (`"Due X"`/`"Set due date"`) is untouched — not mentioned, not removed.
  - Selecting a priority calls
    `taskApi.updateTask` with the task's current field values plus the new priority — there's no
    dedicated "change priority" endpoint, `UpdateTaskPayload` fully replaces mutable fields, so
    every other field has to be resent unchanged (same approach `TaskFormDialog` already used).
    None of this opens a `Dialog` — it's menu-only, by design. Net effect: `TaskRow` no longer
    needs a `projects` prop at all (it never rendered `TaskFormDialog` itself even before this —
    that only ever lived in `TasksPage`/`TaskDetailPanel`) — removed from its `Props` and every
    call site. The `compact` prop (added earlier this entry to hide status-chip+Edit only for the
    Completed bucket) is gone too: once status-chip+Edit are gone from every row, not just
    Completed's, there was nothing left for it to conditionally hide.
  - One functional trade-off from removing Edit off the row entirely: a **subtask** row (rendered
    only inside `TaskDetailPanel`, never independently selectable) now has no way to edit its
    title/description/due date/project at all — only Priority/Status/Delete via the three-dot menu.
    A top-level task doesn't lose this: selecting it still opens `TaskDetailPanel`, which has its
    own separate Edit button (unrelated code, not part of `TaskRow`) for full-field editing.
  - Each of the four sections is independently expandable/collapsible via a chevron
    (`ExpandMoreIcon`/`ChevronRightIcon`) in its header — `TasksPage.tsx`'s `collapsedBuckets`
    (a `Set<TaskBucketKey>`) tracks which are collapsed, defaulting to `{ COMPLETED }` (every other
    section starts expanded — Completed is both dimmed via `opacity` and collapsed by default,
    since it's the one section that's never the day's actionable work). Clicking anywhere in the
    header row toggles it, not just the icon itself, for a larger click target. Collapsed state is
    local component state, not persisted — it resets to the `{ COMPLETED }` default on page
    reload/re-mount, same as every other piece of this page's UI state (`filter`, `selectedTask`).
  - There's no unpaginated "all projects"/"all tasks" endpoint on the backend, so both the project
    list (`TasksSidebar`'s `PROJECT_PICKER_SIZE`) and the dashboard/list fetch
    (`TasksPage.tsx`'s `DASHBOARD_SIZE = 200`) are pragmatic MVP caps, not completeness guarantees —
    revisit if a user's project/task count ever realistically approaches those.

- **`task-service` (Phase 3 — DTOs, MapStruct mappers, REST controllers).** Builds on Phases 1–2
  (below): `dto/{ProjectResponse,TaskResponse}.java` (plain response records — same shape as
  `social-service`'s `FriendRequestResponse`) and `dto/{Create,Update}{Project,Task}Request.java` +
  `dto/ChangeTaskStatusRequest.java` (`@Data` classes with `jakarta.validation` annotations, same
  shape as `content-service`'s `CreateArticleRequest`), `mapper/{ProjectMapper,TaskMapper}.java`
  (plain MapStruct interfaces — unlike `FriendMapper`, nothing here needs an injected collaborator
  like `StorageService`, so no abstract-class workaround needed), and `api/{ProjectApi,TaskApi}.java`
  (+ `api/impl/`) — `/api/v1/projects` and `/api/v1/tasks`, both using `@CurrentUserId` (not
  `@AuthenticationPrincipal CustomOAuth2User`) since only the caller's id is ever needed, avoiding a
  `spring-boot-starter-oauth2-client` dependency in this module.
  - `TaskResponse.projectId`/`contentItemId` are flat `Integer`s, not nested objects — simpler
    mapping, no fetch-join concerns; a client needing full project/content details already has the
    dedicated endpoint for that resource. Revisit only if a future GUI screen genuinely needs those
    details inline with every task in a list.
  - `TaskApi`'s status-change endpoint is `POST /{id}/status`, not `PATCH` — matches this codebase's
    existing action-endpoint style for state transitions (`FriendApi`'s `POST .../accept`) rather
    than introducing an HTTP verb not used anywhere else in the reactor.
  - No `spring-boot-starter-validation` added to `task-service/pom.xml` — same as `content-service`
    (checked: it doesn't declare it either). The `jakarta.validation` annotation types are already
    on the compile classpath transitively via `common`'s `spring-boot-starter-data-jpa`; at runtime
    `@Valid` is enforced because `gateway` (the bootable app) already pulls in
    `spring-boot-starter-validation` transitively through `ai-service`.
  - Not yet built (planned as separate, later phases): `gateway` wiring (pom dependency only),
    `task-service/CLAUDE.md`, tests.

- **`task-service` (Phase 2 — repository + service layer).** Builds on Phase 1's entities/enums
  (below): `repository/{ProjectRepository,TaskRepository}.java` (`TaskRepository` also extends
  `JpaSpecificationExecutor<Task>`), `repository/spec/TaskSpecification.java` (dynamic filtering by
  project/status/priority/due-date range, always scoped to the caller's `ownerId` — same
  static-factory shape as `social-service`'s `UserSpecification`), `exception/TaskErrorCode.java`
  (`PROJECT_NOT_FOUND`/`TASK_NOT_FOUND`/`TASK_CONTENT_ITEM_NOT_FOUND`/
  `TASK_INVALID_STATUS_TRANSITION`), and `service/{ProjectService,TaskService}.java` (+ `impl/`) —
  both return entities, never DTOs, same convention as `social-service`'s `FriendService`.
  - A project/task owned by a different user throws the same `*_NOT_FOUND` as a genuinely missing
    id (no separate 403) — mirrors `FriendServiceImpl`'s mutual-invisibility handling of blocked
    users, avoiding leaking "this id exists but isn't yours" to the caller.
  - `TaskServiceImpl.changeStatus` is `TaskStatus.canTransitionTo`'s one real caller — rejects only
    a no-op transition, throwing `TASK_INVALID_STATUS_TRANSITION`.
  - `TaskServiceImpl` reaches `content-service`'s `ContentItemRepository` directly (not through a
    `content-service` service class) to resolve an optional `Task.contentItem` link — the first
    real use of the `task-service` → `content-service` dependency added in Phase 1; mirrors how
    `content-service`'s own controllers reach `common`'s `UserRepository` directly for the same
    kind of simple existence lookup, rather than adding a service-layer indirection for a one-line
    check.
  - `service/{ProjectCommands,TaskCommands}.java` (`Create`/`Update` records, no validation
    annotations — that's a later REST-DTO concern) and `service/TaskFilter.java` — same
    plain-input-record shape as `content-service`'s `ArticleCommands`.
  - Not yet built (planned as separate, later phases): DTOs/mappers/REST controllers, `gateway`
    wiring, `task-service/CLAUDE.md`, tests.

- **New `task-service` module (Phase 1 — module scaffold: entities, enums, Liquibase migration).**
  Personal task/project management, added as a new sibling module per the established convention
  (own vertical slice, own package root `com.ttg.devknowledgeplatform.task.*`) rather than growing
  `gateway` or an existing module. MVP scope, agreed before implementation: single-user
  ("personal-first" — every `Project`/`Task` has an `owner`, no shared membership yet; team
  collaboration is an explicit future phase), no dependency on `social-service` (ownership is just
  a `User` reference, not a friend-graph concept), tasks may be standalone or grouped under a
  `Project` (`Task.project` is nullable), and an optional link to `content-service`'s `ContentItem`
  so a task can track work against a piece of content (e.g. "write article X").
  - `entity/Project.java` — name, description, `owner` (`User`, `@ManyToOne`), `status`
    (`ProjectStatus`).
  - `entity/Task.java` — `project` (`Project`, `@ManyToOne`, nullable), `owner` (`User`,
    `@ManyToOne`), title, description, `status` (`TaskStatus`, default `TODO`), `priority`
    (`TaskPriority`, default `MEDIUM`), `dueDate` (`Instant`, nullable), `contentItem`
    (`content-service`'s `ContentItem`, `@ManyToOne`, nullable).
  - `enums/{ProjectStatus,TaskPriority,TaskStatus}.java` — `TaskStatus.canTransitionTo(target)`
    guards only the no-op case (`target == this`); deliberately permissive otherwise (any status →
    any other status) rather than a strict linear workflow or a State-pattern class hierarchy — this
    is a personal task tracker, not a team approval process. Revisit if transitions ever need real
    side effects (e.g. auto-stamping a completion timestamp).
  - New dependency: `task-service` → `content-service` (one-directional, real `@ManyToOne` FK on
    `Task.contentItem`) — mirrors `ai-service` → `content-service`, just optional rather than
    required.
  - Liquibase `DKP-0020` (`gateway`'s changelog tree, same as every other module's tables — no
    per-module changelog folder) — new `product.PROJECT`/`product.TASK` tables, both with the
    standard 5 audit columns via `common`'s `AbstractEntity`.
  - Not yet built (planned as separate, later phases): repository/`Specification` + service layer +
    `TaskErrorCode`, DTOs/mappers/REST controllers, `gateway` wiring, `task-service/CLAUDE.md`, tests.

- **`gateway`: real STOMP integration test for `social-service`'s `DmMessagingController`**
  (`gateway/src/test/java/.../ws/DmMessagingStompIntegrationTest.java` +
  `AbstractStompIntegrationTest` base class) — the first test in the whole reactor (every module's
  `src/test` was previously empty). Boots the full `gateway` Spring context rather than a slice,
  because `WebSocketConfig`/`StompAuthChannelInterceptor` (which actually wire
  `DmMessagingController` into a running broker) only ever get assembled together there —
  `social-service` itself has no `@SpringBootApplication`. Uses real Testcontainers instances for
  Postgres (`pgvector/pgvector:pg17`, with Liquibase run against it), Redis, and MinIO (the
  context's cache/storage beans are real, not mocked) plus a real `WebSocketStompClient` — no part
  of the STOMP auth/messaging path is mocked around. Covers: dual-delivery to both participants'
  `/user/queue/dms`; thread reuse across two sends on the same pair (a regression guard for the
  OSIV/lazy-loading constraint documented on `DmMessagingController` and in `gateway/CLAUDE.md`);
  STOMP `CONNECT` rejection on a missing `Authorization` header, an expired JWT, and a refresh token
  presented in place of an access token; and `WsErrorResponse` delivery to `/user/queue/errors` for
  the friend-required (`DM_001`) and unknown-recipient-UUID (`USER_001`) business-rule failures.
  Added `org.testcontainers:junit-jupiter`/`postgresql` (test-scope) to `gateway/pom.xml` — no
  explicit `testcontainers-bom` import needed, `spring-boot-dependencies` (this reactor's
  grandparent POM) already manages every `org.testcontainers:*` artifact at a version tested
  against this Spring Boot release.
  - **Known, deliberately undocumented-by-test gap** (left as `DmMessagingController`'s own Javadoc
    note, not a fix applied here): its `@MessageExceptionHandler` only catches `ApiException` —
    an unrelated unchecked exception (e.g. `@CurrentUserId`'s `IllegalStateException` when no
    principal is present) would not become a `WsErrorResponse` and instead falls through to
    Spring's default STOMP error handling. Not covered by an automated test since triggering it
    deterministically would require bypassing real DI rather than exercising the real flow.

- **`infra`: `Seeder` marker interface (`int seed()`)** — documentation-only, implemented by every
  seeder in the reactor (`CsvSeeder<T>`, so every one of its subclasses gets it for free, plus the
  custom-`seed()` ones: `QuestionAnswerSeeder`, `FriendGraphSeeder`, `DmThreadSeeder`), same
  "Find Implementations" purpose `infra`'s own `ApplicationEventHandler` marker serves for event
  handlers. Deliberately **not** used for polymorphic invocation — `gateway`'s `DataSeedingRunner`
  still calls each seeder by name in an explicit, hardcoded dependency order (categories → tags →
  Q&amp;A → users → friend graph → DM threads → blocks); looping over an injected `List<Seeder>`
  instead would hand that ordering over to Spring's bean-registration order, which isn't guaranteed
  to match the real cross-entity dependencies that order encodes.

- **`social-service`: `DmThreadSeeder` — sample DM conversation data.** One random lorem-ipsum
  conversation (5–15 messages, timestamps backdated across the last ~2 weeks so
  `lastMessageAt`/history ordering looks like a real, aged conversation rather than everything
  created at seed time) per existing `Friendship` row, so the new `@messaging` GUI has data to
  show. Pairs come straight from `FriendshipRepository.findAll()` — no new CSV file, since a DM
  thread can only exist between already-friended users anyway. Idempotent per pair (skips if a
  `DmThread` already exists), mirroring `FriendGraphSeeder`'s `existsBetween`-style guard. Wired
  into `gateway`'s `DataSeedingRunner` right after `friendGraphSeeder.seed()` (it depends on the
  friend graph already existing) and before `userBlockSeeder.seed()`.
  - **New `DmMessageRepository.backdateCreatedAt`** (`@Modifying @Query` JPQL bulk update,
    annotated `@Transactional`) — a message's `dteCreation` (the audit column
    `DmMessageResponse.createdAt` maps straight to) can't be backdated through a normal save:
    `AbstractEntity`'s `@PrePersist` unconditionally resets it to "now" on every `save()`, and the
    column is `updatable = false` for any later JPA-managed update. A JPQL bulk update is a direct
    DML statement rather than a dirty-checked entity update, so it bypasses both. Each message is
    saved normally first (to get its id), then fixed up via this method. `DmThread.lastMessageAt`
    needed no such workaround — it's a plain column, set directly once a thread's messages are all
    generated. The method needs its own `@Transactional`: unlike `JpaRepository`'s `save`/`delete`
    (`@Transactional` on `SimpleJpaRepository` itself), a custom `@Modifying @Query` method gets no
    transaction for free — Hibernate throws `TransactionRequiredException` without one, since
    `DmThreadSeeder` calls it from a plain, non-transactional method (caught at startup on first
    real run: seeding failed immediately on the first message with exactly that exception).
  - No lorem-ipsum/faker dependency existed anywhere in the reactor (checked); since the content
    only needs to be random filler text (not realistic names/sentences), a small hand-rolled word
    bank + random sentence assembly lives directly in `DmThreadSeeder` rather than adding one.

- **`gui`: 1:1 DM chat over STOMP WebSocket (Phase 1 of the group/DM chat GUI — groups/channels are
  Phase 2)** — new `src/features/messaging/` feature folder (alias `@messaging`), fronting
  `social-service`'s `DmApi`/`DmMessagingApi` the same way `@friends` fronts its friend-graph
  endpoints. New dependency: `@stomp/stompjs` (no `sockjs-client` — the backend registers a raw
  WebSocket endpoint only, no SockJS fallback).
  - `api/socket.ts` — a thin facade over `@stomp/stompjs`'s `Client`, the only place this feature
    touches the raw library. `Authorization: Bearer <token>` is sent as a STOMP `CONNECT` frame
    header (not an HTTP header — the WS handshake itself is `permitAll` server-side since browsers
    can't set headers on it), re-read fresh via a `beforeConnect` hook on every (re)connection
    attempt including stompjs's own automatic reconnects.
  - `context/StompConnectionContext.tsx` — one shared STOMP connection for the whole app (opened on
    login, closed on logout), added to `App.tsx` alongside `NotificationProvider`. Holds the single
    real subscriptions to `/user/queue/dms` and `/user/queue/errors`, fanning DMs out to any number
    of listeners (`useDmThread`/`useDmThreads`) and forwarding `WsErrorResponse` payloads to
    `useNotification().showError`.
  - **Design decision: no optimistic local message append.** Both `DmMessagingController` and
    `GroupMessagingController` echo a sent message back to its own sender (`convertAndSendToUser` to
    both DM participants), so `useDmThread`'s `send` only publishes — the message renders once it
    comes back over `/user/queue/dms`, the same path as a message from the other participant. This
    is also how a brand-new conversation (started before any thread exists) discovers its
    `threadId`: sending is always keyed by `recipientUuid`
    (`/app/dms/{recipientUuid}/messages`), never `threadId`, so the first echoed message is what
    resolves one.
  - Routes: `/messages` (conversation list only), `/messages/new/:recipientUuid` (pending
    conversation, no thread yet), `/messages/:threadId` (existing thread) — full-page layout,
    NavBar hidden, mirroring the `/chat`/`/chat/:sessionId` precedent. NavBar gained a "Messages"
    nav button (no unread badge — `DmThreadResponse` has no unread-count field yet).
  - Entry points added to `@friends`: a "Message" icon button next to `FriendsMenuButton` in both
    `FriendsList.tsx` (the Friends tab) and `RelationshipActionButton`'s `FRIENDS` case (search
    results for someone you're already friends with) — both navigate to
    `/messages/new/:userUuid`.
  - **Explicitly out of scope this phase**: attachments (composer is text-only — `social-service`'s
    own `MessageAttachmentRequest` Javadoc says the upload endpoint doesn't exist yet server-side),
    reconnect-across-a-dropped-socket for per-thread subscriptions added after the initial connect
    (the two app-wide subscriptions do survive a reconnect; a per-thread one added later doesn't —
    acceptable for Phase 1, noted in `api/socket.ts`), and any unread-count UI.
  - Verification: `npx tsc --noEmit` and `npm run build` both pass; the only `tsc` errors are
    pre-existing and unrelated (already documented in `gui/CLAUDE.md`: `App.test.tsx`/
    `reportWebVitals.ts`'s missing test-runner/`web-vitals` deps, plus two pre-existing unused-icon
    imports and one pre-existing `KeyboardEvent` type mismatch in `VerifyOtp.tsx`).

- **`gui`: `@tasks`' `TaskRow.tsx` — inline title editing, and frontend-only manual drag-to-reorder
  for the flat-list views.**
  - **Inline title rename.** Clicking a row's title (or selecting the row at all, in the main list)
    swaps its `Typography` for a `TextField` (`variant="standard"`, `disableUnderline`, `autoFocus`)
    committed on blur/Enter, discarded on Escape — no separate edit dialog for a rename-only change.
    An `optimisticTitle` local state bridges the gap between commit and `onChanged()`'s refetch
    landing, avoiding a one-frame blink back to the stale pre-edit title. Selecting a row
    (`onSelect`) now also calls the same `startEditingTitle()` helper the title's own click uses, so
    a single click both opens `TaskDetailPanel` and drops straight into rename mode via the
    `TextField`'s `autoFocus`.
    - **Bug fixed along the way**: entering edit mode visibly shifted the title text up a couple
      pixels, initially misread as a stray MUI underline. Root cause (confirmed via a headless-
      Chrome DevTools-Protocol pixel measurement, not just source-reading): `Typography` is inline
      content subject to half-leading (baseline alignment against the ambient line-height "strut"
      pushes it down inside its box), while `TextField`'s root is `display: inline-flex`, an atomic
      box exempt from half-leading — a positioning-algorithm difference, not a font-metric mismatch,
      so matching `font-size`/`line-height` between the two could never have fixed it. Fix: made the
      wrapping title `Box` a flex container (`display: 'flex', alignItems: 'center'`), putting both
      elements under the same (flexbox) alignment rule regardless of which is mounted.
  - **The "⋯" (`MoreHorizIcon`) button no longer reserves layout space while hidden.** It used to be
    hidden via `opacity: 0` inside the row's normal flex flow, which still reserved its width — so
    the due-date label sat left of the row's true right edge even when the button was invisible.
    It's now `position: absolute` (against a `position: relative` row `Stack`), taken out of flex
    flow entirely, landing in a dedicated gutter reserved by its containers rather than overlapping
    the due date. New exported `TASK_ROW_ACTIONS_GUTTER_PX` constant (`TaskRow.tsx`) is consumed by
    both `TasksPage.tsx` (widens the list column's right padding by that amount, shifting the
    column/`TaskDetailPanel` divider right to make room) and `TaskDetailPanel.tsx` (adds the same
    amount as extra `pr` on its subtask-list container, since that panel has no sibling column to
    widen into) — kept as one shared constant so the two containers' reserved space can't drift out
    of sync with the button's own offset.
  - **New: drag-to-reorder, frontend-only (not persisted server-side).** Initially scoped to the
    flat-list views only (project-filter list, Today/This-week smart filters), then extended to the
    bucketed "All" dashboard too — each of its four buckets (Overdue/Today/Upcoming/Completed) gets
    its own independent manual order and its own `DndContext`, so dragging only ever reorders within
    a bucket. Bucket *membership* itself still always comes from `bucketTasks` (due date/status),
    never from the manual order — there's no cross-bucket drop target, so a task can't be dragged
    out of the bucket its due date/status puts it in. New dependency: `@dnd-kit/core` +
    `@dnd-kit/sortable` + `@dnd-kit/utilities` (chosen over `react-beautiful-dnd`, which Atlassian
    has archived and which has known friction under React 18 `StrictMode`).
    - New `hooks/useTaskOrder.ts`: holds one manually-reordered `Task[]` per "view" (keyed by
      `` `project:${id}` ``/`'today'`/`'week'`; `null` for the bucketed view, making the hook a
      no-op passthrough there — it's still called unconditionally, since React's rules of hooks
      forbid calling it only inside the `filter === 'all'` branch). The saved order is a plain
      `number[]` of task ids in `localStorage`, reconciled against every fresh fetch rather than
      trusted outright: stored ids still present in the fetch keep their saved relative order,
      anything not in the stored list (new tasks, or the first time a view has ever been ordered)
      is appended at the end. A stored id no longer present (task deleted, moved out of the
      filter) is silently dropped — never surfaced as an error — and a missing/corrupted
      `localStorage` entry degrades to "no saved order" rather than throwing, so every task just
      falls back to normal fetch order.
    - New `shared/constants/storage.ts` export `taskOrderStorageKey(viewKey)` (a builder, not a
      fixed `STORAGE_KEYS` entry, since these keys are per-view) — kept routed through this file
      rather than hardcoded in the feature, per its own "one source of truth for `localStorage`
      keys" rule.
    - New `components/SortableTaskRow.tsx` wraps `TaskRow` with a `useSortable`-driven drag handle
      (`DragIndicatorIcon`, hidden until hover the same way the "⋯" button is) rather than making
      the whole row draggable — `TaskRow` already overloads click for select/rename, so a dedicated
      handle avoids click-vs-drag-start gesture conflicts. `TaskRow` itself gained a `dragHandle?:
      ReactNode` prop (rendered before the checkbox) and had its `Props` interface exported as
      `TaskRowProps` so `SortableTaskRow` can type its own props as `Omit<TaskRowProps,
      'dragHandle'>` — kept out of `TaskRow.tsx` entirely so the far more common non-draggable case
      (subtask rows inside `TaskDetailPanel`, not independently orderable) carries no `@dnd-kit`
      dependency.
    - `TasksPage.tsx`'s flat-list branch now renders `orderedTasks` (from `useTaskOrder`) through a
      `DndContext`/`SortableContext` instead of mapping `tasks` directly; `PointerSensor` has a 4px
      activation-distance constraint (avoids hijacking a plain click before it's clearly a drag),
      and a `KeyboardSensor` is wired in too for accessible, non-pointer reordering.
    - **Bucketed "All" dashboard extension**: `buckets` (the `bucketTasks(tasks)` result) is now
      `useMemo`'d, keyed on `tasks` — needed once bucket arrays fed `useTaskOrder`, since an
      unmemoized `bucketTasks(tasks)` call produces a brand-new array reference on every render
      (even ones that don't touch `tasks` at all, e.g. selecting a row), which would otherwise
      re-run each bucket's `localStorage` reconciliation effect on every unrelated re-render
      instead of only on a real fetch. `useTaskOrder` is called 4 times at the top level — once per
      `BUCKET_ORDER` key (`'bucket:OVERDUE'`/`'bucket:TODAY'`/`'bucket:UPCOMING'`/
      `'bucket:COMPLETED'`) — rather than inside the `BUCKET_ORDER.map(...)` render callback, since
      React's rules of hooks require a fixed number of hook calls in the same order every render;
      `BUCKET_ORDER`'s 4 keys are a fixed, known set, so unrolling the loop is safe. Each bucket
      renders its own `DndContext`/`SortableContext` (not one spanning all four), so there is no
      cross-bucket drag target.
    - Verification: `npx tsc --noEmit` and `npm run build` both pass (no new errors beyond the
      pre-existing ones already noted above); not yet click-tested in a live browser in this
      session — no Docker/browser automation available in the sandbox this was written in, so the
      drag interaction itself needs manual verification.
  - **`'today'`/`'week'` filters moved from a flat list to the same bucketed
    Overdue/Today/Upcoming/Completed rendering as `'all'`**, and a new **Inbox** sidebar entry
    (`TasksSidebar.tsx`, `InboxIcon`) was added as an explicit, always-visible way back to `'all'` —
    which was previously only reachable as the filter's initial/default state (or as
    `handleArchive`'s fallback after archiving the active project), with no dedicated sidebar
    button of its own. No new `TaskFilter` variant: "Inbox" is just a label over the existing
    `'all'` value.
    - `TasksPage.tsx` now treats `'all'`/`'today'`/`'week'` as one `isBucketedView` group (only a
      project filter still gets the flat, unsectioned list) — for `'today'`/`'week'` this mostly
      narrows which buckets ever have anything in them, since `fetchTasks` already constrains the
      fetch itself via `dueAfter`/`dueBefore` for those two (e.g. `'today'`'s window means Overdue
      and Upcoming can never populate), but it still separates done from not-done via the
      Completed bucket rather than mixing them into one flat list the way the old flat rendering
      did. The per-bucket `useTaskOrder` calls/keys (`'bucket:OVERDUE'` etc.) are unchanged and
      intentionally shared across all three entry points — a task due today keeps the same manual
      position within "today's tasks" whether reached via Inbox, Today, or This week, since it's
      conceptually the same bucket regardless of which sidebar item got you there.
    - The flat-list `useTaskOrder` call (`orderViewKey`) is now `null` for `'today'`/`'week'` (only
      non-null for a project filter) — those two no longer render through that branch, so leaving
      their old `'today'`/`'week'` view keys wired up would just be dead, unused state.

- **`gui`: `TaskRow.tsx`'s due-date label is now clickable** — opens the Date-presets-plus-Custom
  popover directly, without going through the "⋯" menu first. New shared
  `components/DatePickerMenu.tsx`, extracted out of `TaskQuickAdd.tsx` (which had this exact
  popover inline, kept separate from `TaskOptionsMenu`'s own Date section since its trigger needed
  to live on the quick-add input itself) rather than pasting a third near-identical copy for
  `TaskRow`'s new trigger — `TaskQuickAdd.tsx` now renders `DatePickerMenu` too instead of its old
  inline `Menu`, so there are two call sites and one implementation instead of two near-duplicates.
  `DatePickerMenu` also picked up preset/Custom highlighting (`matchingPreset`/`isCustomDate`,
  copied from `TaskOptionsMenu`'s own Date section) that `TaskQuickAdd`'s inline version never had —
  a free consistency win from unifying the two, not something separately requested.
  `TaskOptionsMenu.tsx`'s own inline Date section is untouched (different shape — a fieldset inside
  one shared combined menu, not its own standalone `Menu` — so it wasn't a clean fit for this
  extraction without a larger restructure that wasn't in scope here).

- **`gui`: `SortableTaskRow.tsx`'s drag handle no longer reserves in-flow width in the row while
  hidden** — same fix as the "⋯" button's own earlier one, applied to the opposite side. It used to
  render in-flow (first flex child, before the checkbox) with a plain `opacity: 0`/`1` toggle, which
  still reserved its width at all times, shifting the checkbox/title/everything else in the row
  right even when not hovering. Now `position: absolute` (against `TaskRow`'s `position: relative`
  `Stack`), landing in a left-side gutter — reusing the same `TASK_ROW_ACTIONS_GUTTER_PX` constant
  the "⋯" button's right-side gutter already used, so both sides reserve the identical width.
  `TasksPage.tsx`'s list column now widens both `pl` and `pr` by that amount (previously only `pr`
  was widened) — `TaskDetailPanel.tsx`'s subtask-list container needed no equivalent `pl` change,
  since subtask rows render plain `TaskRow` (never `SortableTaskRow`) and so never render a drag
  handle to make room for in the first place.

- **`gui`: `TASK_ROW_ACTIONS_GUTTER_PX` reduced 36px → 28px, and both the "⋯" button and the drag
  handle shrunk below MUI's own `fontSize="small"` preset** — the two gutters added on top of the
  list column's existing `p: 2.5` padding read as excessive empty margin once both sides were
  widened by the previous two entries. Both `IconButton`s dropped their `size="small"` prop in favor
  of an explicit `sx={{ p: '4px' }}` (down from `size="small"`'s own ~5px), and their icons
  (`MoreHorizIcon`/`DragIndicatorIcon`) now set `sx={{ fontSize: 18 }}` directly instead of the
  `fontSize="small"` prop (MUI's `"small"` preset is 20px — there's no smaller built-in preset, so
  going below it means setting the CSS `font-size` directly rather than switching presets). The
  `right - 4`/`left - 4` inset convention (a few px of breathing room between the button and the
  gutter's outer edge) is unchanged — it just recalculates against the smaller gutter automatically,
  since both are still expressed off the one shared `TASK_ROW_ACTIONS_GUTTER_PX` constant.

- **`gui`: `TasksPage.tsx`'s list-column `pl`/`pr` flat outer margin dropped 20px → 8px** (the
  `` `${20 + TASK_ROW_ACTIONS_GUTTER_PX}px` `` formula's `20` term) — still looked too wide even
  after the previous entry's gutter/icon shrink, and it turns out that flat `20` term was the
  actual culprit, not `TASK_ROW_ACTIONS_GUTTER_PX` itself: the two terms in that formula do
  different jobs. `TASK_ROW_ACTIONS_GUTTER_PX` sets how far the floating icon clears the row's own
  content (checkbox/date) — it's lower-bounded by the icon button's own footprint (~26px: 18px icon
  + 4px padding per side) and overlaps real row content if shrunk much past that, so it wasn't
  touched again here. The `20` is a flat margin from the column's *true* edge, entirely unrelated to
  the icon's size — safe to shrink freely, and the actual lever for "the whole thing looks too
  wide." `TaskDetailPanel.tsx`'s subtask-list `pr` (`` `${TASK_ROW_ACTIONS_GUTTER_PX}px` ``, no flat
  term) was left as-is — it never had this extra margin added in the first place.

- **`gui`: `TasksPage.tsx` gained a section headline (icon + name) above `TaskQuickAdd`** —
  `InboxIcon`/`'Inbox'` for `filter === 'all'`, `TodayIcon`/`'Today'`, `DateRangeIcon`/`'This week'`,
  matching `TasksSidebar.tsx`'s own icon choices for those three entries so the headline's icon
  always agrees with whichever sidebar item is currently selected. A project filter shows the
  project's own name (`projects.find(p => p.id === filter.projectId)?.name`) with a new `FolderIcon`
  — projects have no icon of their own in the sidebar today (`TasksSidebar`'s project
  `ListItemButton`s are text-only), so this is a new default introduced specifically for this
  headline, not reused from anywhere else in the feature.

### Fixed

- **`gui`: `TaskQuickAdd.tsx`'s input read as narrower than the `TaskRow`s below it.** `TaskRow.tsx`'s
  row `Stack` deliberately bleeds 8px past its container on each side (`mx: -1, px: 1`, for a
  rounded-hover-highlight look rather than a hard-edged rectangle — see `gui/CLAUDE.md`), but
  `TaskQuickAdd`'s outer `Stack` had no equivalent treatment, so its `fullWidth` `TextField`'s own
  edges sat 8px inside where a `TaskRow`'s border-bottom actually ends — visible as the row's
  bottom border extending past the input's width.
  - First attempted fix (giving `TaskQuickAdd`'s `Stack` the identical `mx: -1, px: 1`) turned out
    to be wrong and was corrected in the same pass, verified this time via a real
    `getBoundingClientRect` measurement (headless-Chrome CDP) rather than source-reading alone: it
    matched the two `Stack`s' own bled width, but missed that each component draws its *visible*
    border at a different nesting depth. `TaskRow`'s border-bottom is drawn directly on its own bled
    `Stack`, so the visible edge **is** the bleed. `TaskQuickAdd`'s visible border is the
    `TextField`'s own outline, one level *inside* its `Stack` — adding `px: 1` there inset that
    visible border 8px back in from the bleed, reproducing the exact same mismatch one level
    deeper. Final fix: `mx: -1` alone (no `px: 1`) on `TaskQuickAdd`'s `Stack`, so the `fullWidth`
    `TextField` itself fills the widened box and its outline reaches the same edges `TaskRow`'s
    border does.

- **`gui`: `TasksPage.tsx`'s 3 columns (sidebar, task content, detail panel) are now resizable by
  dragging the divider between any two of them.** New dependency: `react-resizable-panels` (chosen
  over hand-rolling pointer-event drag logic — pointer capture, min/max clamping, keyboard resize,
  and touch support are all easy to get subtly wrong by hand, and this library is built exactly for
  this). The installed version (4.x) uses a different API than the commonly-referenced older docs
  for this library — `Group`/`Panel`/`Separator`, not `PanelGroup`/`Panel`/`PanelResizeHandle` —
  don't assume the older API from memory when touching this again.
  - Layout: `<Group orientation="horizontal">` wraps three `<Panel>`s (`id`s `tasks-sidebar`,
    `task-content`, `task-detail`) separated by two new `components/ResizeHandle.tsx` instances.
    Default sizes are `20`/`40`/`40` (percent) — `task-content` and `task-detail` intentionally
    match exactly, per this feature's own requirement that the middle column be the same size as
    the detail panel by default.
  - **Persistence is frontend-only** (same "stored client-side, never sent to the backend" approach
    as `useTaskOrder`), but uses the library's own built-in `useDefaultLayout({ id, storage:
    window.localStorage, panelIds })` hook rather than a hand-rolled read/write — `panelIds` must
    list the exact same three ids the `Panel`s use, or the persisted layout won't reapply correctly
    on mount.
  - `components/ResizeHandle.tsx` styles the library's unstyled `Separator` via MUI's own `styled()`
    (not raw `@emotion/styled` — its callback `theme` param isn't aware of MUI's `palette`/`spacing`
    augmentations, confirmed by a real `tsc` error when this was tried first) — a 4px-wide hit
    target with a thin 1px line centered inside it via `::after`, widening/recoloring to
    `primary.main` on hover/`:active`/`:focus-visible` (the library exposes no "currently dragging"
    `data-*` attribute to hook a dedicated third state off).
  - `TasksSidebar.tsx` lost its fixed `width: 260, flexShrink: 0` (the Panel controls width now) and
    its own `borderRight` (the `ResizeHandle` between it and `task-content` is the divider now,
    drawing both would double up); `TaskDetailPanel.tsx`'s two return branches (`!task` empty state
    and the main content) both switched `flex: 1` → `height: '100%'`, same reasoning — sizing along
    the horizontal axis is the `Panel`'s job now, these components just need to fill it vertically.
    The middle `task-content` column's own wrapping `Box` (in `TasksPage.tsx`) made the same
    `flex: 2` → `height: '100%'` swap, keeping its `pl`/`pr` gutter styling
    (`TASK_ROW_ACTIONS_GUTTER_PX`) and dropping its own now-redundant `borderRight` for the same
    reason as `TasksSidebar`'s.
  - Verified via a real render + `getBoundingClientRect` measurement (headless-Chrome CDP), not
    layout math alone: default 20/40/40 split confirmed pixel-for-pixel equal between
    `task-content`/`task-detail`; a synthesized drag on the sidebar/`task-content` separator moved
    exactly the dragged distance and left `task-detail` untouched; the resulting layout appeared
    correctly in `localStorage` under
    `react-resizable-panels:tasks-page-layout:tasks-sidebar:task-content:task-detail`.

- **`gui`: `TaskDetailPanel.tsx` rewritten as fully inline-editable — no more separate "Edit"
  dialog.** New header row: checkbox / vertical divider / due-date icon+label (click → opens
  `DatePickerMenu`, same shared popover `TaskRow`/`TaskQuickAdd` use) / a spacer / a priority flag
  icon (click → opens `TaskOptionsMenu` with only `priority`/`onPriorityChange`, the same
  minimal-usage pattern `TaskQuickAdd`'s own priority trigger already established), then a
  horizontal divider, then the title and description — both click-to-edit the same way `TaskRow`'s
  title already worked (`Typography` ⇄ `TextField`, commit on blur/Enter for the single-line title,
  blur-only for the multiline description since Enter needs to insert a real newline there).
  Checkbox toggling reuses `TaskRow`'s exact `changeTaskStatus` DONE/TODO pattern. Every field write
  resends the task's other current field values (`basePayload()`) — same full-replace constraint as
  everywhere else in `@tasks` (no dedicated per-field endpoint).
  - **`components/TaskFormDialog.tsx` deleted outright** — confirmed via a full-codebase grep that
    `TaskDetailPanel.tsx` was its only importer (top-level task creation already went exclusively
    through `TaskQuickAdd`, never this dialog). Two stale comments elsewhere in `@tasks` referencing
    it by name (`TaskRow.tsx`, `utils/taskBuckets.ts`) were updated to point at its replacements
    rather than left dangling.
  - **Explicitly deferred, not solved, in this pass**: reassigning a task's project after creation,
    and adding a new subtask — `TaskFormDialog` was the only UI for both, and neither has a
    replacement yet. The project field/chip is dropped from the panel entirely (a task's project is
    now effectively fixed at creation time, set via `TaskQuickAdd`). The subtask *list* itself is
    unaffected — it doesn't depend on `TaskFormDialog` to render — only its "+" add-subtask trigger
    (which opened the now-deleted dialog with `parentTaskId` set) was removed, with no replacement
    yet; creating a subtask is currently unreachable from this panel. `TaskDetailPanel`'s own
    `projects` prop was removed (fully unused after dropping the project field) — its one call site
    in `TasksPage.tsx` was updated to match, rather than left as a silently-ignored prop.
  - Verified via a real render in headless Chrome (not just `tsc`): confirmed the header row's
    layout and colors match the intended template, confirmed the due-date click opens
    `DatePickerMenu`, confirmed the priority-flag click opens `TaskOptionsMenu` scoped to only its
    Priority section, and confirmed clicking the title/description each swap in a focused
    `TextField`/multiline `textarea`. One false negative during this process: an early combined
    test script reported the priority click as not opening its menu, traced to the test script's
    own flawed menu-closing step (a `document.body.dispatchEvent` synthetic Escape and a bogus
    backdrop-click selector) interfering with the next step, not a defect in the component — an
    isolated re-test of that one interaction confirmed it works correctly.

- **`gui`: full-list "blink" on every row action across `@content`'s three admin list pages and
  `@ai`'s `EmbeddingsPage`.** Same root cause as the `@tasks` fix above (see `Added`): each page's
  fetch function (`CategoryListPage.tsx`'s `fetchCategories`, `TagListPage.tsx`'s `fetchTags`,
  `QuestionAnswerListPage.tsx`'s `fetchQuestions`, `EmbeddingsPage.tsx`'s `load`) unconditionally
  set its `loading` flag, and that same function was also the callback fired after every
  create/edit/delete (and, for `EmbeddingsPage`, reindex/delete-index/index-all/refresh-corpus) —
  so any of those actions blanked the whole table to a spinner and back instead of updating rows in
  place. Applied the same `{ showSpinner }`-flag fix: `true` only for the genuine
  page/search/filter-driven load, `false` for every mutation-triggered refetch.
  `QuestionAnswerListPage` only needed the delete path fixed (create/edit navigate to a separate
  form page, not a dialog, so they were never affected). Audited every other feature
  (`friends`, `messaging`, `chat`, `auth`, `ai`'s `PipelineMetricsPage`) for the same pattern —
  none are affected: `friends`/`messaging`'s list components already guard their spinner branch
  with `loading && data.length === 0`, `chat`'s `SessionSidebar` never re-sets `loading` after the
  initial load, `auth`'s `Dashboard` uses a separate `saving`/`avatarUploading` state for mutations,
  and `PipelineMetricsPage` has no row-level actions to trigger a refetch in the first place.

- **`ConflictingBeanDefinitionException` on startup: `identity-service`'s and `social-service`'s
  `UserController` collided as the same Spring bean name.** Both are `@RestController` with no
  explicit bean name; Spring's default naming is the decapitalized *simple* class name only —
  package-independent — so `identity.api.impl.UserController` and `social.api.impl.UserController`
  both defaulted to `userController` and crashed `gateway`'s single component scan (which covers
  every feature module) the moment both classes existed at once. This was a latent bug from the
  original `UserApi`/`UserController` split documented in both modules' `CLAUDE.md` (pure
  profile-mutation in `identity-service` vs. relationship-enriched public-profile/search in
  `social-service`) — it surfaced now because nothing had actually booted the full reactor with
  both classes present since the split landed. Fixed by giving each an explicit bean name
  (`@RestController("identityUserController")` / `@RestController("socialUserController")`) rather
  than renaming either class — the class name itself matches each module's own controller-naming
  convention and is referenced throughout both `CLAUDE.md` files. A full cross-module grep for
  other same-simple-name `@Component`/`@Service`/`@Repository`/`@Controller`/`@RestController`/
  `@Configuration` classes confirmed this was the only such collision in the reactor.

### Changed

- **Reorganized `gui` from layer-centric folders into feature folders**, ahead of new feature work
  (group/chat) that would otherwise keep growing the old flat `api/`/`types/`/`components/`/`pages/`
  directories. Before: every top-level folder held files for every feature area mixed together —
  working on one feature meant touching four different directories, and `api/index.ts`/
  `types/index.ts` barrels grew one export line per feature forever. After:
  `src/features/{auth,chat,friends,content,ai}/` (each owning its own `api/`, `types.ts`, `pages/`,
  `components/`, `hooks/` — whichever it needs), `src/app/` (routing shell: `App.tsx`, `main.tsx`,
  `theme.ts`, `NavBar`, `GuestRoute`/`PrivateRoute`, `admin-shell/`), `src/shared/` (`httpClient`,
  `types.ts`, `NotificationContext`, `storage.ts`, `colors.ts`, `errorHandler.ts`, `useSubmitGuard`,
  `ConfirmDialog`). Explicitly rejected: splitting into separate npm packages/workspaces (a literal
  mirror of the backend's Maven multi-module split) — that split is justified on the backend by
  microservices-readiness (independently deployable services); this is one SPA with one Vite build
  and one deployment artifact, so workspace tooling would add real overhead for zero payoff today.
  - **The old "admin" grouping was split by backend domain, not kept as one feature** — it conflated
    domain ownership with role-based access. Category/Tag/QuestionAnswer CRUD (`adminApi.ts`'s Tag/
    Category/QuestionAnswer methods, `admin.types.ts`'s corresponding types) → new `content` feature
    (fronts `content-service`). Pipeline-metrics/embeddings monitoring (`adminApi.ts`'s remaining
    methods) → new `ai` feature (fronts `ai-service`'s admin endpoints), alongside chat. Role-gating
    stays exactly where it already was — `PrivateRoute requireRole="ADMIN"` — mirroring how the
    backend keeps `@PreAuthorize("hasRole('ADMIN')")` on individual controller methods inside
    `content-service`/`ai-service` rather than a separate admin module. `AdminLayout`/
    `AdminDashboard` (the nav frame + landing page, not tied to one domain) moved to
    `app/admin-shell/` instead.
  - **`userApi.ts` was itself split**, the same way: `getCurrentUser`/`updateProfile`/`uploadAvatar`
    (identity-service's `UserApi.java`) → new `@auth/api/profileApi.ts`; `getUserById`/`searchUsers`
    (social-service's `UserApi.java`) → `@friends/api/userApi.ts`, kept as its own file rather than
    folded into `friendApi.ts` (mirrors the backend's own `UserApi`/`FriendApi` split, per the
    pre-existing `gui/CLAUDE.md` note this convention was already documented for).
  - `@ai/types.ts` imports `ContentStatus`/`EmbeddingContentType` from `@content/types` — mirrors
    `ai-service`'s own dependency on `content-service` for `ContentItem`, the one deliberate
    cross-feature type reference.
  - Added path aliases (`@shared/*`, `@app/*`, `@auth/*`, `@chat/*`, `@friends/*`, `@content/*`,
    `@ai/*`) to both `tsconfig.json` (`compilerOptions.paths`) and `vite.config.ts`
    (`resolve.alias`) — cross-feature imports use these instead of depth-counted `../../` relative
    paths, which would otherwise have to change on every future file move. Same-feature imports
    (e.g. a page importing its own feature's `api/`) stay relative.
  - Retired the `api/index.ts`/`types/index.ts`/`services/index.ts` barrel files — each consumer now
    imports directly from the specific module (own-feature relative or cross-feature alias) instead
    of a growing barrel re-export.
  - Verification: `npx tsc --noEmit` and `npm run build` (`vite build`) both pass; the only
    remaining `tsc` errors are pre-existing and unrelated to this move (confirmed against the
    pre-move file contents) — `App.test.tsx`'s missing `@testing-library/react`/no test runner
    (`gui/CLAUDE.md` already documented no test framework is configured), `reportWebVitals.ts`'s
    missing `web-vitals` dependency (a create-react-app leftover never wired into the real Vite
    entry, `main.tsx`), a pre-existing `KeyboardEvent<HTMLDivElement>` vs `HTMLInputElement` type
    mismatch in `VerifyOtp.tsx`, and two pre-existing unused-icon-import lints. Two real import bugs
    were caught and fixed during the move itself: a missed multi-line type import in
    `QuestionAnswerFormPage.tsx` still pointing at the deleted `admin.types.ts`, and `index.tsx`
    (also dead/unused, same reason as `App.test.tsx` above) still importing `App` from its old path.
  - `gui/CLAUDE.md` rewritten to document the new structure and the reasoning above.

- **Audited every class in `common` for a genuine cross-module consumer, and moved eight that
  failed the test into `ai-service`** — `common/CLAUDE.md` already stated the rule ("before adding
  any new shared utility here, check whether it's genuinely needed by more than one module"); this
  was the first time it was applied retroactively to everything already there, by grepping real
  Java imports across the whole reactor rather than trusting Javadoc `{@code}`/`{@link}` prose
  (which turned out to describe cross-module usage that was never actually real, e.g.
  `SysParamRepository`'s own Javadoc claimed it was "used by both the `ai-service` and `api`
  modules"). Moved: `entity/{ChatSession,ChatMessage}` (packages `ai/entity/` — their repositories,
  `ChatSessionRepository`/`ChatMessageRepository`, already lived in `ai-service` from an earlier
  pass; only the entities themselves had been left behind in `common`), `entity/SysParam` +
  `enums/ParamKey` + `repository/SysParamRepository` + `service/SysParamService`(`Impl`) (despite a
  doc comment saying it was placed in `common` "so `ai-service` can reach it without depending on
  `gateway`," no second consumer ever materialized — its only two callers, `PromptGuardStage` and
  `CorpusStatisticsServiceImpl`, were always both in `ai-service`), `enums/ChatMessageRole` (field
  type on `ChatMessage`/`ConversationTurn`), `enums/ChatProvider` (`ai-service`'s
  `AiServiceConfig`/`ChatModelsConfig` only), and `dto/{ConversationContext,ConversationTurn}`
  (`ai-service`'s RAG pipeline/chat-session services only). Pure relocation, no logic change — each
  moved file kept its content verbatim aside from the package declaration and updated imports;
  Javadoc that described stale multi-module usage was corrected to describe the actual single
  consumer. New `ai/enums/` package created in `ai-service` (didn't exist before).
  Two more candidates were checked and correctly stayed in `common` despite looking
  single-consumer at first glance: `exception/ErrorResponse` (only `gateway`'s
  `JsonAuthenticationEntryPoint` imports it directly) and `exception/RateLimitExceededException`
  (only `ai-service`'s `ChatRateLimiter`/`ChatRateLimitInterceptor` throw it) — both have a
  compile-time reference from `common`'s own `GlobalExceptionHandler`
  (`@ExceptionHandler(RateLimitExceededException.class)`, direct `ErrorResponse.builder()` calls),
  and `GlobalExceptionHandler` itself must stay cross-module in `common`, so these two have to stay
  with it. `enums/UserProvider` was also checked and stays, for the same shape of reason: it's a
  field type on `User`, which is staying in `common`, so moving `UserProvider` to `identity-service`
  (its only other real consumer) would force `common` to depend on `identity-service` — an illegal
  reverse dependency. `common/CLAUDE.md`, `ai-service/CLAUDE.md`, root `CLAUDE.md`'s module table,
  and `docs/PROJECT_STRUCTURE.md`'s `common`/`ai-service` sections updated to match. Full reactor
  `clean compile` + `clean test` pass.

- **Moved `gateway`'s three event listeners into the module that owns the event each one reacts
  to** — the last piece of `gateway`'s `event/` package, which is now gone entirely.
  `ContentPublishedEventListener` → `ai-service` (package `ai/event/`, co-located with this
  module's own `PipelineCompletedEvent`/`Listener`): it only ever imported `ai-service`'s own
  `ContentIndexingService` and `content-service`'s `ContentPublishedEvent` (an already-allowed
  dependency direction), so leaving it in `gateway` was drift from the same earlier move that put
  `ContentIndexingService` itself in `ai-service` — this closes that gap.
  `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` → `social-service` (package
  `social/event/`, alongside the `FriendRequestSentEvent`/`FriendRequestAcceptedEvent` records they
  react to): both only ever depended on `infra`'s `AsyncEventHandler`/`EventHandler` framework and
  their own module's events, and currently just log — the seam for a future in-app/email
  notification. None of the three had a single `gateway`-specific dependency; this was a pure
  "does this need to live at the entry-point module" audit, same question already applied to
  `config/` and `UserSeeder`/`CorpusStatisticsServiceImpl`, with the same answer (no). One
  side-effect worth flagging: `social-service`'s "notification delivery does NOT live here" rule
  flips to "now CAN, if you're implementing it" — this module already depends on
  `identity-service` (from the earlier `UserApi` move), so reaching its `EmailService` to actually
  send a notification needs no new dependency. `gateway/CLAUDE.md`, `ai-service/CLAUDE.md`,
  `social-service/CLAUDE.md`, `content-service/CLAUDE.md` (its `ContentPublishedEvent` doc pointed
  at the old listener location) and `docs/PROJECT_STRUCTURE.md`'s `content-service`/`ai-service`/
  `social-service`/`gateway` sections updated to match. Full reactor `clean compile` + `clean test`
  pass.

- **Moved `CorpusStatisticsServiceImpl` from `gateway` into `ai-service`** — a class left behind
  when the rest of the content+AI indexing orchestration layer (`IngestionApi`/`Controller`,
  `EmbeddingIndexApi`/`Controller`, etc.) moved into `ai-service` in an earlier pass. Every one of
  its dependencies was already `ai-service`'s own (`ai.dto.RagFilter`, `ai.repository.
  ContentEmbeddingRepository` over `ai-service`'s own `ContentEmbedding` entity, `ai.service.
  CorpusStatisticsService` — the interface it implements — and `ai.utils.VectorUtils`), plus
  `content-service`'s `ContentType` (an already-allowed dependency) and `common`'s
  `SysParamService`/`ParamKey`; nothing `gateway`-specific at all, so this was a pure oversight
  rather than a deliberate separation. Pure relocation, no logic change, no new dependency needed
  on either side. `gateway/CLAUDE.md` and `docs/PROJECT_STRUCTURE.md`'s `common`/`ai-service`/
  `gateway` sections updated to match. Full reactor `clean compile` + `clean test` pass.

- **Moved `UserSeeder` from `gateway` into `identity-service`** — the last seeder that hadn't yet
  followed the precedent `content-service`'s `CategorySeeder`/`TagSeeder` and `social-service`'s
  `FriendGraphSeeder`/`UserBlockSeeder` already set (seeder Java class moves to the module owning
  what it seeds; the CSV data file itself stays under `gateway/src/main/resources/data/csv/`,
  unchanged). Also simplifies a dependency: `UserSeeder` needs `PasswordEncoder`, whose one bean
  definition (`PasswordEncoderConfig`) already lives in `identity-service` — before this move,
  `gateway` reached across the module boundary for it; now it's a plain same-module reference, one
  less cross-module bean dependency to reason about. No new dependency was needed on either side —
  `identity-service` already depended on `common` (for `User`/`UserRepository`) and `infra` (for
  `CsvSeeder`), and `infra`'s `commons-csv` dependency isn't optional, so it was already reachable
  transitively. `gateway`'s `DataSeedingRunner` now imports `UserSeeder` from `identity-service`
  the same way it already imports `content-service`'s and `social-service`'s seeders.
  `gateway/CLAUDE.md` and `identity-service/CLAUDE.md` updated; `docs/PROJECT_STRUCTURE.md`'s
  `common`/`infra`/`identity-service`/`gateway`/`social-service`/Database sections updated to match.
  Full reactor `clean compile` + `clean test` pass.

- **Moved `gateway`'s chat-specific rate limiting into `ai-service`, and split the
  `asyncEventExecutor` thread pool into `infra`** — two more follow-ups auditing what's left in
  `gateway`'s `config/` package, on the same "does this genuinely need to live at the entry-point
  module, or does it belong with the feature it actually serves" question already applied to
  `security/` (kept in `gateway` — see that entry) and the REST-layer extraction.
  - **`ChatRateLimiter`/`RateLimitProperties`/`ChatRateLimitInterceptor` → `ai-service`** (packages
    `ai/config/chat/`, `ai/config/web/`). Unlike `security/`, nothing here has a hard dependency
    forcing it to stay at the edge — Bucket4j rate limiting only ever protected the chat endpoint,
    which already lives in `ai-service`. New `ai/config/web/ChatMvcConfig` (a `WebMvcConfigurer`
    bean local to `ai-service`) registers the interceptor for `/api/v1/chat/**` — Spring composes
    every `WebMvcConfigurer` bean in the context automatically, so `ai-service` doesn't need
    `gateway`'s `WebMvcConfig` to register interceptors on its behalf; that class now registers no
    interceptors of its own. `ai-service/pom.xml` gained `spring-boot-starter-data-redis`,
    `com.bucket4j:bucket4j-redis`, and `spring-boot-starter-oauth2-client` (`optional=true`, type
    support only — `ChatRateLimitInterceptor` reads `common.dto.CustomOAuth2User` off the
    `SecurityContext`, same pattern `content-service` already uses for the same reason).
    `ChatRateLimiter`'s Redis connection (`StatefulRedisConnection<String, byte[]>`, still defined
    by `gateway`'s `RedisCacheConfig`) is picked up by type — no cross-module import needed.
  - **`asyncEventExecutor` bean → `infra`** (new `AsyncEventThreadPoolConfig`/
    `AsyncEventThreadPoolProperties`, package `infra/config/thread/`). `infra`'s own
    `EventHandler`/`AsyncEventHandler` framework is the thing that actually owns this pool's
    purpose — every `@EventHandler` dispatches through it — so the bean definition now lives
    alongside it instead of trusting `gateway` to keep supplying it. Bound from
    `app.threads.async-event.*` (previously nested under `gateway`'s `app.threads.async-event-executor.*`;
    no `application.yml` anywhere overrode the old path, so the prefix changed freely with no
    migration needed). `gateway`'s `sseStreamExecutor` (a separate bulkhead, unaffected) stays in
    `gateway`'s trimmed `ThreadPoolConfig`/`ThreadPoolProperties` — `WebMvcConfig.configureAsyncSupport`,
    its only real consumer beyond `ai-service`'s `SseStreamTemplate` (which already reached it by
    `@Qualifier`/bean-name regardless of location), is inherently a single global MVC-config point.
    `infra/pom.xml` gained `io.micrometer:micrometer-core` (for `ExecutorServiceMetrics`, the same
    Decorator-pattern instrumentation `gateway`'s `sseStreamExecutor` already used).
  - **Considered and rejected**: moving `gateway`'s entire `security/` package (`SecurityConfig`,
    `JwtAuthenticationFilter`, JWT/STOMP wiring) into `identity-service`. Blocked outright for the
    STOMP half (`WebSocketConfig`/`StompAuthChannelInterceptor` depend on `social-service`, and
    `identity-service` must stay a pure leaf so `social-service` can safely depend on it — moving
    them would create a cycle). The REST-auth half *could* move without a cycle, but was kept in
    `gateway` anyway: there's a real distinction between issuing credentials (`identity-service`'s
    job) and enforcing them on every request regardless of which module ends up serving it (an
    edge/gateway concern in any real microservices split, not something that belongs inside the
    identity-issuing service itself).
  - Every module `CLAUDE.md` touched by either move (`ai-service`, `infra`, `gateway`) updated;
    `docs/PROJECT_STRUCTURE.md`'s `infra`/`ai-service`/`gateway` sections updated to match. Full
    reactor `clean compile` + `clean test` pass after both moves.

- **Moved `UserApi.search`/`getPublicProfile` out of `api` into `social-service`, and renamed `api`
  to `gateway`** — the last two follow-ups to the REST-layer extraction below, closing the loop on
  it: with this move, `api` held zero REST controllers of its own, so its name (which used to mean
  "the module that owns the API") no longer described what it did (Spring Boot entry point,
  security/JWT/STOMP edge wiring, Liquibase, cross-domain seeding orchestration).
  - **Reconsidered where `UserApi`'s last two methods (`getPublicProfile`/`search`) belonged.**
    They'd stayed in `api` because they need both `identity-service`'s `UserService` (base profile
    lookup) and `social-service`'s `FriendService` (relationship enrichment) — two parallel siblings
    with no dependency relationship. Moving them into `identity-service` was considered and
    rejected: it would force `identity-service` to depend on `social-service`, inverting the usual
    auth-is-foundational hierarchy and mixing a social-graph view into an auth module. Moved into
    `social-service` instead, which now takes a real, one-directional dependency on
    `identity-service` (mirroring the existing `ai-service` → `content-service` precedent) —
    `identity-service` remains a pure `common`+`infra` leaf, so this direction is safe. Every other
    module keeps depending on `identity-service` in the same direction if it ever needs to.
    `social-service/pom.xml` gained the `identity-service` internal dependency.
  - **Renamed the `api` Maven module to `gateway`** now that it had zero REST controllers left.
    Candidates considered: `platform` (safe, generic), `bootstrap` (too narrow — undersells the
    security-edge/Liquibase/seeding-orchestration responsibilities that remain), `gateway` (chosen —
    forward-looking to the eventual microservices split, where this module's descendant really would
    be an API gateway; slightly overstates today's reality since it doesn't route/proxy to other
    services yet, everything's still one deployable, but was picked anyway as the clearest signal of
    where this module is headed). Mechanical: directory `api/` → `gateway/`, `pom.xml` `artifactId`/
    `name`/`description`, root `pom.xml`'s `<modules>` list, `dev-knowledge-platform-liquibase.yml`'s
    changelog volume mount path. No other module depends on `api`/`gateway` as a Maven dependency (by
    design — dependencies only ever point the other way), so the blast radius was contained to
    `gateway`'s own files plus every doc that names the module. The Java package root
    (`com.ttg.devknowledgeplatform`) was never namespaced `.api.*` for this module's own remaining
    classes (`security/`, `config/`, `event/`, `service/`) — only the now-fully-relocated REST-layer
    subpackage was ever literally named `api`, so no package rename was needed, just the module/
    directory/artifact name.
  - Every module `CLAUDE.md` (`content-service`, `social-service`, `ai-service`, `identity-service`,
    and the renamed `gateway`), root `CLAUDE.md`, and `docs/PROJECT_STRUCTURE.md` updated throughout
    — this rename touches nearly every doc in the repo since they all name the module by convention.
    Caught and fixed several **pre-existing** stale mentions along the way that predated this specific
    rename (e.g. `content-service/CLAUDE.md` still describing `IngestionController` as living in
    `api` after it had already moved to `ai-service`; `common`'s own doc section still listing
    `ChatErrorCode` under `api` after it moved to `ai-service`) — these were drift from earlier moves
    in this same body of work, not introduced by this change, but fixed while touching the same text.
  - Full reactor `clean compile` + `clean test` pass after both the `UserApi` move and the rename.

- **Moved every feature's REST controllers, DTOs, and MapStruct mappers out of `api` into the
  feature module that owns the underlying entities/services** — `content-service`, `social-service`,
  and `ai-service` (see that move's own entry below) each now own a full vertical slice (entity →
  service → REST controller → DTO → mapper), plus a brand-new `identity-service` module for auth.
  This deliberately reverses the transport-agnostic property those three modules had before (DTOs/
  mappers/controllers centralized in `api` so the feature modules stayed reusable outside a REST
  context) in favor of each module being closer to an independently-deployable unit ahead of a
  planned microservices split — a real near-term target, not a someday-maybe, confirmed before this
  work started. `api` keeps only what's genuinely cross-module orchestration or transport/security
  edge infra; see `docs/PROJECT_STRUCTURE.md` for the full before/after per module.
  - **Shared types promoted out of `api`, since more than one destination module needed them and
    neither could depend on `api`**: `dto.PagedResponse` → `common.dto.PagedResponse` (used by
    controllers in every feature module); `annotation.CurrentUserId` → `common.annotation.CurrentUserId`
    (same); `dto.CustomOAuth2User` → `common.dto.CustomOAuth2User` (needed by `content-service`'s
    `ArticleApi`/`QuestionAnswerApi` as an `@AuthenticationPrincipal` parameter type, which is what
    forced this one into `common` rather than `identity-service`); `service.StorageService`(+`impl`)
    → `infra.service.StorageService` (needed by both `social-service`'s `FriendMapper`/
    `MessagingMapper` and `identity-service`'s `UserMapper`/avatar upload — two parallel siblings that
    can't depend on each other). `common/pom.xml` gained `spring-boot-starter-oauth2-client`
    (`optional=true`, same pattern as its other security starters) for `CustomOAuth2User`'s
    `OAuth2User` interface; `infra/pom.xml` gained `spring-boot-starter-web` (for `MultipartFile`) and
    `io.minio:minio`.
  - **New `identity-service` module** (`common` ← `infra` ← `identity-service`, a parallel sibling to
    `content-service`/`social-service`/`ai-service` — nothing depends on it except `api`): owns
    `OAuth2Api`+`impl` in full (login/register/OTP/exchange-state/refresh/logout/current-user);
    `UserMapper`; `dto/auth/*` (8 files), `dto/user/UpdateProfileRequest`, `dto.RegisterRequest`,
    `dto.UserInfoResponse`, the OAuth2-provider-attribute DTOs (`{Google,Facebook}OAuth2UserInfo`,
    `OAuth2UserInfo`, `OAuth2UserInfoFactory`); `JwtTokenProvider`, `security.jwt.*` (`TokenClaims`
    sealed interface + `AccessTokenClaims`/`RefreshTokenClaims`), `PasswordEncoderConfig` (the sole
    `PasswordEncoder` bean in the whole reactor — `api`'s `UserSeeder` now injects it across the
    module boundary); `UserService`/`Impl`, `CustomOAuth2UserService`, `CustomOidcUserService`,
    `RefreshTokenBlacklistService`/`Impl`, `StateTokenService`/`Impl`; `OAuth2LoginSuccessHandler`;
    `EmailService`/`OtpService`(+`impl`).
    - **`UserApi` split rather than moved wholesale**: `updateProfile`/`uploadAvatar` (pure profile
      mutation, only need `UserService`/`UserMapper`/`StorageService`) moved to
      `identity-service`'s own `UserApi`. `getPublicProfile`/`search` stayed in `api` — they need
      `social-service`'s `FriendService`/`FriendMapper`/`RelationshipStatus` for relationship
      enrichment, and `identity-service` must not depend on `social-service` (parallel siblings with
      no relationship to each other) — same "needs 2 modules with no relationship between them"
      precedent that justifies `api`'s existence at all.
    - **`CacheNames`/`CacheTtlProperties` relocated to `infra`**: discovered mid-move —
      `StateTokenServiceImpl` (moving to `identity-service`) needed them, but they also lived in and
      were used by `api`'s `RedisCacheConfig`. Same "two siblings, shared utility" reasoning as
      `StorageService` above.
  - **`content-service` gained its own `api`/`mapper`/`dto` packages**: `Category`/`Tag`/`Article`/
    `QuestionAnswerApi`+`impl` (admin CRUD only — see the `ai-service` entry below for
    `PublicContentApi`), the 4 matching MapStruct mappers, and all 13 `dto/content/*` DTOs
    (flattened to `content.dto`, not nested under `content.dto.content`). `content-service/pom.xml`
    gained `mapstruct` and `spring-boot-starter-oauth2-client` (`optional=true` — needed once
    `ArticleApi`/`QuestionAnswerApi`'s `@AuthenticationPrincipal CustomOAuth2User` parameter moved in).
    `ArticleController`/`QuestionAnswerController` switched their author-id resolution from `api`'s
    `UserService` (auth business logic, can't be reached — `content-service` must never depend on
    `identity-service`) to `common`'s `UserRepository.findByEmail(...)`, the sanctioned mechanism
    for exactly this case per `common/CLAUDE.md`.
  - **`social-service` gained its own `api`/`mapper`/`dto` packages**: `Friend`/`Group`/`Dm`/
    `GroupMessaging`/`DmMessagingApi`+`impl` (REST + the two STOMP `@MessageMapping` controllers),
    `FriendMapper`/`MessagingMapper`, and every `dto/friend/*`/`dto/messaging/*` file (incl.
    `WsErrorResponse`). `social-service/pom.xml` gained `mapstruct` and — discovered only once the
    build failed — `spring-boot-starter-websocket` (the STOMP controllers need `spring-messaging`'s
    annotations and `SimpMessagingTemplate`, which plain `spring-boot-starter-web` doesn't provide).
    STOMP transport wiring itself (`WebSocketConfig`, `StompAuthChannelInterceptor`,
    `CurrentUserIdMessageArgumentResolver`) stays in `api` — edge/transport infra, not a
    `social-service` concern, mirroring why `SecurityConfig`/`JwtAuthenticationFilter` stay in `api`
    while `identity-service` owns the actual auth business logic. `api`'s `WebSocketConfig` updated
    to import `GroupMessagingController`/`DmMessagingController` from their new
    `social.api.impl` package.
  - **Final integration pass**: `api/event/ContentPublishedEventListener` repointed to `ai-service`'s
    `ContentIndexingService` (left intentionally broken by the `ai-service` move below, fixed here);
    `api/auth/UserApi`/`UserController` (the trimmed 2-method remainder) repointed to
    `common.dto.{CustomOAuth2User,PagedResponse}`, `identity-service`'s `UserApi`/`UserMapper`/
    `UserService`, and `social-service`'s `FriendMapper`/`dto.friend.UserSearchResultResponse` (its
    unused `StorageService` field/import, left over from before the `uploadAvatar` split, removed);
    `SecurityConfig`/`JwtAuthenticationFilter`/`StompAuthChannelInterceptor`/`CurrentUserResolver`/
    `CurrentUserIdArgumentResolver`/`CurrentUserIdMessageArgumentResolver`/`ChatRateLimitInterceptor`
    repointed to the new shared-type/`identity-service` locations; several old now-superseded files
    the shared-type-promotion step had left behind (`api`'s own copies of `StorageService`/`Impl`,
    `StorageConfig`/`Properties`, `CacheNames`/`CacheTtlProperties`, `CurrentUserId`,
    `CustomOAuth2User`, `PagedResponse`) deleted. `api` added an internal dependency on
    `identity-service`. Full reactor `clean compile` + `clean test` both pass.
  - `content-service/CLAUDE.md`, `social-service/CLAUDE.md`, `ai-service/CLAUDE.md` flipped from
    "controllers/DTOs/mappers don't live here" to documenting what actually landed; new
    `identity-service/CLAUDE.md`; `api/CLAUDE.md`'s "What lives here" trimmed to match; root
    `CLAUDE.md`'s module table, dependency-order line, and Code Conventions' "mappers and DTOs live
    in `api`" rule updated to the opposite; `docs/PROJECT_STRUCTURE.md` rewritten for all five
    affected modules (new `identity-service` section; `api`'s section cut down to what's actually
    left).

- **Restructured `api`'s controller layer from one flat `api/`+`api/impl/` package into five
  feature subpackages** (`api/content/`, `api/social/`, `api/chat/`, `api/admin/`, `api/auth/`,
  each with its own `impl/`) — the flat layout was starting to hold 16 `Api`/`Controller` pairs in
  one package with no grouping, and the endpoint surface is expected to keep growing across
  distinct feature areas. Mirrors the subpackaging `dto/` already used (`dto/content/`,
  `dto/friend/` + `dto/messaging/`, `dto/chat/`, `dto/admin/`), so this closes the gap between how
  DTOs were organized and how their controllers were.
  - `api/content/`: `CategoryApi`/`TagApi`/`ArticleApi`/`QuestionAnswerApi` (admin CRUD) +
    `PublicContentApi` (previously grouped with the indexing/RAG orchestration layer in this doc's
    module notes — regrouped here since it fronts the same content-service entities and DTOs as
    the CRUD controllers, just read-only).
  - `api/social/`: `FriendApi`, `GroupApi`, `DmApi`, `GroupMessagingApi`, `DmMessagingApi` (STOMP).
  - `api/chat/`: `ChatApi`.
  - `api/admin/`: `IngestionApi`, `EmbeddingIndexApi`, `PipelineMetricsApi`.
  - `api/auth/`: `OAuth2Api`, `UserApi`.
  - Purely mechanical: no behavior, endpoint path, or DTO/mapper/service change. Each
    controller's only cross-package reference was its own `Api` interface, so the move was
    package-declaration + one import fix per file; `security/WebSocketConfig`'s imports of
    `GroupMessagingController`/`DmMessagingController` updated to their new
    `api.social.impl` package. `api/CLAUDE.md`'s "What lives here" section and
    `docs/PROJECT_STRUCTURE.md`'s `api` module section updated to match; the latter's `ws/`
    section (already describing a package that doesn't exist — the real files are split across
    `security/`, `config/web/`, `dto/messaging/`) corrected in passing since it referenced the two
    controllers that moved.

- **Moved `ai-service`'s REST controllers, DTOs, and orchestration services out of `api` into
  `ai-service` itself** (`com.ttg.devknowledgeplatform.ai.*`), mirroring the `content-service`/
  `social-service` extractions: `api/chat/ChatApi`+`impl/ChatController`, `api/admin/{IngestionApi,
  EmbeddingIndexApi,PipelineMetricsApi}`+their `impl/` controllers, and
  `api/content/PublicContentApi`+`impl/PublicContentController` → `ai/api/` + `ai/api/impl/`; the
  chat-session feature (`service/ChatSessionService`/`Impl`, `repository/{ChatSessionRepository,
  ChatMessageRepository}`, `exception/ChatErrorCode`, `dto/chat/*`, and `config/chat/
  ChatSessionProperties`, which the original plan missed but which only `ChatSessionServiceImpl`
  ever used) → `ai/service/`, `ai/repository/`, `ai/exception/`, `ai/dto/chat/`, `ai/config/chat/`;
  the content+AI indexing orchestration layer (`service/ContentIndexingService`/`Impl`,
  `service/IndexingQualityService`/`Impl`, `service/QualityVerdict`, `service/
  EmbeddingIndexService`/`Impl`, `dto/admin/EmbeddingIndexItemResponse`) → `ai/service/`
  (+ `impl/`), `ai/dto/admin/`; and `config/sse/SseStreamTemplate`+`SseEmitterWriter` → `ai/config/sse/`.
  - **Why the old "orchestration stays in `api`" rule no longer applied**: that rule existed as a
    tiebreaker for logic needing two feature modules from a place that could see both. `ai-service`
    already depends on `content-service` for real reasons (the `ContentEmbedding` → `ContentItem`
    FK), so for this specific content+AI pairing `ai-service` was always the more specific owner —
    leaving it in `api` was drift left over from before `content-service` was extracted, not a
    real constraint. `api` remains the only module allowed to depend on *more than one* feature
    module for orchestration that doesn't reduce to a single module's existing dependency, e.g. if
    it ever needed `social-service` too.
  - **`SseStreamTemplate` ↔ `WebMvcConfig` circular-dependency fix**: `SseStreamTemplate` used to
    do `new SseEmitter(WebMvcConfig.SSE_TIMEOUT_MS)`, a hard reference to an `api`-only class.
    `SseStreamTemplate` now owns `SSE_TIMEOUT_MS` (`60_000L`) itself; `api`'s `WebMvcConfig.
    configureAsyncSupport` reads `SseStreamTemplate.SSE_TIMEOUT_MS` instead of the other way round
    — safe only in this direction, since `api` already depends on `ai-service`.
  - `ai-service/pom.xml` gained `spring-boot-starter-web` (controllers/`SseEmitter`),
    `spring-boot-starter-validation` (`@Valid` on `ChatRequest`), and `spring-boot-starter-security`
    (method-security annotations only — `@PreAuthorize` on `IngestionApi`; `@EnableMethodSecurity`
    itself stays in `api`'s `SecurityConfig`, shared across the one Spring context).
  - Purely mechanical otherwise: no behavior, endpoint path, or DTO/mapper/service change beyond
    updating imports to the shared types already promoted out of `api` in a prior step
    (`common.dto.PagedResponse`, `common.annotation.CurrentUserId`) and to the new `ai.*` package
    locations. `api/event/ContentPublishedEventListener` (stays in `api`, still imported
    `ContentIndexingService` from its old `service` package at the time this move landed) was left
    intentionally broken here, fixed by the final integration pass described in the entry above.
    `ai-service/CLAUDE.md` updated to match.

### Added

- **WebSocket/STOMP live push for group/DM chat** — new `com.ttg.devknowledgeplatform.ws` package
  in `api` (`spring-boot-starter-websocket`). The AI chat feature keeps its existing SSE stream;
  this is separate, additive infrastructure only for the social chat feature, chosen over SSE
  specifically because group/DM chat is bidirectional in a way AI-answer streaming isn't.
  - `WebSocketConfig` — `/ws` endpoint registered **without** a SockJS fallback (a raw WebSocket
    handshake, not an emulated transport). Simple broker on `/topic` + `/queue`, `/app` prefix for
    client-sent messages.
  - `StompAuthChannelInterceptor` — authenticates the STOMP `CONNECT` frame (browsers can't set an
    `Authorization` header on the handshake request itself, so the handshake is `permitAll` in
    `SecurityConfig` and real auth happens on the first STOMP frame instead, which *can* carry
    headers). Builds the same `CustomOAuth2User` shape `JwtAuthenticationFilter` already builds for
    REST, reusing `JwtTokenProvider` rather than re-implementing JWT parsing.
    - **Bug caught testing the first real end-to-end DM send**: `preSend` originally obtained its
      accessor via `StompHeaderAccessor.wrap(message)`, which builds a *new* accessor copy from the
      message's headers — `accessor.setUser(...)` on that copy never propagated back to the actual
      message. CONNECT appeared to succeed (no exception thrown), but the principal was silently
      lost, so every later frame on the session (`@CurrentUserId`-annotated `@MessageMapping`
      methods like `DmMessagingController.sendMessage`) threw `IllegalStateException`
      ("`@CurrentUserId` requires an authenticated STOMP session, but none is present"). Fixed by
      fetching the accessor via `MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class)`
      instead — the live, mutable accessor Spring's STOMP handling already attached to the message
      (created with `setLeaveMutable(true)`), so mutating it actually sticks.
  - Same interceptor also authorizes `SUBSCRIBE` frames to `/topic/channels/{id}` via a new
    `GroupService.isChannelMember(userId, channelId)` — the simple in-memory broker has no
    per-destination ACL of its own, so without this check any authenticated user could subscribe to
    any channel's topic string regardless of membership. DMs need no equivalent check: they're
    delivered via `convertAndSendToUser`'s private per-user queue, which has no public topic string
    to subscribe to in the first place.
  - `@CurrentUserId` now works on both transports — `CurrentUserIdMessageArgumentResolver` (Spring
    Messaging's `HandlerMethodArgumentResolver`, registered via `WebSocketConfig`) resolves it for
    `@MessageMapping` methods the same way `CurrentUserIdArgumentResolver` already does for REST.
  - `GroupMessagingController`/`DmMessagingController` are thin — same `GroupService`/`DmService`/
    `MessagingMapper` the REST layer already uses, just publishing to `/topic/channels/{id}` or
    `convertAndSendToUser` instead of returning a `ResponseEntity`. Errors from either surface as a
    `WsErrorResponse` on the sender's private `/queue/errors`.
  - **Bug caught while building `DmMessagingController`**: the natural way to find "the other DM
    participant" — `message.getDmThread().getUser1()`/`.getUser2()` — is a lazy `@ManyToOne` once
    `DmThread` is loaded fresh from an existing-thread lookup, and STOMP message handling isn't
    covered by Spring Boot's default Open-Session-In-View (that's a servlet-filter mechanism tied to
    the HTTP request lifecycle, which a WebSocket message never goes through), unlike the REST
    controllers. Fixed by resolving both usernames directly (sender's from the already-authenticated
    `Principal`, recipient's via a fresh `UserRepository` lookup) instead of touching that
    association post-transaction.
  - Flagged, not fixed (out of scope for this change): the REST list endpoints (`listMessages`,
    `listMyThreads`, etc.) rely on Open-Session-In-View being on — which it is, by default, since
    nothing in `application.yml` overrides `spring.jpa.open-in-view` — to safely map lazy
    associations after their service call returns. Not a live bug, but fragile if OSIV is ever
    turned off (a common production hardening step).

- **Chat MVP (Phase 2 of `social-service`'s planned scope — groups/channels + 1:1 DMs)**: user
  stories in `docs/USER_STORIES_CHAT.md`; entities `Group` (maps to table `MESSAGE_GROUP` — `GROUP`
  is a reserved word in PostgreSQL), `GroupMember` (carries `GroupMemberRole`: `OWNER`/`ADMIN`/
  `MEMBER`), `Channel`, `DmThread` (reuses `Friendship`'s canonical-pair-ordering convention),
  `DmMessage`/`ChannelMessage` (`MessageType`: `TEXT`/`IMAGE`/`FILE`; `content` and the attachment
  columns are independently nullable so a message can carry text, an attachment, or both); new
  repositories for all six; new services `GroupService`/`GroupServiceImpl` and `DmService`/
  `DmServiceImpl` plus the shared `MessageAttachmentInput` record. Key decisions:
  - **DMs require an accepted `Friendship`**; groups use **open add** (owner/admin adds anyone, no
    friendship needed) — deliberately different gates, which is why `GroupService`/`DmService` stay
    two separate services rather than one, and why blocking has **no effect inside a shared group
    channel** (it only gates DMs, matching how Discord/Teams treat a block as a personal DM filter).
  - `DmService` depends on `FriendService` as a collaborator (reuses `getRelationshipStatus`) rather
    than querying `Friendship`/`UserBlock` directly — one implementation of the canonicalization +
    mutual-invisibility logic instead of two. The same rejection covers "not friends" and "blocked"
    without distinguishing which, preserving mutual invisibility.
  - The group owner **cannot leave** the group in this MVP (no ownership-transfer story yet); an
    `ADMIN` can only be removed by the `OWNER`, never by another `ADMIN`; ownership itself is not
    reassignable via `changeRole`.
  - `FriendErrorCode` **renamed to `SocialErrorCode`** and extended with `DM_*`/`GROUP_*`/
    `CHANNEL_*` codes — one `ErrorCode` enum per module (not per sub-domain), matching
    `content-service`'s `ContentErrorCode` precedent. Low blast radius: only `FriendServiceImpl` and
    its test referenced the concrete class.
  - Considered and rejected: unifying `DmThread`/`Channel` under one generic `Conversation`
    abstraction (no current story needs a unified inbox, and `Friendship`/`UserBlock` already
    established the precedent of keeping structurally-similar concepts in separate tables); a
    `MappedSuperclass` shared by `DmMessage`/`ChannelMessage` (Lombok's `@Builder`/`@AllArgsConstructor`
    don't include inherited fields, which would have silently dropped `sender`/`content`/etc. from
    the builder — kept both entities flat instead); a `MessageAttachment` side table mirroring
    `ContentItem`/`QuestionAnswer` (only 4 small nullable columns, doesn't clear the cost of a join).
  - **Liquibase `DKP-0019`**: adds `MESSAGE_GROUP`, `GROUP_MEMBER`, `CHANNEL`, `DM_THREAD`,
    `DM_MESSAGE`, `CHANNEL_MESSAGE`. `ON DELETE CASCADE` from messages up through channel/group and
    from members up through group.
  - 34 unit tests added (`GroupServiceImplTest`, `DmServiceImplTest`) covering the friend-gate,
    block-collapsing, lazy thread creation/reuse, owner protections, admin-can't-remove-admin, and
    role-change guards; existing `FriendServiceImplTest` (9 tests) unaffected by the rename.
  - `GroupService.removeMember`/`changeRole` **fixed to take a UUID for the target user**, not a
    raw `Integer` — `addMember` already took a UUID for the new member, so the other two were an
    inconsistency that would have leaked enumerable internal ids into REST URLs. Caught before the
    controller layer was built on top; see `api/CLAUDE.md`'s new rule on this.
  - **REST layer**: `api`'s `GroupApi`/`GroupController` (`/api/v1/groups/**`,
    `/api/v1/channels/{channelId}/messages`) and `DmApi`/`DmController` (`/api/v1/dms/**`); DTOs in
    `dto/messaging/` (deliberately not `dto/chat/`, which already means the AI-RAG chat feature);
    `MessagingMapper`, delegating to `FriendMapper` (`uses = FriendMapper.class`, plus an injected
    field reference for the one hand-written `expression` that needed to call `toUserSummary`
    directly — MapStruct's `uses` delegation only covers same-named auto-mapped fields). No upload
    endpoint yet for message attachments — `MessageAttachmentRequest.objectKey` assumes a MinIO
    object key obtained some other way.

- **Liquibase `DKP-0018`**: unique-vs-plain index audit across every table — closes gaps where a
  column was already relied on as unique by service-layer code (singular `Optional` finders,
  `existsBy...` pre-checks, get-or-create patterns) with nothing in the schema actually enforcing
  it, plus a couple of hot-path lookups that had no index at all.
  - **Promoted to `UNIQUE`** (was plain or missing): `USER.EMAIL`, `USER.USERNAME`,
    `USER(PROVIDER, PROVIDER_ID)` (composite, no partial `WHERE` needed — Postgres treats each
    `NULL` as distinct, so `LOCAL` accounts with a null `PROVIDER_ID` don't collide),
    `CATEGORY.SLUG`, `TAG.SLUG`, `CONTENT_ITEM.SLUG`, `ARTICLE.CONTENT_ITEM_ID`,
    `QUESTION_ANSWER.CONTENT_ITEM_ID`, `CONTENT_ITEM_TAG(CONTENT_ITEM_ID, TAG_ID)`,
    `CHAT_MESSAGE(CHAT_SESSION_ID, TURN_INDEX)`, `SYS_PARAM.NAME`,
    `CONTENT_EMBEDDING(CONTENT_ITEM_ID, MODEL_NAME, CHUNK_INDEX)`.
  - **New functional unique index** (case-insensitive, matches `existsByNameIgnoreCase`):
    `LOWER(NAME)` on `CATEGORY` and `TAG`. Left as an index rather than a named constraint —
    Postgres's `ADD CONSTRAINT ... UNIQUE USING INDEX` doesn't accept expression indexes.
  - **New plain index**: `PIPELINE_METRICS.TRACE_ID` (was unindexed — the natural key for
    correlating a metrics row to app/tracing logs), `CONTENT_ITEM.AUTHOR_ID` (was unindexed and has
    no FK; FK left out of scope, needs a separate data-validation pass).
  - A preflight `DO` block checks all of the above for existing duplicates before any `ADD
    CONSTRAINT` runs, so a violation fails the whole migration with one clear message instead of
    partially applying. Current seed data has none.

- **Liquibase `DKP-0017`**: index (`IDX_USER_USER_UUID`) on `USER.USER_UUID`. The column had no
  index at all since it was added in `DKP-0004`, despite being the lookup key on the hot path
  (`CurrentUserIdArgumentResolver` resolves it on every authenticated request, plus
  `UserService.findByUserUuid`/`findByUserUuidOptional` and the friend-graph endpoints keyed by
  `userUuid`) — every one of those was a sequential scan. Plain (non-unique) index, matching
  `IDX_USER_EMAIL`/`IDX_USER_USERNAME`: a `UNIQUE` constraint was considered — Postgres backs it
  with the same B-tree, so it would have closed the latent gap where nothing stops two rows from
  sharing a `USER_UUID` (e.g. a future backfill script or a `.toBuilder()` copy that forgets to
  regenerate it) at no extra write cost — but deliberately left as a plain index; that gap is an
  accepted risk here, not an oversight.

- **Sample data for User/FriendRequest/Friendship/UserBlock** — 20 login-able sample accounts and
  a hand-built social graph among them, for exercising the Friend Management GUI end to end
  (distinct from the RAG-corpus content seeding above: this is fixture data for a UI to interact
  with, not retrieval content).
  - **Liquibase `DKP-0016`**: nullable `SEED_ID` column + unique index on `USER`, same pattern as
    `DKP-0013`'s `CATEGORY`/`TAG`/`CONTENT_ITEM` — `User.seedId` field added; `UserRepository`
    (`common`) gained `findBySeedId`. `EMAIL`/`USERNAME` are human-editable
    (`UserServiceImpl.updateProfile`) the same way `CATEGORY.NAME` is, so they can't be the
    idempotency check without risking a duplicate insert if a seeded user's email/username is
    ever edited post-seeding.
  - **`FriendRequest`/`Friendship`/`UserBlock` deliberately do NOT get their own `SEED_ID`** — a
    pair's identity is the `(user, user)` relationship itself, which has no editable-field
    equivalent to `NAME`/`EMAIL` that could invalidate a pair-based idempotency check. New
    `FriendRequestRepository.existsBetween` (mirrors the existing
    `UserBlockRepository.existsEitherDirection`) is the guard instead.
  - `UserSeeder` (`api/service/seed/`, not `common` — needs `PasswordEncoder`, matching where
    `UserService`'s auth-flow logic already lives) — `data/csv/users.csv`, 20 varied sample
    accounts, all `LOCAL` provider / `emailVerified=true` / `enabled=true`, sharing one known demo
    password (`UserSeeder.DEMO_PASSWORD`) so any of them can actually be logged into, not just
    rows to look at in the database.
  - `FriendGraphSeeder` (`social-service/service/seed/`) — `data/csv/friend-requests.csv`
    (`requesterId, addresseeId, status`); an `ACCEPTED` row also inserts the corresponding
    `Friendship` (canonically ordered `user1.id < user2.id`), mirroring
    `FriendServiceImpl.acceptRequest`'s production behavior. Does not extend `infra`'s
    `CsvSeeder<T>` — an `ACCEPTED` row persists two entities per row, which doesn't fit
    `CsvSeeder`'s one-entity-per-row shape (same reasoning as `QuestionAnswerSeeder`'s own
    departure from the template).
  - `UserBlockSeeder` (`social-service/service/seed/`) — `data/csv/user-blocks.csv`
    (`blockerId, blockedId`).
  - **`CsvSeeder<T>` moved from `content-service` to `infra`** — it's a generic Template Method
    with no content-specific logic, and `UserBlockSeeder` (`social-service`) needed it too;
    `social-service` can't depend on `content-service` (independent sibling feature modules), so
    the shared template moved to `infra`, which both already depend on — same reasoning that put
    `SlugService` there. `CategorySeeder`/`TagSeeder` (`content-service`) and `UserSeeder` (`api`)
    updated to import it from the new location; `infra`'s `pom.xml` gained `commons-csv`.
  - `DataSeedingRunner` (`api`) now runs, in order: categories → tags → question-answers → users
    → friend graph → blocks.
  - `docs/SEED_DATA_AUTHORING_GUIDE.md` — new section covering this data's identity design and
    why it departs from the `Category`/`Tag`/`ContentItem` `SEED_ID` pattern for the relationship
    tables specifically.

- **Friend Management GUI** — Facebook-modeled `/friends` page fronting `social-service`'s
  `FriendApi`/`UserApi.search`, the first UI for the friend graph (previously backend-only).
  - `pages/FriendsPage.tsx` — single page, MUI Tabs (Find People / Friends / Requests / Sent
    Requests / Blocked) rather than separate top-level routes — simpler state, no extra layout
    component, deep-linking per tab not needed yet.
  - `components/friends/` — `UserRow` (shared avatar+name+subtitle+actions row across all five
    tabs), `UserAvatar`, `RelationshipActionButton` (renders Add Friend/Cancel/Confirm+Delete/
    Friends▾/Unblock based on `RelationshipStatus` via an exhaustive `switch`, no `default` —
    mirrors `social-service`'s own exhaustive-switch discipline over `FriendRequestStatus`, so a
    new status value is a TypeScript compile error here until handled), `FriendsMenuButton`
    (Unfriend/Block dropdown), and the five tab bodies (`UserSearch`, `FriendsList`,
    `FriendRequestsIncoming`, `FriendRequestsOutgoing`, `BlockedUsersList`).
  - `api/friendApi.ts` (new) + `types/friend.types.ts` (new) — wraps every `/api/v1/friends/**`
    endpoint; `userApi.searchUsers` fixed to match the real backend contract (see Fixed below).
  - `hooks/useFriendRequestsCount.ts` — polls the incoming-request count for `NavBar`'s existing
    (previously hardcoded-dot) Friends badge; guards on `authService.isAuthenticated()` before
    calling the endpoint at all, since the hook runs unconditionally on every route (including
    `/login`) and an unauthenticated call would otherwise trip `httpClient`'s 401→redirect logic.
  - **Known backend gap, worked around rather than faked**: `UserSearchResultResponse` carries
    `relationshipStatus` but no friend-request id, so `RelationshipActionButton` can't call
    accept/reject/cancel (which the backend keys by request id) from a search result — it renders
    an informational chip for `REQUEST_SENT`/`REQUEST_RECEIVED` there instead, and those actions
    stay confirm/delete/cancel-able only from the Requests tabs (`FriendRequest[]`, which does
    carry `.id`).
  - `types/common.types.ts` — `PagedResponse<T>` moved here from `admin.types.ts` (not
    admin-specific — friend-graph list endpoints paginate too); `admin.types.ts` re-exports it so
    existing imports are unaffected.
  - Group/chat (deferred) gets its own `api/`/`types/`/`components/` slice the same way when it
    lands — nothing in this pass should need to change for it.

### Fixed

- **`userApi.searchUsers`** — was typed against a bare `?q=` query returning a flat `User[]`,
  which never matched the real backend contract: `GET /api/v1/users/search` is paginated
  (`PagedResponse<UserSearchResultResponse>`) and each result carries the viewer's
  `relationshipStatus`/`mutualFriendCount`, not just a `User`. Looks like it was written before
  the friend-management backend existed and never reconciled — nothing called it (zero call
  sites) until the Friend Management GUI above became its first real caller.

### Changed

- **Split `CommonErrorCode`'s `FRIEND_*`/`CHAT_*`/`AI_*` codes into new per-module `ErrorCode`
  enums**, following the precedent `content-service`'s `ContentErrorCode` already set: `FRIEND_*`
  moved to `social-service`'s new `exception/FriendErrorCode`, `AI_*` to `ai-service`'s new
  `exception/AiErrorCode`, and `CHAT_*` to `api`'s new `exception/ChatErrorCode` (chat-session
  business logic lives in `api`'s `ChatSessionServiceImpl`, not a dedicated feature module, so it
  gets its own enum there rather than staying in `common`). Error code strings/messages/HTTP
  statuses are unchanged — only which class declares them moved — so this is not a breaking API
  change. `CommonErrorCode` now holds only codes with no single feature-module owner
  (`AUTH_*`/`OAUTH_*`/`USER_*`/`OTP_*`/`VALIDATION_*`/`SERVER_*`/`RESOURCE_*`/`REQUEST_*`/`RATE_*`).
  `common/CLAUDE.md`, `ai-service/CLAUDE.md`, `social-service/CLAUDE.md`, and `api/CLAUDE.md`
  updated to match.
  - Audited every `throw new BusinessException(errorCode, "...")` call for a custom message that
    just repeats the error code's own default `message` verbatim (dead weight, since
    `GlobalExceptionHandler` only falls back to the code's default when no custom message is
    passed) — `FriendServiceImpl.sendRequest`'s `CANNOT_FRIEND_SELF` throw and `block`'s
    `USER_ALREADY_BLOCKED` throw dropped their redundant messages in favor of the single-arg
    constructor.
  - Split `FriendErrorCode.CANNOT_FRIEND_SELF` in two: `block(...)`'s self-block guard was reusing
    it with an overriding message ("You cannot block yourself") that didn't match the code's own
    default ("...send a friend request..."), overloading one machine-readable code (`FRIEND_001`)
    across two distinct business rules. New `CANNOT_BLOCK_SELF` (`FRIEND_007`) added so the code
    and its message stay 1:1 — no current consumer keyed off `errorCode` instead of `errorMessage`,
    but this closes the gap before one does.

- **`FriendApi`/`FriendController` switched from `@AuthenticationPrincipal CustomOAuth2User
  principal` to `@CurrentUserId Integer userId`** — every endpoint only ever needed the caller's
  numeric PK (`FriendService` methods all take `Integer`), so the `principal` → `UserService
  .resolveCurrentUser(principal)` → `.getId()` round trip on every method was an unnecessary DB
  lookup per request; `CurrentUserIdArgumentResolver` resolves the same value from the JWT-backed
  principal without one. Matches the pattern already used by `ChatApi`. `FriendController` no
  longer needs a `UserService` dependency as a result.

- **Moved `UserRepository` from `api` to `common`, retiring `social-service`'s
  `SocialUserRepository`** — `SocialUserRepository` existed solely because `UserRepository` lived
  in `api` and `social-service` can't depend on `api`; it was a near-duplicate (`JpaRepository<User,
  Integer>` + `JpaSpecificationExecutor<User>`, one extra `findByUserUuid` finder) deliberately
  named differently to avoid a Spring bean-name collision with `api`'s copy. `common`'s
  `UserRepository` now extends `JpaSpecificationExecutor<User>` too (needed for `social-service`'s
  `UserSpecification`-based search) and is the sole `User` repository across the whole reactor —
  same precedent as `SysParamRepository`, which already lived in `common` for the identical reason
  (a feature module needing read access without depending on `api`). `UserService`/`Impl` (registration,
  OAuth2 provisioning, password hashing, OTP-gated activation) stayed in `api/security/` — that's
  genuine authentication-flow business logic entangled with the rest of the security stack, not
  generic CRUD, so it doesn't get the same treatment as the repository. `docs/PROJECT_STRUCTURE.md`
  updated (`common`/`social-service`/`api` sections); `common/CLAUDE.md`, `social-service/CLAUDE.md`,
  and `api/CLAUDE.md` updated to match.

- **Extracted Category/Tag/ContentItem/QuestionAnswer/Article into a new `content-service` module** —
  continuing the "one module per big feature area" direction started by `social-service`. These four
  (plus the closely-coupled `Article`, which shares `ContentItemTag` tagging logic with `QuestionAnswer`
  almost verbatim and would otherwise have been left half-broken in `api`) previously had their entities
  in `common` and everything else (repositories, services, controllers, DTOs, mappers, seeders) in `api`.
  - **Module dependency graph reordered**: `common` ← `infra` ← `content-service` ← `ai-service`/
    `social-service` ← `api`. Unlike `social-service` (zero coupling from `ai-service`),
    `ai-service`'s `ContentEmbedding` has a real `@ManyToOne` FK to `ContentItem`, and
    `ContentIngestionService.ingest(...)` takes a `ContentItem` parameter — so `ai-service` now
    depends on `content-service` for those types, and `content-service` was inserted between `infra`
    and `ai-service` in the module order.
  - **What moved to `content-service`** (package root `com.ttg.devknowledgeplatform.content.*`):
    entities (`Category`, `Tag`, `ContentItem`, `ContentItemTag`, `QuestionAnswer`, `Article`) and
    their enums (`ContentType`, `ContentStatus`, `TagStatus`, `QuestionDifficulty`) from `common`;
    repositories + `Specification`s from `api/repository`; the four services (redesigned — see
    below); the CSV/Markdown seeders (`CsvSeeder`, `CategorySeeder`, `TagSeeder`,
    `QuestionAnswerSeeder`); the `ContentPublishedEvent` definition (currently has no publisher
    wired up anywhere — pre-existing scaffold, unchanged by this move).
  - **What stayed in `api`**: REST controllers/API interfaces, all `dto/admin/*` DTOs, all mappers
    (`CategoryMapper`, `TagMapper`, `QuestionAnswerMapper`, `ArticleMapper`), and the indexing/RAG
    orchestration layer (`ContentIndexingService`/`Impl`, `IndexingQualityService`/`Impl`,
    `EmbeddingIndexServiceImpl`, `IngestionApi`/`Controller`, `PublicContentApi`/`Controller`,
    `ContentPublishedEventListener`) — this layer genuinely needs both `content-service` and
    `ai-service`, and `content-service` can't depend on `ai-service` without creating a cycle (since
    `ai-service` already depends on `content-service`), so it stays one layer up in `api`, which
    depends on both. `DataSeedingRunner` also stays in `api` as the cross-domain seeding
    orchestrator, now just importing the seeders from their new package.
  - **Service layer redesigned to return entities, not REST DTOs** — `CategoryService`/
    `TagService`/`QuestionAnswerService`/`ArticleService` used to accept and return `api`'s own
    DTOs directly (`CreateCategoryRequest`, `CategoryResponse`, `PagedResponse<...>`). Moving them
    into `content-service` as-is would have made `content-service` depend on `api`'s DTOs while
    `api` depends on `content-service` — circular. Every method now takes plain params or a new
    content-service-owned command record (`QuestionAnswerCommands.Create/Update`,
    `ArticleCommands.Create/Update`) and returns an entity or `Page<Entity>`, matching the
    `FriendService` precedent (`social-service`) — its own Javadoc already documented this exact
    split ("*Returns entities rather than REST DTOs — api's FriendMapper does the entity-to-response
    mapping*"). `CategoryService.listTree()` returns a new `CategoryTreeNode` record (`Category` +
    resolved children) instead of the DTO-shaped tree node; `api`'s `CategoryMapper` gained
    `toTreeNodeResponse(CategoryTreeNode)` to flatten it. `api`'s controllers build the command from
    the incoming request DTO and call the mapper on the returned entity/`Page`, the same technique
    `FriendController` already used.
  - **`ErrorCode` split into an interface**, since `content-service` needed to own
    `CATEGORY_*`/`TAG_*`/`QA_*`/`ARTICLE_*` codes without a compile-time dependency back onto it
    from `common`. The former single enum is renamed `CommonErrorCode` (keeps
    `AUTH_*`/`OAUTH_*`/`USER_*`/`OTP_*`/`VALIDATION_*`/`SERVER_*`/`RESOURCE_*`/`REQUEST_*`/`AI_*`/
    `RATE_*`/`CHAT_*`/`FRIEND_*` — note `FRIEND_*` stayed here rather than moving to
    `social-service`, unlike this entry's `CATEGORY_*`/etc., since splitting error codes wasn't
    part of the friend-management work); the moved codes now live in `content-service`'s new
    `ContentErrorCode`. `ApiException`/`BusinessException`/`GlobalExceptionHandler` needed no logic
    changes — they already only called `getCode()`/`getMessage()`/`getHttpStatus()` through the
    type that became the interface. `content-service`'s `pom.xml` needed an explicit (non-test,
    non-optional) `spring-boot-starter-web` dependency, unlike `social-service`'s test-scoped-only
    declaration, because `ContentErrorCode` references `HttpStatus` directly in main code.
  - **`SlugService`/`SlugServiceImpl` moved from `api` to `infra`** — a generic utility with no
    content-specific logic that `content-service`'s service impls need but can't reach in `api`
    (that dependency direction runs the other way). `infra` gained a dependency on `common` (had
    none before) since `SlugService.generateUniqueSlug(...)` takes an `ErrorCode` parameter.
  - **No database migration** — schema and table names are unchanged; this is a pure code-ownership
    change. Following the `social-service` precedent, `content-service`'s tables are still migrated
    from `api`'s existing Liquibase changelog tree (new feature modules don't get their own
    changelog folder in this repo); the actual seed data files (`data/csv/*.csv`,
    `data/question-answers/*.md`) also stay under `api/src/main/resources/` — only the Java seeder
    classes moved.
  - `docs/PROJECT_STRUCTURE.md` — new `content-service` module section; `common`/`infra`/`api`
    sections updated to reflect what moved.
  - **Follow-up**: `api`'s Category/Tag/QuestionAnswer/Article DTOs moved from the flat
    `dto/admin/` package into their own `dto/content/` package, matching the existing `dto/friend/`
    and `dto/chat/` colocation convention (one subpackage per feature) instead of the generic
    "admin" bucket. `dto/admin/` now holds only `EmbeddingIndexItemResponse` (a genuinely
    cross-cutting admin-tooling DTO, not owned by one extracted feature module). DTOs were
    deliberately **not** moved into `content-service` itself — they're the REST/HTTP contract
    (Jackson annotations, bean-validation messages written for client-facing errors, pagination
    envelope), not business logic, and `content-service` needs to stay reusable outside a REST
    context; moving them there would also force a dependency on REST-flavored concerns into a
    module that shouldn't know about HTTP at all.

### Added

- **Friend Management (Phase 1 — friend graph)** — search, friend requests, friendships, and
  blocking, the foundation for a later Discord-style chat/groups phase. New `social-service`
  Maven module (`common` ← `infra` ← `social-service` ← `api`, mirroring `ai-service`'s shape)
  rather than adding this into `api` directly — the intent is one module per big feature area
  going forward, so `api` doesn't keep growing indefinitely. Chat/groups/messaging (deferred)
  will be added to this same module later as new packages, not a separate module.
  - **Data model** — three tables rather than one status-column table, since blocking is
    orthogonal to friendship (you can block a stranger; blocking a friend must kill the
    friendship *and* future requests):
    - `FRIEND_REQUEST` — `requester`/`addressee` (FK → `USER`), `status`
      (`PENDING`/`ACCEPTED`/`REJECTED`/`CANCELLED`). Partial unique index on the unordered pair
      `WHERE status = 'PENDING'` — a rejected/cancelled request doesn't block a later re-request.
    - `FRIENDSHIP` — `user1`/`user2` (FK → `USER`), stored once per pair with `user1.id <
      user2.id` enforced by a check constraint, so lookup direction never matters.
    - `USER_BLOCK` — `blocker`/`blocked` (FK → `USER`), directional, independent of the other
      two tables.
    - Liquibase `DKP-0015`.
  - `social-service` entities/enums live in the module itself (`FriendRequest`, `Friendship`,
    `UserBlock`, `FriendRequestStatus`, `RelationshipStatus`), not `common` — following the
    precedent set by `ai-service`'s own domain-specific entities (`ContentEmbedding`,
    `PipelineMetrics`), since these aren't shared across modules the way `User`/`Article` are.
  - `SocialUserRepository` — a second repository over the shared `User` entity, scoped to
    `social-service`. Deliberately not named `UserRepository`: two Spring Data repositories with
    the same simple name in different packages both default to the bean name `userRepository`
    and collide at startup. `social-service` needs its own because it cannot depend on `api`
    (that dependency runs the other way).
  - `FriendService`/`FriendServiceImpl` (`social-service`) — single service, no sub-service
    split (3 tables/~12 operations didn't justify a Facade-over-sub-services layer). Notable
    behavior:
    - **Mutual-request auto-accept** — if both users have sent each other a request, the second
      `sendRequest` call accepts the existing reverse request and creates the friendship
      immediately, instead of leaving two pending requests outstanding.
    - **Block cascade** — blocking removes any existing friendship and cancels any pending
      request between the pair (either direction) before recording the block.
    - **Mutual invisibility** — if the user being looked up (search, public profile, send
      request) has blocked the viewer, the lookup throws the same `USER_NOT_FOUND` error used
      for a genuinely nonexistent user, never a distinguishable "blocked" error — otherwise the
      blocked party could infer they'd been blocked.
    - Request-status transition guard uses a Java 21 exhaustive `switch` (no `default`) over
      `FriendRequestStatus`, so a future status addition is a compile error at every transition
      check site, standing in for a full State-pattern class hierarchy at a scale that doesn't
      justify one.
  - `FriendRequestSentEvent`/`FriendRequestAcceptedEvent` (`social-service`, records) published
    via `ApplicationEventPublisher`; `FriendRequestSentEventListener`/
    `FriendRequestAcceptedEventListener` (`api`, extending `AsyncEventHandler`) currently just
    log — the seam for a future in-app/email notification, mirroring the existing
    `ContentPublishedEvent`/`ContentPublishedEventListener` pattern. Notification delivery
    (`EmailService`) lives in `api`, so the listeners do too, even though the events they react
    to are published from `social-service`.
  - `UserApi`/`UserController` — new `GET /api/v1/users/search` (exact match on email to prevent
    directory scraping, fuzzy match on username/name); `GET /api/v1/users/public/{userUuid}`
    extended with `relationshipStatus`/`mutualFriendCount` for authenticated viewers (`null` for
    anonymous views or your own profile).
  - `FriendApi`/`FriendController` — new `/api/v1/friends/**`: send/accept/reject/cancel
    requests, list incoming/outgoing requests, list friends, unfriend, block/unblock, list
    blocked users. No `SecurityConfig` change needed — these aren't public, so they fall under
    the existing `.anyRequest().authenticated()` rule.
  - `dto/friend/` (`api`, Java records) — `UserSummaryResponse`, `UserSearchResultResponse`,
    `FriendRequestResponse`, `FriendSummaryResponse`; `FriendMapper` (`api/mapper/`) is an
    abstract class rather than a plain interface (like the existing `UserMapper`) since it needs
    an injected `StorageService` for presigned avatar URLs, and MapStruct interfaces can't hold
    instance fields.
  - `UserService.resolveCurrentUser(CustomOAuth2User)` (`api`) — new helper wrapping the
    `principal.getEmail()` → `findByEmail` lookup that would otherwise be repeated in every new
    controller method needing "the acting user." Also documents a pre-existing gap: CLAUDE.md
    referenced a `UserUtils.getCurrentUser()` that never actually existed (`UserUtils` only ever
    had `getUserName()`/`isAuthenticated()`) and architecturally can't, since `UserUtils` lives
    in `common`, which has no repository access.
  - `ErrorCode` (`common`) — new `FRIEND_*` block: `CANNOT_FRIEND_SELF`,
    `FRIEND_REQUEST_ALREADY_EXISTS`, `FRIEND_REQUEST_NOT_FOUND`, `ALREADY_FRIENDS`,
    `NOT_FRIENDS`, `USER_ALREADY_BLOCKED`.
  - `docs/PROJECT_STRUCTURE.md` — new `social-service` module section; also fixed a pre-existing
    gap where `User.java` itself was missing from `common`'s documented entity list.

- **Per-request chat model selection (OpenAI + Anthropic)** — `ChatRequest.chatModel` lets a
  client choose which chat model answers its question (e.g. `"gpt-5.4-mini"` or
  `"claude-sonnet-5"`); omitting it uses the server's configured default. Embeddings are
  unaffected and always run on OpenAI's `text-embedding-3-small` — Anthropic has no embedding
  product, and the embedding model must stay consistent between indexing and query time
  regardless of which chat model answers a given request.
  - `ChatProvider` (`common/enums/`) — bare enum `OPENAI` / `ANTHROPIC`; selects which
    LangChain4j builder family a chat model profile is wired through.
  - `ChatModelsConfig` (`ai-service/config/`) — new `@ConfigurationProperties(prefix =
    "app.ai.chat-models")`; `defaultModel` + a `List<ChatModelProfile>`, each profile fully
    self-contained (`id`, `provider`, `apiKey`, `maxTokens`, `temperature`, `maxRetries`).
    Adding a new selectable model, or a new tier of an existing provider, is now a pure
    `application.yml` change — no code change or redeploy required. The profile list also
    doubles as the request-time allow-list: an unconfigured id is rejected outright.
  - `AiServiceConfig` — the two fixed `ChatLanguageModel`/`StreamingChatLanguageModel` beans are
    replaced by `Map<String, ChatLanguageModel>` / `Map<String, StreamingChatLanguageModel>`
    beans, one entry per configured profile, built via `OpenAiChatModel`/`OpenAiStreamingChatModel`
    or `AnthropicChatModel`/`AnthropicStreamingChatModel` depending on the profile's `provider`.
  - `ChatModelResolver` interface + `ChatModelResolverImpl` (`ai-service/service/`) — looks up a
    model by id in the maps above, falling back to `ChatModelsConfig.defaultModel` when the
    request didn't specify one; throws `BusinessException(ErrorCode.AI_MODEL_UNSUPPORTED)` (new
    error code, `AI_003`, `400 BAD_REQUEST`) for an unrecognised id. `RagQueryServiceImpl`
    resolves the model before any pipeline work starts, so an invalid id rejects immediately
    rather than after retrieval has already spent embedding calls.
  - `ai-service/pom.xml` — added `dev.langchain4j:langchain4j-anthropic` (version-less, resolved
    via the existing `langchain4j-bom` import — verify at build time that the pinned BOM version
    actually publishes this artifact; pin an explicit `<version>` if not).
  - `application.yml` — new `app.ai.chat-models.*` section with two seeded profiles:
    `gpt-5.4-mini` (provider `OPENAI`, reuses `OPENAI_API_KEY`) and `claude-sonnet-5` (provider
    `ANTHROPIC`, new `ANTHROPIC_API_KEY` env var).
  - `PIPELINE_METRICS.CHAT_MODEL` (new nullable column, Liquibase `DKP-0012`) — records which
    model profile actually served each request, so cost/latency can eventually be broken down
    per model in the admin summary endpoint (the endpoint itself isn't changed by this entry —
    only the raw column is now being captured).

- **Startup data seeding for Category/Tag/InterviewQuestion** — idempotent, insert-if-missing
  seeding from classpath seed files, replacing the previous unwired raw-SQL seed scripts. Two
  formats, chosen per content shape:
  - **CSV** for `Category`/`Tag` (`data/csv/categories.csv`, `data/csv/tags.csv`) — genuinely
    flat, short, tabular data; CSV's sweet spot. `CsvSeeder<T>` (`api/service/seed/`) is the
    Template Method: owns CSV reading, iteration, and skip/insert counting; `CategorySeeder`
    and `TagSeeder` supply only the per-row existence check and entity construction.
  - **Markdown + YAML front matter, one file per record** for `InterviewQuestion`
    (`data/interview-questions/*.md`) — front matter carries the short structured fields
    (`title`, `slug`, `categorySlug`, `tagSlugs`, `difficulty`, `isCommon`, `questionBody`,
    `shortAnswer`); everything after the closing `---` is the raw markdown `detailedAnswer`
    body, led by a `<!-- detailedAnswer -->` label (stripped by the parser before persisting)
    so the field mapping is visible when reading the raw file, matching how every other field
    is explicitly labelled in front matter. Chosen over CSV/JSON after hitting real
    quoting/escaping pain authoring long multi-paragraph markdown inside CSV-quoted fields
    (unreadable diffs, error-prone by hand).
    `InterviewQuestionSeeder` does **not** extend `CsvSeeder` — the source is a directory of
    files, not rows in one file, so the iteration shape genuinely differs; it implements its
    own `seed()` instead of forcing that shape through the CSV template.
  - `DataSeedingRunner` (`api/service/seed/`) — `ApplicationRunner` gated by
    `app.seed.enabled` (`false` by default, `true` for `local`/`docker` profiles); runs seeders
    in dependency order (categories → tags → interview questions) since interview-question rows
    reference categories and tags by slug.
  - All seeders write directly via repositories rather than `CategoryService`/`TagService`/
    `InterviewQuestionService.create()`, because those services always derive the slug from the
    name/title and throw on a name conflict — incompatible with seed rows that need a stable,
    externally-supplied slug for cross-file references (e.g. an interview question's
    `categorySlug`) and with idempotent skip-if-exists semantics.
  - `TagRepository.findBySlug` — added; needed to resolve tag slugs when attaching tags to a
    seeded interview question (every other repository used here already had it).
  - New dependencies: `commons-csv` (`org.apache.commons:commons-csv:1.11.0`, root `pom.xml`
    `dependencyManagement` + `api/pom.xml`) for RFC4180-compliant CSV parsing; `snakeyaml`
    (`org.yaml:snakeyaml`, version managed by `spring-boot-starter-parent`) for front-matter
    parsing, constructed with `SafeConstructor` to restrict parsing to plain YAML types rather
    than allowing arbitrary Java-type instantiation via YAML tags.
  - Removed `api/src/main/resources/data/{init-interview-questions.sql,init-sample-data.sql}` —
    hand-written seed scripts that were never wired to any runner, Liquibase changelog, or
    `spring.sql.init` config; superseded by the mechanism above. (`init-admin-user.sql` is
    unrelated — a local-dev admin-user bootstrap script — and was kept.)
  - `data/interview-questions/` now ships the full **100-question** target dataset, spread
    across all 12 leaf categories, generated in parallel batches per category against the
    quality/RAG-chunking criteria in `docs/SEED_DATA_AUTHORING_GUIDE.md` and verified afterward
    (front-matter validity, id uniqueness, category/tag reference validity, body completeness,
    presence of a code example and a `### Common mistakes` section in every file).
  - `docs/SEED_DATA_AUTHORING_GUIDE.md` — new reference doc: file schema, mechanical rules the
    seeder enforces, content quality criteria, and the confirmed `TextChunkingService` chunking
    behavior (LangChain4j `DocumentSplitters.recursive`, not markdown-aware) that shapes how
    `detailedAnswer` sections should be written. Read before authoring further batches.
  - `InterviewQuestionSeeder`'s front-matter `slug` is now optional — when omitted, it's derived
    via `SlugService.toSlug(title)`, the exact method production content creation uses, removing
    both the manual-authoring burden and the risk of a hand-typed slug silently diverging from
    that algorithm. Whether explicit or derived, a slug already used by a *different* title now
    throws instead of being silently treated as "already seeded" — the previous version would
    have dropped the second question with no warning on a genuine slug collision.
  - **Removed the `slug` column from `categories.csv`/`tags.csv` entirely, and switched every
    cross-reference in seed data to names instead of slugs** (`categories.csv`'s `parentSlug` →
    `parentName`; `InterviewQuestion` front matter's `categorySlug`/`tagSlugs` →
    `categoryName`/`tagNames`). Slugs were a purely internal, derived concept that never needed
    to be authored or referenced by a human — `CategorySeeder`/`TagSeeder` now identify existing
    rows by (case-insensitive) name, the same uniqueness rule `CategoryServiceImpl`/
    `TagServiceImpl` already enforce in production, and always generate the slug via
    `SlugService.generateUniqueSlug(..., ErrorCode.CATEGORY_SLUG_CONFLICT / TAG_SLUG_CONFLICT)` —
    the identical call the real create flows make. `InterviewQuestionSeeder` resolves
    `categoryName`/`tagNames` via the new `CategoryRepository`/`TagRepository`
    `findByNameIgnoreCase` methods. Side effect: the tags.csv "Java" row no longer needs its old
    `java-tag` slug workaround (there to avoid colliding with the "Java" category's slug) —
    Category and Tag slugs were never actually cross-table-unique, so the workaround was
    unnecessary once slugs stopped being manually assigned.
  - All 6 `data/interview-questions/*.md` files now omit `slug` entirely (auto-derived from
    `title`), adopted as the default convention — explicit `slug` remains supported for the rare
    case where the auto-derived, full-sentence slug is unworkable.
  - **`SEED_ID` column added to `CATEGORY`, `TAG`, `CONTENT_ITEM`** (Liquibase `DKP-0013`,
    nullable `VARCHAR(100)`, unique index per table) — a permanent, seed-file-only identifier,
    `NULL` for every user/admin-created row, decoupled from both the real sequence-generated PK
    and any human-editable field (`NAME`/`TITLE`/`SLUG`). Reason: this seed data is confirmed
    long-lived, coexisting permanently with user-created rows in the same tables — not
    wipe-and-reseed dev data — so `NAME`/`TITLE`/`SLUG` staying free to edit without ever causing
    a duplicate INSERT on the next seeding run matters for real. A hardcoded literal PK value was
    considered and rejected: it would desync the backing sequence (`CATEGORY_SEQ`/etc.) and
    collide with the next row a real user creates through the app.
    - `Category`/`Tag`/`ContentItem` entities gained a `seedId` field; `CategoryRepository`/
      `TagRepository`/`ContentItemRepository` gained `findBySeedId`.
    - `categories.csv`/`tags.csv` gained a required `id` column (first column); interview-question
      front matter gained a required `id` field. All existing seed data was assigned ids
      (`cat-*`, `tag-*`, reusing the prior `iq-*` values).
    - `CategorySeeder`/`TagSeeder`/`InterviewQuestionSeeder` now check `findBySeedId` first — a
      match means "already seeded", regardless of whether `name`/`title` changed since. An `id`
      reused for a different name/title throws (accidental reuse), as does a *new* id whose
      name/title collides with existing content — same collision-safety philosophy as before,
      now anchored on `id` instead of `name`/`slug`/`title`.
    - `InterviewQuestion`'s `slug` field is now fully independent of idempotency — it remains
      optional, auto-derived from `title` via `SlugService.toSlug()` if omitted, purely for the
      production-facing URL column.
    - Not solved by this change, by design: editing `name`/`title`/body/tags/category on an
      already-seeded row still doesn't update the existing DB row — an `id` match always means
      "skip". That requires real update-in-place logic, out of scope here.
  - **Cross-references in seed data switched from name to `id`**, completing the rationale above:
    `categories.csv`'s `parentName` → `parentId`; `InterviewQuestion` front matter's
    `categoryName`/`tagNames` → `categoryId`/`tagIds` — all now resolved via `findBySeedId`
    instead of `findByNameIgnoreCase` (removed from `CategoryRepository`/`TagRepository`, now
    unused). Reason: a reference by `name` breaks the moment that name is edited, exactly the
    class of bug `SEED_ID` was introduced to fix for self-identity — leaving cross-references on
    `name` would have only half-solved it. `id` values are unaffected by this change
    (`cat-*`/`tag-*`/`iq-*` from the prior entry); only how *other* files point at them changed.

### Changed

- **Replaced hand-built `Map<String, Object>` JWT claims with a typed `TokenClaims` sealed
  hierarchy** (`security/jwt/`) — `JwtTokenProvider.generateToken()`/`generateRefreshToken()`/
  `refreshToken()` each independently spelled out claim key string literals, which is exactly
  how the `userId`/`userUuid` claim-key mismatch (above) happened in the first place. New shape:
  - `TokenClaims` — sealed interface (`permits AccessTokenClaims, RefreshTokenClaims`) declaring
    the claim keys once as constants (`CLAIM_USER_UUID`, `CLAIM_EMAIL`, `CLAIM_USERNAME`,
    `CLAIM_ROLE`, `CLAIM_TYPE`/`TYPE_REFRESH`), plus `toClaimsMap()` (serialize for
    `Jwts.builder()`) and `static parse(Claims)` (deserialize, dispatching on the `type` claim).
  - `AccessTokenClaims(userUuid, email, username, role)` / `RefreshTokenClaims(userUuid, username,
    role)` — records; note the asymmetry is intentional and matches prior behavior: refresh
    tokens never carried an `email` claim (only as the JWT subject), only access tokens did.
    Each has a `from(User)` factory centralizing "how a `User` becomes a token's claims."
  - `JwtTokenProvider.refreshToken()` now does `TokenClaims.parse(claims) instanceof
    RefreshTokenClaims refreshClaims` (Java 21 pattern matching) instead of a manual
    `"refresh".equals(type)` string check — presenting an access token where a refresh token is
    required is now a type mismatch, not a string comparison that happens to fail.
  - New `JwtTokenProvider.parseClaims(String)` returns the full typed claim set in one call;
    `JwtAuthenticationFilter` now calls it once instead of three separate `getClaimFromToken`
    calls, each of which independently re-verified the token's HMAC signature — cuts redundant
    signature verification from 3x to 1x per authenticated request. `getUserUuidFromToken()` is
    kept as a thin convenience wrapper for callers that only need the UUID.
  - **Security fix, enabled by the type split above**: `JwtAuthenticationFilter` previously never
    checked that a presented token was specifically an access token — a refresh token's claims
    satisfied the same shape closely enough (`userUuid`/`username`/`role` all present) to
    authenticate ordinary API calls the same as an access token, a pre-existing gap invisible
    under the old stringly-typed claims. Now gated on `claims instanceof AccessTokenClaims
    accessClaims`; a refresh token (or anything else) presented as a bearer token is rejected
    (logged, request continues unauthenticated) instead of silently authenticating.

- **Renamed `userId` → `userUuid` everywhere it actually held the public UUID, not the numeric
  PK** — both the JWT claim and the login/OAuth2 API response field were named `userId` while
  holding `user.getUserUuid()`, which is exactly the naming confusion behind the
  `NumberFormatException` fixed above. No backward-compat shim — pre-launch, so existing tokens
  and cached frontend state don't need to keep working across the change.
  - **JWT claim** (`JwtTokenProvider`): renamed across `generateToken()`, `generateRefreshToken()`,
    and `refreshToken()`; `getUserIdFromToken()` → `getUserUuidFromToken()` (and its caller,
    `JwtAuthenticationFilter`). Also updated `UserUtils.getUserId()`'s dormant `Jwt`-principal
    branch, which read the same claim key.
  - **API response** (`LoginResponse.userId` → `.userUuid`): updated all four builders in
    `OAuth2Controller` (`login`, `register`, `verifyOtp`, `exchangeState`) and the `tokenData` map
    key produced by `OAuth2LoginSuccessHandler` / consumed by `exchangeState`.
  - **GUI**: `AuthTokens.userId` → `.userUuid`, `STORAGE_KEYS.userId` → `.userUuid` (and the
    underlying localStorage key string), `authService.getUserId()` → `.getUserUuid()`,
    `AdminUser.userId` → `.userUuid`, and the `AuthCallback` field mapping. The GUI never decoded
    this value out of the JWT itself (only the `exp` claim, for expiry checks) — it always came
    from the login/exchange response body, so this is purely a field-name alignment, no behavior
    change.

- **Renamed `InterviewQuestion` → `QuestionAnswer` across the whole stack, and broadened its
  scope to general dev-knowledge Q&A** — the RAG chat feature retrieves and answers from this
  content regardless of whether the underlying question happens to be interview-flavored, so a
  name implying "interview prep only" overstated its actual scope. `difficulty`/`isCommon` are
  now nullable — interview-specific metadata, populated only when a question genuinely has that
  framing, not defining characteristics of the content type. Considered and rejected: splitting
  into two tables (a general `QuestionAnswer` plus a narrower `InterviewQuestion` subtype) — the
  two would have had identical fields except the two optional attributes, which doesn't earn a
  second Class Table Inheritance subtype; the same reasoning that kept `ContentType` about
  *shape* rather than *subject/use-case* earlier in this changelog applies here too.
  - **DB** (Liquibase `DKP-0014`): `INTERVIEW_QUESTION` table, `INTERVIEW_QUESTION_SEQ` sequence,
    and all its constraints/indexes renamed to `QUESTION_ANSWER`/etc.; `DIFFICULTY`/`IS_COMMON`
    columns dropped `NOT NULL`; `CONTENT_ITEM.TYPE` and `CONTENT_EMBEDDING.SOURCE_TYPE` CHECK
    constraints updated to accept `QUESTION_ANSWER` instead of `INTERVIEW_QUESTION` (existing
    rows updated first, in case this ever runs against data written by an earlier version).
  - **`common`**: `ContentType.INTERVIEW_QUESTION` → `ContentType.QUESTION_ANSWER`;
    `InterviewQuestionDifficulty` → `QuestionDifficulty`; `ParamKey.CENTROID_INTERVIEW_QUESTION`
    → `ParamKey.CENTROID_QUESTION_ANSWER`; `ErrorCode.INTERVIEW_QUESTION_*` (`IQ_*`) →
    `ErrorCode.QUESTION_ANSWER_*` (`QA_*`).
  - **`api`**: `InterviewQuestionRepository`/`Specification`/`Service`/`Impl`/`Mapper`,
    `InterviewQuestionResponse`/`Create…Request`/`Update…Request`, `InterviewQuestionApi`/
    `Controller` all renamed to `QuestionAnswer*` equivalents; admin REST path
    `/api/v1/admin/interview-questions` → `/api/v1/admin/question-answers`; public REST path
    `/api/v1/public/interview-questions` → `/api/v1/public/question-answers`
    (`PublicContentApi.listQuestionAnswers`/`getQuestionAnswerBySlug`); `CreateQuestionAnswerRequest`/
    `UpdateQuestionAnswerRequest.difficulty` no longer `@NotNull`.
  - **seed package**: `InterviewQuestionSeeder` → `QuestionAnswerSeeder`; seed directory
    `data/interview-questions/` → `data/question-answers/`; all 100 seed files renamed `iq-*.md`
    → `qa-*.md` with matching `id:` front-matter values (safe to rename — confirmed this seed
    data has never been deployed to any persistent environment yet); `difficulty`/`isCommon` in
    front matter are now genuinely optional (the seeder no longer forces a value via
    `Boolean.TRUE.equals(...)` — it preserves `null` when the field is absent).
  - **`ai-service`**: `LoadedPrompts.interviewQuestion` → `questionAnswer`;
    `system-prompt-interview-question.txt` renamed to `system-prompt-question-answer.txt` **and
    rewritten** — the old text was a dedicated "coach someone for a technical interview" persona,
    which would have kept mis-framing general-knowledge answers as interview coaching even after
    a pure file rename; the new prompt only applies interview-coaching framing when the user's
    own question reads as interview prep. The other three system prompts' descriptive text
    ("articles, interview questions, and blog posts") updated to "articles, question-and-answer
    content, and blog posts" for consistency.
  - **`gui`**: `InterviewQuestionListPage`/`FormPage` → `QuestionAnswerListPage`/`FormPage`;
    deleted `InterviewQuestionFormDialog.tsx` (confirmed dead code — an unused duplicate of the
    form page as a dialog variant); `adminApi.ts`/`admin.types.ts` types and methods renamed to
    match; `QuestionAnswer.difficulty`/`isCommon` are now `Difficulty | null`/`boolean | null` in
    the TypeScript types, with the difficulty `<Select>` gaining a "Not set" option; admin nav
    label and dashboard card relabeled "Questions & Answers"; `EmbeddingContentType`/
    `EmbeddingsPage`/`SourcesPanel` content-type labels updated (`SourcesPanel`'s chat-source
    label changed from "Interview" to "Question", since it's no longer accurate to assume every
    such source is interview content).

- **`ModelConfig` narrowed to embedding settings only** — it used to hold both embedding config
  and the single chat provider's settings (`apiKey`, `chatModel`, `maxTokens`, `temperature`,
  `maxRetries`) under one flat class with one `apiKey`, which stopped making sense once chat
  generation could run on more than one provider. Chat settings moved to the new
  `ChatModelsConfig` above; `ModelConfig` now holds only `apiKey`, `model`, `dimensions`.
- **`RagQueryService.query`/`queryStream`** — new primary overloads add a trailing
  `String chatModel` parameter; the previous primaries (ending in `Integer userId`) became
  `default` methods delegating with `chatModel = null` (server default), preserving backward
  compatibility for any existing callers — same pattern used for the earlier `userId` addition.
- **`PricingConfig`** — rates are now keyed by chat model id (`Map<String, ChatModelPricing>`
  under `app.ai.pricing.chat-models`) instead of one flat rate, since different chat models
  (and providers) price tokens differently. Seeded with `gpt-5.4-mini` ($0.75 / $4.50 per 1M)
  and `claude-sonnet-5` ($2.00 / $10.00 per 1M — Anthropic's introductory pricing through
  2026-08-31; standard pricing after that is $3.00 / $15.00, noted in a comment in
  `application.yml` for whoever updates it then). `embeddingCostPerToken` stays flat (unchanged)
  since only one embedding model is ever active. `PipelineCompletedEventListener.computeEstimatedCost()`
  looks up the chat rate by `RagPipelineContext.resolvedChatModel`; a model missing a pricing
  entry logs a warning and omits the LLM-generation cost rather than throwing.
  - Still open (unchanged by this entry): the TODO on `PipelineCompletedEventListener` about
    adding a `PRICING_VERSION` column to `PIPELINE_METRICS` so historical rows stay unambiguous
    after a future rate change — that's a separate schema concern, out of scope here.

### Fixed

- **`NumberFormatException` on every JWT-authenticated request using `@CurrentUserId`** (e.g.
  `GET /api/v1/chat/sessions`) — `CurrentUserIdArgumentResolver.resolveArgument()` called
  `Integer.parseInt(principal.getId())`, assuming `CustomOAuth2User.getId()` held the numeric
  `User` primary key. It never did: both `JwtAuthenticationFilter` and `CustomOAuth2UserService`
  populate that field with `user.getUserUuid()` — the internal sequential PK is deliberately
  never exposed to the JWT/client (avoids leaking user count/signup ordering and IDOR-style PK
  enumeration). Fixed by injecting `UserRepository` into the resolver and resolving the numeric
  PK via the existing `findByUserUuid()` lookup, throwing `ResourceNotFoundException` /
  `ErrorCode.USER_NOT_FOUND` if the UUID no longer matches a row (e.g. account deleted after the
  token was issued). Downstream code was already correct — every consumer of `@CurrentUserId
  Integer userId` (`ChatSession.userId`, `ChatSessionRepository`) genuinely keys off the numeric
  PK, so only the resolver's extraction logic needed to change.

- **`DataSeedingRunner` startup crash: `LazyInitializationException` on `Tag.contentItemTags`** —
  a pre-existing latent bug in the entity design, first exercised by this seeding work (never
  actually run end-to-end before, since this sandbox's JDK 8 couldn't build/run the Spring Boot
  app to catch it earlier). `Tag`'s `@EqualsAndHashCode` didn't exclude its lazy
  `contentItemTags` collection — `Category` and `ContentItem` already correctly excluded their
  own lazy relation fields, but `Tag` was missed. `QuestionAnswerSeeder.persist()` (like
  `QuestionAnswerServiceImpl.applyTagIds()`) adds a new `ContentItemTag` to a `HashSet`, which
  computes `hashCode()` on it — cascading into `Tag.hashCode()` — outside of a transaction
  (`CategorySeeder`/`TagSeeder`/`QuestionAnswerSeeder` are intentionally not `@Transactional`;
  each repository call is its own short-lived session), so the lazy collection had no open
  session left to initialize against. Fixed by excluding `contentItemTags` from `Tag`'s
  `@EqualsAndHashCode`, matching the existing convention on `Category`/`ContentItem`.
  - Considered and rejected: also excluding `contentItem`/`tag` from `ContentItemTag`'s
    `@EqualsAndHashCode` (defense-in-depth, matching the same convention) — this would have
    introduced a worse, silent bug: before `@PrePersist` runs, every pending `ContentItemTag`'s
    inherited `AbstractEntity` fields are still `null`, so excluding the two fields that
    actually distinguish one pending join row from another would make the `HashSet` treat a
    question's second and third tags as "already present," silently dropping them. Only the one
    genuinely-lazy field (`Tag.contentItemTags`) needed excluding; `contentItem`/`tag` are what
    make each pending `ContentItemTag` actually distinct before it has a real ID.

- **First real indexing request crash: pgvector insert failed with "column is of type vector but
  expression is of type character varying"** — another pre-existing, never-before-exercised bug
  (same root cause as the above: nothing in this environment could actually run the full
  request→index pipeline until now). `FloatArrayToVectorConverter`'s javadoc assumed pgvector's
  `[x,y,z,...]` string format implicitly casts `varchar` → `vector` — true for a literal written
  directly in SQL text, **not** true for a JDBC prepared-statement parameter, which Postgres
  receives typed as `varchar` with no cast applied. `ContentEmbeddingRepository`'s native
  similarity/centroid queries were already safe (they wrap the parameter in an explicit
  `CAST(:embedding AS vector)` in the query text), but `ContentEmbedding.embedding`'s
  JPA-managed `@Convert`-based insert had no such cast.
  - First attempt — `@JdbcTypeCode(SqlTypes.OTHER)` alongside the existing `@Convert` — **did
    not work**: code 1111 (`OTHER`) resolves to Hibernate's built-in `VarbinaryJdbcType` for this
    Hibernate 6 + PostgreSQL combination, which then failed trying to unwrap the converter's
    `String` output into a `byte[]` ("Could not convert 'java.lang.String' to '[B'").
  - Actual fix — new `PgVectorJdbcType` (`ai-service/converter/`), a custom
    `org.hibernate.type.descriptor.jdbc.JdbcType` implementing the bind/extract logic directly:
    `PreparedStatement.setObject(index, value, Types.OTHER)` on write, `ResultSet.getString(...)`
    on read. This bypasses Hibernate's registry resolution for code 1111 entirely instead of
    routing through it. Applied via `@org.hibernate.annotations.JdbcType(PgVectorJdbcType.class)`
    alongside the existing `@Convert(converter = FloatArrayToVectorConverter.class)`. Both
    converter and entity javadoc updated to document why the annotation shortcut doesn't work and
    point future vector-typed fields at the custom `JdbcType` instead.

### Added

- **Async Event Executor Bulkhead** — `@EventHandler` dispatch (all application event listeners,
  e.g. content indexing after publish) now runs on its own dedicated thread pool instead of
  sharing `sseStreamExecutor`. Previously, an SSE stream holds its thread for up to the full
  60 s request timeout; a burst of concurrent chat streams could exhaust the shared pool and
  cause `RejectedExecutionException` to propagate synchronously out of the transactional
  service that published the event (e.g. content publishing itself failing), and conversely a
  burst of event dispatch (bulk publish/reindex) could starve chat responses.

  - `ThreadPoolProperties` (`api/config/thread/`) — new nested `AsyncEventExecutor`:
    `corePoolSize` (default 5), `maxPoolSize` (default 20), `queueCapacity` (default 200),
    `awaitTerminationSeconds` (default 30).
  - `ThreadPoolConfig` — new `asyncEventExecutor` bean (Factory Method), registered with
    Micrometer `ExecutorServiceMetrics` (Decorator). Metrics at `/actuator/metrics` tagged
    `name=async-event`.
  - `infra` — `EventHandler` composed annotation now declares `@Async("asyncEventExecutor")`
    explicitly instead of relying on `@Async`'s single-bean auto-detection.
  - `WebMvcConfig` — Javadoc corrected: `sseStreamExecutor` now documented as feeding only
    `configureAsyncSupport()` (MVC async dispatch), not the `@Async` default.
  - `application.yml` — new `app.threads.async-event-executor.*` section with env-var
    overrides (`ASYNC_EVENT_CORE_POOL_SIZE`, `ASYNC_EVENT_MAX_POOL_SIZE`,
    `ASYNC_EVENT_QUEUE_CAPACITY`, `ASYNC_EVENT_AWAIT_TERMINATION_SECONDS`).

- **Embedding Index Admin API + UI** — `GET /api/v1/admin/embeddings` returns a paginated,
  filterable list of content items merged with their embedding aggregate statistics;
  admin UI page at `/admin/embeddings` supports per-item re-index / delete-index actions
  and bulk "Index All" / "Refresh Corpus" operations.

  - `ai-service` — `EmbeddingStatsProjection` interface projection (contentItemId, chunkCount,
    totalTokens, modelName, lastIndexedAt); `ContentEmbeddingRepository.findStatsByContentItemIds`
    JPQL aggregate query (COUNT / SUM / MAX grouped by content item ID).
  - `api` — `ContentItemRepository` now extends `JpaSpecificationExecutor<ContentItem>`;
    `EmbeddingIndexItemResponse` DTO; `EmbeddingIndexService` / `EmbeddingIndexServiceImpl`
    (two-query pattern: Specification page + batch stats); `EmbeddingIndexApi` interface;
    `EmbeddingIndexController` implementation. Secured via `SecurityConfig` (`/api/v1/admin/**`).
  - `gui` — `EmbeddingIndexItem` and `EmbeddingListParams` types in `admin.types.ts`;
    `listEmbeddings`, `reindexContent`, `deleteContentIndex`, `indexAll`, `refreshCorpus`
    in `adminApi.ts`; `EmbeddingsPage.tsx` with table, filters, and confirmation dialogs;
    nav item added to `AdminLayout`; route `/admin/embeddings` added to `App.tsx`.

- **Tomcat Thread Pool Configuration** — Tomcat worker thread sizing and connection queue exposed
  as configurable, observable properties.

  - `application.yml` — new `server.tomcat` section:
    - `threads.max` (default 200, env `TOMCAT_THREADS_MAX`) — worker thread cap; must be ≥ `sseStreamExecutor.maxPoolSize` because each SSE stream holds one Tomcat thread for its lifetime.
    - `threads.min-spare` (default 10, env `TOMCAT_THREADS_MIN_SPARE`) — idle threads kept alive to absorb traffic spikes without cold-start latency.
    - `accept-count` (default 100, env `TOMCAT_ACCEPT_COUNT`) — connection queue depth when all workers are busy.
    - `mbeanregistry.enabled: true` — required for `TomcatMetricsBinder` (Spring Boot Actuator auto-configuration) to register Tomcat metrics. Without this flag, no `tomcat.*` metrics appear at `/actuator/metrics` even with Actuator on the classpath.
  - Metrics now available at `/actuator/metrics`:
    - `tomcat.threads.busy` — threads actively handling requests
    - `tomcat.threads.config.max` — configured maximum
    - `tomcat.threads.current` — current live thread count
    - `tomcat.connections.current` — open connections
    - `tomcat.connections.config.max` — connection ceiling

- **Thread Pool Centralisation** — all application-managed thread pools declared in one place,
  sized via `@ConfigurationProperties`, and instrumented with Micrometer.

  - `ThreadPoolProperties` (`api/config/thread/`) — `@ConfigurationProperties(prefix = "app.threads")`
    with a nested `SseExecutor` holding `corePoolSize` (default 10), `maxPoolSize` (default 50),
    `queueCapacity` (default 100), `awaitTerminationSeconds` (default 30). All fields overridable
    via env vars (e.g. `SSE_CORE_POOL_SIZE`).
  - `ThreadPoolConfig` (`api/config/thread/`) — `@Configuration` factory (GoF Factory Method) that
    creates the `sseStreamExecutor` bean and registers it with Micrometer `ExecutorServiceMetrics`
    (GoF Decorator). Metrics exposed at `/actuator/metrics` under `executor.active`, `executor.pool.size`,
    `executor.queued`, `executor.completed` tagged `name=sse-stream`.
  - `WebMvcConfig` — removed the inline `@Bean sseStreamExecutor()` method; now injects the bean
    produced by `ThreadPoolConfig`. `configureAsyncSupport()` and `@EnableAsync` continue to
    reference the same pool.
  - `application.yml` — new `app.threads.sse-executor.*` section with env-var overrides.

- **LangChain4j HTTP Timeout Externalisation** — OpenAI HTTP client timeout now configurable
  without recompilation.

  - `OkHttpProperties` (`ai-service/config/`) — `@ConfigurationProperties(prefix = "app.ai.okhttp")`
    with a single `timeout` field (default `60s`, ISO-8601 format). Applies to both blocking
    (`ChatLanguageModel`) and streaming (`StreamingChatLanguageModel`) model builders via their
    `.timeout(Duration)` method. Overridable via `AI_OKHTTP_TIMEOUT`.
  - `AiServiceConfig` — both `@Bean` methods now accept `OkHttpProperties` and wire the timeout.
  - `application.yml` — new `app.ai.okhttp.timeout` entry under the existing `app.ai` section.

  > **Note:** LangChain4j 0.33.0 does not expose an `okHttpClient()` setter on its builders, so
  > the OkHttp `Dispatcher` (concurrency limits, idle thread pool) remains internally managed.
  > Dispatcher configuration will require a LangChain4j upgrade.

- **Per-Request Alerting via Structured Logs (Feature 5)** — two configurable threshold checks
  that fire structured `WARN` log events after every pipeline execution, enabling external log
  aggregators (Grafana Loki, ELK, Datadog) to create alerts without any application code change.

  - `MonitoringConfig` (`ai-service/config/`) — `@ConfigurationProperties(prefix = "app.ai.monitoring")`
    auto-registered by `@ConfigurationPropertiesScan`. Two fields: `slowRequestThresholdMs` (long,
    default 5000) and `highCostThresholdUsd` (BigDecimal, default $0.01). Setting either to `0`
    disables the corresponding check — mirrors the `conversationTopicShiftThreshold: 0` disable
    convention in `GuardConfig`.
  - `application.yml` — new `app.ai.monitoring` section with env-var overrides
    (`MONITORING_SLOW_REQUEST_THRESHOLD_MS`, `MONITORING_HIGH_COST_THRESHOLD_USD`).
  - `PipelineCompletedEventListener` — new `checkThresholds(metrics, ctx)` called after
    `repository.save()`. Two independent checks fire separate log events so aggregator alert rules
    can be routed to different teams:
    - `SLOW_REQUEST` — fires when `TOTAL_PIPELINE_MS > slowRequestThresholdMs`. Logs `traceId`,
      `totalPipelineMs`, `thresholdMs`, `llmGenerationMs`, `contextualizationMs`, `userId`, `estimatedCostUsd`.
    - `HIGH_COST` — fires when `ESTIMATED_COST_USD > highCostThresholdUsd`. Logs `traceId`,
      `estimatedCostUsd`, `thresholdUsd`, `generationInputTokens`, `generationOutputTokens`,
      `contextualizationInputTokens`, `contextualizationOutputTokens`, `userId`.
  - Fixed message keys (`SLOW_REQUEST`, `HIGH_COST`) are the aggregator contract — a Grafana Loki
    query `{app="dev-knowledge-platform"} |= "SLOW_REQUEST"` matches without regex; key=value
    pairs carry all diagnostic context for investigation without a DB query.

- **Pipeline Metrics Summary Endpoint (Feature 4)** — admin-only `GET /api/v1/admin/pipeline-metrics/summary`
  returning aggregated cost, token usage, and latency percentiles for a rolling time window.
  - `MetricsPeriod` enum (`ai-service/dto/`) — `LAST_24H`, `LAST_7_DAYS`, `LAST_30_DAYS`, each holding
    a `Duration getLookback()`. Time arithmetic is in the service (`Instant.now().minus(period.getLookback())`)
    not in the enum, keeping the enum purely descriptive and easy to test.
  - `PipelineMetricsSummary` record (`ai-service/dto/`) — top-level fields: `period`, `totalRequests`,
    `abortedRequests`, `estimatedCostUsd`, `latencyP50Ms`, `latencyP95Ms`, `tokenUsage` (nested record
    `TokenUsageSummary`: `prompt`, `completion`, `embedding`). Latency fields are `Long` (nullable) to
    distinguish "no data in window" from "zero latency".
  - `PipelineMetricsSummaryProjection` interface (`ai-service/dto/`) — Spring Data JPA interface projection;
    column aliases in the native SQL (`total_requests`, `latency_p50_ms`, …) are mapped to getters by
    Spring Data's underscore-to-camelCase convention. An interface (not a record) is required because
    Spring Data generates the implementation at runtime via a JDK dynamic proxy.
  - `PipelineMetricsRepository` — new `@Query(nativeQuery = true)` method `fetchSummary(Instant since)`
    using `percentile_cont(p) WITHIN GROUP (ORDER BY total_pipeline_ms)` — a PostgreSQL ordered-set
    aggregate not expressible in JPQL. The existing `IDX_PIPELINE_METRICS_CREATED_AT` index prunes the
    window; percentile computation then sorts the matching rows in memory. `COALESCE(SUM(...), 0)` in the
    token columns handles aborted rows where token columns are NULL (stage never ran).
  - `PipelineMetricsSummaryService` interface + `PipelineMetricsSummaryServiceImpl` (`ai-service/service/`) —
    `@Transactional(readOnly = true)` for connection-pool routing hint; maps projection to response record.
  - `PipelineMetricsApi` interface + `PipelineMetricsController` (`api/api/`, `api/api/impl/`) —
    mounted at `/api/v1/admin/pipeline-metrics/summary`; inherits `hasRole("ADMIN")` from `SecurityConfig`
    without any per-method annotation. `@RequestParam(defaultValue = "LAST_7_DAYS") MetricsPeriod period`
    — Spring MVC converts the string parameter to the enum by name (uppercase, e.g. `?period=LAST_30_DAYS`).

- **Cost & Latency Monitoring (Features 1–3)** — per-request stage latencies, token usage, estimated
  cost, and user attribution persisted to `PIPELINE_METRICS` after every RAG execution.

  **Feature 1 — Stage latency persistence:**
  - `PipelineMetrics` entity — five new columns: `contextualizationMs`, `embeddingMs`, `retrievalMs`,
    `llmGenerationMs`, `totalPipelineMs`. Stage latencies are derived at event-dispatch time from the
    existing `StageSpan` list (no new context fields needed per stage). LLM generation time required
    a new `llmGenerationMs` field on `RagPipelineContext` because the LLM call runs outside the pipeline
    runner; it is set by `RagQueryServiceImpl` immediately after the model responds.
  - `PipelineCompletedEventListener` — new `spanMs()` helper extracts stage durations from
    `ctx.getSpans()` by name; `totalPipelineMs` uses the existing `ctx.elapsedMs()`.

  **Feature 2 — Token usage & estimated cost:**
  - `EmbedResult` record (`ai-service/dto/`) — pairs `float[] vector` with `int tokenCount`; replaces
    `float[]` as the return type of `EmbeddingService.embed()` so token counts reach callers without a
    second API call. Java 21 record chosen for immutability.
  - `EmbeddingService` — `embed()` now returns `EmbedResult`; Javadoc updated.
  - `OpenAiEmbeddingServiceImpl` — captures `response.tokenUsage().inputTokenCount()` from the
    LangChain4j `Response<Embedding>` object (previously discarded by `.content().vector()`).
  - `EmbeddingStage` — stores `result.tokenCount()` on `ctx.setEmbeddingTokens()`.
  - `ContextualizationStage` — saves the full `Response<AiMessage>` instead of only `.content()`;
    new private `recordTokenUsage()` copies `inputTokenCount` / `outputTokenCount` onto the context.
  - `AnswerQualityServiceImpl` — updates to `EmbedResult.vector()`; sets `ctx.setQualityEmbeddingTokens()`.
  - `RagQueryServiceImpl` — new private `captureGenerationTokens()` reads `TokenUsage` from the final
    LLM response in both the blocking and streaming paths; `llmGenerationMs` timed with
    `System.currentTimeMillis()` around the model call.
  - `RagPipelineContext` — seven new fields: `llmGenerationMs`, `contextualizationInputTokens`,
    `contextualizationOutputTokens`, `embeddingTokens`, `qualityEmbeddingTokens`,
    `generationInputTokens`, `generationOutputTokens`.
  - `PipelineMetrics` entity — seven token columns + `estimatedCostUsd` (`DECIMAL(12,8)`).
  - `PipelineCompletedEventListener` — `computeEstimatedCost()` calculates USD from pricing constants
    (gpt-4o-mini: $0.15/1M input, $0.60/1M output; text-embedding-3-small: $0.020/1M). Returns `null`
    when all token counts are zero (aborted pipeline) to avoid storing a misleading $0.00. `nullIfZero()`
    converts primitive-zero token counts to `NULL` in the DB.

  **Feature 3 — User attribution:**
  - `RagPipelineContext` — new nullable `userId` field (Integer).
  - `RagQueryService` — `query()` and `queryStream()` primary methods gain a `Integer userId` parameter.
    All convenience default overloads delegate to the primary with `null`; backward compatibility preserved.
  - `RagQueryServiceImpl` — sets `pipelineCtx.setUserId(userId)` before the pipeline runs.
  - `ChatController` — passes `userId` from the controller layer into `ragQueryService.query()` and
    `queryStream()`.
  - `PipelineMetrics` entity — new nullable `userId` column (no FK: analytics rows must survive user deletion).

  **Liquibase migration `DKP-0011`** — `ALTER TABLE product.PIPELINE_METRICS` adds 13 columns (5 latency,
  7 token/cost, 1 user). Partial index `IDX_PIPELINE_METRICS_USER_ID` created on `(USER_ID) WHERE USER_ID
  IS NOT NULL` to keep index size proportional to authenticated traffic only.

- **`infra` module** — new Maven module (`common` ← `infra` ← `ai-service` ← `api`) housing
  shared Spring infrastructure that requires Spring Context but belongs to neither domain module.
  - `ApplicationEventHandler` marker interface — every listener implements this; `Find
    Implementations` in any IDE lists all active event handlers across all modules in one view.
  - `@EventHandler` composed annotation — composes `@EventListener + @Async`; enforces async
    dispatch on every listener; single grep target for auditing the full event bus.
  - `AsyncEventHandler<E>` abstract base class — Template Method; provides async dispatch (via
    `@EventHandler`), MDC `traceId` binding (opt-in via `resolveTraceId()`), wall-clock timing,
    and exception safety. Subclasses implement only `doHandle()`.
  - `MdcKeys` — MDC key constants (`TRACE_ID = "traceId"`) shared across modules; eliminates
    magic strings in `AsyncEventHandler` and any future MDC usage.
  - `PipelineCompletedEventListener` (renamed from `PipelineMetricsRecorderImpl`, moved from
    `ai-service/service/impl/` to `ai-service/event/`) and `ContentPublishedEventListener`
    migrated to extend `AsyncEventHandler`.

- **Pipeline Quality Metrics Persistence (Feature 3 — data-driven threshold tuning)** — one
  `PIPELINE_METRICS` row written per RAG request (success or aborted) via Spring application
  events, enabling SQL queries over real traffic to tune the 8+ thresholds currently set by intuition.
  - `PipelineCompletedEvent` record (`ai-service/event/`) — immutable event carrying
    `RagPipelineContext` + `AnswerQualityVerdict`; published by `RagQueryServiceImpl` after every
    pipeline execution. The record type guarantees immutability across threads.
  - `PipelineMetrics` entity (`ai-service/entity/`) — append-only, no `AbstractEntity`, no `@version`.
    Columns: `traceId`, `createdAt`, `abortedAt`, `candidateCount`, `afterScoringCount`,
    `selectedCount`, `evidenceMeanScore`, `effectiveSimThreshold`, `answerContextSim`,
    `answerQuerySim`, `answerDrifted`. All post-`createdAt` columns are nullable.
  - `PipelineMetricsRepository` (`ai-service/repository/`) — standard Spring Data `JpaRepository`.
  - `PipelineMetricsRecorderImpl` (`ai-service/service/impl/`) — `@EventListener` + `@Async` +
    `@Transactional`. Listens for `PipelineCompletedEvent` on the existing `sseStreamExecutor` thread
    pool; decoupled from the publisher via the event bus.
  - `EvidenceQualityStage` — `ctx.setEvidenceMeanScore(mean)` added after `computeMean()` so the
    score is available for persistence even when the guard triggers an abort.
  - `RagPipelineContext` — new `evidenceMeanScore` field (Float, nullable).
  - `RagQueryServiceImpl` — `ApplicationEventPublisher` injected; `assessAnswerQuality()` changed
    to return `AnswerQualityVerdict` (previously void); `publishEvent(PipelineCompletedEvent)` called
    in all four outcome paths: aborted (blocking), aborted (streaming), completed (blocking),
    completed (streaming).
  - Liquibase migration `DKP-0010` — creates `PIPELINE_METRICS_SEQ`, `PIPELINE_METRICS` table,
    plus two partial/standard indexes: `IDX_PIPELINE_METRICS_CREATED_AT` (time-range queries)
    and `IDX_PIPELINE_METRICS_ABORTED_AT` (guard-firing analysis, partial on non-null rows).

- **Pipeline Micrometer Metrics (Feature 2 — Actuator-backed observability)** — six Micrometer instruments
  recorded in `RagQueryServiceImpl` after every pipeline run (success or aborted). No new dependency —
  `spring-boot-starter-actuator` already provides `MeterRegistry` in the `api` module.
  - `rag.stage.latency` (`Timer`, tagged by `stage`) — p50/p95/p99 per stage across all requests.
  - `rag.pipeline.requests` (`Counter`, tagged by `outcome`) — `"success"` or `"aborted.<StageName>"`
    (e.g. `"aborted.EvidenceQualityStage"`). Reveals guard firing rates without additional instrumentation.
  - `rag.retrieval.candidates` / `rag.retrieval.after_scoring` / `rag.retrieval.selected`
    (`DistributionSummary`) — retrieval funnel from ANN search → scoring → MMR. Null-guarded: only
    recorded when the corresponding stage ran.
  - All six instruments are visible at `/actuator/metrics` (already exposed in `application.yml`).
  - `RagQueryServiceImpl` — `MeterRegistry` injected via `@RequiredArgsConstructor`; new private
    `recordPipelineMetrics(RagPipelineContext)` helper called immediately after `pipelineRunner.run()`
    in both the blocking and streaming paths.

- **Pipeline Request Tracing (Feature 1 — lightweight per-request trace)** — every RAG query now
  produces a single structured `PIPELINE_TRACE` log line that captures the full 11-stage lifecycle:
  stage names, wall-clock latency per stage, and the abort point (if any).
  - `StageSpan` record (`ai-service/dto/`) — immutable snapshot of one stage: name, duration, aborted flag.
  - `RagPipelineContext` — three new trace fields: `traceId` (UUID, generated once per context),
    `pipelineStartMs` (epoch-ms at construction), `spans` (ordered `List<StageSpan>`).
    New helpers: `recordSpan()` (called by stage infrastructure) and `elapsedMs()`.
  - `RagPipelineStage` — new default `execute(RagPipelineContext)` method (Template Method pattern).
    Times `process()`, then calls `ctx.recordSpan()`. `RagPipelineRunner` now calls `execute()` instead
    of `process()`; all 11 stage implementations are unchanged.
  - `RagPipelineRunner` — emits `PIPELINE_TRACE` at INFO level after the loop; covers both
    completed and aborted runs.
  - `RagQueryServiceImpl` — `traceId` threaded through completion, abort, and drift-detection log lines
    so every post-pipeline event can be correlated to its full trace.
  - Example output:
    ```
    INFO PIPELINE_TRACE [f3a8b2c1-...]: totalMs=1121 aborted=EvidenceQualityStage
         stages=[PromptGuardStage(12ms) → ContextualizationStage(850ms) → EmbeddingStage(95ms)
                 → QueryAnomalyStage(2ms) → RetrievalStage(130ms) → ScoringStage(8ms)
                 → RetrievalAnomalyStage(3ms) → RetrievedContentGuardStage(1ms)
                 → MmrStage(18ms) → EvidenceQualityStage(2ms,ABORTED)]
    ```

### Changed

- **AI config namespace restructured** — `EmbeddingProperties` (single flat class, prefix `app.ai.embedding`) split into five focused `@ConfigurationProperties` classes and system prompts extracted to classpath files.
  - `EmbeddingProperties` deleted. Replaced by: `ModelConfig` (`app.ai.model`), `IndexingConfig` (`app.ai.indexing`), `RetrievalConfig` (`app.ai.retrieval`), `GuardConfig` (`app.ai.guards`), `LabelsConfig` (`app.ai.labels`).
  - `RateLimitProperties` prefix changed from `app.ai.embedding.rate-limit` to `app.ai.rate-limit`.
  - Six system/utility prompts extracted from `application.yml` to `ai-service/src/main/resources/prompts/*.txt`. Loaded at startup by new `PromptsLoader` bean into `LoadedPrompts` record.
  - All 11 pipeline stages and all ai-service + api service classes updated to inject only the sub-config they need.
  - `application.yml`, `application-local.yml`, `application-docker.yml` updated to the new namespace.
  - `CorpusStatisticsServiceImpl#@Scheduled` path updated to `app.ai.indexing.centroid-refresh-interval`.
  - `RetrievalConfig.outlierGapThreshold` renamed from `retrievalOutlierGapThreshold` (now `app.ai.retrieval.outlier-gap-threshold` — no redundant prefix since property is already in the `retrieval` namespace).

- **Input Format Enrichment in `ContextualizationStage`** — applies the *Context + Task + Constraints + Output Format* prompt-engineering pattern to every user question before generation.
  - `ContextualizationStage` now makes a single LLM call that produces five labelled lines: `STANDALONE` (clean standalone question for vector embedding), `CONTEXT`, `TASK`, `CONSTRAINTS`, `OUTPUT_FORMAT`.
  - `RagPipelineContext` — new `enrichedQuestion` field carries the four-part enriched form (CONTEXT / TASK / CONSTRAINTS / OUTPUT_FORMAT) from `ContextualizationStage` to `MessageBuildingStage`.
  - `MessageBuildingStage` — uses `enrichedQuestion` (if non-null) as the final user message sent to the generation LLM, giving it explicit scope, depth level, and output structure guidance. Falls back to `originalQuestion` if enrichment is unavailable.
  - `EmbeddingProperties` — `contextualizationPrompt` renamed to `inputEnrichmentPrompt`; bound to `app.ai.embedding.input-enrichment-prompt`. The new prompt instructs the LLM to produce the five-label block in one call.
  - **Trade-off:** Fresh sessions (no conversation history) now incur one extra LLM call that was previously skipped. Sessions with history retain the same single-call count; the same call now does more.

### Added

- **Conversation Topic Shift Detection (Case 7 — pre-pipeline context guard)** — detects sudden topic
  pivots in multi-turn conversations and strips recent turns from the context before the pipeline runs,
  preventing cross-topic pronoun resolution errors in `ContextualizationStage`.
  - Problem: `ContextualizationStage` uses recent turns to resolve pronouns. After 10 turns about SQL,
    asking "How does it work?" about cryptography would be wrongly contextualized to SQL — the embedding
    would point at SQL, retrieval would return SQL chunks, the LLM would answer the wrong question.
  - Detection: cosine similarity between new question embedding and history fingerprint (rolling summary
    if present, otherwise concatenated recent USER turns). One `embedBatch()` call with both texts —
    one API round trip. Hard pivots (Backend → Medicine) ≈ 0.05–0.20; developer-adjacent pivots
    (Spring → Cryptography) ≈ 0.40–0.60 and are not flagged on a developer platform.
  - Action on shift: returns a `ConversationContext` with `recentTurns = []` (recent turns cleared)
    but `summary` preserved. `ContextualizationStage` treats the question as standalone. No abort,
    no user-facing message — retrieval simply resets silently.
  - No action on no history: fresh sessions or sessions below the summarisation threshold return
    the original context unchanged (nothing to compare against).
  - `ConversationTopicGuardService` interface + `ConversationTopicGuardServiceImpl` (`ai-service/service/`).
  - `EmbeddingProperties` — new `conversationTopicShiftThreshold` field (default `0.35`).
  - `application.yml` — `conversation-topic-shift-threshold: ${CONVERSATION_TOPIC_SHIFT_THRESHOLD:0.35}`.
  - `RagQueryServiceImpl` — guard called before `RagPipelineContext` is created in both
    `query()` and `queryStream()`.

- **Answer Drift Detection (Case 6 — post-generation monitoring)** — two-sided quality check run after
  LLM generation completes, in both the blocking and streaming paths. Detects answers that diverge from
  the retrieved context or from the user's question without blocking or altering the response (monitoring-only).
  - *Context similarity*: embeds the generated answer and computes cosine similarity against the
    L2-normalised centroid of the MMR-selected chunks. A low score means the LLM departed from the
    retrieved material and likely drew on training data (hallucination).
  - *Query similarity*: computes cosine similarity between the answer embedding and the query embedding
    already stored in the pipeline context (no extra API call). A low score means the LLM addressed a
    different topic than what was asked (topic drift). The SQL→Docker example would score ~0.20 here.
  - `AnswerQualityVerdict` record (`ai-service/dto/`) — `boolean drifted, float contextSimilarity,
    float querySimilarity`; sentinel `skipped()` factory when the check cannot run.
  - `AnswerQualityService` interface + `AnswerQualityServiceImpl` (`ai-service/service/`) — embeds the
    answer, computes the normalised context centroid, and evaluates both thresholds independently.
  - In the **blocking path**: check runs before `RagAnswer` is returned.
  - In the **streaming path**: check runs inside `StreamingResponseHandler.onComplete` after all tokens
    are sent; a failure in the check is caught and logged, never swallowing the `done` SSE event.
  - Assessment failures are swallowed (`WARN` log) — a failed quality check must not affect the
    response the user already received.
  - `EmbeddingProperties` — two new fields: `answerContextSimilarityThreshold` (0.70),
    `answerQuerySimilarityThreshold` (0.65). Both softer than retrieval thresholds — generated prose
    dilutes the topical signal.
  - `application.yml` — `answer-context-similarity-threshold`, `answer-query-similarity-threshold`;
    both env-var overridable.

- **Prompt Injection Detection (Case 5 — security guard)** — new `PromptGuardStage` runs as the
  first pipeline stage, before `ContextualizationStage` makes any LLM call. Combines two independent
  detection layers; each layer aborts immediately on detection, so later layers are only reached when
  earlier ones pass (avoiding unnecessary cost):
  - *Layer 1 — Length guard*: rejects queries exceeding `injection-detection.max-query-length` (default
    1000 chars). Oversized inputs are a common prompt-stuffing vector; no network cost.
  - *Layer 2 — Lexical matching (Option A)*: case-insensitive substring match against a configurable
    list of known injection phrases (`injection-detection.patterns`). Catches direct, unparaphrased
    attacks at zero latency.
  - *Layer 3 — Semantic similarity (Option B)*: at startup, each phrase in
    `injection-detection.prototypes` is embedded once via `EmbeddingService.embedBatch()`. At request
    time, the raw query is embedded and its max cosine similarity to all prototypes is compared against
    `injection-detection.similarity-threshold` (default `0.80`). Catches paraphrases that bypass the
    lexical layer. Cost: one extra `embed()` call — only made when layers 1–2 both pass.
  - Rejection message is intentionally vague (`rejection-message`) — reveals neither which layer fired
    nor what pattern matched, to prevent iterative payload refinement by the attacker.
  - Graceful degradation: if the embedding API is unavailable at startup, the semantic layer is
    disabled with a `WARN` log; lexical protection (layers 1–2) remains active.
  - `EmbeddingProperties` — new nested `InjectionDetectionProperties` inner class groups all
    injection-detection config: `maxQueryLength`, `patterns`, `prototypes`, `similarityThreshold`,
    `rejectionMessage`. Accessed via `properties.getInjectionDetection()`.
  - `application.yml` — `injection-detection:` block with 25 lexical patterns and 9 semantic
    prototypes; threshold and length are env-var overridable (`INJECTION_SIMILARITY_THRESHOLD`,
    `MAX_QUERY_LENGTH`).
  - `RagPipelineRunner` — stage order updated: **prompt-guard** → contextualize → embed → … → MMR → **retrieved-content-guard** → evidence-quality → build messages.

- **Indirect Injection Detection (Case 5b — corpus data-channel guard)** — new `RetrievedContentGuardStage`
  runs after `MmrStage`, before `EvidenceQualityStage`. Guards the *data channel*: a malicious actor
  who can publish documents to the corpus could embed injection instructions inside otherwise
  legitimate article text. Such a document passes the user-input guard (the user asked a legitimate
  question), scores well against relevant queries (the embedding is dominated by legitimate content),
  and reaches the LLM context window — where the payload executes.
  - Scans each MMR-selected chunk's `chunkText` for the same lexical patterns used by `PromptGuardStage`
    (reuses `injection-detection.patterns` — no extra config needed).
  - Infected chunks are removed from `ctx.selectedChunks`; the cleaned list is written back.
    `EvidenceQualityStage` then validates the post-sanitisation count — if too few clean chunks
    remain it aborts with `evidence-insufficient-answer`. No new abort message is needed.
  - Every removed chunk is logged at `WARN` with `contentItemId`, `chunkIndex`, and `embeddingId`
    so admins can identify and remove the malicious document.
  - Semantic similarity (using the existing chunk embedding) was explicitly rejected for this stage:
    an injection phrase appended to 512 tokens of legitimate content contributes near-zero influence
    to the embedding direction, making cosine similarity to injection prototypes unreliable. Lexical
    scanning is exact and zero-cost.
  - No new configuration required.

- **Indexing Quality Check (Case 4 — corpus protection)** — detects anomalous documents at
  indexing time before bad embeddings pollute the corpus centroid and degrade retrieval.
  Uses Option B (centroid distance): mean cosine similarity of all chunk embeddings vs the
  corpus centroid. Score stored on `ContentItem.qualityScore` for admin visibility.
  - `ContentItem` entity — new nullable `qualityScore` (`Double`) field; `NULL` = not yet assessed.
  - Liquibase migration `202606260002__0.0.1__DKP-0009__add_quality_score_to_content_item.sql` —
    `ADD COLUMN QUALITY_SCORE DECIMAL(5,4)` on `product.CONTENT_ITEM` (safe ADD COLUMN, no downtime).
    `DECIMAL(5,4)` chosen over a boolean flag so the raw score is preserved — threshold can be
    changed in config without a re-assessment migration.
  - `QualityVerdict` record (`api/service/`) — `boolean lowQuality, float score`; factory
    methods `pass(score)`, `flag(score)`, `skipped()`. Score of `-1` is the cold-start sentinel.
  - `IndexingQualityService` interface (`api/service/`) — `assess(contentItemId, contentType)`.
    Lives in `api/` (not `ai-service/`) because it depends on `ContentEmbeddingRepository`
    and `ContentItemRepository`, which are JPA repositories available only in the `api/` JPA context.
  - `IndexingQualityServiceImpl` (`api/service/impl/`) — loads embeddings via
    `ContentEmbeddingRepository`; resolves type-specific centroid via `CorpusStatisticsService`;
    computes mean `dotProduct(chunkEmbedding, centroid)`; returns verdict.
    Graceful cold-start: returns `skipped()` when no centroid is cached.
  - `ContentIndexingServiceImpl` — new `assessAndRecordQuality()` called after every ingestion;
    persists `qualityScore` onto `ContentItem`; logs `WARN` when below threshold.
    Does NOT change `ContentItem.status` — quality is orthogonal to lifecycle state.
  - `EmbeddingProperties` — new `indexingCoherenceThreshold` field (default `0.35`).
  - `application.yml` — `indexing-coherence-threshold: ${INDEXING_COHERENCE_THRESHOLD:0.35}`.

- **Evidence Quality Guard (Case 3 — hallucination prevention)** — new `EvidenceQualityStage` runs
  between `MmrStage` and `MessageBuildingStage`. Evaluates the final MMR-selected chunks before
  an LLM call is made; aborts if either of two independent guards fails:
  - *Minimum chunk count* (`evidence-min-chunks`, default 2): fewer surviving chunks means the
    corpus coverage is too thin — the LLM would over-extrapolate from a single source.
  - *Mean similarity score* (`evidence-mean-threshold`, default 0.82): chunks that collectively
    hover near the absolute floor represent marginal evidence even though each passed individually.
  - Both values are env-var overridable (`EVIDENCE_MIN_CHUNKS`, `EVIDENCE_MEAN_THRESHOLD`).
  - Assessed post-MMR rather than post-scoring because MMR may select lower-scoring diverse chunks;
    the final evidence quality must be evaluated on what the LLM actually receives.
  - `EmbeddingProperties` — two new fields: `evidenceMinChunks` (2), `evidenceMeanThreshold` (0.82).
  - `RagPipelineRunner` — stage order updated: … → MMR → **evidence-quality** → build messages.

- **Retrieval Anomaly Detection (Case 2 — largest-gap pruning)** — new `RetrievalAnomalyStage` runs
  between `ScoringStage` and `MmrStage`. After the global similarity threshold filters clearly
  irrelevant chunks, this stage removes *relative* outliers — chunks that pass the floor but are
  statistically far below the rest of the result set for this specific query.
  - Algorithm: scan consecutive (score[i], score[i+1]) pairs on the sorted-descending chunk list;
    find the largest gap; if it exceeds `retrieval-outlier-gap-threshold` (default 0.15), discard
    everything below that gap.
  - Safety guard: never prunes to zero — `ScoringStage` owns the "no chunks" abort path.
  - Set `retrieval-outlier-gap-threshold: 0.0` to disable pruning entirely without code changes.
  - `EmbeddingProperties` — new `retrievalOutlierGapThreshold` field (default 0.15).
  - `application.yml` — `retrieval-outlier-gap-threshold: ${RETRIEVAL_OUTLIER_GAP_THRESHOLD:0.15}`.
  - `RagPipelineRunner` — stage order updated: … → score → **retrieval-anomaly** → MMR → …

- **Query Anomaly Detection (Case 1 — domain guard)** — new pipeline stage `QueryAnomalyStage` that
  runs immediately after `EmbeddingStage`. Computes cosine similarity between the query embedding
  and the L2-normalised corpus centroid to detect queries outside the platform's knowledge domain.
  - *Hard anomaly* (similarity < `anomaly-hard-threshold`, default 0.20): pipeline aborted with an
    out-of-scope user message — no retrieval or LLM call is made.
  - *Soft anomaly* (similarity in [0.20, `anomaly-soft-threshold`), default [0.20, 0.40)): pipeline
    continues but `effectiveSimilarityThreshold` is raised to `anomaly-soft-similarity-threshold`
    (default 0.82), requiring retrieved chunks to be a tighter match before entering LLM context.
  - Graceful degradation: if no centroid is cached (cold-start or empty corpus) the stage passes through.
  - `EmbeddingProperties` — three new fields: `anomalyHardThreshold` (0.20), `anomalySoftThreshold` (0.40),
    `anomalySoftSimilarityThreshold` (0.82); all overridable via env vars.
  - `RagPipelineContext` — new `effectiveSimilarityThreshold` field; set by `QueryAnomalyStage` for
    soft-anomaly requests; read by `ScoringStage` in place of the configured default.
  - `ScoringStage` — uses `ctx.getEffectiveSimilarityThreshold()` when non-null; falls back to
    `properties.getSimilarityThreshold()` for normal requests.
  - `RagPipelineRunner` — stage order updated: contextualize → embed → **anomaly-check** → retrieve → score → MMR → build messages.

- **Corpus Statistics** — persisted corpus centroid vectors for the anomaly detector.
  - `SYS_PARAM` table (Liquibase migration `202606260001__0.0.1__DKP-0008__add_sys_param.sql`):
    general-purpose key-value store in the `product` schema; surrogate PK + unique `NAME` constraint +
    full audit columns + sequence. Reusable beyond AI/centroid purposes.
  - `ParamKey` enum (`common/enums/`) — typed keys for `SYS_PARAM.NAME`. Current constants:
    `CENTROID_ALL`, `CENTROID_ARTICLE`, `CENTROID_INTERVIEW_QUESTION`, `CENTROID_BLOG_POST`,
    `ANOMALY_HARD_THRESHOLD`, `ANOMALY_SOFT_THRESHOLD`. Warning: renaming a constant is a breaking
    change requiring a DB migration.
  - `SysParam` entity (`common/entity/`) — `@Entity` backed by `SYS_PARAM`; fields: `name` (ParamKey),
    `value` (TEXT), `computedAt` (Instant).
  - `SysParamRepository` (`api/repository/`) — `JpaRepository<SysParam, Integer>` with `findByName(ParamKey)`.
    Placed in `api/` (not `ai-service/`) because `ai-service` has no JPA context.
  - `CorpusStatisticsService` interface (`ai-service/service/`) — defines `getCentroidFor(RagFilter)`
    and `refresh()`; lives in `ai-service` so pipeline stages can inject it without a circular dependency.
  - `CorpusStatisticsServiceImpl` (`api/service/impl/`) — `@PostConstruct` loads 4 centroid vectors from
    `SYS_PARAM`; `@Scheduled(centroid-refresh-interval, default PT6H)` recomputes via SQL `avg(embedding)`
    (one call per content type + global), persists via upsert, reloads cache.
    Cache fields are `volatile float[]` — single writer (scheduler), many readers (pipeline threads).
    Centroid vectors are L2-normalised (`VectorUtils.normalize()`) after loading for accurate cosine similarity.
  - `ContentEmbeddingRepository` — two new native `@Query` methods: `computeGlobalCentroid()` and
    `computeCentroidBySourceType(String sourceType)` (both return `avg(embedding)::text`).
  - `VectorUtils` — new `parseVector(String)` method (parses pgvector `[f1,f2,...,fn]` → `float[]`);
    new `normalize(float[])` method (L2-normalises a vector in-place copy).
  - `AiServiceConfig` — added `@EnableScheduling` to activate the centroid refresh scheduler.
  - `EmbeddingProperties` — new `centroidRefreshInterval` field (default `PT6H`; ISO-8601 duration string).
  - `IngestionApi` — new admin endpoint `POST /api/v1/admin/indexing/corpus/refresh` — triggers an
    immediate centroid recomputation without waiting for the next scheduled refresh.
  - `IngestionController` — implements `refreshCorpus()` → `corpusStatisticsService.refresh()` → 204.
  - `application.yml` — `centroid-refresh-interval: PT6H` + three anomaly threshold properties.

- **Conversation history summarisation** — rolling LLM-generated summary of older turns stored on `ChatSession.SUMMARY` (TEXT column, DKP-0007 migration). Prevents linear token growth in long sessions while preserving full context.
  - `ConversationContext` — Java 21 record in `common/dto/` carrying `summary` (nullable rolling summary) + `recentTurns` (last N verbatim Q&A pairs); factory `withoutSummary(turns)` for fresh sessions.
  - `ConversationSummarisationService` — new `ai-service` interface; implementation uses the blocking `ChatLanguageModel` with a configurable `summarisation-prompt`. On LLM failure the previous summary is returned unchanged (graceful degradation).
  - `ChatSessionServiceImpl` — injects `ConversationSummarisationService`; triggers summarisation in `addTurn()` at pair 8 and every 4 pairs thereafter; compresses all turns outside the last-5 verbatim window; clears `summary` on session expiry. Constants: `SUMMARY_THRESHOLD=8`, `SUMMARY_TRIGGER_INTERVAL=4`, `SUMMARY_RECENT_WINDOW=5`.
  - `ChatSessionService` — new `getConversationContext(sessionId, maxTurns)` method returning `ConversationContext`.
  - `RagQueryService` — `ConversationContext` replaces `List<ConversationTurn>` as the primary overload parameter; all existing `List<ConversationTurn>` overloads become backwards-compatible `default` delegates.
  - `RagQueryServiceImpl` — `buildMessages()` prepends summary as a synthetic User/Assistant exchange; `contextualizeQuestion()` includes summary in the rewrite prompt so pronoun resolution works for compressed older turns.
  - `EmbeddingProperties` — new `summarisationPrompt` field bound to `app.ai.embedding.summarisation-prompt`.
  - `ChatController` — calls `getConversationContext()` instead of `getRecentTurns()`.

- **Pipes-and-Filters RAG pipeline** — the RAG retrieval logic is extracted from `RagQueryServiceImpl`
  into seven independent, Spring-managed pipeline stages (new package `ai.pipeline`):
  `ContextualizationStage` → `EmbeddingStage` → `RetrievalStage` → `ScoringStage` →
  `DeduplicationStage` → `MmrStage` → `MessageBuildingStage`.
  Shared infrastructure: `RagPipelineContext` (mutable per-request carrier with abort support),
  `RagPipelineStage` (functional interface), `RagPipelineRunner` (ordered executor).
  `RagQueryServiceImpl` is reduced to: create context → run pipeline → call LLM.
  Each stage is now independently unit-testable and new stages (e.g. cross-encoder reranker,
  embedding cache) can be inserted by adding a `@Component` and updating the runner's stage list.

- **Context compaction — MMR** in the RAG pipeline:
  - *MMR re-ranking* (Carbonell & Goldstein, 1998): selects `topK` chunks by maximising
    `λ × sim(chunk, query) − (1−λ) × max sim(chunk, already_selected)`. Handles both
    cross-document and within-document diversity: redundant chunks from the same source are
    penalised by the similarity term; complementary chunks covering different sub-topics of
    the same document can still be selected if their MMR score beats any single chunk from
    other documents.
  - `EmbeddingProperties.mmrLambda` (default `0.5`) — controls the relevance/diversity trade-off;
    bound to `app.ai.embedding.mmr-lambda` / `RAG_MMR_LAMBDA` env var.
  - `RetrievalStage` always oversamples (`topK × oversampleFactor`) unconditionally — previously
    oversampled only when a filter was active; now covers the case where many top candidates come
    from the same document.
  - `DeduplicationStage` removed from the active pipeline (retained as a non-bean reference class).
    MMR's redundancy penalty replaces the hard one-chunk-per-document cap it enforced.

- **`ContentEmbeddingMetadata` DTO** — Java 21 record replacing the untyped `Map<String, Object>`
  previously stored in the `CONTENT_EMBEDDING.METADATA` JSONB column.
  Hibernate serialises/deserialises it via Jackson (`@JdbcTypeCode(SqlTypes.JSON)`).
  Fields: `type`, `status`, `title`, `categoryId`, `categoryName`, `tagIds`, `tagNames`,
  `difficulty` (interview questions only), `isCommon` (interview questions only).
  `@JsonInclude(NON_NULL)` suppresses absent fields from the stored JSON.

- **Domain-aware system prompts (Context Isolation)** — the single global system prompt is replaced by a
  switch-based selection in `MessageBuildingStage.resolvePromptPrefix()` that picks a domain-specific prompt
  when the active `RagFilter` targets exactly one content type, falling back to the default prompt for
  mixed or unfiltered queries.
  - `MessageBuildingStage` — new private `resolvePromptPrefix(RagFilter)` method; switch expression over
    `ContentType`; passes the resolved prefix into `buildSystemPrompt()` alongside the numbered context chunks.
  - `EmbeddingProperties` — three new `@NotBlank` fields: `systemPromptArticle`, `systemPromptInterviewQuestion`,
    `systemPromptBlogPost`; all bound to `app.ai.embedding.*` and populated in `application.yml`.
    - `systemPromptArticle` — promotes in-depth concept synthesis and practical examples.
    - `systemPromptInterviewQuestion` — structures output as Direct Answer → Interviewer intent → Common pitfalls.
    - `systemPromptBlogPost` — favours accessible narrative prose and lead-with-insight structure.

- **Dynamic RAG Filters** — post-retrieval filtering of vector-search candidates.
  - `ai-service` — `filter/` package: `RagFilter` Java 21 record carrying optional filter dimensions:
    `sourceTypes`, `tags`, `categoryId`.
  - `ScoringStage` — `buildPredicate(RagFilter)` composes AND predicates directly from the three filter
    dimensions (source type, tags, category); null/empty dimensions are skipped (pass-through).
  - `RagQueryService` — new three-argument primary overloads accepting `RagFilter`; existing two- and
    one-argument overloads delegate to `RagFilter.none()` for full backwards compatibility.
  - `EmbeddingProperties` — new `oversampleFactor` field (default 3), bound to `app.ai.embedding.oversample-factor`.

### Changed

- **`ContentIndexingServiceImpl`** — single `buildMetadata(contentItem, difficulty, isCommon)`
  method now constructs the full `ContentEmbeddingMetadata` (absorbing the former
  `buildTagMetadata()` and the `buildBaseMetadata()` that lived in `ContentIngestionServiceImpl`).
  Eliminates the previous two-step map-merge that duplicated `categoryId`/`categoryName` across
  both classes.
- **`ChatRequest`** — three optional filter fields added: `sourceTypes` (`Set<ContentType>`),
  `categoryId` (`Integer`), `tags` (`Set<String>`). Existing clients sending only `question` /
  `sessionId` are unaffected (fields default to `null` → `RagFilter.none()`).
- **`ChatController`** — constructs `RagFilter` from request fields and passes it to
  `RagQueryService` on both the blocking and streaming code paths.
- **`application.yml`** — added `app.ai.embedding.oversample-factor: ${RAG_OVERSAMPLE_FACTOR:3}`.
- **`ModelConfig`** — prefix renamed from `app.ai.model` to `app.ai.embedding-model` so the YAML
  path reads as "the embedding model's config" rather than the ambiguous "model config" (this
  class only ever held embedding settings — chat model settings live in `ChatModelsConfig`).
  Updated in `application.yml`, `application-local.yml`, `application-docker.yml`.
- **`SysParamRepository` moved from `api/repository/` to `common/repository/`**, and a new
  `SysParamService`/`SysParamServiceImpl` (`common/service/`) added on top of it — a thin,
  deliberately format-agnostic get/upsert wrapper (`getValue(ParamKey)`, `upsert(ParamKey, String)`)
  shared by both `ai-service` and `api`. `ai-service` cannot depend on `api` (where the repository
  used to live), so this was blocking a new `ai-service`-side `SYS_PARAM` consumer without an
  awkward interface-in-ai-service/impl-in-api split; moving the repository down to `common` (which
  both modules already depend on, and already ships `spring-boot-starter-data-jpa` for entity
  annotations) removed the need for that split entirely. `CorpusStatisticsServiceImpl` (`api`)
  refactored to use `SysParamService` instead of the repository directly, dropping its own
  hand-rolled find-or-create-and-save upsert logic.
- **`PromptGuardStage` prototype-embedding cache** — the semantic injection-detection layer used
  to call `EmbeddingService.embedBatch()` on every application startup, even though
  `injection-detection.prototypes` rarely changes. Prototype embeddings are now cached in
  `SYS_PARAM` under the new `ParamKey.PROMPT_INJECTION_PROTOTYPE_EMBEDDINGS`, keyed by a SHA-256
  fingerprint of the embedding model id + prototype list. On startup, a fingerprint match skips
  the embedding API call entirely; a mismatch (prototype list edited, or embedding model changed)
  or missing cache falls through to embedding and persisting as before. No `@Scheduled` refresh —
  unlike corpus centroids, prototypes only change via a config edit + redeploy, so a startup-time
  check is sufficient.

### Fixed

- **`PipelineMetrics` startup crash** — `evidenceMeanScore`, `effectiveSimThreshold`,
  `answerContextSim`, `answerQuerySim` were declared as `Float` with `@Column(precision = 5,
  scale = 4)`. Hibernate maps `Float` to the SQL floating-point type (`FLOAT`/`REAL`), and
  `scale` only has meaning on `NUMERIC`/`DECIMAL` types — this threw
  `IllegalArgumentException: scale has no meaning for SQL floating point types` while building
  the `entityManagerFactory` bean, failing application startup entirely. The underlying columns
  were always `DECIMAL(5, 4)` (see `202606290001__...__create_pipeline_metrics_table.sql`), so
  the entity's Java type was the actual mismatch, not the DDL. Changed all four fields to
  `BigDecimal` to match the column type; `PipelineCompletedEventListener` now converts the raw
  `float` similarity values via a new `toScaledDecimal(...)` helper (`setScale(4,
  RoundingMode.HALF_UP)`) before assigning them, consistent with the existing
  `computeEstimatedCost(...)` → `setScale(8, ...)` pattern for `estimatedCostUsd`.

### Removed

- **`Task` → `content-service`'s `ContentItem` link.** Dropped the optional `@ManyToOne
  Task.contentItem` field (and its `CONTENT_ITEM_ID` column/FK/index — initially removed by editing
  `DKP-0020` in place, on the mistaken assumption that changeset hadn't shipped anywhere yet; it
  turned out to have already executed against a real dev DB, which surfaced as a Liquibase
  checksum-mismatch failure, so `DKP-0020` was reverted to exactly the version that ran and the
  column drop moved to its own `DKP-0022` changeset instead) — no client ever set this field (the
  GUI's Tasks feature never grew a content-item picker), so it was pure unused surface area.
  Removed alongside it: `TaskErrorCode.TASK_CONTENT_ITEM_NOT_FOUND`,
  `TaskCommands.{Create,Update}.contentItemId`, `TaskResponse.contentItemId`,
  `{Create,Update}TaskRequest.contentItemId`, `TaskMapper`'s `contentItemId` mapping,
  `TaskServiceImpl.resolveContentItemOrNull`/its `ContentItemRepository` dependency, and
  `task-service`'s `pom.xml` dependency on `content-service` (now `common`+`infra` only, a parallel
  sibling of `content-service`/`identity-service` rather than a downstream dependent, mirroring the
  removal's ripple into the root/`task-service` `CLAUDE.md` and `docs/PROJECT_STRUCTURE.md`
  dependency-order notes). If a real need for a task↔content link resurfaces, re-add the dependency
  deliberately rather than assuming this note is stale.

### Added

- **`task-service`: subtasks.** A `Task` can now have subtasks, capped at one level deep (a
  subtask cannot itself have subtasks). Mirrors `content-service`'s `Category` self-referential
  parent/child tree shape (`Task.parentTask` `@ManyToOne` self-FK + `subtasks` `@OneToMany`), not a
  new entity or a formal GoF Component/Leaf class hierarchy — a subtask is just a `Task` with
  `parentTask` set, so every existing endpoint (`GET/PUT /{id}`, `POST /{id}/status`,
  `DELETE /{id}`) works on it unchanged.
  - New `PARENT_TASK_ID` column/self-FK/index in a new `DKP-0021` changeset (initially added
    in-place to `DKP-0020`, then split out once it turned out `DKP-0020` had already executed
    against a real dev DB — editing an already-`EXECUTED` changeset causes a Liquibase
    checksum-mismatch failure on the next `update`; see `task-service/CLAUDE.md`'s Liquibase rule)
    and `Task.parentTask`/`subtasks` (the latter `CascadeType.ALL, orphanRemoval = true`, same
    shape as `ContentItem`→`ContentItemTag`).
  - New `TaskErrorCode.TASK_INVALID_PARENT`, thrown by `TaskServiceImpl.validateParentAssignment`
    (mirrors `CategoryServiceImpl.validateParentAssignment`, adapted for a one-level cap instead of
    full-tree cycle detection) for: self-parent, a parent that is itself a subtask, or assigning a
    parent to a task that already has subtasks of its own.
  - Three deliberate divergences from `Category`'s tree, each documented in `task-service/CLAUDE.md`
    so they don't read as oversights later: deleting a task **cascades** to its subtasks (`Category`
    blocks delete via `CATEGORY_HAS_CHILDREN` instead); a parent's `status` never reacts to its
    subtasks' statuses (stays fully manual, consistent with `TaskStatus.canTransitionTo`'s
    already-permissive design); and `GET /api/v1/tasks` always excludes subtasks
    (`TaskSpecification.withFilters` unconditionally adds a `parentTask IS NULL` predicate, the same
    way `ownerId` already is) — subtasks are only reachable via the new
    `GET /api/v1/tasks/{id}/subtasks` (`TaskService.listSubtasks`, deliberately unpaginated).
  - `TaskCommands.{Create,Update}`, `{Create,Update}TaskRequest`, and `TaskResponse` all gained a
    flat `parentTaskId`, same convention as `projectId`.

### Fixed

- **Reactor-wide component-scan gap: none of the six standalone services actually reached `infra`'s
  bean definitions.** Found while verifying that moving `gateway`'s `JacksonConfig` into `infra`
  (see the `Changed` entry below) would actually work reactor-wide. Spring Boot's default
  `@ComponentScan` is rooted at the `@SpringBootApplication`-annotated class's own package and does
  not recurse into a sibling package — `infra` is a sibling of every standalone service's own
  package, not a parent, so only `gateway` (whose main class happens to sit at this reactor's root
  package, with `infra` as a true child) ever picked it up for free. This had been silently breaking
  real, already-shipping code, never caught before now because none of these six apps had ever
  actually been booted against a real environment in this codebase's history:
  - `identity-service`'s `UserController`/`UserMapper` and `social-service`'s `FriendMapper`/
    `MessagingMapper` inject `infra.service.StorageService` (avatar/attachment presigned URLs) —
    would have failed at Spring context startup with an unsatisfied-dependency error.
  - `ecommerce-service`'s `ProductServiceImpl`/`ProductCategoryServiceImpl` and `content-service`'s
    `CategorySeeder`/`TagSeeder` inject/extend `infra.service.SlugService`/`infra.service.seed.CsvSeeder`
    — same failure mode.
  - `social-service`'s `FriendRequestSentEventListener`/`FriendRequestAcceptedEventListener` and
    `ai-service`'s `PipelineCompletedEventListener` extend `infra`'s `AsyncEventHandler`, which
    dispatches through `infra`'s own `AsyncEventThreadPoolConfig` bean — same failure mode.
  - `social-service` had a second, quieter bug on top: it was missing `@EnableAsync` entirely, which
    doesn't error the way a missing bean does — Spring just silently ignores `@Async` and runs the
    method synchronously instead. Its two `FriendRequest*EventListener`s had been dispatching on the
    calling thread instead of the dedicated `asyncEventExecutor` pool the whole time, with no crash
    to reveal it.

  Fixed by adding an explicit `@ComponentScan(basePackages = {<own-package>,
  "com.ttg.devknowledgeplatform.infra"})` to all six services' `@SpringBootApplication` classes
  (`EcommerceServiceApplication`, `IdentityServiceApplication`, `TaskServiceApplication`,
  `SocialServiceApplication`, `ContentServiceApplication`, `AiServiceApplication`) — an explicit
  `@ComponentScan` replaces rather than adds to the implicit single-package default, so each
  service's own package had to be listed alongside `infra`'s — plus `@EnableAsync` on
  `SocialServiceApplication`. `task-service` genuinely uses no `infra` bean today (confirmed via a
  reactor-wide grep), so its fix is currently a no-op, added purely for consistency and to prevent
  this exact gap from resurfacing silently the moment it does add one. See each service's own
  `CLAUDE.md`/entry-point Javadoc and `infra/CLAUDE.md`'s `JacksonConfig` note for the full detail.
  Verified via a clean full-reactor `./mvnw clean package`; a live boot-test of a previously-broken
  service was not possible in this environment (no local Docker/Postgres access), so this remains
  unverified at runtime the same way every standalone service's own schema has been at first landing
  — see each service's own `CLAUDE.md` for that recurring caveat.

### Changed

- **Moved `gateway`'s `JacksonConfig` (shared `ObjectMapper` customization — `JavaTimeModule`,
  tolerant deserialization, ISO-8601 dates instead of epoch-millis) into `infra`'s new
  `config/json/` package.** Before the component-scan fix above, this bean only ever applied to
  `gateway`'s own JSON serialization — which has no practical effect, since `gateway` has zero REST
  controllers of its own — while every one of the six standalone services silently fell back to
  Spring Boot's un-customized default `ObjectMapper` instead of ever having a copy of their own.
  Living in `infra` now means all seven apps in this reactor pick up the same customization
  automatically, with no per-service duplicate needed. `gateway` has no `config/` package left at
  all as a result — `JacksonConfig` was the last class in it.

---

## [0.0.1] — Initial release — the original monolith

### Added

- Multi-module Maven project: `common`, `ai-service`, `api`, `gui`.
- RAG pipeline: embedding (OpenAI text-embedding-3-small), pgvector HNSW cosine search, LLM generation (gpt-4o-mini via LangChain4j).
- SSE streaming chat endpoint with source citations.
- Question contextualisation for multi-turn conversations.
- Rate limiting: 10 req/min, 100 req/hour per user (Bucket4j + Redis).
- Content indexing pipeline: `ContentPublishedEvent` → `ContentIndexingService` → `TextChunkingService` → `EmbeddingService`.
- JWT + OAuth2 (Google) security.
- Liquibase migrations for all tables in `product` schema.
- React 18 + TypeScript + MUI frontend (Vite).
