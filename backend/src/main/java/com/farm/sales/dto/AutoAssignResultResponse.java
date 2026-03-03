package com.farm.sales.dto;

import java.util.List;

public record AutoAssignResultResponse(
    int totalApprovedOrders,
    int assignedOrders,
    int unassignedOrders,
    double estimatedTotalDistanceKm,
    List<AutoAssignItemResponse> assignments
) {
}
