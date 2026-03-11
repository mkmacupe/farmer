package com.farm.sales.dto;

import java.util.List;

public record AutoAssignRouteTripResponse(
    int tripNumber,
    int assignedOrders,
    double estimatedRouteDistanceKm,
    double totalWeightKg,
    double totalVolumeM3,
    boolean returnsToDepot,
    List<AutoAssignRoutePointResponse> points,
    List<AutoAssignRoutePathPointResponse> path
) {
}
