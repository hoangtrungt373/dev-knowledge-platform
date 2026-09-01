package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.entity.Order;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
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
import com.ttg.devknowledgeplatform.ecommerce.service.CouponRedemptionService;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.SavedAddressService;
import com.ttg.devknowledgeplatform.ecommerce.shipping.ShippingFeeCalculator;
import com.ttg.devknowledgeplatform.ecommerce.shipping.ShippingFeeQuote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CheckoutServiceImpl} — Epic 2's US-2.5–2.7 (address capture, review +
 * confirm, stale-line revalidation), plus Phase 2 of the Coupon feature (coupon-code resolution
 * and redemption). {@link CouponRedemptionService} is mocked here — its own eligibility/
 * discount-calculation logic is covered by {@code CouponRedemptionServiceImplTest}; these tests
 * only pin down how {@code CheckoutServiceImpl} wires its two coupon slots into the totals and
 * when it does (and does not) record a redemption.
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
    @Mock
    private ShippingFeeCalculator shippingFeeCalculator;
    @Mock
    private CouponRedemptionService couponRedemptionService;

    @InjectMocks
    private CheckoutServiceImpl service;

    private CheckoutCommands.AddressInput addressInput;
    private CheckoutCommands.AddressSelection address;

    @BeforeEach
    void setUp() {
        // lenient — several tests (an empty/all-unavailable cart, a selection matching nothing)
        // reject before the fee is ever computed, and strict stubbing would flag this as unused
        // for those specific tests otherwise.
        lenient().when(shippingFeeCalculator.calculate(any(), any()))
                .thenReturn(new ShippingFeeQuote(FLAT_SHIPPING_FEE, FLAT_SHIPPING_FEE));
        addressInput = new CheckoutCommands.AddressInput(
                "Ada Lovelace", "+44 20 7946 0958", "ada@example.com", "1 Analytical Engine Way", null, "London",
                "England", "SW1A 1AA", "UK");
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

    private static Coupon subtotalCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(1);
        coupon.setCode("SAVE10");
        coupon.setTarget(CouponTarget.SUBTOTAL);
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(new BigDecimal("10.00"));
        return coupon;
    }

    private static Coupon shippingCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(2);
        coupon.setCode("FREESHIP");
        coupon.setTarget(CouponTarget.SHIPPING_FEE);
        coupon.setType(CouponType.FIXED_AMOUNT);
        coupon.setValue(FLAT_SHIPPING_FEE);
        return coupon;
    }

    @Nested
    class Preview {

        @Test
        void computesTotalsFromAvailableLinesOnly() {
            CartLine available = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine unavailable = unavailableLine(2, 1);
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available, unavailable)));

            CheckoutPreview preview = service.preview(USER_UUID, null, null, null);

            assertThat(preview.subtotal()).isEqualByComparingTo("20.00");
            assertThat(preview.subtotalDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.shippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.originalShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("25.00");
            assertThat(preview.lines()).containsExactly(available, unavailable);
        }

        @Test
        void reportsAWaivedFeeSeparatelyFromWhatItWouldHaveBeen() {
            CartLine available = availableLine(1, 1, new BigDecimal("60.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(shippingFeeCalculator.calculate(any(), any()))
                    .thenReturn(new ShippingFeeQuote(BigDecimal.ZERO, FLAT_SHIPPING_FEE));

            CheckoutPreview preview = service.preview(USER_UUID, null, null, null);

            // The total is built from the actually-charged fee (zero), not the waived original —
            // this is the one thing this test exists to pin down; the shipping.* tests own the
            // free-over-threshold business rule itself.
            assertThat(preview.shippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.originalShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("60.00");
        }

        @Test
        void appliesASubtotalCouponWithoutTouchingTheShippingFee() {
            CartLine available = availableLine(1, 1, new BigDecimal("50.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            Coupon coupon = subtotalCoupon();
            when(couponRedemptionService.resolve("SAVE10", CouponTarget.SUBTOTAL, USER_UUID, new BigDecimal("50.00")))
                    .thenReturn(coupon);
            when(couponRedemptionService.calculateDiscount(coupon, new BigDecimal("50.00")))
                    .thenReturn(new BigDecimal("10.00"));

            CheckoutPreview preview = service.preview(USER_UUID, null, "SAVE10", null);

            assertThat(preview.subtotal()).isEqualByComparingTo("50.00");
            assertThat(preview.subtotalDiscountAmount()).isEqualByComparingTo("10.00");
            assertThat(preview.shippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("45.00"); // 50 - 10 + 5
            // preview never consumes a redemption slot — only confirm does.
            verify(couponRedemptionService, never()).redeem(any(), any(), any(), any());
        }

        @Test
        void appliesAShippingCouponOnTopOfWhateverTheAutomaticStrategyAlreadyCharges() {
            CartLine available = availableLine(1, 1, new BigDecimal("20.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            Coupon coupon = shippingCoupon();
            when(couponRedemptionService.resolve("freeship", CouponTarget.SHIPPING_FEE, USER_UUID, new BigDecimal("20.00")))
                    .thenReturn(coupon);
            when(couponRedemptionService.calculateDiscount(coupon, FLAT_SHIPPING_FEE)).thenReturn(FLAT_SHIPPING_FEE);

            CheckoutPreview preview = service.preview(USER_UUID, null, null, "freeship");

            assertThat(preview.subtotalDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.shippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(preview.originalShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(preview.total()).isEqualByComparingTo("20.00");
        }

        @Test
        void propagatesAnIneligibleCouponRejection() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));
            when(couponRedemptionService.resolve(eq("EXPIRED10"), eq(CouponTarget.SUBTOTAL), eq(USER_UUID), any()))
                    .thenThrow(new BusinessException(EcommerceErrorCode.COUPON_EXPIRED, "EXPIRED10"));

            assertThatThrownBy(() -> service.preview(USER_UUID, null, "EXPIRED10", null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_EXPIRED);
        }

        @Test
        void rejectsAnEmptyCart() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of()));

            assertThatThrownBy(() -> service.preview(USER_UUID, null, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);
        }

        @Test
        void rejectsACartWithNoAvailableLines() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.preview(USER_UUID, null, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_NO_VALID_ITEMS);
        }

        @Test
        void withASelectionOnlyComputesTotalsFromTheSelectedLines() {
            CartLine selected = availableLine(1, 2, new BigDecimal("10.00"));
            CartLine notSelected = availableLine(2, 1, new BigDecimal("50.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(selected, notSelected)));

            CheckoutPreview preview = service.preview(USER_UUID, List.of(1), null, null);

            assertThat(preview.subtotal()).isEqualByComparingTo("20.00");
            assertThat(preview.total()).isEqualByComparingTo("25.00");
            assertThat(preview.lines()).containsExactly(selected);
        }

        @Test
        void rejectsASelectionThatMatchesNothingInTheCart() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));

            assertThatThrownBy(() -> service.preview(USER_UUID, List.of(999), null, null))
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

            CheckoutResult result = service.confirm(USER_UUID, address, null, null, null);

            Order order = result.order();
            assertThat(order.getId()).isEqualTo(100);
            assertThat(order.getOwnerUuid()).isEqualTo(USER_UUID);
            assertThat(order.getSubtotal()).isEqualByComparingTo("20.00");
            assertThat(order.getSubtotalDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getSubtotalCouponCode()).isNull();
            assertThat(order.getShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(order.getOriginalShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(order.getShippingCouponCode()).isNull();
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
            verify(couponRedemptionService, never()).redeem(any(), any(), any(), any());
        }

        @Test
        void persistsAWaivedFeeSeparatelyFromWhatItWouldHaveBeen() {
            CartLine available = availableLine(1, 1, new BigDecimal("60.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(shippingFeeCalculator.calculate(any(), any()))
                    .thenReturn(new ShippingFeeQuote(BigDecimal.ZERO, FLAT_SHIPPING_FEE));

            CheckoutResult result = service.confirm(USER_UUID, address, null, null, null);

            Order order = result.order();
            assertThat(order.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getOriginalShippingFee()).isEqualByComparingTo(FLAT_SHIPPING_FEE);
            assertThat(order.getTotal()).isEqualByComparingTo("60.00");
        }

        @Test
        void persistsBothCouponCodesAndDiscountAmountsAndRecordsRedemptionsOnlyAfterSaving() {
            CartLine available = availableLine(1, 1, new BigDecimal("50.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(200);
                return saved;
            });
            Coupon subtotalCoupon = subtotalCoupon();
            Coupon shippingCoupon = shippingCoupon();
            when(couponRedemptionService.resolve("SAVE10", CouponTarget.SUBTOTAL, USER_UUID, new BigDecimal("50.00")))
                    .thenReturn(subtotalCoupon);
            when(couponRedemptionService.calculateDiscount(subtotalCoupon, new BigDecimal("50.00")))
                    .thenReturn(new BigDecimal("10.00"));
            when(couponRedemptionService.resolve("FREESHIP", CouponTarget.SHIPPING_FEE, USER_UUID, new BigDecimal("50.00")))
                    .thenReturn(shippingCoupon);
            when(couponRedemptionService.calculateDiscount(shippingCoupon, FLAT_SHIPPING_FEE)).thenReturn(FLAT_SHIPPING_FEE);

            CheckoutResult result = service.confirm(USER_UUID, address, null, "SAVE10", "FREESHIP");

            Order order = result.order();
            assertThat(order.getSubtotalDiscountAmount()).isEqualByComparingTo("10.00");
            assertThat(order.getSubtotalCouponCode()).isEqualTo("SAVE10");
            assertThat(order.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(order.getShippingCouponCode()).isEqualTo("FREESHIP");
            assertThat(order.getTotal()).isEqualByComparingTo("40.00"); // 50 - 10 + 0

            InOrder ordering = inOrder(orderRepository, couponRedemptionService);
            ordering.verify(orderRepository).save(any(Order.class));
            ordering.verify(couponRedemptionService).redeem(subtotalCoupon, order, USER_UUID, new BigDecimal("10.00"));
            ordering.verify(couponRedemptionService).redeem(shippingCoupon, order, USER_UUID, FLAT_SHIPPING_FEE);
        }

        @Test
        void neverRedeemsWhenNoCouponCodeWasGiven() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.confirm(USER_UUID, address, null, null, null);

            verify(couponRedemptionService, never()).redeem(any(), any(), any(), any());
        }

        @Test
        void reportsDroppedLinesWithoutIncludingThemInTheOrderOrRemovingThemFromTheCart() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            CartLine unavailable = unavailableLine(2, 3);
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available, unavailable)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CheckoutResult result = service.confirm(USER_UUID, address, null, null, null);

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

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null, null, null))
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

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null, null, null))
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

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null, null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.CHECKOUT_CART_EMPTY);

            verify(orderRepository, never()).save(any());
            verify(cartService, never()).removeItems(eq(USER_UUID), any());
        }

        @Test
        void rejectsACartWithNoAvailableLinesAndNeverSavesOrRemoves() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(unavailableLine(2, 1))));

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, null, null, null))
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

            CheckoutResult result = service.confirm(USER_UUID, address, List.of(1), null, null);

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

            assertThatThrownBy(() -> service.confirm(USER_UUID, address, List.of(999), null, null))
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
            saved.setPhone("+1 703-555-0100");
            saved.setEmail("grace@example.com");
            saved.setLine1("1 Compiler Ave");
            saved.setCity("Arlington");
            saved.setState("VA");
            saved.setPostalCode("22201");
            saved.setCountry("USA");
            when(savedAddressService.getOwned(7, USER_UUID)).thenReturn(saved);

            // adHocAddress is deliberately non-null here too — resolveAddress must still prefer the
            // saved address and ignore it entirely, not just work when adHocAddress is absent.
            var selection = new CheckoutCommands.AddressSelection(7, addressInput, false, null);
            CheckoutResult result = service.confirm(USER_UUID, selection, null, null, null);

            assertThat(result.order().getShippingAddress().getFullName()).isEqualTo("Grace Hopper");
            verify(savedAddressService, never()).create(any(), any());
        }

        @Test
        void rejectsAnIncompleteAdHocAddressWhenNoSavedAddressIdIsGiven() {
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(availableLine(1, 1, new BigDecimal("10.00")))));
            var incomplete = new CheckoutCommands.AddressInput(
                    "Ada Lovelace", "+44 20 7946 0958", "ada@example.com", "", null, "London", "England",
                    "SW1A 1AA", "UK");
            var selection = new CheckoutCommands.AddressSelection(null, incomplete, false, null);

            assertThatThrownBy(() -> service.confirm(USER_UUID, selection, null, null, null))
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

            service.confirm(USER_UUID, selection, null, null, null);

            verify(savedAddressService).create(eq(USER_UUID), eq(new SavedAddressCommands.Create(
                    "Home", "Ada Lovelace", "+44 20 7946 0958", "ada@example.com", "1 Analytical Engine Way", null,
                    "London", "England", "SW1A 1AA", "UK", false)));
        }

        @Test
        void doesNotSaveToTheAddressBookWhenAnExistingSavedAddressWasUsed() {
            CartLine available = availableLine(1, 1, new BigDecimal("10.00"));
            when(cartService.getCart(USER_UUID)).thenReturn(new Cart(List.of(available)));
            when(productVariantRepository.reserve(1, 1)).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
            SavedAddress saved = new SavedAddress();
            saved.setFullName("Grace Hopper");
            saved.setPhone("+1 703-555-0100");
            saved.setEmail("grace@example.com");
            saved.setLine1("1 Compiler Ave");
            saved.setCity("Arlington");
            saved.setState("VA");
            saved.setPostalCode("22201");
            saved.setCountry("USA");
            when(savedAddressService.getOwned(7, USER_UUID)).thenReturn(saved);
            // saveAddress: true here is meaningless once a savedAddressId is given — there's
            // nothing new to save — and must not be acted on regardless.
            var selection = new CheckoutCommands.AddressSelection(7, addressInput, true, "Home");

            service.confirm(USER_UUID, selection, null, null, null);

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

            CheckoutResult result = service.confirm(USER_UUID, selection, null, null, null);

            assertThat(result.order().getId()).isEqualTo(101);
        }
    }
}
