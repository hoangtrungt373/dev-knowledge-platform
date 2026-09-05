package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PaymentProcessingOrderStatusHandler} — US-3.6's queued-cancel flag and
 * US-3.3's payment-resolution transitions, including the queued-cancel-wins-over-the-gateway rule.
 */
@ExtendWith(MockitoExtension.class)
class PaymentProcessingOrderStatusHandlerTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private PaymentProcessingOrderStatusHandler handler;

    private static Order paymentProcessingOrderWithLine() {
        Order order = new Order();
        order.setStatus(OrderStatus.PAYMENT_PROCESSING);
        OrderLine line = new OrderLine();
        line.setProductVariantId(1);
        line.setQuantity(2);
        order.getLines().add(line);
        return order;
    }

    @Test
    void statusIsPaymentProcessing() {
        assertThat(handler.status()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
    }

    @Test
    void cancelSetsTheFlagWithoutTransitioningOrWritingHistory() {
        Order order = paymentProcessingOrderWithLine();
        order.setCancelRequested(false);

        handler.cancel(order);

        assertThat(order.getCancelRequested()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(order.getStatusHistory()).isEmpty();
    }

    @Nested
    class Expire {

        @Test
        void withNoQueuedCancelReleasesAndTransitionsToExpired() {
            Order order = paymentProcessingOrderWithLine();

            handler.expire(order);

            verify(productVariantRepository).release(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(order.getStatusHistory()).hasSize(1);
            assertThat(order.getStatusHistory().get(0).getReason()).contains("abandoned");
        }

        @Test
        void withAQueuedCancelStillReleasesButTransitionsToCancelledInstead() {
            Order order = paymentProcessingOrderWithLine();
            order.setCancelRequested(true);

            handler.expire(order);

            verify(productVariantRepository).release(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    class ConfirmPayment {

        @Test
        void withNoQueuedCancelConfirmsSaleAndTransitionsToConfirmed() {
            Order order = paymentProcessingOrderWithLine();

            handler.confirmPayment(order);

            verify(productVariantRepository).confirmSale(1, 2);
            verify(productVariantRepository, never()).restock(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getStatusHistory()).hasSize(1);
            assertThat(order.getStatusHistory().get(0).getReason()).isNull();
        }

        @Test
        void withAQueuedCancelRestocksAndTransitionsToCancelledInstead() {
            Order order = paymentProcessingOrderWithLine();
            order.setCancelRequested(true);

            handler.confirmPayment(order);

            verify(productVariantRepository).confirmSale(1, 2);
            verify(productVariantRepository).restock(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getStatusHistory().get(0).getReason()).contains("isn't wired");
        }
    }

    @Nested
    class FailPayment {

        @Test
        void withNoQueuedCancelReleasesAndTransitionsToFailed() {
            Order order = paymentProcessingOrderWithLine();

            handler.failPayment(order);

            verify(productVariantRepository).release(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        }

        @Test
        void withAQueuedCancelStillReleasesButTransitionsToCancelledInstead() {
            Order order = paymentProcessingOrderWithLine();
            order.setCancelRequested(true);

            handler.failPayment(order);

            verify(productVariantRepository).release(1, 2);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
