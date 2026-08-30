package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.Address;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderStatusHistory;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of {@link CheckoutService}.
 *
 * <p>{@link #flatShippingFee} is externalized via {@code app.ecommerce.checkout.flat-shipping-fee}
 * rather than a hardcoded constant, same convention {@code CartServiceImpl}'s own
 * {@code app.ecommerce.cart.ttl} already established for a tunable business value.
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

    @Value("${app.ecommerce.checkout.flat-shipping-fee:5.00}")
    private BigDecimal flatShippingFee;

    @Override
    @Transactional(readOnly = true)
    public CheckoutPreview preview(String userUuid) {
        Cart cart = cartService.getCart(userUuid);
        List<CartLine> availableLines = requireCheckoutableCart(cart);
        BigDecimal subtotal = computeSubtotal(availableLines);
        return new CheckoutPreview(cart.lines(), subtotal, flatShippingFee, subtotal.add(flatShippingFee));
    }

    @Override
    public CheckoutResult confirm(String userUuid, CheckoutCommands.AddressInput address) {
        Cart cart = cartService.getCart(userUuid);
        List<CartLine> availableLines = requireCheckoutableCart(cart);
        List<CartLine> droppedLines = cart.lines().stream().filter(line -> !line.available()).toList();

        BigDecimal subtotal = computeSubtotal(availableLines);
        BigDecimal total = subtotal.add(flatShippingFee);

        // US-3.1: reserve every line's stock before the order itself is ever persisted — an
        // insufficient-stock line throws here, and the whole transaction (including any
        // reservation already claimed by an earlier line in this loop) rolls back with it.
        for (CartLine line : availableLines) {
            reserveStock(line);
        }

        Order order = new Order();
        order.setOwnerUuid(userUuid);
        order.setStatus(OrderStatus.PENDING);
        order.setShippingAddress(toAddress(address));
        order.setSubtotal(subtotal);
        order.setShippingFee(flatShippingFee);
        order.setTotal(total);
        for (CartLine line : availableLines) {
            order.getLines().add(toOrderLine(order, line));
        }
        order.getStatusHistory().add(toInitialStatusHistory(order));

        Order saved = orderRepository.save(order);
        // Only after the order is durably saved — never before (US-2.6).
        cartService.clear(userUuid);
        log.info("Created order id={} for userUuid={} lineCount={} droppedLineCount={}",
                saved.getId(), userUuid, availableLines.size(), droppedLines.size());
        return new CheckoutResult(saved, droppedLines);
    }

    /**
     * Rejects an empty cart (US-2.6) and a cart with no currently-available line (every line
     * failed US-2.7's revalidation) — shared by both {@link #preview} and {@link #confirm} so the
     * two calls apply identical guards.
     */
    private List<CartLine> requireCheckoutableCart(Cart cart) {
        Validator.isFalse(cart.lines().isEmpty(), EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        List<CartLine> availableLines = cart.lines().stream().filter(CartLine::available).toList();
        Validator.isFalse(availableLines.isEmpty(), EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);
        return availableLines;
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
                input.fullName(), input.line1(), input.line2(), input.city(), input.state(), input.postalCode(), input.country());
    }
}
