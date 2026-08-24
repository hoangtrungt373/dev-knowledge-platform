package com.ttg.devknowledgeplatform.ecommerce.service;

/**
 * Plain input records for {@link CheckoutService}, mirroring {@code api}'s {@code AddressRequest}
 * field-for-field but without any REST/validation concerns — same pattern as
 * {@code ProductCommands}.
 */
public final class CheckoutCommands {

    private CheckoutCommands() {}

    public record AddressInput(
            String fullName, String line1, String line2, String city, String state, String postalCode, String country) {
    }
}
