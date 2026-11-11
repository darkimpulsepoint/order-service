package by.darkimpulsepoint.orderservice.repository;

import by.darkimpulsepoint.orderservice.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    boolean existsByItemId(Long itemId);
}
