package com.farm.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DashboardSummaryResponse(
    Instant from,
    Instant to,
    Integer totalOrders,
    Integer deliveredOrders,
    BigDecimal totalRevenue,
    BigDecimal averageCheck,
    Integer activeUsers,
    List<DashboardStatusCountResponse> ordersByStatus
) {
}
