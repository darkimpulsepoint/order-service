package by.darkimpulsepoint.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces the lines of an order that is still {@code NEW}.
 */
public record UpdateOrderRequest(
        @NotEmpty(message = "items must not be empty")
        @Size(max = 100, message = "order can contain at most 100 lines")
        List<@Valid @NotNull(message = "item must not be null") OrderItemRequest> items) {
}
