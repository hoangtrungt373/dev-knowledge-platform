package com.ttg.devknowledgeplatform.ecommerce.outbox;

import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxEventStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls {@code OutboxEvent} for {@code PENDING} rows and dispatches each one via
 * {@link OutboxEventProcessor}.
 *
 * <p>{@code @EnableScheduling} is already active for the whole app (declared once in
 * {@code ai-service}'s {@code AiServiceConfig}, which {@code gateway} already depends on) — this
 * class doesn't need its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE = 20;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    @Scheduled(fixedDelayString = "${app.ecommerce.outbox.relay.poll-interval:PT5S}")
    public void pollAndDispatch() {
        List<Integer> pendingIds = outboxEventRepository.findIdsByStatus(
                OutboxEventStatus.PENDING, PageRequest.of(0, BATCH_SIZE));
        for (Integer id : pendingIds) {
            // Each id goes through the real proxy on a different bean, so its own
            // @Transactional boundary applies — see OutboxEventProcessor's Javadoc.
            outboxEventProcessor.processOne(id);
        }
    }
}
