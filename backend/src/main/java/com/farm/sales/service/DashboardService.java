package com.farm.sales.service;

import com.farm.sales.dto.DashboardStatusCountResponse;
import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.model.Order;
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
    List<Order> orders = orderRepository.findForDashboard(from, to);

    EnumMap<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
    for (OrderStatus status : OrderStatus.values()) {
      byStatus.put(status, 0L);
    }

    BigDecimal totalDeliveredRevenue = BigDecimal.ZERO;
    BigDecimal totalAllOrders = BigDecimal.ZERO;
    for (Order order : orders) {
      byStatus.put(order.getStatus(), byStatus.get(order.getStatus()) + 1L);
      totalAllOrders = totalAllOrders.add(order.getTotalAmount());
      if (order.getStatus() == OrderStatus.DELIVERED) {
        totalDeliveredRevenue = totalDeliveredRevenue.add(order.getTotalAmount());
      }
    }

    BigDecimal averageCheck = BigDecimal.ZERO;
    if (!orders.isEmpty()) {
      averageCheck = totalAllOrders.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }

    List<DashboardStatusCountResponse> statusRows = Arrays.stream(OrderStatus.values())
        .map(status -> new DashboardStatusCountResponse(status.name(), byStatus.get(status)))
        .toList();

    return new DashboardSummaryResponse(
        from,
        to,
        orders.size(),
        byStatus.get(OrderStatus.DELIVERED).intValue(),
        totalDeliveredRevenue.setScale(2, RoundingMode.HALF_UP),
        averageCheck,
        statusRows
    );
  }
}
