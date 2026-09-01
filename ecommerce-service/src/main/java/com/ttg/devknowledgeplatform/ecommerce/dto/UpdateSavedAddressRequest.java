package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** Request payload for editing an existing AddressBook entry. No {@code makeDefault} flag here —
 * promoting an address to default is its own dedicated action ({@code POST .../set-default}),
 * kept separate from a plain field edit. */
@Data
public class UpdateSavedAddressRequest {

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
}
