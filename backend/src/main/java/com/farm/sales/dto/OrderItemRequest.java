package com.farm.sales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
    @NotNull(message = "Товар обязателен") Long productId,
    @NotNull(message = "Количество обязательно")
    @Min(value = 1, message = "Количество должно быть не меньше 1")
    Integer quantity
) {
}
