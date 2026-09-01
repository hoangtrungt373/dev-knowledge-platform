package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** Request payload for adding a new address to the caller's AddressBook. */
@Data
public class CreateSavedAddressRequest {

    @Size(max = 50, message = "Label must not exceed 50 characters")
    private String label;

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

    /** Explicitly requests this address become the caller's default — the caller's very first
     * address is auto-defaulted regardless of this flag (see {@code SavedAddressServiceImpl}). */
    private boolean makeDefault;
}
