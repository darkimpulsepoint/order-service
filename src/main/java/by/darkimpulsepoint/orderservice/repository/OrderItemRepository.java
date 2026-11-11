package by.darkimpulsepoint.orderservice.repository;

import by.darkimpulsepoint.orderservice.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Counts lines of soft-deleted orders too: they still reference the item. */
    boolean existsByItemId(Long itemId);
}
