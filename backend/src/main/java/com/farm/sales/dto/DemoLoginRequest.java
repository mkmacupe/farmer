package com.farm.sales.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
    @NotBlank(message = "Логин обязателен") String username
) {
}
