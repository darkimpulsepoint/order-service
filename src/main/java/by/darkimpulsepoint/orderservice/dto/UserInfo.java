package by.darkimpulsepoint.orderservice.dto;

import java.time.LocalDate;

public record UserInfo(
        Long id,
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        Boolean active) {
}
