package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.infra.scheduling.AbstractBatchJob;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Polls for {@code PENDING} orders whose reservation has outlived
 * {@code orderJobProperties.reservationTimeout()} and expires each one via
 * {@link OrderReservationExpiryProcessor} (US-3.2) — same poller/single-item-processor split as
 * {@code outbox.OutboxRelay}/{@code OutboxEventProcessor}, for the identical
 * self-invocation-bypasses-{@code @Transactional} reason. {@code @EnableScheduling} already lives
 * on {@code EcommerceServiceApplication} (enabled for {@code OutboxRelay}), so this class doesn't
 * need its own.
 *
 * <p>Extends infra's {@link AbstractBatchJob}, like {@link OrderReconciliationJob}/
 * {@link RefundReconciliationJob}. It used to be the one poller here that couldn't: the old
 * module-local template assumed inline per-item work, while this job's work had to go through a
 * separate {@code @Transactional} bean. The infra template is built around exactly that split
 * ({@link #processOne} calls another bean through its proxy), so all three jobs share it now — and
 * this one gained the failure isolation it never had (one bad order used to abort the whole batch).
 */
@Component
@RequiredArgsConstructor
public class OrderReservationExpiryJob extends AbstractBatchJob<Integer> {

    private final OrderRepository orderRepository;
    private final OrderReservationExpiryProcessor orderReservationExpiryProcessor;
    private final OrderJobProperties orderJobProperties;

    @Scheduled(fixedDelayString = "${app.ecommerce.order.expiry-check.poll-interval:PT1M}")
    public void expireAbandonedReservations() {
        runBatch();
    }

    @Override
    protected List<Integer> pollBatch(int batchSize) {
        Instant cutoff = Instant.now().minus(orderJobProperties.reservationTimeout());
        return orderRepository.findIdsByStatusAndDteCreationBefore(
                OrderStatus.PENDING, cutoff, PageRequest.of(0, batchSize));
    }

    @Override
    protected boolean processOne(Integer orderId) {
        return orderReservationExpiryProcessor.process(orderId);
    }
}
