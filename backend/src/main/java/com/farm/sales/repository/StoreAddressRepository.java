package com.farm.sales.repository;

import com.farm.sales.model.StoreAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, Long> {
  List<StoreAddress> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<StoreAddress> findByIdAndUserId(Long id, Long userId);

  Optional<StoreAddress> findByUserIdAndLabelIgnoreCase(Long userId, String label);

  boolean existsByUserIdAndLabelIgnoreCase(Long userId, String label);
}
