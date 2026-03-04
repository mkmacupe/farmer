package com.farm.sales.dto;

import java.time.Instant;
import java.util.List;

public record DashboardTrendResponse(
    Instant from,
    Instant to,
    List<DashboardTrendPointResponse> points
) {
}
