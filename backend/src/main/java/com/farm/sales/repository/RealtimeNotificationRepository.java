package com.farm.sales.repository;

import com.farm.sales.model.RealtimeNotification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface RealtimeNotificationRepository extends JpaRepository<RealtimeNotification, Long> {
  List<RealtimeNotification> findTop200ByIdGreaterThanOrderByIdAsc(Long id);

  @Query("select min(notification.id) from RealtimeNotification notification where notification.createdAt >= :cutoff")
  Optional<Long> findEarliestRetainedId(@Param("cutoff") Instant cutoff);

  @Query("select max(notification.id) from RealtimeNotification notification")
  Optional<Long> findMaxId();

  @Transactional
  long deleteByCreatedAtBeforeAndIdLessThanEqual(Instant cutoff, Long id);
}
