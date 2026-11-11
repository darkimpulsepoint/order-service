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
import by.darkimpulsepoint.orderservice.exception.UserNotFoundException;
import by.darkimpulsepoint.orderservice.exception.UserServiceUnavailableException;
import by.darkimpulsepoint.orderservice.mapper.OrderMapperImpl;
import by.darkimpulsepoint.orderservice.repository.ItemRepository;
import by.darkimpulsepoint.orderservice.repository.OrderRepository;
import by.darkimpulsepoint.orderservice.security.AccessChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long ORDER_ID = 100L;
    private static final String EMAIL = "ivan@mail.com";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private AccessChecker accessChecker;

    private OrderService orderService;
    private Item phone;
    private Item cable;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, itemRepository, new OrderMapperImpl(),
                userClient, accessChecker);
        phone = item(1L, "Phone", "500.00");
        cable = item(2L, "Cable", "10.50");
    }

    @Test
    void create_savesNewOrderWithTotalAndReturnsUser() {
        UserInfo user = user(true);
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user);
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(true);
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(phone, cable));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        OrderResponse response = orderService.create(new CreateOrderRequest(EMAIL, List.of(
                new OrderItemRequest(1L, 1), new OrderItemRequest(2L, 2))));

        assertThat(response.user()).isEqualTo(user);
        assertThat(response.order().userId()).isEqualTo(USER_ID);
        assertThat(response.order().status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.order().totalPrice()).isEqualByComparingTo("521.00");
        assertThat(response.order().items()).extracting("itemName").containsExactly("Phone", "Cable");
    }

    @Test
    void create_withSameItemTwice_mergesQuantities() {
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user(true));
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(true);
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(cable));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        OrderResponse response = orderService.create(new CreateOrderRequest(EMAIL, List.of(
                new OrderItemRequest(2L, 1), new OrderItemRequest(2L, 3))));

        assertThat(response.order().items()).hasSize(1);
        assertThat(response.order().items().getFirst().quantity()).isEqualTo(4);
        assertThat(response.order().totalPrice()).isEqualByComparingTo("42.00");
    }

    @Test
    void create_withUnknownItem_throwsNotFound() {
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user(true));
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(true);
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(phone));

        CreateOrderRequest request = new CreateOrderRequest(EMAIL, List.of(
                new OrderItemRequest(1L, 1), new OrderItemRequest(99L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_forAnotherUser_throwsAccessDenied() {
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user(true));
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(false);

        CreateOrderRequest request = new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(AccessDeniedException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_forDeactivatedUser_throwsInvalidRequest() {
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user(false));
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(true);

        CreateOrderRequest request = new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("deactivated");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_whenUserMissing_propagatesNotFound() {
        when(userClient.getUserByEmail(EMAIL)).thenThrow(new UserNotFoundException("User with email " + EMAIL + " not found"));

        CreateOrderRequest request = new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void create_whenUserServiceUnavailable_propagatesError() {
        when(userClient.getUserByEmail(EMAIL)).thenThrow(new UserServiceUnavailableException("down"));

        CreateOrderRequest request = new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(UserServiceUnavailableException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getById_returnsOrderWithUser() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.NEW)));
        when(userClient.getUserById(USER_ID)).thenReturn(Optional.of(user(true)));

        OrderResponse response = orderService.getById(ORDER_ID);

        assertThat(response.order().id()).isEqualTo(ORDER_ID);
        assertThat(response.user().email()).isEqualTo(EMAIL);
    }

    @Test
    void getById_whenUserUnavailable_returnsOrderWithoutUser() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.NEW)));
        when(userClient.getUserById(USER_ID)).thenReturn(Optional.empty());

        OrderResponse response = orderService.getById(ORDER_ID);

        assertThat(response.order().id()).isEqualTo(ORDER_ID);
        assertThat(response.user()).isNull();
    }

    @Test
    void getById_whenMissing_throwsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ORDER_ID));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAll_callsUserServiceOncePerUser() {
        Pageable pageable = PageRequest.of(0, 10);
        Order first = order(OrderStatus.NEW);
        Order second = order(OrderStatus.SHIPPED);
        second.setId(ORDER_ID + 1);
        when(orderRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(userClient.getUserById(USER_ID)).thenReturn(Optional.of(user(true)));

        Page<OrderResponse> page = orderService.getAll(
                new OrderFilter(Instant.parse("2026-01-01T00:00:00Z"), null, List.of(OrderStatus.NEW)), pageable);

        assertThat(page.getContent()).hasSize(2).allSatisfy(response -> assertThat(response.user()).isNotNull());
        verify(userClient, times(1)).getUserById(USER_ID);
    }

    @Test
    void getAll_withFromAfterTo_throwsInvalidRequest() {
        OrderFilter filter = new OrderFilter(Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), null);

        Pageable pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> orderService.getAll(filter, pageable))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void getByUserId_returnsOrdersOfUser() {
        Pageable pageable = PageRequest.of(0, 10);
        when(orderRepository.findAllByUserId(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(order(OrderStatus.NEW)), pageable, 1));
        when(userClient.getUserById(USER_ID)).thenReturn(Optional.of(user(true)));

        Page<OrderResponse> page = orderService.getByUserId(USER_ID, pageable);

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> assertThat(response.order().userId()).isEqualTo(USER_ID));
    }

    @Test
    void update_replacesItemsAndRecalculatesTotal() {
        Order order = order(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(cable));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);
        when(userClient.getUserById(USER_ID)).thenReturn(Optional.of(user(true)));

        OrderResponse response = orderService.update(ORDER_ID, new UpdateOrderRequest(List.of(new OrderItemRequest(2L, 3))));

        assertThat(response.order().items()).singleElement()
                .satisfies(line -> assertThat(line.itemName()).isEqualTo("Cable"));
        assertThat(response.order().totalPrice()).isEqualByComparingTo("31.50");
    }

    @Test
    void update_keepsExistingLineOfSameItem() {
        Order order = order(OrderStatus.NEW);
        Long lineId = order.getItems().getFirst().getId();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(phone));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = orderService.update(ORDER_ID, new UpdateOrderRequest(List.of(new OrderItemRequest(1L, 2))));

        assertThat(response.order().items()).singleElement()
                .satisfies(line -> {
                    assertThat(line.id()).isEqualTo(lineId);
                    assertThat(line.quantity()).isEqualTo(2);
                });
        assertThat(response.order().totalPrice()).isEqualByComparingTo("1000.00");
    }

    @Test
    void update_whenNotNew_throwsNotEditable() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.SHIPPED)));

        UpdateOrderRequest request = new UpdateOrderRequest(List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.update(ORDER_ID, request))
                .isInstanceOf(OrderNotEditableException.class)
                .hasMessageContaining("SHIPPED");
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_whenMissing_throwsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        UpdateOrderRequest request = new UpdateOrderRequest(List.of(new OrderItemRequest(1L, 1)));

        assertThatThrownBy(() -> orderService.update(ORDER_ID, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_changesStatus() {
        Order order = order(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = orderService.updateStatus(ORDER_ID, OrderStatus.SHIPPED);

        assertThat(response.order().status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void updateStatus_ofFinishedOrder_throwsNotEditable() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order(OrderStatus.CANCELLED)));

        assertThatThrownBy(() -> orderService.updateStatus(ORDER_ID, OrderStatus.NEW))
                .isInstanceOf(OrderNotEditableException.class);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void delete_marksOrderDeleted() {
        Order order = order(OrderStatus.NEW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.delete(ORDER_ID);

        assertThat(order.isDeleted()).isTrue();
        verify(orderRepository, never()).delete(any(Order.class));
    }

    @Test
    void delete_whenMissing_throwsNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_passesNewOrderToRepository() {
        when(userClient.getUserByEmail(EMAIL)).thenReturn(user(true));
        when(accessChecker.isAdminOrOwner(USER_ID)).thenReturn(true);
        when(itemRepository.findAllById(anyIterable())).thenReturn(List.of(phone));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        orderService.create(new CreateOrderRequest(EMAIL, List.of(new OrderItemRequest(1L, 1))));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).singleElement()
                .satisfies(line -> assertThat(line.getOrder()).isSameAs(captor.getValue()));
        assertThat(captor.getValue().isDeleted()).isFalse();
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setUserId(USER_ID);
        order.setItemQuantities(Map.of(phone, 1));
        order.getItems().getFirst().setId(1000L);
        order.setStatus(status);
        return withId(order);
    }

    private static Order withId(Order order) {
        order.setId(ORDER_ID);
        return order;
    }

    private static Item item(Long id, String name, String price) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static UserInfo user(boolean active) {
        return new UserInfo(USER_ID, "Ivan", "Petrov", LocalDate.of(1995, 5, 20), EMAIL, active);
    }
}
