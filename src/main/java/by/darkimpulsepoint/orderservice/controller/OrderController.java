package by.darkimpulsepoint.orderservice.controller;

import by.darkimpulsepoint.orderservice.dto.CreateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.OrderFilter;
import by.darkimpulsepoint.orderservice.dto.OrderResponse;
import by.darkimpulsepoint.orderservice.dto.PageResponse;
import by.darkimpulsepoint.orderservice.dto.UpdateOrderRequest;
import by.darkimpulsepoint.orderservice.dto.UpdateOrderStatusRequest;
import by.darkimpulsepoint.orderservice.entity.OrderStatus;
import by.darkimpulsepoint.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.order().id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PreAuthorize("hasRole('ADMIN') or @access.ownsOrder(#id)")
    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/orders")
    public ResponseEntity<PageResponse<OrderResponse>> getAll(
            @RequestParam(name = "createdFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(name = "createdTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(name = "statuses", required = false) List<OrderStatus> statuses,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        OrderFilter filter = new OrderFilter(createdFrom, createdTo, statuses);
        return ResponseEntity.ok(PageResponse.from(orderService.getAll(filter, pageable)));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.isOwner(#userId)")
    @GetMapping("/users/{userId}/orders")
    public ResponseEntity<PageResponse<OrderResponse>> getByUserId(
            @PathVariable("userId") Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(orderService.getByUserId(userId, pageable)));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.ownsOrder(#id)")
    @PutMapping("/orders/{id}")
    public ResponseEntity<OrderResponse> update(@PathVariable("id") Long id,
                                                @Valid @RequestBody UpdateOrderRequest request) {
        return ResponseEntity.ok(orderService.update(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable("id") Long id,
                                                      @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request.status()));
    }

    @PreAuthorize("hasRole('ADMIN') or @access.ownsOrder(#id)")
    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
