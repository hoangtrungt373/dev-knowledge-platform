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

/**
 * Implementation of {@link CheckoutApi}.
 */
@RestController
@RequiredArgsConstructor
public class CheckoutController implements CheckoutApi {

    private final CheckoutService checkoutService;
    private final CheckoutMapper checkoutMapper;

    @Override
    public ResponseEntity<CheckoutPreviewResponse> preview(String userUuid) {
        return ResponseEntity.ok(checkoutMapper.toPreviewResponse(checkoutService.preview(userUuid)));
    }

    @Override
    public ResponseEntity<CheckoutConfirmResponse> confirm(String userUuid, AddressRequest request) {
        CheckoutCommands.AddressInput address = new CheckoutCommands.AddressInput(
                request.getFullName(), request.getLine1(), request.getLine2(),
                request.getCity(), request.getState(), request.getPostalCode(), request.getCountry());
        var result = checkoutService.confirm(userUuid, address);
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutMapper.toConfirmResponse(result));
    }
}
