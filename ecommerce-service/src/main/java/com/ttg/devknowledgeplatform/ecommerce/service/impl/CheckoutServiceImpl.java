package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderStatusHistory;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;
import com.ttg.devknowledgeplatform.ecommerce.service.CartService;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutPreview;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutResult;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutService;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponRedemptionService;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressService;
import com.ttg.devknowledgeplatform.ecommerce.shipping.ShippingFeeCalculator;
import com.ttg.devknowledgeplatform.ecommerce.shipping.ShippingFeeQuote;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link CheckoutService}.
 *
 * <p>The shipping fee itself is computed by the injected {@link ShippingFeeCalculator} — a GoF
 * Strategy seam (see that interface's own Javadoc) — rather than a field on this class; this class
 * no longer knows or cares how the fee was priced, only that it needs one.
 *
 * <p><strong>Coupon feature, Phase 2</strong>: {@link #resolveDiscounts} resolves both coupon
 * slots (see {@link CouponRedemptionService}'s own Javadoc for why {@code resolve}/
 * {@code calculateDiscount} are separate steps) into the actual discount each produces —
 * {@code subtotalDiscount} is reported and total-adjusted separately (never touching
 * {@link Order#getSubtotal()} itself), while a shipping-targeting coupon reduces
 * {@code shippingFee} directly, on top of whatever {@link ShippingFeeCalculator} already waived.
 * {@link #confirm} records a {@code CouponRedemption} for each coupon actually applied,
 * immediately after the order itself is saved — never during {@link #preview}.
 *
 * <p>{@link #confirm} implements US-3.1 (Epic 3): every line's stock is reserved via
 * {@link ProductVariantRepository#reserve} in this same {@code @Transactional} method, before the
 * order is ever saved — an insufficient-stock line throws, and since nothing has committed yet,
 * Postgres rolls back both the new {@link Order} row and every reservation already claimed by an
 * earlier line in the same loop. This is what makes "create the order and reserve stock
 * atomically" true: a single local ACID transaction, not a saga (see
 * {@code docs/user-stories/03-order-lifecycle-inventory.md}'s locked decisions — the saga only
 * starts at Epic 4's payment-gateway call).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class CheckoutServiceImpl implements CheckoutService {

    private final CartService cartService;
    private final OrderRepository orderRepository;
    private final ProductVariantRepository productVariantRepository;
    private final SavedAddressService savedAddressService;
    private final ShippingFeeCalculator shippingFeeCalculator;
    private final CouponRedemptionService couponRedemptionService;

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(
            String userUuid, List<Integer> selectedVariantIds, String subtotalCouponCode, String shippingCouponCode) {
        Cart cart = cartService.getCart(userUuid);
        List<CartLine> candidateLines = filterBySelection(cart.lines(), selectedVariantIds);
        List<CartLine> availableLines = requireCheckoutableCart(candidateLines);
        BigDecimal subtotal = computeSubtotal(availableLines);
        ShippingFeeQuote shippingQuote = shippingFeeCalculator.calculate(availableLines, subtotal);
        Discounts discounts = resolveDiscounts(userUuid, subtotal, shippingQuote.fee(), subtotalCouponCode, shippingCouponCode);
        BigDecimal total = subtotal.subtract(discounts.subtotalDiscount()).add(discounts.finalShippingFee());
        return new CheckoutPreview(
                candidateLines, subtotal, discounts.subtotalDiscount(),
                discounts.finalShippingFee(), shippingQuote.originalFee(), total);
    }

    @Override
    public CheckoutResult confirm(
            String userUuid, CheckoutCommands.AddressSelection addressSelection, List<Integer> selectedVariantIds,
            String subtotalCouponCode, String shippingCouponCode) {
        Cart cart = cartService.getCart(userUuid);
        List<CartLine> candidateLines = filterBySelection(cart.lines(), selectedVariantIds);
        List<CartLine> availableLines = requireCheckoutableCart(candidateLines);
        List<CartLine> droppedLines = candidateLines.stream().filter(line -> !line.available()).toList();

        BigDecimal subtotal = computeSubtotal(availableLines);
        ShippingFeeQuote shippingQuote = shippingFeeCalculator.calculate(availableLines, subtotal);
        // Re-resolved fresh here, never trusting a client-cached preview — same "confirm
        // re-validates" philosophy this method already applies to cart lines/stock/address.
        Discounts discounts = resolveDiscounts(userUuid, subtotal, shippingQuote.fee(), subtotalCouponCode, shippingCouponCode);
        BigDecimal total = subtotal.subtract(discounts.subtotalDiscount()).add(discounts.finalShippingFee());
        Address shippingAddress = resolveAddress(userUuid, addressSelection);

        // US-3.1: reserve every line's stock before the order itself is ever persisted — an
        // insufficient-stock line throws here, and the whole transaction (including any
        // reservation already claimed by an earlier line in this loop) rolls back with it.
        for (CartLine line : availableLines) {
            reserveStock(line);
        }

        Order order = new Order();
        order.setOwnerUuid(userUuid);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(shippingAddress);
        order.setSubtotal(subtotal);
        order.setSubtotalDiscountAmount(discounts.subtotalDiscount());
        order.setSubtotalCouponCode(discounts.subtotalCoupon() != null ? discounts.subtotalCoupon().getCode() : null);
        order.setShippingFee(discounts.finalShippingFee());
        order.setOriginalShippingFee(shippingQuote.originalFee());
        order.setShippingCouponCode(discounts.shippingCoupon() != null ? discounts.shippingCoupon().getCode() : null);
        order.setTotal(total);
        for (CartLine line : availableLines) {
            order.getLines().add(toOrderLine(order, line));
        }
        order.getStatusHistory().add(toInitialStatusHistory(order));

        Order saved = orderRepository.save(order);
        // Only recorded once the order is durably saved — a redemption must never be counted
        // against a coupon's limits for an order that (due to some later failure in this same
        // method) never actually happens; nothing after this point can fail without rolling back
        // the whole transaction (including this save) anyway, but the ordering itself documents
        // the intent, same reasoning US-3.1's stock reservation already established for this method.
        if (discounts.subtotalCoupon() != null) {
            couponRedemptionService.redeem(discounts.subtotalCoupon(), saved, userUuid, discounts.subtotalDiscount());
        }
        if (discounts.shippingCoupon() != null) {
            couponRedemptionService.redeem(discounts.shippingCoupon(), saved, userUuid, discounts.shippingDiscount());
        }
        // Only the lines actually ordered leave the cart — never a whole-cart clear() anymore, so
        // anything excluded by selectedVariantIds (or dropped by this final revalidation) stays in
        // the cart untouched, only after the order is durably saved (US-2.6).
        cartService.removeItems(userUuid, availableLines.stream().map(CartLine::variantId).toList());
        maybeSaveAddressForFuture(userUuid, addressSelection);
        log.info("Created order id={} for userUuid={} lineCount={} droppedLineCount={}",
                saved.getId(), userUuid, availableLines.size(), droppedLines.size());
        return new CheckoutResult(saved, droppedLines);
    }

    /**
     * Resolves both coupon slots (at most one {@link CouponTarget#SUBTOTAL}, one
     * {@link CouponTarget#SHIPPING_FEE} — see this class's own Javadoc) into the actual discount
     * each produces, and the shipping fee left after the {@code SHIPPING_FEE} one (if any) further
     * reduces {@code shippingFee} on top of whatever {@code shippingFeeCalculator} already waived.
     * A blank/{@code null} code for a slot means "no coupon there" and resolves to a zero discount
     * with no {@link Coupon} — shared by both {@link #preview} and {@link #confirm} so the two
     * apply identical validation.
     */
    private Discounts resolveDiscounts(
            String userUuid, BigDecimal subtotal, BigDecimal shippingFee,
            String subtotalCouponCode, String shippingCouponCode) {
        Coupon subtotalCoupon = null;
        BigDecimal subtotalDiscount = BigDecimal.ZERO;
        if (isNotBlank(subtotalCouponCode)) {
            subtotalCoupon = couponRedemptionService.resolve(subtotalCouponCode, CouponTarget.SUBTOTAL, userUuid, subtotal);
            subtotalDiscount = couponRedemptionService.calculateDiscount(subtotalCoupon, subtotal);
        }

        Coupon shippingCoupon = null;
        BigDecimal shippingDiscount = BigDecimal.ZERO;
        if (isNotBlank(shippingCouponCode)) {
            shippingCoupon = couponRedemptionService.resolve(shippingCouponCode, CouponTarget.SHIPPING_FEE, userUuid, subtotal);
            shippingDiscount = couponRedemptionService.calculateDiscount(shippingCoupon, shippingFee);
        }

        BigDecimal finalShippingFee = shippingFee.subtract(shippingDiscount);
        return new Discounts(subtotalCoupon, subtotalDiscount, shippingCoupon, shippingDiscount, finalShippingFee);
    }

    /** The resolved outcome of both coupon slots for one {@link #preview}/{@link #confirm} call —
     * {@code subtotalCoupon}/{@code shippingCoupon} are {@code null} when that slot had no code. */
    private record Discounts(
            Coupon subtotalCoupon, BigDecimal subtotalDiscount, Coupon shippingCoupon, BigDecimal shippingDiscount,
            BigDecimal finalShippingFee) {
    }

    /**
     * Resolves the actual shipping {@link Address} to snapshot onto the order — either copied
     * from an existing AddressBook entry ({@code savedAddressId}, ownership re-checked via
     * {@link SavedAddressService#getOwned}) or from a fresh, one-off {@code adHocAddress}. The
     * latter can no longer be enforced complete via {@code @NotBlank} (see
     * {@code CheckoutCommands.AddressSelection}'s own Javadoc for why), so every required field is
     * checked here instead, imperatively — same idiom every other cross-field business rule in
     * this reactor's service layer already uses ({@code Validator}, not Bean Validation).
     */
    private Address resolveAddress(String userUuid, CheckoutCommands.AddressSelection selection) {
        if (selection.savedAddressId() != null) {
            return toAddress(savedAddressService.getOwned(selection.savedAddressId(), userUuid));
        }
        CheckoutCommands.AddressInput input = selection.adHocAddress();
        boolean complete = input != null
                && isNotBlank(input.fullName()) && isNotBlank(input.phone()) && isNotBlank(input.email())
                && isNotBlank(input.line1()) && isNotBlank(input.city()) && isNotBlank(input.state())
                && isNotBlank(input.postalCode()) && isNotBlank(input.country());
        Validator.isTrue(complete, EcommerceErrorCode.CHECKOUT_ADDRESS_REQUIRED);
        return toAddress(input);
    }

    /**
     * Persists {@code adHocAddress} into the caller's AddressBook when {@code saveAddress} was
     * requested — a no-op whenever an existing {@code savedAddressId} was used instead (nothing
     * new to save). Deliberately best-effort: a failure here (unexpected, since nothing about this
     * write depends on anything the checkout itself validated) is logged and swallowed rather than
     * rethrown, so it can never roll back an order that has already reserved real stock — the
     * order succeeding matters far more than this convenience side effect.
     */
    private void maybeSaveAddressForFuture(String userUuid, CheckoutCommands.AddressSelection selection) {
        if (selection.savedAddressId() != null || !selection.saveAddress()) {
            return;
        }
        try {
            CheckoutCommands.AddressInput input = selection.adHocAddress();
            savedAddressService.create(userUuid, new SavedAddressCommands.Create(
                    selection.label(), input.fullName(), input.phone(), input.email(), input.line1(), input.line2(),
                    input.city(), input.state(), input.postalCode(), input.country(), false));
        } catch (Exception e) {
            log.warn("Could not save address to AddressBook for userUuid={} after checkout: {}", userUuid, e.getMessage());
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Rejects an empty candidate list (US-2.6) and one with no currently-available line (every
     * line failed US-2.7's revalidation) — shared by both {@link #preview} and {@link #confirm} so
     * the two calls apply identical guards. {@code candidateLines} is already narrowed to
     * {@code selectedVariantIds} (if any) by {@link #filterBySelection} — reusing
     * {@code CHECKOUT_CART_EMPTY} for "the selection matched nothing currently in the cart" rather
     * than adding a distinct error code for that edge case, since the shopper-facing meaning is the
     * same either way: nothing to check out.
     */
    private List<CartLine> requireCheckoutableCart(List<CartLine> candidateLines) {
        Validator.isFalse(candidateLines.isEmpty(), EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        List<CartLine> availableLines = candidateLines.stream().filter(CartLine::available).toList();
        Validator.isFalse(availableLines.isEmpty(), EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);
        return availableLines;
    }

    /**
     * Narrows {@code lines} down to just the ids in {@code selectedVariantIds}, or returns
     * {@code lines} unchanged when {@code selectedVariantIds} is {@code null} — the single seam
     * both {@link #preview} and {@link #confirm} filter through, so "no selection" and "every line
     * happens to be selected" both flow through the exact same downstream logic as the original
     * whole-cart behavior.
     */
    private static List<CartLine> filterBySelection(List<CartLine> lines, List<Integer> selectedVariantIds) {
        if (selectedVariantIds == null) {
            return lines;
        }
        Set<Integer> selected = new HashSet<>(selectedVariantIds);
        return lines.stream().filter(line -> selected.contains(line.variantId())).toList();
    }

    /**
     * Atomically claims {@code line}'s quantity against its variant's available stock (US-3.1) —
     * see {@link ProductVariantRepository#reserve} for why this is a single conditional
     * {@code UPDATE} rather than a read-then-write. Throws
     * {@link EcommerceErrorCode#ORDER_INSUFFICIENT_STOCK} on a lost race (someone else reserved the
     * remaining stock between this request's cart revalidation and this exact statement), rolling
     * back the whole {@code confirm} transaction per this class's Javadoc.
     */
    private void reserveStock(CartLine line) {
        int reserved = productVariantRepository.reserve(line.variantId(), line.quantity());
        Validator.isTrue(reserved == 1, EcommerceErrorCode.ORDER_INSUFFICIENT_STOCK, line.variant().getSku());
    }

    /**
     * The first {@link OrderStatusHistory} row for a newly-created order (US-3.5) — {@code null}
     * {@code fromStatus}, since order creation has no "from" state.
     */
    private static OrderStatusHistory toInitialStatusHistory(Order order) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setToStatus(OrderStatus.PENDING);
        return history;
    }

    private static BigDecimal computeSubtotal(List<CartLine> availableLines) {
        return availableLines.stream()
                .map(line -> line.variant().getPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static OrderLine toOrderLine(Order order, CartLine line) {
        OrderLine orderLine = new OrderLine();
        orderLine.setOrder(order);
        orderLine.setProductVariantId(line.variantId());
        orderLine.setSku(line.variant().getSku());
        orderLine.setProductName(line.variant().getProduct().getName());
        orderLine.setUnitPrice(line.variant().getPrice());
        orderLine.setQuantity(line.quantity());
        return orderLine;
    }

    private static Address toAddress(CheckoutCommands.AddressInput input) {
        return new Address(
                input.fullName(), input.phone(), input.email(), input.line1(), input.line2(), input.city(),
                input.state(), input.postalCode(), input.country());
    }

    private static Address toAddress(SavedAddress saved) {
        return new Address(
                saved.getFullName(), saved.getPhone(), saved.getEmail(), saved.getLine1(), saved.getLine2(),
                saved.getCity(), saved.getState(), saved.getPostalCode(), saved.getCountry());
    }
}
