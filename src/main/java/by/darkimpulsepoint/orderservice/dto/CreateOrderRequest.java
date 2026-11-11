package by.darkimpulsepoint.orderservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "userEmail must not be blank")
        @Email(message = "userEmail must be a valid email address")
        String userEmail,

        @NotEmpty(message = "items must not be empty")
        @Size(max = 100, message = "order can contain at most 100 lines")
        List<@Valid @NotNull(message = "item must not be null") OrderItemRequest> items) {
}
