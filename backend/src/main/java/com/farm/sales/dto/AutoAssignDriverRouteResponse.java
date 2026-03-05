package com.farm.sales.dto;

import java.util.List;

public record AutoAssignDriverRouteResponse(
    Long driverId,
    String driverName,
    int assignedOrders,
    double estimatedRouteDistanceKm,
    double totalWeightKg,
    double totalVolumeM3,
    List<AutoAssignRoutePointResponse> points
) {
}
