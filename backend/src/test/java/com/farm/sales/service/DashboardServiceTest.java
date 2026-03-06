package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.DashboardCategoryInsightResponse;
import com.farm.sales.dto.DashboardTrendResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    OrderRepository.DashboardSummaryAggregate aggregate = org.mockito.Mockito.mock(OrderRepository.DashboardSummaryAggregate.class);
    when(aggregate.getTotalOrders()).thenReturn(3L);
    when(aggregate.getTotalAmount()).thenReturn(new BigDecimal("220.00"));
    when(aggregate.getDeliveredRevenue()).thenReturn(new BigDecimal("170.00"));
    when(aggregate.getCreatedCount()).thenReturn(1L);
    when(aggregate.getApprovedCount()).thenReturn(0L);
    when(aggregate.getAssignedCount()).thenReturn(0L);
    when(aggregate.getDeliveredCount()).thenReturn(2L);
    when(orderRepository.summarizeForDashboard(from, to)).thenReturn(aggregate);

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

  @Test
  void summaryHandlesNullAggregateAndZeroTotals() {
    when(orderRepository.summarizeForDashboard(null, null)).thenReturn(null);

    var summary = dashboardService.getSummary(null, null);

    assertThat(summary.totalOrders()).isZero();
    assertThat(summary.deliveredOrders()).isZero();
    assertThat(summary.totalRevenue()).isEqualByComparingTo("0.00");
    assertThat(summary.averageCheck()).isEqualByComparingTo("0");
    assertThat(summary.ordersByStatus())
        .extracting(status -> status.count())
        .containsOnly(0L);
  }

  @Test
  void summaryFallsBackToInMemoryOrdersWhenAggregateQueryFails() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-31T23:59:59Z");
    Order delivered = new Order();
    delivered.setStatus(OrderStatus.DELIVERED);
    delivered.setTotalAmount(new BigDecimal("120.00"));
    delivered.setCreatedAt(Instant.parse("2026-01-05T12:00:00Z"));

    Order created = new Order();
    created.setStatus(OrderStatus.CREATED);
    created.setTotalAmount(new BigDecimal("80.00"));
    created.setCreatedAt(Instant.parse("2026-01-06T12:00:00Z"));

    when(orderRepository.summarizeForDashboard(from, to)).thenThrow(new RuntimeException("sql failed"));
    when(orderRepository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(delivered, created));

    var summary = dashboardService.getSummary(from, to);

    assertThat(summary.totalOrders()).isEqualTo(2);
    assertThat(summary.deliveredOrders()).isEqualTo(1);
    assertThat(summary.totalRevenue()).isEqualByComparingTo("120.00");
    assertThat(summary.averageCheck()).isEqualByComparingTo("100.00");
  }

  @Test
  void trendsMapsRows() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-02T23:59:59Z");
    OrderRepository.DailyTrendRow row = org.mockito.Mockito.mock(OrderRepository.DailyTrendRow.class);
    when(row.getDay()).thenReturn(LocalDate.of(2026, 1, 1));
    when(row.getTotalOrders()).thenReturn(3L);
    when(row.getTotalAmount()).thenReturn(new BigDecimal("120.555"));
    when(row.getDeliveredCount()).thenReturn(1L);
    when(orderRepository.findDailyTrends(from, to)).thenReturn(List.of(row));

    DashboardTrendResponse trends = dashboardService.getTrends(from, to);

    assertThat(trends.points()).hasSize(1);
    assertThat(trends.points().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(trends.points().get(0).orders()).isEqualTo(3);
    assertThat(trends.points().get(0).delivered()).isEqualTo(1);
    assertThat(trends.points().get(0).revenue()).isEqualByComparingTo("120.56");
  }

  @Test
  void categoryInsightsMapsRows() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-02T23:59:59Z");
    OrderRepository.CategoryUnitsRow row = org.mockito.Mockito.mock(OrderRepository.CategoryUnitsRow.class);
    when(row.getCategory()).thenReturn("Овощи");
    when(row.getTotalUnits()).thenReturn(15L);
    when(orderRepository.findCategoryUnits(from, to)).thenReturn(List.of(row));

    List<DashboardCategoryInsightResponse> insights = dashboardService.getCategoryInsights(from, to);

    assertThat(insights).hasSize(1);
    assertThat(insights.get(0).category()).isEqualTo("Овощи");
    assertThat(insights.get(0).units()).isEqualTo(15L);
  }
}
