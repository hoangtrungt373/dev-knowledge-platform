package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Data;

import java.util.List;

/**
 * Request payload for checkout confirm (US-2.5's shipping address, plus an optional selection).
 *
 * <p><strong>Two ways to supply the shipping address (AddressBook follow-up)</strong>: either
 * {@code savedAddressId} (an address already in the caller's AddressBook — every field below is
 * ignored when this is present) or the full address fields below, entered fresh. Neither is
 * {@code @NotBlank} anymore — this class can no longer enforce "one or the other" declaratively,
 * so {@code CheckoutServiceImpl} validates the actual choice imperatively (via {@code Validator}),
 * same as every other cross-field business rule in this reactor's service layer.
 */
@Data
public class AddressRequest {

    /** An address already in the caller's AddressBook — when present, every field below is
     * ignored (ownership is re-checked server-side; a savedAddressId belonging to someone else
     * behaves exactly like a nonexistent one, same as {@code SavedAddressApi}'s own convention). */
    private Integer savedAddressId;

    private String fullName;

    private String line1;

    private String line2;

    private String city;

    private String state;

    private String postalCode;

    private String country;

    /** Only meaningful when {@code savedAddressId} is {@code null} (a fresh, one-off address) —
     * also saves this address into the caller's AddressBook once the order is created. Best-effort:
     * a failure here never blocks placing the order (see {@code CheckoutServiceImpl}). */
    private boolean saveAddress;

    /** Optional label for the address saved via {@code saveAddress} above (e.g. "Home"). Ignored
     * when {@code saveAddress} is false or {@code savedAddressId} is present. */
    private String addressLabel;

    /**
     * Optional subset of the cart's variant ids to check out — a bolt-on field alongside the
     * address fields above rather than its own request DTO, same pragmatic-extension precedent as
     * {@code CartLineResponse.availableQuantity}. Omitted (or {@code null}) checks out the whole
     * cart, exactly this endpoint's behavior before this field existed.
     */
    private List<Integer> selectedVariantIds;
}
