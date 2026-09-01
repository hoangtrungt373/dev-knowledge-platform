package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** REST response shape for {@code SavedAddress} (the AddressBook feature). */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SavedAddressResponse {

    private Integer id;
    private String label;
    private String fullName;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private boolean defaultAddress;
    private Instant createdAt;
    private Instant updatedAt;
}
