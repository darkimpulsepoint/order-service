package by.darkimpulsepoint.orderservice.security;

import by.darkimpulsepoint.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("access")
@RequiredArgsConstructor
public class AccessChecker {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

    private final OrderRepository orderRepository;

    public boolean isOwner(Long userId) {
        return userId != null && SecurityUtils.findCurrentUserId()
                .map(userId::equals)
                .orElse(false);
    }

    public boolean ownsOrder(Long orderId) {
        return orderId != null && SecurityUtils.findCurrentUserId()
                .map(userId -> orderRepository.existsByIdAndUserId(orderId, userId))
                .orElse(false);
    }

    public boolean isAdminOrOwner(Long userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return admin || isOwner(userId);
    }
}
