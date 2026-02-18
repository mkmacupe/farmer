package com.farm.sales.repository;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  interface DashboardSummaryAggregate {
    Long getTotalOrders();

    BigDecimal getTotalAmount();

    BigDecimal getDeliveredRevenue();

    Long getCreatedCount();

    Long getApprovedCount();

    Long getAssignedCount();

    Long getDeliveredCount();
  }

  interface ReportRow {
    Long getOrderId();

    String getStoreName();

    OrderStatus getStatus();

    Instant getCreatedAt();

    BigDecimal getTotalAmount();

    Long getItemCount();

    String getDeliveryAddressText();

    String getDriverName();
  }

  @Override
  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  Optional<Order> findById(Long id);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByAssignedDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

  @Query("""
      select o.id as orderId,
             coalesce(c.legalEntityName, c.fullName) as storeName,
             o.status as status,
             o.createdAt as createdAt,
             o.totalAmount as totalAmount,
             count(oi.id) as itemCount,
             o.deliveryAddressText as deliveryAddressText,
             d.fullName as driverName
      from Order o
      join o.customer c
      left join o.assignedDriver d
      left join o.items oi
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
        and (:status is null or o.status = :status)
      group by o.id, c.legalEntityName, c.fullName, o.status, o.createdAt, o.totalAmount, o.deliveryAddressText, d.fullName
      order by o.createdAt desc
      """)
  List<ReportRow> findReportRows(@Param("fromInstant") Instant fromInstant,
                                 @Param("toInstant") Instant toInstant,
                                 @Param("status") OrderStatus status);

  @Query("""
      select count(o) as totalOrders,
             coalesce(sum(o.totalAmount), 0) as totalAmount,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.DELIVERED then o.totalAmount end), 0) as deliveredRevenue,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.CREATED then 1 else 0 end), 0) as createdCount,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.APPROVED then 1 else 0 end), 0) as approvedCount,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.ASSIGNED then 1 else 0 end), 0) as assignedCount,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.DELIVERED then 1 else 0 end), 0) as deliveredCount
      from Order o
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
      """)
  DashboardSummaryAggregate summarizeForDashboard(@Param("fromInstant") Instant fromInstant,
                                                  @Param("toInstant") Instant toInstant);
}
