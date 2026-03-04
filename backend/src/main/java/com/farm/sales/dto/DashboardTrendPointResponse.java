package com.farm.sales.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardTrendPointResponse(
    LocalDate date,
    Integer orders,
    BigDecimal revenue,
    Integer delivered
) {
}
