package by.darkimpulsepoint.orderservice.dto;

/**
 * Order together with its owner. {@code user} is {@code null} when user-service could not provide the user
 * (it is unavailable or the user was removed), the order itself is still returned.
 */
public record OrderResponse(OrderDto order, UserInfo user) {
}
