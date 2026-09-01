package com.ttg.devknowledgeplatform.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A shipping address, embedded directly on {@link Order} rather than its own table/entity.
 *
 * <p>This module originally locked "single inline address, no saved address book" for Epic 2 (see
 * {@code docs/user-stories/02-cart-checkout.md}, US-2.5) — a real address book has since been
 * added ({@link SavedAddress}, a genuinely independent, reusable entity), but this class' own role
 * is unchanged: whichever address a shopper actually chooses at checkout time (saved or one-off)
 * is still copied into a plain JPA {@code @Embeddable} value object and snapshotted onto the order
 * that used it, the same "frozen at purchase time" treatment {@link Order} already gives price
 * (see {@link OrderLine}) — an order's own shipping address must never change just because the
 * {@link SavedAddress} it was copied from is later edited or deleted.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Column(name = "FULL_NAME", length = 150, nullable = false)
    private String fullName;

    /** Nullable — unlike every other field on this class, this column was added after
     * {@code CUSTOMER_ORDER} already had real rows with no phone data to backfill (see this
     * column's own migration), so it can't retroactively become {@code NOT NULL} the way the
     * others were from day one. Required going forward regardless, via
     * {@code CheckoutServiceImpl#resolveAddress}'s own imperative completeness check (fresh, ad-hoc
     * addresses) and {@code Create}/{@code UpdateSavedAddressRequest}'s {@code @NotBlank} (AddressBook
     * entries) — an order copied from an old {@link SavedAddress} that predates this column is the
     * only way a {@code null} can still reach here today. */
    @Column(name = "PHONE", length = 30)
    private String phone;

    /** Nullable for the same reason {@link #phone} is — the shopper's invoice/order-confirmation
     * recipient, deliberately independent of their Keycloak login email (the JWT's own {@code
     * email} claim), since the two can legitimately differ (a shared inbox, an accountant, etc.).
     * Required going forward via the same imperative/{@code @NotBlank} checks as every other field
     * added after this table's original rows already existed. */
    @Column(name = "EMAIL", length = 255)
    private String email;

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
