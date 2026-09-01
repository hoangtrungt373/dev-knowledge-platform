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

    /**
     * The two ways a shopper can supply {@link #confirm}'s shipping address (AddressBook
     * follow-up) — exactly one of {@code savedAddressId}/{@code adHocAddress} is meaningful at a
     * time: a non-null {@code savedAddressId} means "use this AddressBook entry," and
     * {@code adHocAddress} is ignored entirely in that case; a null {@code savedAddressId} means
     * "use {@code adHocAddress}," which {@code CheckoutServiceImpl} then validates is actually
     * present and complete (can't be a compile-time {@code @NotBlank} anymore now that the field is
     * conditionally required — see {@code AddressRequest}'s own Javadoc).
     *
     * @param saveAddress only consulted when {@code savedAddressId} is {@code null} — persists
     *                    {@code adHocAddress} into the caller's AddressBook once the order is
     *                    created, best-effort (never blocks the order itself on failure)
     * @param label       optional label for the address saved via {@code saveAddress}
     */
    public record AddressSelection(Integer savedAddressId, AddressInput adHocAddress, boolean saveAddress, String label) {
    }
}
