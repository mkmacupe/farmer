package com.farm.sales.dto;

import jakarta.validation.constraints.NotNull;

public record DriverAssignRequest(
    @NotNull(message = "Необходимо выбрать водителя") Long driverId
) {
}
