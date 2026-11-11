package by.darkimpulsepoint.orderservice.dto;

import by.darkimpulsepoint.orderservice.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

public record OrderFilter(Instant createdFrom, Instant createdTo, List<OrderStatus> statuses) {
}
