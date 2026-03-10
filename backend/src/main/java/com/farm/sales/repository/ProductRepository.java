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
  String LEGACY_DUPLICATE_DEMO_PRODUCTS_FILTER = """
      and lower(cast(p.name as string)) not in (
        'мёд 0.5 кг',
        'йогурт натуральный 0.5 л',
        'кефир 1 л',
        'клубника 0.5 кг',
        'молоко 1 л',
        'сливочное масло 0.2 кг',
        'сметана 0.4 л',
        'сыр 0.5 кг',
        'творог 0.5 кг',
        'яйца 10 шт',
        'говядина 1 кг',
        'курица охлаждённая 1 кг',
        'свинина 1 кг',
        'картофель 5 кг',
        'лук репчатый 2 кг',
        'морковь 2 кг',
        'огурцы 1 кг',
        'томаты 1 кг',
        'груши 1 кг',
        'яблоки 1 кг'
      )
      """;

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id in :ids")
  List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

  boolean existsByNameIgnoreCase(String name);

  Optional<Product> findByNameIgnoreCase(String name);

  boolean existsByPhotoUrlIgnoreCase(String photoUrl);

  Optional<Product> findByPhotoUrlIgnoreCase(String photoUrl);

  boolean existsByPhotoUrlIgnoreCaseAndIdNot(String photoUrl, Long id);

  @Query("""
      select p from Product p
      where (:category is null or lower(cast(p.category as string)) = lower(cast(:category as string)))
        and (:searchTerm is null
             or lower(cast(p.name as string)) like lower(cast(concat('%', :searchTerm, '%') as string))
             or lower(cast(coalesce(p.description, '') as string)) like lower(cast(concat('%', :searchTerm, '%') as string)))
      """ + LEGACY_DUPLICATE_DEMO_PRODUCTS_FILTER + """
      order by p.category asc, p.name asc
      """)
  List<Product> search(@Param("category") String category,
                       @Param("searchTerm") String searchTerm,
                       Pageable pageable);

  @Query("""
      select count(p) from Product p
      where (:category is null or lower(cast(p.category as string)) = lower(cast(:category as string)))
        and (:searchTerm is null
             or lower(cast(p.name as string)) like lower(cast(concat('%', :searchTerm, '%') as string))
             or lower(cast(coalesce(p.description, '') as string)) like lower(cast(concat('%', :searchTerm, '%') as string)))
      """ + LEGACY_DUPLICATE_DEMO_PRODUCTS_FILTER + """
      """)
  long countSearch(@Param("category") String category,
                   @Param("searchTerm") String searchTerm);

  @Query("""
      select distinct p.category from Product p
      where lower(cast(p.name as string)) not in (
        'мёд 0.5 кг',
        'йогурт натуральный 0.5 л',
        'кефир 1 л',
        'клубника 0.5 кг',
        'молоко 1 л',
        'сливочное масло 0.2 кг',
        'сметана 0.4 л',
        'сыр 0.5 кг',
        'творог 0.5 кг',
        'яйца 10 шт',
        'говядина 1 кг',
        'курица охлаждённая 1 кг',
        'свинина 1 кг',
        'картофель 5 кг',
        'лук репчатый 2 кг',
        'морковь 2 кг',
        'огурцы 1 кг',
        'томаты 1 кг',
        'груши 1 кг',
        'яблоки 1 кг'
      )
      order by p.category asc
      """)
  List<String> findDistinctCategories();
}
