package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link PendingOrderStatusHandler} — US-3.2's expiry and US-3.6's
 * before-payment cancel, both of which only ever release a reservation (nothing was sold yet).
 */
@ExtendWith(MockitoExtension.class)
class PendingOrderStatusHandlerTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private PendingOrderStatusHandler handler;

    private static Order pendingOrderWithLines() {
        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        OrderLine line1 = new OrderLine();
        line1.setProductVariantId(1);
        line1.setQuantity(2);
        OrderLine line2 = new OrderLine();
        line2.setProductVariantId(2);
        line2.setQuantity(3);
        order.getLines().add(line1);
        order.getLines().add(line2);
        return order;
    }

    @Test
    void statusIsPending() {
        assertThat(handler.status()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void expireReleasesEveryLineAndTransitionsToExpired() {
        Order order = pendingOrderWithLines();

        handler.expire(order);

        verify(productVariantRepository).release(1, 2);
        verify(productVariantRepository).release(2, 3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order.getStatusHistory().get(0).getReason()).isNotBlank();
    }

    @Test
    void cancelReleasesEveryLineAndTransitionsToCancelled() {
        Order order = pendingOrderWithLines();

        handler.cancel(order);

        verify(productVariantRepository).release(1, 2);
        verify(productVariantRepository).release(2, 3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shipIsNotValidFromPendingAndTouchesNothing() {
        Order order = pendingOrderWithLines();

        assertThatThrownBy(() -> handler.ship(order))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getStatusHistory()).isEmpty();
    }

    @Test
    void startPaymentProcessingStampsIdempotencyKeyAndClockAndTransitions() {
        Order order = pendingOrderWithLines();
        order.setId(42);

        handler.startPaymentProcessing(order);

        // A random UUID, deliberately not derived from the order's own id (a recycled primary key
        // after a local dev database reset could otherwise collide with an unrelated earlier
        // charge attempt at Stripe — see this method's own updated Javadoc for the incident).
        assertThat(order.getIdempotencyKey()).isNotBlank();
        assertThatCode(() -> UUID.fromString(order.getIdempotencyKey())).doesNotThrowAnyException();
        assertThat(order.getPaymentProcessingStartedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(order.getStatusHistory()).hasSize(1);
        assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        verifyNoInteractions(productVariantRepository);
    }
}
