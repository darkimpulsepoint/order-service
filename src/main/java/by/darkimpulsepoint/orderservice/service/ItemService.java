package by.darkimpulsepoint.orderservice.service;

import by.darkimpulsepoint.orderservice.dto.ItemDto;
import by.darkimpulsepoint.orderservice.entity.Item;
import by.darkimpulsepoint.orderservice.exception.DuplicateEntityException;
import by.darkimpulsepoint.orderservice.exception.ResourceNotFoundException;
import by.darkimpulsepoint.orderservice.mapper.ItemMapper;
import by.darkimpulsepoint.orderservice.repository.ItemRepository;
import by.darkimpulsepoint.orderservice.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ItemMapper itemMapper;

    @Transactional
    public ItemDto create(ItemDto dto) {
        if (itemRepository.existsByName(dto.name())) {
            throw new DuplicateEntityException("Item with name " + dto.name() + " already exists");
        }
        return itemMapper.toDto(itemRepository.save(itemMapper.toEntity(dto)));
    }

    @Transactional(readOnly = true)
    public ItemDto getById(Long id) {
        return itemMapper.toDto(findItem(id));
    }

    @Transactional(readOnly = true)
    public Page<ItemDto> getAll(Pageable pageable) {
        return itemRepository.findAll(pageable).map(itemMapper::toDto);
    }

    @Transactional
    public ItemDto update(Long id, ItemDto dto) {
        Item item = findItem(id);
        if (itemRepository.existsByNameAndIdNot(dto.name(), id)) {
            throw new DuplicateEntityException("Item with name " + dto.name() + " already exists");
        }
        itemMapper.updateEntity(dto, item);
        return itemMapper.toDto(itemRepository.saveAndFlush(item));
    }

    @Transactional
    public void delete(Long id) {
        Item item = findItem(id);
        if (orderItemRepository.existsByItemId(id)) {
            throw new DuplicateEntityException("Item with id " + id + " is used in orders and cannot be deleted");
        }
        itemRepository.delete(item);
    }

    private Item findItem(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item with id " + id + " not found"));
    }
}
