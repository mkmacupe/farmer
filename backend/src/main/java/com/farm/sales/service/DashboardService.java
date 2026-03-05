package com.farm.sales.service;

import com.farm.sales.dto.DashboardCategoryInsightResponse;
import com.farm.sales.dto.DashboardStatusCountResponse;
import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.dto.DashboardTrendPointResponse;
import com.farm.sales.dto.DashboardTrendResponse;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
    OrderRepository.DashboardSummaryAggregate aggregate = orderRepository.summarizeForDashboard(from, to);
    long totalOrders = longOrZero(aggregate == null ? null : aggregate.getTotalOrders());
    BigDecimal totalAllOrders = decimalOrZero(aggregate == null ? null : aggregate.getTotalAmount());
    BigDecimal totalDeliveredRevenue = decimalOrZero(aggregate == null ? null : aggregate.getDeliveredRevenue());

    EnumMap<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
    byStatus.put(OrderStatus.CREATED, longOrZero(aggregate == null ? null : aggregate.getCreatedCount()));
    byStatus.put(OrderStatus.APPROVED, longOrZero(aggregate == null ? null : aggregate.getApprovedCount()));
    byStatus.put(OrderStatus.ASSIGNED, longOrZero(aggregate == null ? null : aggregate.getAssignedCount()));
    byStatus.put(OrderStatus.DELIVERED, longOrZero(aggregate == null ? null : aggregate.getDeliveredCount()));

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

  @Transactional(readOnly = true)
  public DashboardTrendResponse getTrends(Instant from, Instant to) {
    List<DashboardTrendPointResponse> points = orderRepository.findDailyTrends(from, to).stream()
        .map(row -> new DashboardTrendPointResponse(
            toLocalDate(row.getDay()),
            intOrZero(row.getTotalOrders()),
            decimalOrZero(row.getTotalAmount()).setScale(2, RoundingMode.HALF_UP),
            intOrZero(row.getDeliveredCount())
        ))
        .toList();
    return new DashboardTrendResponse(from, to, points);
  }

  @Transactional(readOnly = true)
  public List<DashboardCategoryInsightResponse> getCategoryInsights(Instant from, Instant to) {
    return orderRepository.findCategoryUnits(from, to).stream()
        .map(row -> new DashboardCategoryInsightResponse(
            row.getCategory(),
            longOrZero(row.getTotalUnits())
        ))
        .toList();
  }

  private LocalDate toLocalDate(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof java.time.LocalDate ld) {
      return ld;
    }
    if (value instanceof java.sql.Date sd) {
      return sd.toLocalDate();
    }
    if (value instanceof java.sql.Timestamp ts) {
      return ts.toLocalDateTime().toLocalDate();
    }
    if (value instanceof java.util.Date d) {
      return new java.sql.Date(d.getTime()).toLocalDate();
    }
    try {
      return LocalDate.parse(value.toString());
    } catch (Exception e) {
      return null;
    }
  }

  private long longOrZero(Long value) {
    return value == null ? 0L : value;
  }

  private int intOrZero(Long value) {
    return value == null ? 0 : Math.toIntExact(value);
  }

  private BigDecimal decimalOrZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO : value;
  }
}
