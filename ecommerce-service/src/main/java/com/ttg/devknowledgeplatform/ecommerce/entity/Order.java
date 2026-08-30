package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A shopper's order, created at checkout (Epic 2, US-2.6) from whatever cart lines were still
 * available at confirm time. Epic 3 (see {@code docs/user-stories/03-order-lifecycle-inventory.md})
 * drives this row through the rest of its life — reservation, payment handoff, cancellation,
 * shipment, delivery — via {@link OrderStatus}'s full state machine (see that enum's own Javadoc).
 *
 * <p><b>Table is {@code CUSTOMER_ORDER}, not {@code ORDER}</b> — {@code ORDER} is a reserved SQL
 * keyword in PostgreSQL, the same reason {@code social-service}'s {@code Group} entity maps to
 * {@code MESSAGE_GROUP} instead of {@code GROUP}.
 *
 * <p>{@link #ownerUuid} is a plain column (the Keycloak JWT's {@code sub} claim), never a
 * {@code @ManyToOne} FK onto a {@code User} row — same "Option C" claims-based-ownership shape
 * every other module in this reactor already follows (see root {@code CLAUDE.md}'s Security
 * section). {@link #shippingAddress} and every {@link OrderLine} are permanent snapshots of what
 * was true at order-creation time (address entered inline, price/SKU/product name copied from the
 * catalog as it stood then) — {@link #subtotal}/{@link #shippingFee}/{@link #total} are stored
 * here for the same reason, rather than re-derived from current {@link OrderLine} data on every
 * read, since a flat shipping fee read from config could change after this order was placed.
 *
 * <p>{@link #idempotencyKey} (US-3.3) is stamped once, immediately before the {@code PENDING} →
 * {@code PAYMENT_PROCESSING} transition, so a crash between "payment succeeded" and "order
 * confirmed" can be recovered by re-querying the gateway for this same key rather than risking a
 * double charge — nullable, since it has no meaning until that transition happens.
 * {@link #paymentProcessingStartedAt} is the reconciliation job's (US-3.4) own "how long has this
 * been stuck" clock, separate from {@link com.ttg.devknowledgeplatform.common.entity.AbstractEntity#getDteCreation()}
 * since an order can sit {@code PENDING} for a while before payment is ever attempted.
 * {@link #cancelRequested} implements the state machine's queued-cancel rule: a shopper cancelling
 * mid-{@code PAYMENT_PROCESSING} can't jump straight to {@code CANCELLED} (a gateway call is
 * literally in flight), so this flag is set instead and consulted once that call resolves.
 */
@Entity
@Table(name = "CUSTOMER_ORDER", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "CUSTOMER_ORDER_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"lines", "statusHistory"})
@ToString(exclude = {"lines", "statusHistory"})
public class Order extends AbstractEntity {

    @Column(name = "OWNER_UUID", length = 36, nullable = false)
    private String ownerUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Embedded
    private Address shippingAddress;

    @Column(name = "SUBTOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "SHIPPING_FEE", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee;

    @Column(name = "TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "IDEMPOTENCY_KEY", length = 64)
    private String idempotencyKey;

    @Column(name = "PAYMENT_PROCESSING_STARTED_AT")
    private Instant paymentProcessingStartedAt;

    @Column(name = "CANCEL_REQUESTED", nullable = false)
    private Boolean cancelRequested = false;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();
}
