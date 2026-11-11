package by.darkimpulsepoint.orderservice.integration;

import by.darkimpulsepoint.orderservice.dto.CreateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.OrderItemRequest;
import by.darkimpulsepoint.orderservice.dto.OrderResponse;
import by.darkimpulsepoint.orderservice.exception.ApiError;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class UserServiceCircuitBreakerIntegrationTest extends AbstractIntegrationTest {

    private static final String ORDERS = "/api/v1/orders";

    @Test
    void createOrder_whenUserServiceFails_returns503() {
        Long itemId = createItem("Phone", "10.00");
        USER_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));

        ResponseEntity<ApiError> response = createOrder(itemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void createOrder_whenUserServiceIsTooSlow_returns503() {
        Long itemId = createItem("Phone", "10.00");
        // read timeout in tests is 1s
        USER_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse()
                .withFixedDelay((int) Duration.ofSeconds(2).toMillis())
                .withStatus(200)));

        ResponseEntity<ApiError> response = createOrder(itemId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void getOrder_whenUserServiceFails_returnsOrderWithoutUser() {
        Long itemId = createItem("Phone", "10.00");
        stubUser(7L, "ivan@mail.com");
        Long orderId = admin.post().uri(ORDERS)
                .body(new CreateOrderRequest("ivan@mail.com", List.of(new OrderItemRequest(itemId, 1))))
                .retrieve().body(OrderResponse.class).order().id();
        USER_SERVICE.resetAll();
        USER_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(503)));

        ResponseEntity<OrderResponse> response = admin.get().uri(ORDERS + "/{id}", orderId)
                .retrieve().toEntity(OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().order().id()).isEqualTo(orderId);
        assertThat(response.getBody().user()).isNull();
    }

    @Test
    void circuitOpens_afterRepeatedFailures_andStopsCallingUserService() {
        Long itemId = createItem("Phone", "10.00");
        USER_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(500)));
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userService");

        // minimum-number-of-calls is 5 and every call fails
        for (int i = 0; i < 5; i++) {
            assertThat(createOrder(itemId).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = USER_SERVICE.findAll(anyRequestedFor(anyUrl())).size();

        ResponseEntity<ApiError> rejected = createOrder(itemId);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(USER_SERVICE.findAll(anyRequestedFor(anyUrl()))).hasSize(callsWhenOpened);
    }

    @Test
    void notFoundAnswers_doNotOpenTheCircuit() {
        Long itemId = createItem("Phone", "10.00");
        USER_SERVICE.stubFor(get(anyUrl()).willReturn(aResponse().withStatus(404)));

        for (int i = 0; i < 6; i++) {
            assertThat(createOrder(itemId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("userService").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private ResponseEntity<ApiError> createOrder(Long itemId) {
        return admin.post().uri(ORDERS)
                .body(new CreateOrderRequest("ivan@mail.com", List.of(new OrderItemRequest(itemId, 1))))
                .retrieve().toEntity(ApiError.class);
    }
}
