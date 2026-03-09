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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
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

  long countByStatus(OrderStatus status);

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

  boolean existsByDeliveryAddressIdAndStatusIn(Long deliveryAddressId, Collection<OrderStatus> statuses);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("""
      update Order o
      set o.deliveryAddress = null
      where o.deliveryAddress.id = :deliveryAddressId
      """)
  int clearDeliveryAddressReference(@Param("deliveryAddressId") Long deliveryAddressId);

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
      where (:applyFromFilter = false or o.createdAt >= :fromInstant)
        and (:applyToFilter = false or o.createdAt <= :toInstant)
        and (:applyStatusFilter = false or o.status = :status)
      group by o.id, c.legalEntityName, c.fullName, o.status, o.createdAt, o.totalAmount, o.deliveryAddressText, d.fullName
      order by o.createdAt desc
      """)
  List<ReportRow> findReportRows(@Param("fromInstant") Instant fromInstant,
                                 @Param("toInstant") Instant toInstant,
                                 @Param("status") OrderStatus status,
                                 @Param("applyFromFilter") boolean applyFromFilter,
                                 @Param("applyToFilter") boolean applyToFilter,
                                 @Param("applyStatusFilter") boolean applyStatusFilter);

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
      where (:applyFromFilter = false or o.createdAt >= :fromInstant)
        and (:applyToFilter = false or o.createdAt <= :toInstant)
        and (:applyStatusFilter = false or o.status = :status)
      group by o.id, c.legalEntityName, c.fullName, o.status, o.createdAt, o.totalAmount, o.deliveryAddressText, d.fullName
      order by o.createdAt desc
      """)
  List<ReportRow> findReportRows(@Param("fromInstant") Instant fromInstant,
                                 @Param("toInstant") Instant toInstant,
                                 @Param("status") OrderStatus status,
                                 @Param("applyFromFilter") boolean applyFromFilter,
                                 @Param("applyToFilter") boolean applyToFilter,
                                 @Param("applyStatusFilter") boolean applyStatusFilter,
                                 Pageable pageable);
}
