package com.farm.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank(message = "ФИО обязательно")
    @Size(min = 3, max = 120, message = "ФИО должно содержать от 3 до 120 символов")
    String fullName,
    @Size(max = 30, message = "Телефон не должен превышать 30 символов")
    @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "Телефон содержит недопустимые символы")
    String phone
) {
}
