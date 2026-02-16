package com.farm.sales.repository;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
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
      select o from Order o
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
        and (:status is null or o.status = :status)
      order by o.createdAt desc
      """)
  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items"})
  List<Order> findForReport(@Param("fromInstant") Instant fromInstant,
                            @Param("toInstant") Instant toInstant,
                            @Param("status") OrderStatus status);

  @Query("""
      select o from Order o
      where (:fromInstant is null or o.createdAt >= :fromInstant)
        and (:toInstant is null or o.createdAt <= :toInstant)
      order by o.createdAt desc
      """)
  @EntityGraph(attributePaths = {"customer", "deliveryAddress", "assignedDriver", "items"})
  List<Order> findForDashboard(@Param("fromInstant") Instant fromInstant,
                               @Param("toInstant") Instant toInstant);
}
