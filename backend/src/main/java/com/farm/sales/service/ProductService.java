package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.ProductPageResponse;
import com.farm.sales.dto.ProductRequest;
import com.farm.sales.dto.ProductResponse;
import com.farm.sales.model.Product;
import com.farm.sales.model.StockMovementType;
import com.farm.sales.repository.ProductRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductService {
  private static final int DEFAULT_PRODUCTS_PAGE_SIZE = 24;
  private static final int MAX_PRODUCTS_PAGE_SIZE = 100;
  private final ProductRepository productRepository;
  private final AuditTrailPublisher auditTrailPublisher;
  private final StockMovementService stockMovementService;

  public ProductService(ProductRepository productRepository,
                        AuditTrailPublisher auditTrailPublisher,
                        StockMovementService stockMovementService) {
    this.productRepository = productRepository;
    this.auditTrailPublisher = auditTrailPublisher;
    this.stockMovementService = stockMovementService;
  }

  public ProductPageResponse getPage(String category, String search, Integer page, Integer size) {
    String normalizedCategory = normalizeFilter(category);
    String normalizedSearch = normalizeFilter(search);
    int normalizedPage = normalizePage(page);
    int normalizedSize = normalizePageSize(size);

    List<ProductResponse> items = productRepository.search(
            normalizedCategory,
            normalizedSearch,
            PageRequest.of(normalizedPage, normalizedSize)
        ).stream()
        .map(this::toResponse)
        .collect(Collectors.toList());

    long totalItems = productRepository.countSearch(normalizedCategory, normalizedSearch);
    int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / normalizedSize);
    boolean hasNext = (long) (normalizedPage + 1) * normalizedSize < totalItems;

    return new ProductPageResponse(
        items,
        normalizedPage,
        normalizedSize,
        totalItems,
        totalPages,
        hasNext
    );
  }

  public List<String> getCategories() {
    return productRepository.findDistinctCategories();
  }

  public ProductResponse create(ProductRequest request) {
    Product product = new Product(
        request.name().trim(),
        request.category().trim(),
        normalizeNullable(request.description()),
        normalizeNullable(request.photoUrl()),
        request.price(),
        request.stockQuantity()
    );
    Product saved = productRepository.save(product);
    if (saved.getStockQuantity() > 0) {
      stockMovementService.record(saved.getId(), null, StockMovementType.INBOUND, saved.getStockQuantity(), "PRODUCT_CREATED");
    }
    auditTrailPublisher.publish(
        "PRODUCT_CREATED",
        "PRODUCT",
        String.valueOf(saved.getId()),
        "name=" + saved.getName() + ",stock=" + saved.getStockQuantity()
    );
    return toResponse(saved);
  }

  public ProductResponse update(Long id, ProductRequest request) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
    Integer previousStock = product.getStockQuantity();
    product.setName(request.name().trim());
    product.setCategory(request.category().trim());
    product.setDescription(normalizeNullable(request.description()));
    product.setPhotoUrl(normalizeNullable(request.photoUrl()));
    product.setPrice(request.price());
    product.setStockQuantity(request.stockQuantity());
    Product saved = productRepository.save(product);
    int stockDiff = saved.getStockQuantity() - previousStock;
    if (stockDiff != 0) {
      StockMovementType movementType = stockDiff > 0 ? StockMovementType.INBOUND : StockMovementType.ADJUSTMENT;
      stockMovementService.record(saved.getId(), null, movementType, stockDiff, "PRODUCT_UPDATED");
    }
    auditTrailPublisher.publish(
        "PRODUCT_UPDATED",
        "PRODUCT",
        String.valueOf(saved.getId()),
        "name=" + saved.getName() + ",stock=" + saved.getStockQuantity()
    );
    return toResponse(saved);
  }

  @Transactional
  public void delete(Long id) {
    if (!productRepository.existsById(id)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
    }
    try {
      productRepository.deleteById(id);
      productRepository.flush();
      auditTrailPublisher.publish("PRODUCT_DELETED", "PRODUCT", String.valueOf(id), null);
    } catch (DataIntegrityViolationException ex) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Нельзя удалить товар, который уже используется в заказах"
      );
    }
  }

  private ProductResponse toResponse(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getCategory(),
        product.getDescription(),
        product.getPhotoUrl(),
        product.getPrice(),
        product.getStockQuantity()
    );
  }

  private String normalizeFilter(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String normalizeNullable(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private int normalizePage(Integer rawPage) {
    if (rawPage == null || rawPage < 0) {
      return 0;
    }
    return rawPage;
  }

  private int normalizePageSize(Integer rawSize) {
    if (rawSize == null || rawSize <= 0) {
      return DEFAULT_PRODUCTS_PAGE_SIZE;
    }
    return Math.min(rawSize, MAX_PRODUCTS_PAGE_SIZE);
  }
}
