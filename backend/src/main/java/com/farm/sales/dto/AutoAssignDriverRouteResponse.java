package com.farm.sales.dto;

import java.util.List;

public record AutoAssignDriverRouteResponse(
    Long driverId,
    String driverName,
    int assignedOrders,
    double estimatedRouteDistanceKm,
    List<AutoAssignRoutePointResponse> points
) {
}
