package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.Coupon;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRedemptionRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.CouponRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponCommands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Unit tests for {@link CouponServiceImpl} — Phase 1 ("ProductDiscount" feature) admin CRUD.
 * Mirrors {@code ProductTagServiceImplTest}'s shape, the closest precedent for a flat CRUD entity
 * in this module.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    @InjectMocks
    private CouponServiceImpl service;

    private Coupon existing;

    @BeforeEach
    void setUp() {
        existing = new Coupon();
        existing.setId(1);
        existing.setCode("SAVE10");
        existing.setTarget(CouponTarget.SUBTOTAL);
        existing.setType(CouponType.PERCENTAGE);
        existing.setValue(new BigDecimal("10"));
        existing.setActive(true);
    }

    private static CouponCommands.Create createCommand(String code, CouponType type, BigDecimal value) {
        return new CouponCommands.Create(code, CouponTarget.SUBTOTAL, type, value, true, null, null, null, null, null);
    }

    private static CouponCommands.Update updateCommand(CouponType type, BigDecimal value) {
        return new CouponCommands.Update(CouponTarget.SUBTOTAL, type, value, true, null, null, null, null, null);
    }

    @Nested
    class Create {

        @Test
        void createsCouponWhenCodeIsAvailable() {
            when(couponRepository.existsByCode("SUMMER20")).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
                Coupon saved = invocation.getArgument(0);
                saved.setId(2);
                return saved;
            });

            Coupon result = service.create(createCommand("summer20", CouponType.PERCENTAGE, new BigDecimal("20")));

            assertThat(result.getId()).isEqualTo(2);
            assertThat(result.getCode()).isEqualTo("SUMMER20"); // normalized to uppercase
        }

        @Test
        void rejectsCodeThatAlreadyExists() {
            when(couponRepository.existsByCode("SAVE10")).thenReturn(true);

            assertThatThrownBy(() -> service.create(createCommand("SAVE10", CouponType.PERCENTAGE, BigDecimal.TEN)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_CODE_CONFLICT);

            verify(couponRepository, never()).save(any());
        }

        @Test
        void rejectsAZeroOrNegativeValue() {
            when(couponRepository.existsByCode("SAVE0")).thenReturn(false);

            assertThatThrownBy(() -> service.create(createCommand("SAVE0", CouponType.FIXED_AMOUNT, BigDecimal.ZERO)))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_INVALID_VALUE);
        }

        @Test
        void rejectsAPercentageValueOver100() {
            when(couponRepository.existsByCode("TOOMUCH")).thenReturn(false);

            assertThatThrownBy(() -> service.create(createCommand("TOOMUCH", CouponType.PERCENTAGE, new BigDecimal("150"))))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_INVALID_VALUE);
        }

        @Test
        void allowsAFixedAmountValueOver100() {
            when(couponRepository.existsByCode("BIGOFF")).thenReturn(false);
            when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Coupon result = service.create(createCommand("BIGOFF", CouponType.FIXED_AMOUNT, new BigDecimal("150")));

            assertThat(result.getValue()).isEqualByComparingTo("150");
        }

        @Test
        void rejectsAnEndDateNotAfterTheStartDate() {
            Instant now = Instant.now();
            when(couponRepository.existsByCode("BADRANGE")).thenReturn(false);
            CouponCommands.Create command = new CouponCommands.Create(
                    "BADRANGE", CouponTarget.SUBTOTAL, CouponType.PERCENTAGE, BigDecimal.TEN, true,
                    now, now.minus(1, ChronoUnit.DAYS), null, null, null);

            assertThatThrownBy(() -> service.create(command))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_INVALID_DATE_RANGE);
        }
    }

    @Nested
    class Update {

        @Test
        void updatesFieldsWhenValid() {
            when(couponRepository.findById(1)).thenReturn(Optional.of(existing));
            when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            Coupon result = service.update(1, updateCommand(CouponType.FIXED_AMOUNT, new BigDecimal("5")));

            assertThat(result.getType()).isEqualTo(CouponType.FIXED_AMOUNT);
            assertThat(result.getValue()).isEqualByComparingTo("5");
            assertThat(result.getCode()).isEqualTo("SAVE10"); // code is immutable
        }

        @Test
        void rejectsAnInvalidValueOnUpdate() {
            when(couponRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(1, updateCommand(CouponType.PERCENTAGE, new BigDecimal("200"))))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_INVALID_VALUE);
        }

        @Test
        void throwsWhenCouponDoesNotExist() {
            when(couponRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, updateCommand(CouponType.PERCENTAGE, BigDecimal.TEN)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetById {

        @Test
        void returnsCouponWhenFound() {
            when(couponRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThat(service.getById(1)).isEqualTo(existing);
        }

        @Test
        void throwsWhenNotFound() {
            when(couponRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ListCoupons {

        @Test
        void delegatesToRepositoryWithSpecAndPageable() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "code"));
            when(couponRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(existing)));

            Page<Coupon> result = service.list(pageable, "save", true, CouponTarget.SUBTOTAL);

            assertThat(result.getContent()).containsExactly(existing);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesCouponWhenNeverRedeemed() {
            when(couponRepository.findById(1)).thenReturn(Optional.of(existing));
            when(couponRedemptionRepository.existsByCouponId(1)).thenReturn(false);

            service.delete(1);

            // Cast needed: CouponRepository extends both JpaRepository (delete(T)) and
            // JpaSpecificationExecutor (which gained its own delete(Specification<T>) in recent
            // Spring Data JPA) — same disambiguation CouponRepository's own ProductTagRepository
            // precedent needs inside a verify() chain (see ProductTagServiceImplTest's own note).
            verify((JpaRepository<Coupon, Integer>) couponRepository).delete(existing);
        }

        @Test
        void rejectsDeletingACouponThatHasBeenRedeemed() {
            when(couponRepository.findById(1)).thenReturn(Optional.of(existing));
            when(couponRedemptionRepository.existsByCouponId(1)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.COUPON_IN_USE);

            verify((JpaRepository<Coupon, Integer>) couponRepository, never()).delete(any(Coupon.class));
        }

        @Test
        void throwsWhenCouponDoesNotExist() {
            when(couponRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
