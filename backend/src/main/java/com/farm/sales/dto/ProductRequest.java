package com.farm.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank(message = "Название товара обязательно")
    @Size(max = 120, message = "Название товара не должно превышать 120 символов")
    String name,
    @NotBlank(message = "Категория обязательна")
    @Size(max = 100, message = "Категория не должна превышать 100 символов")
    String category,
    @Size(max = 500, message = "Описание не должно превышать 500 символов")
    String description,
    @Size(max = 500, message = "URL фото не должен превышать 500 символов")
    String photoUrl,
    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.0", message = "Цена должна быть неотрицательной")
    BigDecimal price,
    @NotNull(message = "Количество обязательно")
    @Min(value = 0, message = "Количество должно быть неотрицательным")
    Integer stockQuantity
) {
}
