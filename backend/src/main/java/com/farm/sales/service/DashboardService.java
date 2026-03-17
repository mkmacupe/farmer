package com.farm.sales.service;

import com.farm.sales.dto.DashboardCategoryInsightResponse;
import com.farm.sales.dto.DashboardStatusCountResponse;
import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.dto.DashboardTrendPointResponse;
import com.farm.sales.dto.DashboardTrendResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
  private static final int DASHBOARD_SCAN_LIMIT = 10_000;
  private final OrderRepository orderRepository;
  private final UserRepository userRepository;

  public DashboardService(OrderRepository orderRepository, UserRepository userRepository) {
    this.orderRepository = orderRepository;
    this.userRepository = userRepository;
  }

  @Transactional(readOnly = true)
  public DashboardSummaryResponse getSummary(Instant from, Instant to) {
    return buildSummaryFromOrders(from, to, loadOrdersForDashboard(from, to));
  }

  @Transactional(readOnly = true)
  public DashboardTrendResponse getTrends(Instant from, Instant to) {
    return buildTrendsFromOrders(from, to, loadOrdersForDashboard(from, to));
  }

  @Transactional(readOnly = true)
  public List<DashboardCategoryInsightResponse> getCategoryInsights(Instant from, Instant to) {
    return buildCategoriesFromOrders(loadOrdersForDashboard(from, to));
  }

  private DashboardSummaryResponse buildSummaryFromOrders(Instant from, Instant to, List<Order> orders) {
    EnumMap<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
    for (OrderStatus status : OrderStatus.values()) {
      byStatus.put(status, 0L);
    }

    BigDecimal totalAllOrders = BigDecimal.ZERO;
    BigDecimal deliveredRevenue = BigDecimal.ZERO;
    for (Order order : orders) {
      if (order == null) {
        continue;
      }
      OrderStatus status = order.getStatus();
      if (status != null) {
        byStatus.put(status, byStatus.get(status) + 1L);
      }
      BigDecimal totalAmount = decimalOrZero(order.getTotalAmount());
      totalAllOrders = totalAllOrders.add(totalAmount);
      if (status == OrderStatus.DELIVERED) {
        deliveredRevenue = deliveredRevenue.add(totalAmount);
      }
    }

    return buildSummaryResponse(from, to, orders.size(), totalAllOrders, deliveredRevenue, byStatus);
  }

  private DashboardSummaryResponse buildSummaryResponse(Instant from,
                                                        Instant to,
                                                        long totalOrders,
                                                        BigDecimal totalAllOrders,
                                                        BigDecimal totalDeliveredRevenue,
                                                        EnumMap<OrderStatus, Long> byStatus) {
    BigDecimal averageCheck = BigDecimal.ZERO;
    if (totalOrders > 0) {
      averageCheck = totalAllOrders.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP);
    }

    List<DashboardStatusCountResponse> statusRows = Arrays.stream(OrderStatus.values())
        .map(status -> new DashboardStatusCountResponse(status.name(), byStatus.getOrDefault(status, 0L)))
        .toList();

    return new DashboardSummaryResponse(
        from,
        to,
        (int) totalOrders,
        byStatus.getOrDefault(OrderStatus.DELIVERED, 0L).intValue(),
        totalDeliveredRevenue.setScale(2, RoundingMode.HALF_UP),
        averageCheck,
        Math.toIntExact(userRepository.count()),
        statusRows
    );
  }

  private DashboardTrendResponse buildTrendsFromOrders(Instant from, Instant to, List<Order> orders) {
    record TrendAccumulator(long totalOrders, BigDecimal totalAmount, long deliveredCount) {
      private TrendAccumulator add(Order order) {
        BigDecimal amount = order == null || order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
        long delivered = order != null && order.getStatus() == OrderStatus.DELIVERED ? 1L : 0L;
        return new TrendAccumulator(totalOrders + 1L, totalAmount.add(amount), deliveredCount + delivered);
      }
    }

    Map<LocalDate, TrendAccumulator> grouped = new HashMap<>();
    for (Order order : orders) {
      if (order == null || order.getCreatedAt() == null) {
        continue;
      }
      LocalDate day = LocalDate.ofInstant(order.getCreatedAt(), ZoneOffset.UTC);
      grouped.compute(day, (ignored, current) -> (current == null
          ? new TrendAccumulator(0L, BigDecimal.ZERO, 0L)
          : current).add(order));
    }

    List<DashboardTrendPointResponse> points = new ArrayList<>(grouped.size());
    grouped.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> points.add(new DashboardTrendPointResponse(
            entry.getKey(),
            Math.toIntExact(entry.getValue().totalOrders()),
            entry.getValue().totalAmount().setScale(2, RoundingMode.HALF_UP),
            Math.toIntExact(entry.getValue().deliveredCount())
        )));

    return new DashboardTrendResponse(from, to, points);
  }

  private List<DashboardCategoryInsightResponse> buildCategoriesFromOrders(List<Order> orders) {
    Map<String, Long> byCategory = new HashMap<>();
    for (Order order : orders) {
      if (order == null || order.getItems() == null) {
        continue;
      }
      order.getItems().forEach(item -> {
        if (item == null || item.getProduct() == null || item.getQuantity() == null) {
          return;
        }
        String category = item.getProduct().getCategory();
        if (category == null || category.isBlank()) {
          return;
        }
        byCategory.merge(category, item.getQuantity().longValue(), Long::sum);
      });
    }

    return byCategory.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .map(entry -> new DashboardCategoryInsightResponse(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<Order> loadOrdersForDashboard(Instant from, Instant to) {
    List<Order> candidates = orderRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, DASHBOARD_SCAN_LIMIT));
    return candidates.stream()
        .filter(order -> isInRange(order, from, to))
        .toList();
  }

  private boolean isInRange(Order order, Instant from, Instant to) {
    if (order == null || order.getCreatedAt() == null) {
      return false;
    }
    Instant createdAt = order.getCreatedAt();
    if (from != null && createdAt.isBefore(from)) {
      return false;
    }
    if (to != null && createdAt.isAfter(to)) {
      return false;
    }
    return true;
  }

  private BigDecimal decimalOrZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
