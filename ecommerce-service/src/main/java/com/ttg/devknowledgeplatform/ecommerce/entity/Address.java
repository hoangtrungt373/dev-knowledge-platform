package com.ttg.devknowledgeplatform.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A shipping address, embedded directly on {@link Order} rather than its own table/entity.
 *
 * <p>This module locked "single inline address, no saved address book" for Epic 2 (see
 * {@code docs/user-stories/02-cart-checkout.md}, US-2.5) — there is no independent lifecycle or
 * reuse to justify a standalone entity, so this is a plain JPA {@code @Embeddable} value object,
 * captured fresh at checkout and snapshotted onto the order that used it, the same "frozen at
 * purchase time" treatment {@link Order} already gives price (see {@link OrderLine}).
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Column(name = "FULL_NAME", length = 150, nullable = false)
    private String fullName;

    @Column(name = "LINE_1", length = 255, nullable = false)
    private String line1;

    @Column(name = "LINE_2", length = 255)
    private String line2;

    @Column(name = "CITY", length = 100, nullable = false)
    private String city;

    @Column(name = "STATE", length = 100, nullable = false)
    private String state;

    @Column(name = "POSTAL_CODE", length = 20, nullable = false)
    private String postalCode;

    @Column(name = "COUNTRY", length = 100, nullable = false)
    private String country;
}
