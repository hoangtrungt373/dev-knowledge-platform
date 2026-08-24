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
import java.util.ArrayList;
import java.util.List;

/**
 * A shopper's order, created at checkout (Epic 2, US-2.6) from whatever cart lines were still
 * available at confirm time. This epic's own responsibility ends here — {@link OrderStatus} has
 * only {@link OrderStatus#PENDING} today; Epic 3's reservation step and Epic 4's payment step pick
 * up from this row once they're built (see that enum's own Javadoc).
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
 */
@Entity
@Table(name = "CUSTOMER_ORDER", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "CUSTOMER_ORDER_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"lines"})
@ToString(exclude = {"lines"})
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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();
}
