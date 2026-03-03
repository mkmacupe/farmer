package com.farm.sales.dto;

public record AutoAssignRoutePointResponse(
    Long orderId,
    String deliveryAddress,
    double latitude,
    double longitude,
    int stopSequence,
    double distanceFromPreviousKm
) {
}
