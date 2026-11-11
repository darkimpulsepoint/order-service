package by.darkimpulsepoint.orderservice.service;

import by.darkimpulsepoint.orderservice.dto.ItemDto;
import by.darkimpulsepoint.orderservice.entity.Item;
import by.darkimpulsepoint.orderservice.exception.DuplicateEntityException;
import by.darkimpulsepoint.orderservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.orderservice.mapper.ItemMapperImpl;
import by.darkimpulsepoint.orderservice.repository.ItemRepository;
import by.darkimpulsepoint.orderservice.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    private static final Long ITEM_ID = 1L;

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private ItemService itemService;

    @BeforeEach
    void setUp() {
        itemService = new ItemService(itemRepository, orderItemRepository, new ItemMapperImpl());
    }

    @Test
    void create_whenNameIsFree_savesItem() {
        when(itemRepository.existsByName("Phone")).thenReturn(false);
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ItemDto created = itemService.create(request("Phone", "499.90"));

        assertThat(created.id()).isEqualTo(ITEM_ID);
        assertThat(created.name()).isEqualTo("Phone");
        assertThat(created.price()).isEqualByComparingTo("499.90");
    }

    @Test
    void create_whenNameExists_throwsDuplicate() {
        when(itemRepository.existsByName("Phone")).thenReturn(true);

        assertThatThrownBy(() -> itemService.create(request("Phone", "1.00")))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("Phone");
        verify(itemRepository, never()).save(any());
    }

    @Test
    void getById_whenExists_returnsDto() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item("Phone", "10.00")));

        assertThat(itemService.getById(ITEM_ID).name()).isEqualTo("Phone");
    }

    @Test
    void getById_whenMissing_throwsNotFound() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getById(ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(ITEM_ID));
    }

    @Test
    void getAll_mapsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(itemRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(item("Phone", "10.00")), pageable, 1));

        Page<ItemDto> page = itemService.getAll(pageable);

        assertThat(page.getContent()).extracting(ItemDto::name).containsExactly("Phone");
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void update_changesNameAndPrice() {
        Item item = item("Phone", "10.00");
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(itemRepository.existsByNameAndIdNot("Tablet", ITEM_ID)).thenReturn(false);
        when(itemRepository.saveAndFlush(item)).thenReturn(item);

        ItemDto updated = itemService.update(ITEM_ID, request("Tablet", "20.00"));

        assertThat(updated.name()).isEqualTo("Tablet");
        assertThat(updated.price()).isEqualByComparingTo("20.00");
        assertThat(updated.id()).isEqualTo(ITEM_ID);
    }

    @Test
    void update_withNameOfAnotherItem_throwsDuplicate() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item("Phone", "10.00")));
        when(itemRepository.existsByNameAndIdNot("Tablet", ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> itemService.update(ITEM_ID, request("Tablet", "20.00")))
                .isInstanceOf(DuplicateEntityException.class);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    @Test
    void update_whenMissing_throwsNotFound() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.update(ITEM_ID, request("Tablet", "20.00")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_whenNotUsed_deletesItem() {
        Item item = item("Phone", "10.00");
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(orderItemRepository.existsByItemId(ITEM_ID)).thenReturn(false);

        itemService.delete(ITEM_ID);

        verify(itemRepository).delete(item);
    }

    @Test
    void delete_whenUsedInOrders_throwsConflict() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item("Phone", "10.00")));
        when(orderItemRepository.existsByItemId(ITEM_ID)).thenReturn(true);

        assertThatThrownBy(() -> itemService.delete(ITEM_ID))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessageContaining("used in orders");
        verify(itemRepository, never()).delete(any());
    }

    @Test
    void delete_whenMissing_throwsNotFound() {
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.delete(ITEM_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static ItemDto request(String name, String price) {
        return new ItemDto(null, name, new BigDecimal(price), null, null);
    }

    private static Item item(String name, String price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        return withId(item);
    }

    private static Item withId(Item item) {
        item.setId(ITEM_ID);
        return item;
    }
}
