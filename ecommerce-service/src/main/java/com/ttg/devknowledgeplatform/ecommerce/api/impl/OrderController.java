package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.OrderApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.mapper.OrderMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link OrderApi}.
 */
@RestController
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Override
    public ResponseEntity<PagedResponse<OrderResponse>> list(String userUuid, int page, int size) {
        var responses = orderService.listOrders(userUuid, PageRequest.of(page, size)).map(orderMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    public ResponseEntity<OrderResponse> getById(String userUuid, Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.getOrder(id, userUuid)));
    }

    @Override
    public ResponseEntity<OrderResponse> cancel(String userUuid, Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.cancel(id, userUuid)));
    }

    @Override
    public ResponseEntity<OrderResponse> pay(String userUuid, Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.initiatePayment(id, userUuid)));
    }
}
