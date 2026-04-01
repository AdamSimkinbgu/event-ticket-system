package com.bgu.se.ticketing.api.controllers;

import com.bgu.se.ticketing.application.dto.CreateOrderRequestDTO;
import com.bgu.se.ticketing.application.dto.OrderDTO;
import com.bgu.se.ticketing.application.services.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for order lifecycle management.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders
     * Creates a new order and reserves tickets.
     */
    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequestDTO request) {
        OrderDTO order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * GET /api/orders/{id}
     * Retrieves an order by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    /**
     * GET /api/orders?buyerId={buyerId}
     * Lists all orders for a buyer.
     */
    @GetMapping
    public ResponseEntity<List<OrderDTO>> getOrdersByBuyer(@RequestParam String buyerId) {
        return ResponseEntity.ok(orderService.getOrdersByBuyer(buyerId));
    }

    /**
     * PUT /api/orders/{id}/confirm
     * Confirms a pending order.
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<OrderDTO> confirmOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }

    /**
     * PUT /api/orders/{id}/cancel
     * Cancels an order and releases tickets.
     */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderDTO> cancelOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
