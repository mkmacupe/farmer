package com.farm.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    Long customerId,
    String customerName,
    Long deliveryAddressId,
    String deliveryAddressText,
    BigDecimal deliveryLatitude,
    BigDecimal deliveryLongitude,
    Long assignedDriverId,
    String assignedDriverName,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant approvedAt,
    Instant assignedAt,
    Instant deliveredAt,
    BigDecimal totalAmount,
    List<OrderItemResponse> items
) {
}
