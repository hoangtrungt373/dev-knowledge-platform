package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.OrderRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;
import com.ttg.devknowledgeplatform.ecommerce.service.CartService;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutPreview;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutResult;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressService;

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
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private SavedAddressService savedAddressService;

    @InjectMocks
    private CheckoutServiceImpl service;

    private CheckoutCommands.AddressInput addressInput;
    private CheckoutCommands.AddressSelection address;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "flatShippingFee", FLAT_SHIPPING_FEE);
        addressInput = new CheckoutCommands.AddressInput(
                "Ada Lovelace", "1 Analytical Engine Way", null, "London", "England", "SW1A 1AA", "UK");
        address = new CheckoutCommands.AddressSelection(null, addressInput, false, null);
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

            CheckoutPreview preview = service.preview(USER_UUID, null);

            assertThat(preview.subtotal()).isEqualByComparingTo("20.00");
            assertThat(preview.shippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("25.00");
            assertThat(preview.lines()).containsExactly(available, unavailable);
        }

        @Test
        void rejectsAnEmptyCart() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of()));

            assertThatThrownBy(() -> service.preview(USER_UUID, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        }

        @Test
        void rejectsACartWithNoAvailableLines() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.preview(USER_UUID, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);
        }

        @Test
        void withASelectionOnlyComputesTotalsFromTheSelectedLines() {
            CartLine selected = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine notSelected = availableLine(2, 1, new BigDecimal("50.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(selected, notSelected)));

            CheckoutPreview preview = service.preview(USER_UUID, List.of(1));

            assertThat(preview.subtotal()).isEqualByComparingTo("20.00");
            assertThat(preview.total()).isEqualByComparingTo("25.00");
            assertThat(preview.lines()).containsExactly(selected);
        }

        @Test
        void rejectsASelectionThatMatchesNothingInTheCart() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));

            assertThatThrownBy(() -> service.preview(USER_UUID, List.of(999)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        }
    }

    @Nested
    class Confirm {

        @Test
        void createsOrderFromAvailableLinesAndRemovesOnlyOrderedLinesOnlyAfterSaving() {
            CartLine available = availableLine(1, 2, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 2)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(100);
                return saved;
            });

            CheckoutResult result = service.confirm(USER_UUID, address, null);

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

            // US-3.5: order creation writes the very first status-history row (no "from" state).
            assertThat(order.getStatusHistory()).hasSize(1);
            assertThat(order.getStatusHistory().get(0).getFromStatus()).isNull();
            assertThat(order.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.PENDING);

            InOrder ordering = inOrder(productVariantRepository, orderRepository, cartService);
            ordering.verify(productVariantRepository).reserve(1, 2);
            ordering.verify(orderRepository).save(any(Order.class));
            ordering.verify(cartService).removeItems(USER_UUID, List.of(1));
        }

        @Test
        void reportsDroppedLinesWithoutIncludingThemInTheOrderOrRemovingThemFromTheCart() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            CartLine unavailable = unavailableLine(2, 3);
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available, unavailable)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CheckoutResult result = service.confirm(USER_UUID, address, null);

            assertThat(result.order().getLines()).hasSize(1);
            assertThat(result.droppedLines()).containsExactly(unavailable);
            // A dropped (unavailable) line was never a candidate for reservation in the first place.
            verify(productVariantRepository, never()).reserve(eq(2), any(Integer.class));
            // Only the ordered line leaves the cart — the dropped line stays, unlike the old
            // whole-cart clear().
            verify(cartService).removeItems(USER_UUID, List.of(1));
        }

        @Test
        void rejectsWhenStockReservationFailsAndNeverSavesOrRemoves() {
            CartLine available = availableLine(1, 5, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 5)).thenReturn(0);

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_INSUFFICIENT_STOCK);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void rollsBackEarlierReservationsInTheSameRequestWhenALaterLineFails() {
            CartLine first = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine second = availableLine(2, 3, new BigDecimal("15.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(first, second)));
            when(productVariantRepository.reserve(1, 2)).thenReturn(1);
            when(productVariantRepository.reserve(2, 3)).thenReturn(0);

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.ORDER_INSUFFICIENT_STOCK);

            // The first line's reservation was claimed before the second line failed — this test
            // documents that undoing it is the surrounding @Transactional rollback's job (a real
            // DB transaction abort), not something this service method does by hand; a unit test
            // with a mocked repository has no transaction to actually roll back.
            verify(productVariantRepository).reserve(1, 2);
            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void rejectsAnEmptyCartAndNeverSavesOrRemoves() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of()));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void rejectsACartWithNoAvailableLinesAndNeverSavesOrRemoves() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void withASelectionOnlyOrdersAndRemovesTheSelectedLinesLeavingTheRestInTheCart() {
            CartLine selected = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine notSelected = availableLine(2, 1, new BigDecimal("50.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(selected, notSelected)));
            when(productVariantRepository.reserve(1, 2)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CheckoutResult result = service.confirm(USER_UUID, address, List.of(1));

            assertThat(result.order().getLines()).hasSize(1);
            assertThat(result.order().getLines().get(0).getProductVariantId()).isEqualTo(1);
            assertThat(result.order().getSubtotal()).isEqualByComparingTo("20.00");
            assertThat(result.droppedLines()).isEmpty();
            // The variant not part of this selection was never a candidate for reservation, and
            // stays in the cart afterward — never passed to removeItems.
            verify(productVariantRepository, never()).reserve(eq(2), any(Integer.class));
            verify(cartService).removeItems(USER_UUID, List.of(1));
        }

        @Test
        void rejectsASelectionThatMatchesNothingInTheCartAndNeverSavesOrRemoves() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, List.of(999)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void usesAnExistingAddressBookEntryWhenSavedAddressIdIsGiven() {
            CartLine available = availableLine(1, 2, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 2)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            SavedAddress saved = new SavedAddress();
            saved.setFullName("Grace Hopper");
            saved.setLine1("1 Compiler Ave");
            saved.setCity("Arlington");
            saved.setState("VA");
            saved.setPostalCode("22201");
            saved.setCountry("USA");
            when(savedAddressService.getOwned(7, USER_UUID)).thenReturn(saved);

            // adHocAddress is deliberately non-null here too — resolveAddress must still prefer the
            // saved address and ignore it entirely, not just work when adHocAddress is absent.
            var selection = new CheckoutCommands.AddressSelection(7, addressInput, false, null);
            CheckoutResult result = service.confirm(USER_UUID, selection, null);

            assertThat(result.order().getShippingAddress().getFullName()).isEqualTo("Grace Hopper");
            verify(savedAddressService, never()).create(any(), any());
        }

        @Test
        void rejectsAnIncompleteAdHocAddressWhenNoSavedAddressIdIsGiven() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));
            var incomplete = new CheckoutCommands.AddressInput(
                    "Ada Lovelace", "", null, "London", "England", "SW1A 1AA", "UK");
            var selection = new CheckoutCommands.AddressSelection(null, incomplete, false, null);

            assertThatThrownBy(() -> service.confirm(USER_UUID, selection, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_ADDRESS_REQUIRED);

            verify(orderRepository, never()).save(any());
        }

        @Test
        void savesTheAdHocAddressToTheAddressBookWhenRequested() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            var selection = new CheckoutCommands.AddressSelection(null, addressInput, true, "Home");

            service.confirm(USER_UUID, selection, null);

            verify(savedAddressService).create(eq(USER_UUID), eq(new SavedAddressCommands.Create(
                    "Home", "Ada Lovelace", "1 Analytical Engine Way", null, "London", "England", "SW1A 1AA", "UK", false)));
        }

        @Test
        void doesNotSaveToTheAddressBookWhenAnExistingSavedAddressWasUsed() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            SavedAddress saved = new SavedAddress();
            saved.setFullName("Grace Hopper");
            saved.setLine1("1 Compiler Ave");
            saved.setCity("Arlington");
            saved.setState("VA");
            saved.setPostalCode("22201");
            saved.setCountry("USA");
            when(savedAddressService.getOwned(7, USER_UUID)).thenReturn(saved);
            // saveAddress: true here is meaningless once a savedAddressId is given — there's
            // nothing new to save — and must not be acted on regardless.
            var selection = new CheckoutCommands.AddressSelection(7, addressInput, true, "Home");

            service.confirm(USER_UUID, selection, null);

            verify(savedAddressService, never()).create(any(), any());
        }

        @Test
        void placingTheOrderSucceedsEvenWhenSavingTheAddressToTheAddressBookFails() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(101);
                return saved;
            });
            when(savedAddressService.create(any(), any())).thenThrow(new RuntimeException("boom"));
            var selection = new CheckoutCommands.AddressSelection(null, addressInput, true, "Home");

            CheckoutResult result = service.confirm(USER_UUID, selection, null);

            assertThat(result.order().getId()).isEqualTo(101);
        }
    }
}
