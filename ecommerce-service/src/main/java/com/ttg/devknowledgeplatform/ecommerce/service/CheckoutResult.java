package com.ttg.devknowledgeplatform.ecommerce.service;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;

import java.util.List;

/**
 * The outcome of a successful {@link CheckoutService#confirm}. {@code droppedLines} is normally
 * empty — the shopper already saw and dropped any unavailable lines via {@link CheckoutPreview} —
 * but confirm re-validates the cart fresh rather than trusting that preview, so this still reports
 * anything that changed in the (short) window between the two calls (e.g. a variant deactivated by
 * an admin moments before the shopper clicked "place order").
 */
public record CheckoutResult(Order order, List<CartLine> droppedLines) {
}
