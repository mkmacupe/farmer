package com.farm.sales.dto;

import java.util.List;

public record AutoAssignPreviewResponse(
    String depotLabel,
    double depotLatitude,
    double depotLongitude,
    int totalApprovedOrders,
    int plannedOrders,
    int unplannedOrders,
    double estimatedTotalDistanceKm,
    List<AutoAssignDriverRouteResponse> routes,
    boolean approximatePlanningDistances,
    List<String> planningHighlights
) {
}
