package com.ttg.devknowledgeplatform.ecommerce.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxAggregateType;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxEventStatus;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * A transactional-outbox row: written in the same DB transaction as the write-side change that
 * caused it (e.g. a {@link Product} create/update, later a payment outcome), and read by a
 * separate scheduled relay that dispatches it to whichever projector/handler is registered for
 * {@link #eventType}.
 *
 * <p>Why this exists rather than just publishing an in-process Spring event: {@code infra}'s
 * {@code AsyncEventHandler} dispatches in-memory only — if the process crashes between a
 * transaction commit and that listener actually running, the event is silently gone and nothing
 * remembers it needed to happen. A row in this table survives that crash; the relay simply picks
 * it up on its next poll. See {@code docs/user-stories/01-catalog-search.md} (US-1.5) and
 * {@code docs/user-stories/04-payments.md} (US-4.4) for the two motivating cases.
 *
 * <p>{@link #status} is the relay's primary signal (see {@link OutboxEventStatus} for why a plain
 * processed/unprocessed flag isn't enough); the relay's poll query is
 * {@code WHERE STATUS = 'PENDING'}, backed by a partial index (see the Liquibase migration) so
 * the index only ever covers the small pending tail of an otherwise-growing table.
 * {@link #processedAt} is kept alongside it purely as a "when did this succeed" timestamp for
 * lag/observability queries, not as the state signal itself. {@link #attemptCount} and
 * {@link #lastError} exist so a poison message (one that fails every time it's dispatched) is
 * queryable and diagnosable instead of just retrying silently forever.
 *
 * <p>{@link #aggregateType} is {@link OutboxAggregateType} — a small, slow-growing set (roughly
 * one new value per future epic), so a shared enum + DB {@code CHECK} costs little to maintain.
 * {@link #eventType} stays a plain string, deliberately not an enum: it's one Java field, so it
 * could only ever be backed by a single enum type, and every future epic (payments, reviews) will
 * keep adding its own event types to this same shared table — forcing all of them into one
 * ever-growing shared enum (and widening a DB {@code CHECK} constraint every time) fights the
 * whole point of this table being generic infrastructure. The relay's handler registry looks
 * {@link #eventType} up as a plain routing key regardless.
 */
@Entity
@Table(name = "OUTBOX_EVENT", schema = "ecommerce")
@AttributeOverride(name = "id", column = @Column(name = "OUTBOX_EVENT_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class OutboxEvent extends AbstractEntity {

    @Column(name = "EVENT_TYPE", length = 100, nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "AGGREGATE_TYPE", length = 100, nullable = false)
    private OutboxAggregateType aggregateType;

    @Column(name = "AGGREGATE_ID", nullable = false)
    private Integer aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PAYLOAD", columnDefinition = "JSONB", nullable = false)
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20, nullable = false)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(name = "ATTEMPT_COUNT", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "LAST_ERROR", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "PROCESSED_AT")
    private Instant processedAt;
}
