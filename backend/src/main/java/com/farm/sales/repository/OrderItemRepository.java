package com.farm.sales.repository;

import com.farm.sales.model.OrderItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  @Query("""
      select oi from OrderItem oi
      join fetch oi.product p
      join fetch oi.order o
      where o.id in :orderIds
      order by o.id asc, oi.id asc
      """)
  List<OrderItem> findByOrderIdInWithProduct(@Param("orderIds") Collection<Long> orderIds);

  @Query("""
      select oi from OrderItem oi
      join fetch oi.product p
      join fetch oi.order o
      where o.id = :orderId
      order by oi.id asc
      """)
  List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);
}
