package by.darkimpulsepoint.orderservice.dto;

import by.darkimpulsepoint.orderservice.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalPrice,
        List<OrderItemDto> items,
        Instant createdAt,
        Instant updatedAt) {
}
