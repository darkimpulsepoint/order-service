package by.darkimpulsepoint.orderservice.integration;

import by.darkimpulsepoint.orderservice.dto.CreateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.ItemDto;
import by.darkimpulsepoint.orderservice.dto.OrderItemRequest;
import by.darkimpulsepoint.orderservice.exception.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ITEMS = "/api/v1/items";

    @Test
    void createItem_returns201WithLocation() {
        ResponseEntity<ItemDto> response = admin.post().uri(ITEMS)
                .body(new ItemDto(null, "Phone", new BigDecimal("499.90"), null, null))
                .retrieve().toEntity(ItemDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo(ITEMS + "/" + response.getBody().id());
    }

    @Test
    void createItem_withDuplicateName_returns409() {
        createItem("Phone", "10.00");

        ResponseEntity<ApiError> response = admin.post().uri(ITEMS)
                .body(new ItemDto(null, "Phone", new BigDecimal("20.00"), null, null))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createItem_withInvalidBody_returns400() {
        ResponseEntity<ApiError> response = admin.post().uri(ITEMS)
                .body(new ItemDto(null, " ", new BigDecimal("-1"), null, null))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().validationErrors()).containsKeys("name", "price");
    }

    @Test
    void createItem_asUser_returns403() {
        ResponseEntity<ApiError> response = clientWithToken(userToken(1L)).post().uri(ITEMS)
                .body(new ItemDto(null, "Phone", new BigDecimal("10.00"), null, null))
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(itemRepository.count()).isZero();
    }

    @Test
    void getItems_asUser_returns200() {
        Long id = createItem("Phone", "10.00");

        ResponseEntity<ItemDto> response = clientWithToken(userToken(1L)).get().uri(ITEMS + "/{id}", id)
                .retrieve().toEntity(ItemDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Phone");
    }

    @Test
    void updateItem_returns200() {
        Long id = createItem("Phone", "10.00");

        ResponseEntity<ItemDto> response = admin.put().uri(ITEMS + "/{id}", id)
                .body(new ItemDto(null, "Phone X", new BigDecimal("15.00"), null, null))
                .retrieve().toEntity(ItemDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Phone X");
        assertThat(response.getBody().price()).isEqualByComparingTo("15.00");
    }

    @Test
    void deleteItem_returns204_andMissingItemReturns404() {
        Long id = createItem("Phone", "10.00");

        assertThat(admin.delete().uri(ITEMS + "/{id}", id).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(admin.get().uri(ITEMS + "/{id}", id).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteItem_usedInOrder_returns409() {
        Long id = createItem("Phone", "10.00");
        stubUser(7L, "ivan@mail.com");
        admin.post().uri("/api/v1/orders")
                .body(new CreateOrderRequest("ivan@mail.com", List.of(new OrderItemRequest(id, 1))))
                .retrieve().toBodilessEntity();

        ResponseEntity<ApiError> response = admin.delete().uri(ITEMS + "/{id}", id)
                .retrieve().toEntity(ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(itemRepository.existsById(id)).isTrue();
    }
}
