package by.darkimpulsepoint.orderservice.controller;

import by.darkimpulsepoint.orderservice.dto.ItemDto;
import by.darkimpulsepoint.orderservice.dto.PageResponse;
import by.darkimpulsepoint.orderservice.exception.InvalidRequestException;
import by.darkimpulsepoint.orderservice.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ItemDto> create(@Valid @RequestBody ItemDto dto) {
        ItemDto created = itemService.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDto> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(itemService.getById(id));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ItemDto>> getAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(itemService.getAll(pageable)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ItemDto> update(@PathVariable("id") Long id, @Valid @RequestBody ItemDto dto) {
        if (dto.id() != null && !dto.id().equals(id)) {
            throw new InvalidRequestException("Item id in body (" + dto.id() + ") does not match path id (" + id + ")");
        }
        return ResponseEntity.ok(itemService.update(id, dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
