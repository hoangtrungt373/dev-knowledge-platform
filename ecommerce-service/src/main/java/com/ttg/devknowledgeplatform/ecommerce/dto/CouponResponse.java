package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponTarget;
import com.ttg.devknowledgeplatform.ecommerce.enums.CouponType;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** REST response shape for {@code Coupon}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CouponResponse {

    private Integer id;
    private String code;
    private CouponTarget target;
    private CouponType type;
    private BigDecimal value;
    private Boolean active;
    private Instant startAt;
    private Instant endAt;
    private BigDecimal minSubtotal;
    private Integer maxRedemptions;
    private Integer maxRedemptionsPerUser;
    private Instant createdAt;
    private Instant updatedAt;
}
