package by.darkimpulsepoint.orderservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemDto(
        Long id,

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.01", message = "price must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 10 integer and 2 fraction digits")
        BigDecimal price,

        Instant createdAt,
        Instant updatedAt) {
}
