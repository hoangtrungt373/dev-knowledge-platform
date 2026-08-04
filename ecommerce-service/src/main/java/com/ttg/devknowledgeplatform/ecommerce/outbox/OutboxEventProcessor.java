package com.ttg.devknowledgeplatform.ecommerce.outbox;

import com.ttg.devknowledgeplatform.common.util.DateUtils;
import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxEventStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims and dispatches a single {@code OutboxEvent}, one row per call.
 *
 * <p>Deliberately a separate bean from {@link OutboxRelay} (the {@code @Scheduled} entry point)
 * rather than a second method on it: Spring's {@code @Transactional} is proxy-based, and a bean
 * calling its own {@code @Transactional} method via {@code this.processOne(...)} bypasses the
 * proxy entirely, silently running with no transaction at all — a classic Spring AOP
 * self-invocation pitfall. Keeping this on its own bean means {@link OutboxRelay} calls it
 * through the real proxy and the transaction boundary is honored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    /** After this many failed attempts, a poison message stops retrying and is left {@code FAILED}. */
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventDispatcher dispatcher;

    @Transactional(rollbackFor = Throwable.class)
    public void processOne(Integer id) {
        int claimed = outboxEventRepository.claim(id, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING);
        if (claimed == 0) {
            // Already claimed/finished by another call — safe no-op, not an error.
            return;
        }

        OutboxEvent event = outboxEventRepository.findById(id).orElse(null);
        if (event == null) {
            return;
        }

        try {
            OutboxEventHandler handler = dispatcher.resolve(event.getEventType())
                    .orElseThrow(() -> new IllegalStateException("No handler registered for eventType=" + event.getEventType()));
            handler.handle(event);
            event.setStatus(OutboxEventStatus.PROCESSED);
            event.setProcessedAt(DateUtils.getCurrentDateTime());
            event.setLastError(null);
        } catch (Exception e) {
            int attempts = event.getAttemptCount() + 1;
            event.setAttemptCount(attempts);
            event.setLastError(truncate(e.getMessage()));
            event.setStatus(attempts >= MAX_ATTEMPTS ? OutboxEventStatus.FAILED : OutboxEventStatus.PENDING);
            log.warn("Outbox event id={} eventType={} failed on attempt {}/{}: {}",
                    id, event.getEventType(), attempts, MAX_ATTEMPTS, e.getMessage());
        }

        outboxEventRepository.save(event);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }
}
