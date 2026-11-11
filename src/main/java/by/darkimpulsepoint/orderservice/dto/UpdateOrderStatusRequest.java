package by.darkimpulsepoint.orderservice.dto;

import by.darkimpulsepoint.orderservice.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status must not be null")
        OrderStatus status) {
}
