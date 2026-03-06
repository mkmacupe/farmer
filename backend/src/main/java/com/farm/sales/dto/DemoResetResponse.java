package com.farm.sales.dto;

import java.time.Instant;
import java.util.List;

public record DemoResetResponse(
    String scenarioName,
    Instant resetAt,
    long totalUsers,
    long directors,
    long products,
    long storeAddresses,
    long totalOrders,
    long approvedOrders,
    List<String> defenseFlow
) {
}
