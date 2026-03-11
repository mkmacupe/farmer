package com.farm.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record AutoAssignApproveItemRequest(
    @NotNull Long orderId,
    @NotNull Long driverId,
    @Positive Integer tripNumber,
    @PositiveOrZero Integer stopSequence,
    @PositiveOrZero Double estimatedDistanceKm
) {
}
