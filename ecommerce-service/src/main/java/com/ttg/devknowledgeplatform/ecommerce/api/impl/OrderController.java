package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.OrderApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.OrderResponse;
import com.ttg.devknowledgeplatform.ecommerce.enums.OrderStatus;
import com.ttg.devknowledgeplatform.ecommerce.mapper.OrderMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.OrderService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link OrderApi}.
 */
@RestController
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Override
    public ResponseEntity<PagedResponse<OrderResponse>> list(String userUuid, List<OrderStatus> statuses, int page, int size) {
        // Explicit sort — findAll(Specification, Pageable) has no inherent ordering the way the
        // derived query this replaced did (see OrderRepository's own Javadoc).
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var responses = orderService.listOrders(userUuid, statuses, pageable).map(orderMapper::toResponse);
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
        OrderService.PaymentInitiationResult result = orderService.initiatePayment(id, userUuid);
        OrderResponse response = orderMapper.toResponse(result.order());
        response.setPaymentClientSecret(result.clientSecret());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<OrderResponse> reconcile(String userUuid, Integer id) {
        return ResponseEntity.ok(orderMapper.toResponse(orderService.reconcilePayment(id, userUuid)));
    }
}
