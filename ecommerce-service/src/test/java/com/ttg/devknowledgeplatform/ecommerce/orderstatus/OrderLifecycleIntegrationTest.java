package com.ttg.devknowledgeplatform.ecommerce.orderstatus;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.OrderLine;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Epic 3, Phase 6: drives a real {@link Order} through the full state machine using the real
 * {@link OrderStatusHandlerRegistry} wired with every real handler — unlike every other test in
 * this package, which isolates exactly one handler/processor/service at a time (with its
 * collaborators mocked). This class exists to prove the <i>wiring</i> between handlers is correct,
 * not just that each one individually does the right thing in isolation — most importantly, the
 * queued-cancel-wins-over-the-gateway rule, which spans {@link PaymentProcessingOrderStatusHandler}'s
 * own {@code cancel}/{@code confirmPayment}/{@code failPayment} methods and can't be observed by
 * unit-testing any one of them alone. {@link ProductVariantRepository} is the only mock — it's the
 * actual persistence boundary this whole package talks to.
 */
@ExtendWith(MockitoExtension.class)
class OrderLifecycleIntegrationTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    private OrderStatusHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new OrderStatusHandlerRegistry(List.of(
                new PendingOrderStatusHandler(productVariantRepository),
                new PaymentProcessingOrderStatusHandler(productVariantRepository),
                new ConfirmedOrderStatusHandler(productVariantRepository),
                new ShippedOrderStatusHandler()));
    }

    private static Order pendingOrderWithOneLine() {
        Order order = new Order();
        order.setId(1);
        order.setStatus(OrderStatus.PENDING);
        OrderLine line = new OrderLine();
        line.setProductVariantId(42);
        line.setQuantity(2);
        order.getLines().add(line);
        return order;
    }

    @Test
    void happyPathReachesDeliveredWithOneConfirmSaleAndAFullHistoryTrail() {
        Order order = pendingOrderWithOneLine();

        registry.startPaymentProcessing(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        assertThat(order.getIdempotencyKey()).isEqualTo("1");

        registry.confirmPayment(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        registry.ship(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        registry.deliver(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);

        verify(productVariantRepository).confirmSale(42, 2);
        verify(productVariantRepository, never()).release(42, 2);
        verify(productVariantRepository, never()).restock(42, 2);

        List<OrderStatus> toStatuses = order.getStatusHistory().stream().map(h -> h.getToStatus()).toList();
        assertThat(toStatuses).containsExactly(
                OrderStatus.PAYMENT_PROCESSING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
    }

    @Test
    void cancelBeforePaymentReleasesTheReservation() {
        Order order = pendingOrderWithOneLine();

        registry.cancel(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantRepository).release(42, 2);
        verify(productVariantRepository, never()).confirmSale(42, 2);
    }

    @Test
    void cancelAfterConfirmationRestocksInsteadOfReleasing() {
        Order order = pendingOrderWithOneLine();
        registry.startPaymentProcessing(order);
        registry.confirmPayment(order);

        registry.cancel(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantRepository).confirmSale(42, 2);
        verify(productVariantRepository).restock(42, 2);
        verify(productVariantRepository, never()).release(42, 2);
    }

    @Test
    void aQueuedCancelDuringPaymentProcessingWinsOverASubsequentGatewaySuccess() {
        Order order = pendingOrderWithOneLine();
        registry.startPaymentProcessing(order);

        registry.cancel(order); // queues — doesn't transition, gateway call is "in flight"
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);

        registry.confirmPayment(order); // gateway says success, arrives after the queued cancel

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantRepository).confirmSale(42, 2); // the sale did happen, if only for a moment
        verify(productVariantRepository).restock(42, 2); // ...and had to be undone
    }

    @Test
    void aQueuedCancelDuringPaymentProcessingWinsOverASubsequentGatewayDecline() {
        Order order = pendingOrderWithOneLine();
        registry.startPaymentProcessing(order);

        registry.cancel(order);
        registry.failPayment(order); // gateway declined anyway

        // Same end state and same compensating action failPayment would have taken regardless —
        // only the final status label differs (CANCELLED instead of FAILED).
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantRepository).release(42, 2);
        verify(productVariantRepository, never()).confirmSale(42, 2);
    }

    @Test
    void cancelIsBlockedOnceShippedEvenThoughEveryEarlierStatusAllowedIt() {
        Order order = pendingOrderWithOneLine();
        registry.startPaymentProcessing(order);
        registry.confirmPayment(order);
        registry.ship(order);

        assertThatThrownBy(() -> registry.cancel(order))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }
}
