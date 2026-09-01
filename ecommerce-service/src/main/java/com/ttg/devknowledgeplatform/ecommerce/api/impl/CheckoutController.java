package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.CheckoutApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.AddressRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutConfirmResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.CheckoutPreviewResponse;
import com.ttg.devknowledgeplatform.ecommerce.mapper.CheckoutMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.CheckoutService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link CheckoutApi}.
 */
@RestController
@RequiredArgsConstructor
public class CheckoutController implements CheckoutApi {

    private final CheckoutService checkoutService;
    private final CheckoutMapper checkoutMapper;

    @Override
    public ResponseEntity<CheckoutPreviewResponse> preview(
            String userUuid, List<Integer> selectedVariantIds, String subtotalCouponCode, String shippingCouponCode) {
        var preview = checkoutService.preview(userUuid, selectedVariantIds, subtotalCouponCode, shippingCouponCode);
        return ResponseEntity.ok(checkoutMapper.toPreviewResponse(preview));
    }

    @Override
    public ResponseEntity<CheckoutConfirmResponse> confirm(String userUuid, AddressRequest request) {
        // adHocAddress is only meaningful when savedAddressId is null — built either way (cheap,
        // plain field copies) rather than conditionally, since CheckoutServiceImpl's own
        // resolveAddress is what actually decides which one to use.
        CheckoutCommands.AddressInput adHocAddress = new CheckoutCommands.AddressInput(
                request.getFullName(), request.getPhone(), request.getEmail(), request.getLine1(), request.getLine2(),
                request.getCity(), request.getState(), request.getPostalCode(), request.getCountry());
        var addressSelection = new CheckoutCommands.AddressSelection(
                request.getSavedAddressId(), adHocAddress, request.isSaveAddress(), request.getAddressLabel());
        var result = checkoutService.confirm(userUuid, addressSelection, request.getSelectedVariantIds(),
                request.getSubtotalCouponCode(), request.getShippingCouponCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutMapper.toConfirmResponse(result));
    }
}
