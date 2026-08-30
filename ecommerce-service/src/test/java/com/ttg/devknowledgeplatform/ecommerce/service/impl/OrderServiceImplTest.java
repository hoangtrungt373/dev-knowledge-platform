package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.OrderStatusHandlerRegistry;
import com.ttg.devknowledgeplatform.ecommerce.orderstatus.PaymentHandoffService;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentGatewayPort;
import com.ttg.devknowledgeplatform.ecommerce.payment.PaymentOutcome;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderServiceImpl} — US-3.5's ownership-checked get/list, US-3.6's
 * ownership-checked cancel, US-3.7/3.8's admin ship/deliver (all thin wrappers around a mocked
 * {@link OrderStatusHandlerRegistry}), and US-3.3's {@link OrderServiceImpl#initiatePayment}
 * orchestration (mocked {@link PaymentHandoffService}/{@link PaymentGatewayPort} — the two durable
 * steps and the gateway call are each covered by their own dedicated test class, not re-verified
 * here).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final String OWNER_UUID = "owner-uuid-1";
    private static final String OTHER_UUID = "someone-else-uuid";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHandlerRegistry orderStatusHandlerRegistry;
    @Mock
    private PaymentHandoffService paymentHandoffService;
    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @InjectMocks
    private OrderServiceImpl service;

    private static Order orderOwnedBy(String ownerUuid) {
        Order order = new Order();
        order.setId(1);
        order.setOwnerUuid(ownerUuid);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    @Nested
    class GetOrder {

        @Test
        void returnsTheOrderWhenTheCallerOwnsIt() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThat(service.getOrder(1, OWNER_UUID)).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.getOrder(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getOrder(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class ListOrders {

        @Test
        void delegatesToTheRepositoryMostRecentFirst() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(OWNER_UUID)));
            when(orderRepository.findByOwnerUuidOrderByIdDesc(OWNER_UUID, pageable)).thenReturn(page);

            var result = service.listOrders(OWNER_UUID, pageable);

            assertThat(result).isSameAs(page);
        }
    }

    @Nested
    class ListAllOrders {

        @Test
        void delegatesToTheRepositoryWithASpecificationBuiltFromStatus() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(OWNER_UUID)));
            // The Specification itself is a fresh lambda built inside listAllOrders — never equal
            // by reference/value to one built here, so this only verifies delegation (page/filter
            // wiring), not the Specification's own filtering logic (same reasoning
            // ProductSpecification/ProductCategorySpecification are left to their own devices,
            // untested at the unit level, in this module).
            when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

            Page<Order> result = service.listAllOrders(OrderStatus.CONFIRMED, pageable);

            assertThat(result).isSameAs(page);
        }
    }

    @Nested
    class Cancel {

        @Test
        void dispatchesToTheRegistryAndSavesWhenTheCallerOwnsTheOrder() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.cancel(1, OWNER_UUID);

            verify(orderStatusHandlerRegistry).cancel(order);
            verify(orderRepository).save(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderBelongsToSomeoneElse() {
            Order order = orderOwnedBy(OWNER_UUID);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.cancel(1, OTHER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).cancel(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancel(1, OWNER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
        }
    }

    @Nested
    class Ship {

        @Test
        void dispatchesToTheRegistryAndSaves() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.CONFIRMED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.ship(1);

            verify(orderStatusHandlerRegistry).ship(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.ship(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).ship(any());
        }
    }

    @Nested
    class Deliver {

        @Test
        void dispatchesToTheRegistryAndSaves() {
            Order order = orderOwnedBy(OWNER_UUID);
            order.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(1)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            Order result = service.deliver(1);

            verify(orderStatusHandlerRegistry).deliver(order);
            assertThat(result).isSameAs(order);
        }

        @Test
        void rejectsAsNotFoundWhenTheOrderDoesNotExist() {
            when(orderRepository.findById(1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deliver(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_NOT_FOUND);
            verify(orderStatusHandlerRegistry, never()).deliver(any());
        }
    }

    @Nested
    class InitiatePayment {

        @Test
        void chainsStartPaymentProcessingTheGatewayCallAndResolvePaymentInOrder() {
            Order pending = orderOwnedBy(OWNER_UUID);
            pending.setStatus(OrderStatus.PAYMENT_PROCESSING);
            pending.setIdempotencyKey("1");
            pending.setTotal(new BigDecimal("25.00"));
            Order confirmed = orderOwnedBy(OWNER_UUID);
            confirmed.setStatus(OrderStatus.CONFIRMED);
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID)).thenReturn(pending);
            when(paymentGatewayPort.charge("1", new BigDecimal("25.00"))).thenReturn(PaymentOutcome.SUCCEEDED);
            when(paymentHandoffService.resolvePayment(1, PaymentOutcome.SUCCEEDED)).thenReturn(confirmed);

            Order result = service.initiatePayment(1, OWNER_UUID);

            assertThat(result).isSameAs(confirmed);
            verify(paymentHandoffService).startPaymentProcessing(1, OWNER_UUID);
            verify(paymentGatewayPort).charge("1", new BigDecimal("25.00"));
            verify(paymentHandoffService).resolvePayment(1, PaymentOutcome.SUCCEEDED);
        }

        @Test
        void neverCallsTheGatewayOrResolvesWhenStartingPaymentProcessingFails() {
            when(paymentHandoffService.startPaymentProcessing(1, OWNER_UUID))
                    .thenThrow(new BusinessException(
                            EcommerceErrorCode.ORDER_INVALID_STATUS_TRANSITION, "startPaymentProcessing", OrderStatus.SHIPPED));

            assertThatThrownBy(() -> service.initiatePayment(1, OWNER_UUID)).isInstanceOf(ApiException.class);

            verify(paymentGatewayPort, never()).charge(any(), any());
            verify(paymentHandoffService, never()).resolvePayment(any(), any());
        }
    }
}
