package com.farm.sales.repository;

import com.farm.sales.model.RealtimeNotification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RealtimeNotificationRepository extends JpaRepository<RealtimeNotification, Long> {
  List<RealtimeNotification> findTop200ByIdGreaterThanOrderByIdAsc(Long id);

  @Query("select max(notification.id) from RealtimeNotification notification")
  Optional<Long> findMaxId();
}
