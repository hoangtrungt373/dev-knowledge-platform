package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A shopper's reusable, independently-owned shipping address — the AddressBook feature, reversing
 * Epic 2's original "single inline address, no saved address book" scope lock (see {@link
 * Address}'s own Javadoc for that earlier decision and why it no longer applies).
 *
 * <p>Deliberately a separate entity from {@link Address} rather than a reuse of it — {@link
 * Address} stays a plain {@code @Embeddable} value object snapshotted onto {@link Order} with no
 * lifecycle of its own (frozen at purchase time, same treatment {@code OrderLine} gives price);
 * this entity is the opposite — a row a shopper creates, edits, and deletes independently of any
 * order. {@link com.ttg.devknowledgeplatform.ecommerce.service.impl.CheckoutServiceImpl} still
 * converts whichever one was actually chosen at checkout time (a saved address here, or a
 * one-off entry) into the same frozen {@link Address} snapshot either way — this entity is a
 * reusable *template*, never itself referenced by a foreign key from {@code CUSTOMER_ORDER}.
 *
 * <p>{@code ownerUuid} is a plain column, not a {@code User} foreign key — this module persists no
 * caller-identity row at all (see root {@code CLAUDE.md}'s Security section); the only question
 * this table ever needs answered is "is this row's owner the caller," via the JWT's own {@code
 * sub} claim, mirroring {@code Order.ownerUuid} exactly.
 */
@Entity
@Table(name = "SAVED_ADDRESS", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "SAVED_ADDRESS_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class SavedAddress extends AbstractEntity {

    @Column(name = "OWNER_UUID", length = 36, nullable = false)
    private String ownerUuid;

    /** Optional nickname (e.g. "Home", "Work") to tell multiple addresses apart at a glance. */
    @Column(name = "LABEL", length = 50)
    private String label;

    @Column(name = "FULL_NAME", length = 150, nullable = false)
    private String fullName;

    /** Nullable at the DB level only because pre-existing rows have nothing to backfill (see this
     * column's own migration) — {@code Create}/{@code UpdateSavedAddressRequest} both make it
     * {@code @NotBlank} for every write going forward, same treatment {@link Address#getPhone()}
     * gets on the order-snapshot side. */
    @Column(name = "PHONE", length = 30)
    private String phone;

    /** Nullable at the DB level for the same "no backfill" reason as {@link #phone} — the
     * invoice/order-confirmation recipient for orders placed with this address, deliberately
     * independent of the caller's Keycloak login email (see {@link Address#getEmail()}'s own
     * Javadoc). {@code @NotBlank} on both {@code Create}/{@code UpdateSavedAddressRequest} for
     * every write going forward. */
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

    /** At most one row per {@code ownerUuid} may be {@code true} — enforced both here (service
     * layer, unset-then-set) and by a partial unique index in the DB (see this table's own
     * migration). Named {@code defaultAddress}, not {@code isDefault}, so Lombok's generated
     * accessors ({@code isDefaultAddress()}/{@code setDefaultAddress(boolean)}) stay unambiguous. */
    @Column(name = "IS_DEFAULT", nullable = false)
    private boolean defaultAddress;
}
