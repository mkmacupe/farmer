package com.farm.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record OrderCreateRequest(
    @NotNull(message = "Необходимо выбрать адрес доставки") Long deliveryAddressId,
    @NotEmpty(message = "Добавьте хотя бы один товар в заказ")
    @Size(max = 100, message = "Заказ не может содержать более 100 позиций")
    List<@Valid OrderItemRequest> items
) {
}
