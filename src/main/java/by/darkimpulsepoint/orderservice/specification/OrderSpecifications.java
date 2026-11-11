package by.darkimpulsepoint.orderservice.specification;

import by.darkimpulsepoint.orderservice.entity.Order;
import by.darkimpulsepoint.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;

public final class OrderSpecifications {

    private static final String CREATED_AT = "createdAt";

    private OrderSpecifications() {
    }

    /**
     * Every argument is optional: a missing bound or an empty status list does not filter.
     */
    public static Specification<Order> byFilter(Instant createdFrom, Instant createdTo, Collection<OrderStatus> statuses) {
        return createdFrom(createdFrom)
                .and(createdTo(createdTo))
                .and(statusIn(statuses));
    }

    public static Specification<Order> createdFrom(Instant from) {
        return (root, query, cb) -> from == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    public static Specification<Order> createdTo(Instant to) {
        return (root, query, cb) -> to == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get(CREATED_AT), to);
    }

    public static Specification<Order> statusIn(Collection<OrderStatus> statuses) {
        return (root, query, cb) -> statuses == null || statuses.isEmpty()
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }
}
