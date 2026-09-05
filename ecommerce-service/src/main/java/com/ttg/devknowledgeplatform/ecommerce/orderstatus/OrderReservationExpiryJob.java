package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.config.OrderJobProperties;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * <p>Implements {@link ReconciliationJob} directly rather than extending
 * {@link AbstractReconciliationJob} — this job's own per-item work must be genuinely
 * {@code @Transactional} (it directly transitions an {@code Order}'s own status), which is exactly
 * why it's delegated to {@link OrderReservationExpiryProcessor} instead of an inline try/catch; see
 * {@link AbstractReconciliationJob}'s own Javadoc for the full reasoning.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderReservationExpiryJob implements ReconciliationJob {

    private static final int BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderReservationExpiryProcessor orderReservationExpiryProcessor;
    private final OrderJobProperties orderJobProperties;

    @Scheduled(fixedDelayString = "${app.ecommerce.order.expiry-check.poll-interval:PT1M}")
    public void expireAbandonedReservations() {
        Instant cutoff = Instant.now().minus(orderJobProperties.reservationTimeout());
        List<Integer> pendingIds = orderRepository.findIdsByStatusAndDteCreationBefore(
                OrderStatus.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (Integer id : pendingIds) {
            // Each id goes through the real proxy on a different bean, so its own
            // @Transactional boundary applies — see OrderReservationExpiryProcessor's Javadoc.
            orderReservationExpiryProcessor.expireOne(id);
        }
    }
}
