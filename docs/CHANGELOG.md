# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

`0.0.1` is the original monolith; `0.0.2` is when the break-up into standalone microservices
began (the full six-service extraction — `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service` — plus `gateway`'s routing/CORS consolidation and
the reactor-wide `@ComponentScan` fix). Full unabridged entry-by-entry history for both lives in
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) — split out once this file grew past ~3700 lines
under a single `[Unreleased]` section that had never actually been cut into a real release. New
entries start fresh below `[Unreleased]`.

---

## [Unreleased]

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

## [0.0.2] — 2026-08-11

Retroactive cut of everything that had accumulated under `[Unreleased]` up to this point — the
start of the microservices break-up. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the
complete, unabridged entry-by-entry history.

---

## [0.0.1] — Initial release

The original monolith. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the full entry.
