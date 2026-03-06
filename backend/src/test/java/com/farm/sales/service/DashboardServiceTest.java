package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.DashboardCategoryInsightResponse;
import com.farm.sales.dto.DashboardTrendResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderItem;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Product;
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
    Order deliveredA = order(OrderStatus.DELIVERED, "120.00", "2026-01-05T12:00:00Z");
    Order deliveredB = order(OrderStatus.DELIVERED, "50.00", "2026-01-06T12:00:00Z");
    Order created = order(OrderStatus.CREATED, "50.00", "2026-01-07T12:00:00Z");
    when(orderRepository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(deliveredA, deliveredB, created));

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
    when(orderRepository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of());

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
    Order delivered = order(OrderStatus.DELIVERED, "120.00", "2026-01-05T12:00:00Z");
    Order created = order(OrderStatus.CREATED, "80.00", "2026-01-06T12:00:00Z");
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
    Order delivered = order(OrderStatus.DELIVERED, "100.555", "2026-01-01T10:00:00Z");
    Order created = order(OrderStatus.CREATED, "20.00", "2026-01-01T11:00:00Z");
    when(orderRepository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(delivered, created));

    DashboardTrendResponse trends = dashboardService.getTrends(from, to);

    assertThat(trends.points()).hasSize(1);
    assertThat(trends.points().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(trends.points().get(0).orders()).isEqualTo(2);
    assertThat(trends.points().get(0).delivered()).isEqualTo(1);
    assertThat(trends.points().get(0).revenue()).isEqualByComparingTo("120.56");
  }

  @Test
  void categoryInsightsMapsRows() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-02T23:59:59Z");
    Product product = new Product();
    product.setCategory("Овощи");
    OrderItem item = new OrderItem();
    item.setProduct(product);
    item.setQuantity(15);
    Order order = order(OrderStatus.DELIVERED, "100.00", "2026-01-01T10:00:00Z");
    order.setItems(List.of(item));
    when(orderRepository.findAllByOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
        .thenReturn(List.of(order));

    List<DashboardCategoryInsightResponse> insights = dashboardService.getCategoryInsights(from, to);

    assertThat(insights).hasSize(1);
    assertThat(insights.get(0).category()).isEqualTo("Овощи");
    assertThat(insights.get(0).units()).isEqualTo(15L);
  }

  private Order order(OrderStatus status, String totalAmount, String createdAt) {
    Order order = new Order();
    order.setStatus(status);
    order.setTotalAmount(new BigDecimal(totalAmount));
    order.setCreatedAt(Instant.parse(createdAt));
    return order;
  }
}
