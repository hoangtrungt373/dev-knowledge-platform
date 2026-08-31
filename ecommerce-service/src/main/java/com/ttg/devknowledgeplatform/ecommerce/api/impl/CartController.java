package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.CartApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.AddCartItemRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.CartResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.RemoveCartItemsRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateCartItemRequest;
import com.ttg.devknowledgeplatform.ecommerce.mapper.CartMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.CartService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link CartApi}.
 */
@RestController
@RequiredArgsConstructor
public class CartController implements CartApi {

    private final CartService cartService;
    private final CartMapper cartMapper;

    @Override
    public ResponseEntity<CartResponse> getCart(String userUuid) {
        return ResponseEntity.ok(cartMapper.toResponse(cartService.getCart(userUuid)));
    }

    @Override
    public ResponseEntity<CartResponse> addItem(String userUuid, AddCartItemRequest request) {
        cartService.addItem(userUuid, request.getVariantId(), request.getQuantity());
        return ResponseEntity.ok(cartMapper.toResponse(cartService.getCart(userUuid)));
    }

    @Override
    public ResponseEntity<CartResponse> updateItem(String userUuid, Integer variantId, UpdateCartItemRequest request) {
        cartService.setQuantity(userUuid, variantId, request.getQuantity());
        return ResponseEntity.ok(cartMapper.toResponse(cartService.getCart(userUuid)));
    }

    @Override
    public ResponseEntity<CartResponse> removeItem(String userUuid, Integer variantId) {
        cartService.setQuantity(userUuid, variantId, 0);
        return ResponseEntity.ok(cartMapper.toResponse(cartService.getCart(userUuid)));
    }

    @Override
    public ResponseEntity<CartResponse> removeItems(String userUuid, RemoveCartItemsRequest request) {
        cartService.removeItems(userUuid, request.getVariantIds());
        return ResponseEntity.ok(cartMapper.toResponse(cartService.getCart(userUuid)));
    }
}
