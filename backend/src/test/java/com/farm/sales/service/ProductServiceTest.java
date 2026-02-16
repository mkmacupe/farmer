package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.ProductRequest;
import com.farm.sales.dto.ProductResponse;
import com.farm.sales.model.Product;
import com.farm.sales.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ProductServiceTest {
  private ProductRepository productRepository;
  private AuditTrailPublisher auditTrailPublisher;
  private StockMovementService stockMovementService;
  private ProductService productService;

  @BeforeEach
  void setUp() {
    productRepository = org.mockito.Mockito.mock(ProductRepository.class);
    auditTrailPublisher = org.mockito.Mockito.mock(AuditTrailPublisher.class);
    stockMovementService = org.mockito.Mockito.mock(StockMovementService.class);
    productService = new ProductService(productRepository, auditTrailPublisher, stockMovementService);
  }

  @Test
  void deleteReturnsNotFoundWhenProductDoesNotExist() {
    when(productRepository.existsById(1L)).thenReturn(false);

    assertThatThrownBy(() -> productService.delete(1L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        });

    verify(productRepository, never()).deleteById(1L);
  }

  @Test
  void deleteReturnsConflictWhenProductIsUsedInOrders() {
    when(productRepository.existsById(1L)).thenReturn(true);
    doNothing().when(productRepository).deleteById(1L);
    doThrow(new DataIntegrityViolationException("FK constraint"))
        .when(productRepository).flush();

    assertThatThrownBy(() -> productService.delete(1L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getReason()).contains("уже используется в заказах");
        });
  }

  @Test
  void updateReturnsNotFoundWhenProductMissing() {
    when(productRepository.findById(55L)).thenReturn(Optional.empty());

    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "https://example.com/milk.jpg",
        new BigDecimal("10.00"),
        5
    );
    assertThatThrownBy(() -> productService.update(55L, request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        });
  }

  @Test
  void createSavesProductAndReturnsMappedResponse() {
    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "https://example.com/milk.jpg",
        new BigDecimal("10.00"),
        5
    );
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
      Product product = invocation.getArgument(0);
      product.setId(9L);
      return product;
    });

    ProductResponse response = productService.create(request);

    assertThat(response.id()).isEqualTo(9L);
    assertThat(response.name()).isEqualTo("Молоко 1 л");
    assertThat(response.category()).isEqualTo("Молочная продукция");
    assertThat(response.price()).isEqualByComparingTo("10.00");
    assertThat(response.stockQuantity()).isEqualTo(5);
  }

  @Test
  void getPageReturnsMetadataAndItems() {
    Product productA = new Product("Молоко 1 л", "Молочная продукция", "Свежее молоко", "/images/products/milk.webp", new BigDecimal("45.00"), 10);
    productA.setId(1L);
    Product productB = new Product("Кефир 1 л", "Молочная продукция", "Кефир 2,5%", "/images/products/kefir.webp", new BigDecimal("38.00"), 8);
    productB.setId(2L);

    when(productRepository.search(any(), any(), any())).thenReturn(List.of(productA, productB));
    when(productRepository.countSearch(any(), any())).thenReturn(3L);

    var page = productService.getPage("Молочная продукция", null, 0, 2);

    assertThat(page.items()).hasSize(2);
    assertThat(page.totalItems()).isEqualTo(3);
    assertThat(page.totalPages()).isEqualTo(2);
    assertThat(page.hasNext()).isTrue();
    assertThat(page.size()).isEqualTo(2);
  }

  @Test
  void getPageNormalizesNegativeValues() {
    when(productRepository.search(any(), any(), any())).thenReturn(List.of());
    when(productRepository.countSearch(any(), any())).thenReturn(0L);

    var page = productService.getPage(null, null, -5, -10);

    assertThat(page.page()).isEqualTo(0);
    assertThat(page.size()).isEqualTo(24);
    assertThat(page.items()).isEmpty();
    assertThat(page.hasNext()).isFalse();
  }
}
