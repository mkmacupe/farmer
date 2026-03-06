package com.farm.sales.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
    @NotBlank(message = "Логин обязателен") String username,
    @NotBlank(message = "Пароль обязателен") String password
) {
}
