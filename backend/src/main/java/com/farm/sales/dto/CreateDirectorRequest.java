package com.farm.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDirectorRequest(
    @NotBlank(message = "Логин обязателен")
    @Size(min = 4, max = 50, message = "Логин должен содержать от 4 до 50 символов")
    String username,
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 120, message = "Пароль должен содержать от 8 до 120 символов")
    String password,
    @NotBlank(message = "ФИО обязательно")
    @Size(min = 3, max = 120, message = "ФИО должно содержать от 3 до 120 символов")
    String fullName,
    @Size(max = 30, message = "Телефон не должен превышать 30 символов")
    @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "Телефон содержит недопустимые символы")
    String phone,
    @NotBlank(message = "Название юрлица обязательно")
    @Size(min = 2, max = 255, message = "Название юрлица должно содержать от 2 до 255 символов")
    String legalEntityName
) {
}
