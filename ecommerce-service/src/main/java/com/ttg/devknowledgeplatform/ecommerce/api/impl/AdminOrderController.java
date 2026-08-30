package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.AdminOrderApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.mapper.OrderMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link AdminOrderApi}.
 */
@RestController
@RequiredArgsConstructor
public class AdminOrderController implements AdminOrderApi {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Override
    public ResponseEntity<PagedResponse<OrderResponse>> list(OrderStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var responses = orderService.listAllOrders(status, pageable).map(orderMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    @Override
    public ResponseEntity<OrderResponse> ship(Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.ship(id)));
    }

    @Override
    public ResponseEntity<OrderResponse> deliver(Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.deliver(id)));
    }
}
