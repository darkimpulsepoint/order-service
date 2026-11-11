package by.darkimpulsepoint.orderservice.client;

import by.darkimpulsepoint.orderservice.dto.UserInfo;
import by.darkimpulsepoint.orderservice.exception.UserNotFoundException;
import by.darkimpulsepoint.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;


@Slf4j
@Component
public class UserClient {

    private static final String CIRCUIT_BREAKER = "userService";
    private static final String USERS = "/api/v1/users";

    private final RestClient restClient;

    public UserClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getUserByEmailFallback")
    public UserInfo getUserByEmail(String email) {
        try {
            return restClient.get()
                    .uri(USERS + "/by-email?email={email}", email)
                    .retrieve()
                    .body(UserInfo.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new UserNotFoundException("User with email " + email + " not found");
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new AccessDeniedException("Orders can be created only for yourself");
        }
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getUserByIdFallback")
    public Optional<UserInfo> getUserById(Long id) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(USERS + "/{id}", id)
                    .retrieve()
                    .body(UserInfo.class));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden ex) {
            log.warn("User {} is not available for the caller: {}", id, ex.getStatusCode());
            return Optional.empty();
        }
    }

    private UserInfo getUserByEmailFallback(String email, Exception ex) {
        if (ex instanceof UserNotFoundException || ex instanceof AccessDeniedException) {
            throw (RuntimeException) ex;
        }
        log.warn("user-service is unavailable, cannot find user by email: {}", ex.toString());
        throw new UserServiceUnavailableException("User service is unavailable, try again later", ex);
    }

    private Optional<UserInfo> getUserByIdFallback(Long id, Exception ex) {
        log.warn("user-service is unavailable, user {} is returned empty: {}", id, ex.toString());
        return Optional.empty();
    }
}
