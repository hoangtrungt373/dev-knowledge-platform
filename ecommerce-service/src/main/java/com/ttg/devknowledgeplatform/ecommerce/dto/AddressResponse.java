package com.ttg.devknowledgeplatform.ecommerce.dto;

import lombok.Builder;
import lombok.Data;

/** REST response shape for an order's snapshotted shipping address. */
@Data
@Builder
public class AddressResponse {

    private String fullName;
    private String phone;
    private String email;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
}
