package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentHandoffService} — US-3.3's two durable steps, in isolation from
 * {@link OrderStatusHandlerRegistry}'s real transition logic (mocked here).
 */
@ExtendWith(MockitoExtension.class)
class PaymentHandoffServiceTest {

    private static final String OWNER_UUID = "owner-uuid-1";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;

    @InjectMocks
    private PaymentHandoffService service;

    private static Order orderOwnedBy(String ownerUuid) {
        Order order = new Order();
        order.setId(1);
        order.setOwnerUuid(ownerUuid);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    @Nested
    class StartPaymentProcessing {

        @Test
        void dispatchesToTheRegistryAndSavesWhenTheCallerOwnsTheOrder() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.startPaymentProcessing(1, OWNER_UUID);

            verify(orderStatusHandlerRegistry).startPaymentProcessing(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.startPaymentProcessing(1, "someone-else-uuid"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).startPaymentProcessing(order);
        }
    }

    @Nested
    class ResolvePayment {

        @Test
        void succeededDispatchesToConfirmPayment() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            service.resolvePayment(1, PaymentOutcome.SUCCEEDED);

            verify(orderStatusHandlerRegistry).confirmPayment(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(order);
        }

        @Test
        void declinedDispatchesToFailPayment() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            service.resolvePayment(1, PaymentOutcome.DECLINED);

            verify(orderStatusHandlerRegistry).failPayment(order);
            verify(orderStatusHandlerRegistry, never()).confirmPayment(order);
        }

        @Test
        void pendingLeavesTheOrderUntouchedForTheNextPoll() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            service.resolvePayment(1, PaymentOutcome.PENDING);

            verify(orderStatusHandlerRegistry, never()).confirmPayment(order);
            verify(orderStatusHandlerRegistry, never()).failPayment(order);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderNoLongerExists() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolvePayment(1, PaymentOutcome.SUCCEEDED))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }
}
