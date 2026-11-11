package by.darkimpulsepoint.orderservice.integration;

import by.darkimpulsepoint.orderservice.dto.CreateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.OrderItemRequest;
import by.darkimpulsepoint.orderservice.dto.OrderResponse;
import by.darkimpulsepoint.orderservice.dto.PageResponse;
import by.darkimpulsepoint.orderservice.dto.UpdateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.UpdateOrderStatusRequest;
import by.darkimpulsepoint.orderservice.entity.OrderStatus;
import by.darkimpulsepoint.orderservice.exception.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OrderControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final Long IVAN_ID = 7L;
    private static final String IVAN_EMAIL = "ivan@mail.com";
    private static final Long PETR_ID = 8L;
    private static final String PETR_EMAIL = "petr@mail.com";

    private Long phoneId;
    private Long cableId;

    @BeforeEach
    void setUp() {
        phoneId = createItem("Phone", "500.00");
        cableId = createItem("Cable", "10.50");
        stubUser(IVAN_ID, IVAN_EMAIL);
        stubUser(PETR_ID, PETR_EMAIL);
    }

    @Test
    void createOrder_returns201WithOrderAndUser() {
        ResponseEntity<OrderResponse> response = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest(IVAN_EMAIL, List.of(
                        new OrderItemRequest(phoneId, 1), new OrderItemRequest(cableId, 2))))
                .retrieve().toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OrderResponse body = response.getBody();
        assertThat(body.order().id()).isNotNull();
        assertThat(body.order().userId()).isEqualTo(IVAN_ID);
        assertThat(body.order().status()).isEqualTo(OrderStatus.NEW);
        assertThat(body.order().totalPrice()).isEqualByComparingTo("521.00");
        assertThat(body.order().items()).hasSize(2);
        assertThat(body.order().createdAt()).isNotNull();
        assertThat(body.user().email()).isEqualTo(IVAN_EMAIL);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(ORDERS + "/" + body.order().id());
        // the caller's token is forwarded to user-service
        USER_SERVICE.verify(getRequestedFor(urlPathEqualTo(USERS + "/by-email"))
                .withHeader("Authorization", matching("Bearer .+")));
    }

    @Test
    void createOrder_withUnknownEmail_returns404() {
        USER_SERVICE.stubFor(get(urlPathEqualTo(USERS + "/by-email"))
                .withQueryParam("email", equalTo("ghost@mail.com"))
                .willReturn(aResponse().withStatus(404)));

        ResponseEntity<ApiError> response = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest("ghost@mail.com", List.of(new OrderItemRequest(phoneId, 1))))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("ghost@mail.com");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrder_withUnknownItem_returns404() {
        ResponseEntity<ApiError> response = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest(IVAN_EMAIL, List.of(new OrderItemRequest(999L, 1))))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrder_withInvalidBody_returns400() {
        ResponseEntity<ApiError> response = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest("not-an-email", List.of()))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().validationErrors()).containsKeys("userEmail", "items");
    }

    @Test
    void createOrder_asUser_forHimself_returns201() {
        ResponseEntity<OrderResponse> response = clientWithToken(userToken(IVAN_ID)).post().uri(ORDERS)
                .body(new CreateOrderRequest(IVAN_EMAIL, List.of(new OrderItemRequest(phoneId, 1))))
                .retrieve().toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().order().userId()).isEqualTo(IVAN_ID);
    }

    @Test
    void createOrder_asUser_forAnotherUser_returns403() {
        ResponseEntity<ApiError> response = clientWithToken(userToken(IVAN_ID)).post().uri(ORDERS)
                .body(new CreateOrderRequest(PETR_EMAIL, List.of(new OrderItemRequest(phoneId, 1))))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrder_withoutToken_returns401() {
        ResponseEntity<ApiError> response = anonymous.post().uri(ORDERS)
                .body(new CreateOrderRequest(IVAN_EMAIL, List.of(new OrderItemRequest(phoneId, 1))))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOrder_returnsOrderWithUser() {
        Long orderId = createOrder(IVAN_EMAIL, phoneId, 1);

        ResponseEntity<OrderResponse> response = admin.get().uri(ORDERS + "/{id}", orderId)
                .retrieve().toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().order().id()).isEqualTo(orderId);
        assertThat(response.getBody().user().id()).isEqualTo(IVAN_ID);
    }

    @Test
    void getOrder_ofAnotherUser_returns403() {
        Long orderId = createOrder(PETR_EMAIL, phoneId, 1);

        ResponseEntity<ApiError> response = clientWithToken(userToken(IVAN_ID)).get().uri(ORDERS + "/{id}", orderId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getOrder_whenMissing_returns404() {
        ResponseEntity<ApiError> response = admin.get().uri(ORDERS + "/{id}", 999)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getOrders_filtersByStatusesAndCreationDate() {
        Long newOrder = createOrder(IVAN_EMAIL, phoneId, 1);
        Long shipped = createOrder(PETR_EMAIL, cableId, 1);
        Long cancelled = createOrder(IVAN_EMAIL, cableId, 2);
        changeStatus(shipped, OrderStatus.SHIPPED);
        changeStatus(cancelled, OrderStatus.CANCELLED);
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        PageResponse<OrderResponse> byStatus = getPage(
                ORDERS + "?statuses=NEW,SHIPPED&createdFrom=" + from + "&createdTo=" + to + "&sort=id");
        PageResponse<OrderResponse> future = getPage(ORDERS + "?createdFrom=" + to);

        assertThat(byStatus.content()).extracting(response -> response.order().id())
                .containsExactly(newOrder, shipped);
        assertThat(byStatus.content()).allSatisfy(response -> assertThat(response.user()).isNotNull());
        assertThat(future.totalElements()).isZero();
    }

    @Test
    void getOrders_withWrongStatus_returns400() {
        ResponseEntity<ApiError> response = admin.get().uri(ORDERS + "?statuses=LOST")
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getOrders_asUser_returns403() {
        ResponseEntity<ApiError> response = clientWithToken(userToken(IVAN_ID)).get().uri(ORDERS)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getOrdersOfUser_returnsOnlyHisOrders() {
        Long ivanOrder = createOrder(IVAN_EMAIL, phoneId, 1);
        createOrder(PETR_EMAIL, phoneId, 1);
        RestClient ivan = clientWithToken(userToken(IVAN_ID));

        ResponseEntity<PageResponse<OrderResponse>> own = ivan.get().uri(USERS + "/{id}/orders", IVAN_ID)
                .retrieve().toEntity(new ParameterizedTypeReference<>() {
                });
        ResponseEntity<ApiError> foreign = ivan.get().uri(USERS + "/{id}/orders", PETR_ID)
                .retrieve().toEntity(ApiError.class);

        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(own.getBody().content()).extracting(response -> response.order().id()).containsExactly(ivanOrder);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateOrder_replacesItemsAndRecalculatesTotal() {
        Long orderId = createOrder(IVAN_EMAIL, phoneId, 1);

        ResponseEntity<OrderResponse> response = clientWithToken(userToken(IVAN_ID)).put().uri(ORDERS + "/{id}", orderId)
                .body(new UpdateOrderRequest(List.of(new OrderItemRequest(phoneId, 2), new OrderItemRequest(cableId, 1))))
                .retrieve().toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().order().totalPrice()).isEqualByComparingTo("1010.50");
        assertThat(response.getBody().order().items()).hasSize(2);
        assertThat(response.getBody().user()).isNotNull();
        Integer lines = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(lines).isEqualTo(2);
    }

    @Test
    void updateOrder_whenNotNew_returns409() {
        Long orderId = createOrder(IVAN_EMAIL, phoneId, 1);
        changeStatus(orderId, OrderStatus.SHIPPED);

        ResponseEntity<ApiError> response = admin.put().uri(ORDERS + "/{id}", orderId)
                .body(new UpdateOrderRequest(List.of(new OrderItemRequest(cableId, 1))))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateStatus_asUser_returns403() {
        Long orderId = createOrder(IVAN_EMAIL, phoneId, 1);

        ResponseEntity<ApiError> response = clientWithToken(userToken(IVAN_ID)).patch()
                .uri(ORDERS + "/{id}/status", orderId)
                .body(new UpdateOrderStatusRequest(OrderStatus.DELIVERED))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteOrder_softDeletesIt() {
        Long orderId = createOrder(IVAN_EMAIL, phoneId, 1);

        ResponseEntity<Void> deleted = clientWithToken(userToken(IVAN_ID)).delete().uri(ORDERS + "/{id}", orderId)
                .retrieve().toBodilessEntity();

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Boolean flag = jdbcTemplate.queryForObject("SELECT deleted FROM orders WHERE id = ?", Boolean.class, orderId);
        assertThat(flag).isTrue();
        Integer lines = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(lines).isEqualTo(1);
        assertThat(admin.get().uri(ORDERS + "/{id}", orderId).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getPage(ORDERS).totalElements()).isZero();
    }

    @Test
    void deleteOrder_ofAnotherUser_returns403() {
        Long orderId = createOrder(PETR_EMAIL, phoneId, 1);

        ResponseEntity<ApiError> response = clientWithToken(userToken(IVAN_ID)).delete().uri(ORDERS + "/{id}", orderId)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(orderRepository.existsById(orderId)).isTrue();
    }

    private Long createOrder(String email, Long itemId, int quantity) {
        ResponseEntity<OrderResponse> response = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest(email, List.of(new OrderItemRequest(itemId, quantity))))
                .retrieve().toEntity(OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().order().id();
    }

    private void changeStatus(Long orderId, OrderStatus status) {
        ResponseEntity<OrderResponse> response = admin.patch().uri(ORDERS + "/{id}/status", orderId)
                .body(new UpdateOrderStatusRequest(status))
                .retrieve().toEntity(OrderResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().order().status()).isEqualTo(status);
    }

    private PageResponse<OrderResponse> getPage(String uri) {
        ResponseEntity<PageResponse<OrderResponse>> response = admin.get().uri(uri)
                .retrieve().toEntity(new ParameterizedTypeReference<>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
}
