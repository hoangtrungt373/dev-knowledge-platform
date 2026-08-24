package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;
import com.ttg.devknowledgeplatform.ecommerce.service.CartService;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutPreview;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckoutServiceImpl} — Epic 2's US-2.5–2.7 (address capture, review +
 * confirm, stale-line revalidation).
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    private static final String USER_UUID = "user-uuid-1";
    private static final BigDecimal FLAT_SHIPPING_FEE = new BigDecimal("5.00");

    @Mock
    private CartService cartService;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private CheckoutServiceImpl service;

    private CheckoutCommands.AddressInput address;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "flatShippingFee", FLAT_SHIPPING_FEE);
        address = new CheckoutCommands.AddressInput(
                "Ada Lovelace", "1 Analytical Engine Way", null, "London", "England", "SW1A 1AA", "UK");
    }

    private static CartLine availableLine(Integer variantId, int quantity, BigDecimal price) {
        Product product = new Product();
        product.setId(10);
        product.setName("404 Not Found T-Shirt");
        ProductVariant variant = new ProductVariant(product, "SKU-" + variantId, price, 100, 0, Map.of());
        variant.setId(variantId);
        return CartLine.available(variantId, quantity, variant);
    }

    private static CartLine unavailableLine(Integer variantId, int quantity) {
        return CartLine.unavailable(variantId, quantity);
    }

    @Nested
    class Preview {

        @Test
        void computesTotalsFromAvailableLinesOnly() {
            CartLine available = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine unavailable = unavailableLine(2, 1);
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available, unavailable)));

            CheckoutPreview preview = service.preview(USER_UUID);

            assertThat(preview.subtotal()).isEqualByComparingTo("20.00");
            assertThat(preview.shippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("25.00");
            assertThat(preview.lines()).containsExactly(available, unavailable);
        }

        @Test
        void rejectsAnEmptyCart() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of()));

            assertThatThrownBy(() -> service.preview(USER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        }

        @Test
        void rejectsACartWithNoAvailableLines() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.preview(USER_UUID))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);
        }
    }

    @Nested
    class Confirm {

        @Test
        void createsOrderFromAvailableLinesAndClearsCartOnlyAfterSaving() {
            CartLine available = availableLine(1, 2, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(100);
                return saved;
            });

            CheckoutResult result = service.confirm(USER_UUID, address);

            Order order = result.order();
            assertThat(order.getId()).isEqualTo(100);
            assertThat(order.getOwnerUuid()).isEqualTo(USER_UUID);
            assertThat(order.getSubtotal()).isEqualByComparingTo("20.00");
            assertThat(order.getShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(order.getTotal()).isEqualByComparingTo("25.00");
            assertThat(order.getShippingAddress().getFullName()).isEqualTo("Ada Lovelace");
            assertThat(order.getLines()).hasSize(1);
            assertThat(order.getLines().get(0).getProductVariantId()).isEqualTo(1);
            assertThat(order.getLines().get(0).getSku()).isEqualTo("SKU-1");
            assertThat(order.getLines().get(0).getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(order.getLines().get(0).getQuantity()).isEqualTo(2);
            assertThat(result.droppedLines()).isEmpty();

            InOrder ordering = inOrder(orderRepository, cartService);
            ordering.verify(orderRepository).save(any(Order.class));
            ordering.verify(cartService).clear(USER_UUID);
        }

        @Test
        void reportsDroppedLinesWithoutIncludingThemInTheOrder() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            CartLine unavailable = unavailableLine(2, 3);
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available, unavailable)));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CheckoutResult result = service.confirm(USER_UUID, address);

            assertThat(result.order().getLines()).hasSize(1);
            assertThat(result.droppedLines()).containsExactly(unavailable);
        }

        @Test
        void rejectsAnEmptyCartAndNeverSavesOrClears() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of()));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).clear(eq(USER_UUID));
        }

        @Test
        void rejectsACartWithNoAvailableLinesAndNeverSavesOrClears() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).clear(eq(USER_UUID));
        }
    }
}
