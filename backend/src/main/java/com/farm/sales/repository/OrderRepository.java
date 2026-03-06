package com.farm.sales.repository;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  interface DashboardSummaryAggregate {
    Number getTotalOrders();

    BigDecimal getTotalAmount();

    BigDecimal getDeliveredRevenue();

    Number getCreatedCount();

    Number getApprovedCount();

    Number getAssignedCount();

    Number getDeliveredCount();
  }

  interface ReportRow {
    Long getOrderId();

    String getStoreName();

    OrderStatus getStatus();

    Instant getCreatedAt();

    BigDecimal getTotalAmount();

    Number getItemCount();

    String getDeliveryAddressText();

    String getDriverName();
  }

  interface DriverLoadAggregate {
    Long getDriverId();

    Number getTotal();
  }

  interface DailyTrendRow {
    Object getDay();

    Number getTotalOrders();

    BigDecimal getTotalAmount();

    Number getDeliveredCount();
  }

  interface CategoryUnitsRow {
    String getCategory();

    Number getTotalUnits();
  }

  @Override
  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  Optional<Order> findById(Long id);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver"})
  @Query("""
      select o from Order o
      where o.customer.id = :customerId
      order by o.createdAt desc
      """)
  Page<Order> findPageByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver"})
  @Query("""
      select o from Order o
      order by o.createdAt desc
      """)
  Page<Order> findPageAllByOrderByCreatedAtDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver"})
  @Query("""
      select o from Order o
      where o.assignedDriver.id = :driverId
      order by o.createdAt desc
      """)
  Page<Order> findPageByAssignedDriverIdOrderByCreatedAtDesc(@Param("driverId") Long driverId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver"})
  @Query("""
      select o from Order o
      where o.status = :status
      order by o.createdAt desc
      """)
  Page<Order> findPageByStatusOrderByCreatedAtDesc(@Param("status") OrderStatus status, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByStatus(OrderStatus status);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByAssignedDriverIdOrderByCreatedAtDesc(Long driverId, Pageable pageable);

  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items", "items.product"})
  @Query("""
      select o from Order o
      where o.status = :status
      order by o.createdAt desc
      """)
  List<Order> findByStatusOrderByCreatedAtDescForUpdate(@Param("status") OrderStatus status, Pageable pageable);

  long countByAssignedDriverIdAndStatus(Long driverId, OrderStatus status);

  @Query("""
      select o.assignedDriver.id as driverId, count(o) as total
      from Order o
      where o.status = :status
        and o.assignedDriver.id in :driverIds
      group by o.assignedDriver.id
      """)
  List<DriverLoadAggregate> countByAssignedDriverIdsAndStatus(@Param("driverIds") Collection<Long> driverIds,
                                                               @Param("status") OrderStatus status);

  Optional<Order> findFirstByAssignedDriverIdAndStatusOrderByAssignedAtDescIdDesc(Long driverId, OrderStatus status);

  boolean existsByDeliveryAddressId(Long deliveryAddressId);

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
                                 @Param("status") OrderStatus status,
                                 Pageable pageable);

  @Query("""
      select count(o) as totalOrders,
             coalesce(sum(o.totalAmount), 0) as totalAmount,
             coalesce(sum(case when o.status = com.farm.sales.model.OrderStatus.DELIVERED then o.totalAmount end), 0) as deliveredRevenue,
             sum(case when o.status = com.farm.sales.model.OrderStatus.CREATED then 1L else 0L end) as createdCount,
             sum(case when o.status = com.farm.sales.model.OrderStatus.APPROVED then 1L else 0L end) as approvedCount,
             sum(case when o.status = com.farm.sales.model.OrderStatus.ASSIGNED then 1L else 0L end) as assignedCount,
             sum(case when o.status = com.farm.sales.model.OrderStatus.DELIVERED then 1L else 0L end) as deliveredCount
      from Order o
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
      """)
  DashboardSummaryAggregate summarizeForDashboard(@Param("fromInstant") Instant fromInstant,
                                                  @Param("toInstant") Instant toInstant);

  @Query("""
      select cast(o.createdAt as date) as day,
             count(o) as totalOrders,
             coalesce(sum(o.totalAmount), 0) as totalAmount,
             sum(case when o.status = com.farm.sales.model.OrderStatus.DELIVERED then 1L else 0L end) as deliveredCount
      from Order o
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
      group by cast(o.createdAt as date)
      order by cast(o.createdAt as date) asc
      """)
  List<DailyTrendRow> findDailyTrends(@Param("fromInstant") Instant fromInstant,
                                      @Param("toInstant") Instant toInstant);

  @Query("""
      select p.category as category,
             coalesce(sum(oi.quantity), 0L) as totalUnits
      from Order o
      join o.items oi
      join oi.product p
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
      group by p.category
      order by totalUnits desc
      """)
  List<CategoryUnitsRow> findCategoryUnits(@Param("fromInstant") Instant fromInstant,
                                           @Param("toInstant") Instant toInstant);
}
