package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

import java.util.List;

/** Request payload for checkout confirm (US-2.5's shipping address, plus an optional selection). */
@Data
public class AddressRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Address line 1 is required")
    private String line1;

    private String line2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @NotBlank(message = "Country is required")
    private String country;

    /**
     * Optional subset of the cart's variant ids to check out — a bolt-on field alongside the
     * address fields above rather than its own request DTO, same pragmatic-extension precedent as
     * {@code CartLineResponse.availableQuantity}. Omitted (or {@code null}) checks out the whole
     * cart, exactly this endpoint's behavior before this field existed.
     */
    private List<Integer> selectedVariantIds;
}
