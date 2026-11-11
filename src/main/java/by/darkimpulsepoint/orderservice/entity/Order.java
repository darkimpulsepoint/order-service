package by.darkimpulsepoint.orderservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
@SQLRestriction("deleted = false")
public class Order extends AbstractEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "total_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean deleted;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();


    public void setItemQuantities(Map<Item, Integer> quantities) {
        Set<Long> itemIds = quantities.keySet().stream().map(Item::getId).collect(Collectors.toSet());
        items.removeIf(line -> !itemIds.contains(line.getItem().getId()));
        quantities.forEach((item, quantity) -> items.stream()
                .filter(line -> line.getItem().getId().equals(item.getId()))
                .findFirst()
                .ifPresentOrElse(line -> line.setQuantity(quantity), () -> addItem(new OrderItem(item, quantity))));
        recalculateTotalPrice();
    }

    public void markDeleted() {
        deleted = true;
    }

    private void addItem(OrderItem line) {
        line.setOrder(this);
        items.add(line);
    }

    private void recalculateTotalPrice() {
        totalPrice = items.stream()
                .map(OrderItem::getLinePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
