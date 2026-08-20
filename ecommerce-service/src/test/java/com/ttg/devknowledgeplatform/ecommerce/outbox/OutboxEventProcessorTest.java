package com.ttg.devknowledgeplatform.ecommerce.outbox;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxEventStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutboxEventProcessor} — the generic claim-dispatch-mark-processed
 * mechanism every future epic's outbox handler rides on top of (US-1.5's own
 * {@code PRODUCT_CHANGED} handler included). Verifies the mechanism in isolation from any real
 * handler's logic via a mocked {@link OutboxEventHandler}.
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OutboxEventDispatcher dispatcher;
    @Mock
    private OutboxEventHandler handler;

    @InjectMocks
    private OutboxEventProcessor processor;

    private static OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(1);
        event.setEventType("PRODUCT_CHANGED");
        event.setAttemptCount(0);
        return event;
    }

    @Test
    void aLostClaimRaceIsASafeNoOp() {
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(0);

        processor.processOne(1);

        verify(outboxEventRepository, never()).findById(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void aClaimedEventThatVanishedBeforeLookupIsASafeNoOp() {
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.empty());

        processor.processOne(1);

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void successfulDispatchMarksProcessedAndClearsAnyPriorError() {
        OutboxEvent event = pendingEvent();
        event.setLastError("a previous failure");
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(dispatcher.resolve("PRODUCT_CHANGED")).thenReturn(Optional.of(handler));

        processor.processOne(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(captor.getValue().getLastError()).isNull();
        assertThat(captor.getValue().getProcessedAt()).isNotNull();
    }

    @Test
    void aHandlerFailureBelowTheAttemptCeilingStaysPendingForRetry() {
        OutboxEvent event = pendingEvent();
        event.setAttemptCount(1); // this attempt will be the 2nd
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(dispatcher.resolve("PRODUCT_CHANGED")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("boom")).when(handler).handle(event);

        processor.processOne(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(2);
        assertThat(captor.getValue().getLastError()).isEqualTo("boom");
    }

    @Test
    void theFifthFailedAttemptStopsRetryingAndMarksFailed() {
        OutboxEvent event = pendingEvent();
        event.setAttemptCount(4); // this attempt will be the 5th (MAX_ATTEMPTS)
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(dispatcher.resolve("PRODUCT_CHANGED")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException("still broken")).when(handler).handle(event);

        processor.processOne(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(5);
    }

    @Test
    void noHandlerRegisteredForTheEventTypeIsTreatedAsAFailedAttempt() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(dispatcher.resolve("PRODUCT_CHANGED")).thenReturn(Optional.empty());

        processor.processOne(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("No handler registered");
    }

    @Test
    void anOverlyLongErrorMessageIsTruncated() {
        OutboxEvent event = pendingEvent();
        String longMessage = "x".repeat(2000);
        when(outboxEventRepository.claim(1, OutboxEventStatus.PENDING, OutboxEventStatus.PROCESSING)).thenReturn(1);
        when(outboxEventRepository.findById(1)).thenReturn(Optional.of(event));
        when(dispatcher.resolve("PRODUCT_CHANGED")).thenReturn(Optional.of(handler));
        doThrow(new RuntimeException(longMessage)).when(handler).handle(event);

        processor.processOne(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getLastError()).hasSize(1000);
    }
}
