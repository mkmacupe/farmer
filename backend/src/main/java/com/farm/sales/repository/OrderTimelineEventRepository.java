package com.farm.sales.repository;

import com.farm.sales.model.OrderTimelineEvent;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTimelineEventRepository extends JpaRepository<OrderTimelineEvent, Long> {
  @EntityGraph(attributePaths = {"order"})
  List<OrderTimelineEvent> findByOrderIdOrderByCreatedAtDesc(Long orderId);
}
