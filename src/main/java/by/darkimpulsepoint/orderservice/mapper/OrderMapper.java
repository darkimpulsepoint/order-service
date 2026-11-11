package by.darkimpulsepoint.orderservice.mapper;

import by.darkimpulsepoint.orderservice.dto.OrderDto;
import by.darkimpulsepoint.orderservice.dto.OrderItemDto;
import by.darkimpulsepoint.orderservice.entity.Order;
import by.darkimpulsepoint.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    OrderDto toDto(Order order);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "itemName", source = "item.name")
    @Mapping(target = "itemPrice", source = "item.price")
    OrderItemDto toDto(OrderItem orderItem);
}
