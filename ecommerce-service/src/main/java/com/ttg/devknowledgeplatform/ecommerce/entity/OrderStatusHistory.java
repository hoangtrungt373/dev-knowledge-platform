package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * One row per {@link Order} lifecycle transition (US-3.5) — the audit trail behind "view order
 * status and a timeline of how it got there", not just {@link Order#getStatus()}'s current value.
 *
 * <p>{@link #fromStatus} is {@code null} only for the very first row (order creation has no "from"
 * state); every later row is written by whichever {@code OrderStatusHandler} performs that
 * transition, in the same transaction as the {@link Order#setStatus} write it accompanies —
 * {@link AbstractEntity#getDteCreation()} (the audit column every entity already carries) doubles
 * as this row's own "when did this happen" timestamp, so there's no separate {@code occurredAt}
 * column. {@link #reason} is a free-text note for transitions where "why" isn't obvious from the
 * status alone (e.g. {@code EXPIRED}: "reservation timeout"; {@code CANCELLED}: "shopper
 * cancelled"); left {@code null} where the transition is self-explanatory.
 */
@Entity
@Table(name = "ORDER_STATUS_HISTORY", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "ORDER_STATUS_HISTORY_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"order"})
@ToString(exclude = {"order"})
public class OrderStatusHistory extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "FROM_STATUS", length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "TO_STATUS", length = 20, nullable = false)
    private OrderStatus toStatus;

    @Column(name = "REASON", length = 255)
    private String reason;
}
