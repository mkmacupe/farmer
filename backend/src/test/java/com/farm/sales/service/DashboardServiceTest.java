package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
  private OrderRepository orderRepository;
  private DashboardService dashboardService;

  @BeforeEach
  void setUp() {
    orderRepository = org.mockito.Mockito.mock(OrderRepository.class);
    dashboardService = new DashboardService(orderRepository);
  }

  @Test
  void summaryCalculatesRevenueAverageAndStatuses() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-31T23:59:59Z");

    OrderRepository.DashboardStatusAggregate delivered = org.mockito.Mockito.mock(OrderRepository.DashboardStatusAggregate.class);
    when(delivered.getStatus()).thenReturn(OrderStatus.DELIVERED);
    when(delivered.getCount()).thenReturn(2L);
    OrderRepository.DashboardStatusAggregate created = org.mockito.Mockito.mock(OrderRepository.DashboardStatusAggregate.class);
    when(created.getStatus()).thenReturn(OrderStatus.CREATED);
    when(created.getCount()).thenReturn(1L);
    when(orderRepository.countForDashboard(from, to)).thenReturn(3L);
    when(orderRepository.sumTotalForDashboard(from, to)).thenReturn(new BigDecimal("220.00"));
    when(orderRepository.sumDeliveredForDashboard(from, to)).thenReturn(new BigDecimal("170.00"));
    when(orderRepository.countByStatusForDashboard(from, to)).thenReturn(List.of(delivered, created));

    var summary = dashboardService.getSummary(from, to);

    assertThat(summary.totalOrders()).isEqualTo(3);
    assertThat(summary.deliveredOrders()).isEqualTo(2);
    assertThat(summary.totalRevenue()).isEqualByComparingTo("170.00");
    assertThat(summary.averageCheck()).isEqualByComparingTo("73.33");
    assertThat(summary.ordersByStatus()).anySatisfy(row -> {
      assertThat(row.status()).isEqualTo("CREATED");
      assertThat(row.count()).isEqualTo(1L);
    });
  }
}
