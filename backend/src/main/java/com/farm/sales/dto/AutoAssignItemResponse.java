package com.farm.sales.dto;

public record AutoAssignItemResponse(
    Long orderId,
    Long driverId,
    String driverName,
    double estimatedDistanceKm
) {
}
