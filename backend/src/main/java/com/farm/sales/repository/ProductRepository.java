package com.farm.sales.repository;

import com.farm.sales.model.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id in :ids")
  List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

  boolean existsByNameIgnoreCase(String name);

  Optional<Product> findByNameIgnoreCase(String name);

  boolean existsByPhotoUrlIgnoreCase(String photoUrl);

  boolean existsByPhotoUrlIgnoreCaseAndIdNot(String photoUrl, Long id);

  @Query("""
      select p from Product p
      where (:category is null or lower(p.category) = lower(:category))
        and (:searchTerm is null
             or lower(p.name) like lower(concat('%', :searchTerm, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :searchTerm, '%')))
      order by p.category asc, p.name asc
      """)
  List<Product> search(@Param("category") String category,
                       @Param("searchTerm") String searchTerm,
                       Pageable pageable);

  @Query("""
      select count(p) from Product p
      where (:category is null or lower(p.category) = lower(:category))
        and (:searchTerm is null
             or lower(p.name) like lower(concat('%', :searchTerm, '%'))
             or lower(coalesce(p.description, '')) like lower(concat('%', :searchTerm, '%')))
      """)
  long countSearch(@Param("category") String category,
                   @Param("searchTerm") String searchTerm);

  @Query("select distinct p.category from Product p order by p.category asc")
  List<String> findDistinctCategories();
}
