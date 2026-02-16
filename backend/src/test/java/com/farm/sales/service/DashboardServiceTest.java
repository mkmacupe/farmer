package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
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

    when(orderRepository.findForDashboard(from, to)).thenReturn(List.of(
        order(1L, OrderStatus.DELIVERED, "100.00"),
        order(2L, OrderStatus.CREATED, "50.00"),
        order(3L, OrderStatus.DELIVERED, "70.00")
    ));

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

  private Order order(Long id, OrderStatus status, String total) {
    User customer = new User();
    customer.setId(10L + id);
    customer.setUsername("customer-" + id);
    customer.setFullName("Customer " + id);
    customer.setPasswordHash("hash");
    customer.setRole(Role.DIRECTOR);

    Order order = new Order();
    order.setId(id);
    order.setCustomer(customer);
    order.setStatus(status);
    order.setCreatedAt(Instant.now());
    order.setUpdatedAt(Instant.now());
    order.setTotalAmount(new BigDecimal(total));
    return order;
  }
}
