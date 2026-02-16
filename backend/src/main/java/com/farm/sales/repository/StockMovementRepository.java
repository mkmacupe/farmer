package com.farm.sales.repository;

import com.farm.sales.model.StockMovement;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
  @EntityGraph(attributePaths = {"product", "order"})
  @Query("""
      select sm from StockMovement sm
      where (:productId is null or sm.product.id = :productId)
        and (:fromInstant is null or sm.createdAt >= :fromInstant)
        and (:toInstant is null or sm.createdAt <= :toInstant)
      order by sm.createdAt desc
      """)
  List<StockMovement> findForList(@Param("productId") Long productId,
                                  @Param("fromInstant") Instant fromInstant,
                                  @Param("toInstant") Instant toInstant,
                                  Pageable pageable);
}
