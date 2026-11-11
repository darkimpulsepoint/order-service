package by.darkimpulsepoint.orderservice.dto;

import java.time.LocalDate;

/**
 * User data received from user-service.
 */
public record UserInfo(
        Long id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active) {
}
