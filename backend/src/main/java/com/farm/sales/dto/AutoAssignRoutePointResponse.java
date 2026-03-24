package com.farm.sales.dto;

import java.util.List;

public record AutoAssignRoutePointResponse(
    Long orderId,
    String deliveryAddress,
    double latitude,
    double longitude,
    int tripNumber,
    int stopSequence,
    double distanceFromPreviousKm,
    String selectionReason,
    List<OrderItemResponse> items
) {
}
