package com.farm.sales.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StoreAddressRequest(
    @NotBlank(message = "Название точки обязательно")
    @Size(min = 2, max = 120, message = "Название точки должно содержать от 2 до 120 символов")
    String label,
    @NotBlank(message = "Адрес обязателен")
    @Size(min = 5, max = 500, message = "Адрес должен содержать от 5 до 500 символов")
    String addressLine,
    @DecimalMin(value = "-90.0", inclusive = true, message = "Широта должна быть не меньше -90")
    @DecimalMax(value = "90.0", inclusive = true, message = "Широта должна быть не больше 90")
    BigDecimal latitude,
    @DecimalMin(value = "-180.0", inclusive = true, message = "Долгота должна быть не меньше -180")
    @DecimalMax(value = "180.0", inclusive = true, message = "Долгота должна быть не больше 180")
    BigDecimal longitude
) {
}
