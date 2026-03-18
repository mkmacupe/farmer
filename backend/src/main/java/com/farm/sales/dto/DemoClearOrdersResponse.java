package com.farm.sales.dto;

import java.time.Instant;

public record DemoClearOrdersResponse(
    String message,
    Instant clearedAt,
    long storeAddresses,
    long totalOrders,
    long approvedOrders
) {
}
