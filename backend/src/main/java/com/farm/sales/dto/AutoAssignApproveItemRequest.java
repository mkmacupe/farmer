package com.farm.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AutoAssignApproveItemRequest(
    @NotNull Long orderId,
    @NotNull Long driverId,
    @PositiveOrZero Integer stopSequence
) {
}
