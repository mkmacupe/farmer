package com.farm.sales.service;

import com.farm.sales.dto.DashboardStatusCountResponse;
import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private final OrderRepository orderRepository;

  public DashboardService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @Transactional(readOnly = true)
  public DashboardSummaryResponse getSummary(Instant from, Instant to) {
    long totalOrders = orderRepository.countForDashboard(from, to);
    BigDecimal totalAllOrders = orderRepository.sumTotalForDashboard(from, to);
    BigDecimal totalDeliveredRevenue = orderRepository.sumDeliveredForDashboard(from, to);
    List<OrderRepository.DashboardStatusAggregate> aggregatedStatuses = orderRepository.countByStatusForDashboard(from, to);

    EnumMap<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
    for (OrderStatus status : OrderStatus.values()) {
      byStatus.put(status, 0L);
    }
    for (OrderRepository.DashboardStatusAggregate row : aggregatedStatuses) {
      if (row.getStatus() != null && row.getCount() != null) {
        byStatus.put(row.getStatus(), row.getCount());
      }
    }

    BigDecimal averageCheck = BigDecimal.ZERO;
    if (totalOrders > 0) {
      averageCheck = totalAllOrders.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
    }

    List<DashboardStatusCountResponse> statusRows = Arrays.stream(OrderStatus.values())
        .map(status -> new DashboardStatusCountResponse(status.name(), byStatus.get(status)))
        .toList();

    return new DashboardSummaryResponse(
        from,
        to,
        (int) totalOrders,
        byStatus.get(OrderStatus.DELIVERED).intValue(),
        totalDeliveredRevenue.setScale(2, RoundingMode.HALF_UP),
        averageCheck,
        statusRows
    );
  }
}
