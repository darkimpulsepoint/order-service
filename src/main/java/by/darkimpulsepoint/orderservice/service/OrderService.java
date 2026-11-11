package by.darkimpulsepoint.orderservice.service;

import by.darkimpulsepoint.orderservice.client.UserClient;
import by.darkimpulsepoint.orderservice.dto.CreateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.OrderFilter;
import by.darkimpulsepoint.orderservice.dto.OrderItemRequest;
import by.darkimpulsepoint.orderservice.dto.OrderResponse;
import by.darkimpulsepoint.orderservice.dto.UpdateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.UserInfo;
import by.darkimpulsepoint.orderservice.entity.Item;
import by.darkimpulsepoint.orderservice.entity.Order;
import by.darkimpulsepoint.orderservice.entity.OrderStatus;
import by.darkimpulsepoint.orderservice.exception.InvalidRequestException;
import by.darkimpulsepoint.orderservice.exception.OrderNotEditableException;
import by.darkimpulsepoint.orderservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.orderservice.mapper.OrderMapper;
import by.darkimpulsepoint.orderservice.repository.ItemRepository;
import by.darkimpulsepoint.orderservice.repository.OrderRepository;
import by.darkimpulsepoint.orderservice.security.AccessChecker;
import by.darkimpulsepoint.orderservice.specification.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserClient userClient;
    private final AccessChecker accessChecker;

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        UserInfo user = userClient.getUserByEmail(request.userEmail());
        if (!accessChecker.isAdminOrOwner(user.id())) {
            throw new AccessDeniedException("Orders can be created only for yourself");
        }
        if (Boolean.FALSE.equals(user.active())) {
            throw new InvalidRequestException("User with email " + user.email() + " is deactivated");
        }

        Order order = new Order();
        order.setUserId(user.id());
        order.setStatus(OrderStatus.NEW);
        order.setItemQuantities(resolveItems(request.items()));
        Order saved = orderRepository.save(order);
        return new OrderResponse(orderMapper.toDto(saved), user);
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(Long id) {
        Order order = findOrder(id);
        return new OrderResponse(orderMapper.toDto(order), userClient.getUserById(order.getUserId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAll(OrderFilter filter, Pageable pageable) {
        if (filter.createdFrom() != null && filter.createdTo() != null
                && filter.createdFrom().isAfter(filter.createdTo())) {
            throw new InvalidRequestException("createdFrom must not be after createdTo");
        }
        Page<Order> orders = orderRepository.findAll(
                OrderSpecifications.byFilter(filter.createdFrom(), filter.createdTo(), filter.statuses()), pageable);
        return withUsers(orders);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getByUserId(Long userId, Pageable pageable) {
        return withUsers(orderRepository.findAllByUserId(userId, pageable));
    }

    @Transactional
    public OrderResponse update(Long id, UpdateOrderRequest request) {
        Order order = findOrder(id);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new OrderNotEditableException(
                    "Order with id " + id + " is " + order.getStatus() + ", only NEW orders can be changed");
        }
        order.setItemQuantities(resolveItems(request.items()));
        Order saved = orderRepository.saveAndFlush(order);
        return new OrderResponse(orderMapper.toDto(saved), userClient.getUserById(saved.getUserId()).orElse(null));
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus status) {
        Order order = findOrder(id);
        if (order.getStatus().isFinal() && order.getStatus() != status) {
            throw new OrderNotEditableException(
                    "Order with id " + id + " is already " + order.getStatus() + ", its status cannot be changed");
        }
        order.setStatus(status);
        Order saved = orderRepository.saveAndFlush(order);
        return new OrderResponse(orderMapper.toDto(saved), userClient.getUserById(saved.getUserId()).orElse(null));
    }


    @Transactional
    public void delete(Long id) {
        findOrder(id).markDeleted();
    }

    private Map<Item, Integer> resolveItems(List<OrderItemRequest> lines) {
        Map<Long, Integer> quantities = lines.stream()
                .collect(Collectors.toMap(OrderItemRequest::itemId, OrderItemRequest::quantity,
                        Integer::sum, LinkedHashMap::new));
        Map<Long, Item> items = itemRepository.findAllById(quantities.keySet()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Set<Long> missing = quantities.keySet().stream()
                .filter(itemId -> !items.containsKey(itemId))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Items with ids " + missing + " not found");
        }
        Map<Item, Integer> result = new LinkedHashMap<>();
        quantities.forEach((itemId, quantity) -> result.put(items.get(itemId), quantity));
        return result;
    }

    private Page<OrderResponse> withUsers(Page<Order> orders) {
        Map<Long, Optional<UserInfo>> users = new HashMap<>();
        return orders.map(order -> new OrderResponse(
                orderMapper.toDto(order),
                users.computeIfAbsent(order.getUserId(), userClient::getUserById).orElse(null)));
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + id + " not found"));
    }
}
