package com.ttg.devknowledgeplatform.ecommerce.service;

/**
 * Plain input records for {@link SavedAddressService}, mirroring {@code api}'s
 * {@code CreateSavedAddressRequest}/{@code UpdateSavedAddressRequest} field-for-field but without
 * any REST/validation concerns — same pattern as {@link ProductCommands}/{@link CheckoutCommands}.
 */
public final class SavedAddressCommands {

    private SavedAddressCommands() {}

    /**
     * @param makeDefault explicitly request this new address become the caller's default: the
     *                    service also auto-defaults a caller's very first address regardless of
     *                    this flag (see {@code SavedAddressServiceImpl#create}) — this only
     *                    matters from the caller's *second* address onward.
     */
    public record Create(
            String label, String fullName, String phone, String email, String line1, String line2, String city,
            String state, String postalCode, String country, boolean makeDefault) {
    }

    public record Update(
            String label, String fullName, String phone, String email, String line1, String line2, String city,
            String state, String postalCode, String country) {
    }
}
