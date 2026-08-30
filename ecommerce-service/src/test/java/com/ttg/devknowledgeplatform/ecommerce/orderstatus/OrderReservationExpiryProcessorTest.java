package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderReservationExpiryProcessor} — the per-order expiry mechanism (US-3.2)
 * in isolation from {@link OrderStatusHandlerRegistry}'s real transition logic (mocked here).
 */
@ExtendWith(MockitoExtension.class)
class OrderReservationExpiryProcessorTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    @InjectMocks
    private OrderReservationExpiryProcessor processor;

    @Test
    void anOrderThatVanishedBeforeLookupIsASafeNoOp() {
        when(orderRepository.findById(1)).thenReturn(Optional.empty());

        processor.expireOne(1);

        verify(orderStatusHandlerRegistry, never()).expire(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void anOrderNoLongerPendingIsASafeNoOp() {
        // The shopper cancelled it themselves (or some other path already moved it) in the gap
        // between the poll query and this call — expiring it now would be wrong.
        Order order = new Order();
        order.setId(1);
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        processor.expireOne(1);

        verify(orderStatusHandlerRegistry, never()).expire(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void aStillPendingOrderIsExpiredAndSaved() {
        Order order = new Order();
        order.setId(1);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepository.findById(1)).thenReturn(Optional.of(order));

        processor.expireOne(1);

        verify(orderStatusHandlerRegistry).expire(order);
        verify(orderRepository).save(order);
    }
}
