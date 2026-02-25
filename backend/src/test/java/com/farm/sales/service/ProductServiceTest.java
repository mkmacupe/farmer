package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.ArgumentCaptor;
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
        "/images/products/milk.webp",
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
        "/images/products/milk.webp",
        new BigDecimal("10.00"),
        5
    );
    when(productRepository.existsByPhotoUrlIgnoreCase("/images/products/milk.webp")).thenReturn(false);
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
    verify(productRepository).existsByPhotoUrlIgnoreCase("/images/products/milk.webp");
  }

  @Test
  void createRejectsInvalidPhotoPath() {
    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "https://example.com/milk.jpg",
        new BigDecimal("10.00"),
        5
    );

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("/images/products/<slug>.webp");
        });

    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void createRejectsDuplicatePhotoPath() {
    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "/images/products/milk.webp",
        new BigDecimal("10.00"),
        5
    );
    when(productRepository.existsByPhotoUrlIgnoreCase("/images/products/milk.webp")).thenReturn(true);

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getReason()).contains("уникальная картинка");
        });

    verify(productRepository, never()).save(any(Product.class));
  }

  @Test
  void updateRejectsDuplicatePhotoPathFromAnotherProduct() {
    Product existing = new Product(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "/images/products/milk.webp",
        new BigDecimal("10.00"),
        5
    );
    existing.setId(55L);
    when(productRepository.findById(55L)).thenReturn(Optional.of(existing));
    when(productRepository.existsByPhotoUrlIgnoreCaseAndIdNot("/images/products/cheese.webp", 55L))
        .thenReturn(true);

    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "/images/products/cheese.webp",
        new BigDecimal("10.00"),
        5
    );

    assertThatThrownBy(() -> productService.update(55L, request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getReason()).contains("уникальная картинка");
        });

    verify(productRepository, never()).save(any(Product.class));
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

  @Test
  void getPageCapsRequestedSize() {
    when(productRepository.search(any(), any(), any())).thenReturn(List.of());
    when(productRepository.countSearch(any(), any())).thenReturn(0L);

    var page = productService.getPage("  Категория ", "  query ", null, 1000);

    assertThat(page.page()).isEqualTo(0);
    assertThat(page.size()).isEqualTo(100);
    verify(productRepository).search(eq("Категория"), eq("query"), any());
  }

  @Test
  void getPageTreatsBlankFiltersAsNullAndUsesDefaultSizeWhenNull() {
    when(productRepository.search(any(), any(), any())).thenReturn(List.of());
    when(productRepository.countSearch(any(), any())).thenReturn(0L);

    var page = productService.getPage("   ", "   ", 1, null);

    assertThat(page.page()).isEqualTo(1);
    assertThat(page.size()).isEqualTo(24);
    verify(productRepository).search(eq(null), eq(null), any());
  }

  @Test
  void getCategoriesReturnsRepositoryValues() {
    when(productRepository.findDistinctCategories()).thenReturn(List.of("Мёд", "Овощи"));

    assertThat(productService.getCategories()).containsExactly("Мёд", "Овощи");
  }

  @Test
  void createDoesNotRecordStockMovementWhenInitialStockIsZero() {
    ProductRequest request = new ProductRequest(
        "Вода 1 л",
        "Напитки",
        null,
        "/images/products/water.webp",
        new BigDecimal("1.20"),
        0
    );
    when(productRepository.existsByPhotoUrlIgnoreCase("/images/products/water.webp")).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
      Product product = invocation.getArgument(0);
      product.setId(77L);
      return product;
    });

    ProductResponse response = productService.create(request);

    assertThat(response.id()).isEqualTo(77L);
    verify(stockMovementService, never()).record(any(Product.class), any(), any(), anyInt(), any());
  }

  @Test
  void createRejectsBlankPhotoPath() {
    ProductRequest request = new ProductRequest(
        "Вода 1 л",
        "Напитки",
        null,
        "   ",
        new BigDecimal("1.20"),
        1
    );

    assertThatThrownBy(() -> productService.create(request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("обязательно фото");
        });
  }

  @Test
  void updateRecordsInboundMovementWhenStockIncreases() {
    Product existing = new Product("Мёд", "Мёд", "old", "/images/products/honey.webp", new BigDecimal("9.00"), 10);
    existing.setId(4L);
    when(productRepository.findById(4L)).thenReturn(Optional.of(existing));
    when(productRepository.existsByPhotoUrlIgnoreCaseAndIdNot("/images/products/honey.webp", 4L)).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProductRequest request = new ProductRequest(
        "  Мёд  ",
        "  Мёд  ",
        "  свежий ",
        "/images/products/honey.webp",
        new BigDecimal("10.50"),
        14
    );

    ProductResponse response = productService.update(4L, request);

    assertThat(response.stockQuantity()).isEqualTo(14);
    verify(stockMovementService).record(existing, null, com.farm.sales.model.StockMovementType.INBOUND, 4, "PRODUCT_UPDATED");
  }

  @Test
  void updateRecordsAdjustmentWhenStockDecreases() {
    Product existing = new Product("Сыр", "Молочная продукция", "old", "/images/products/cheese.webp", new BigDecimal("10.00"), 20);
    existing.setId(8L);
    when(productRepository.findById(8L)).thenReturn(Optional.of(existing));
    when(productRepository.existsByPhotoUrlIgnoreCaseAndIdNot("/images/products/cheese.webp", 8L)).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProductRequest request = new ProductRequest(
        "Сыр",
        "Молочная продукция",
        "",
        "/images/products/cheese.webp",
        new BigDecimal("10.00"),
        15
    );

    ProductResponse response = productService.update(8L, request);

    assertThat(response.stockQuantity()).isEqualTo(15);
    verify(stockMovementService).record(existing, null, com.farm.sales.model.StockMovementType.ADJUSTMENT, -5, "PRODUCT_UPDATED");
  }

  @Test
  void updateDoesNotRecordMovementWhenStockUnchanged() {
    Product existing = new Product("Кефир", "Молочная продукция", "desc", "/images/products/kefir.webp", new BigDecimal("3.00"), 9);
    existing.setId(9L);
    when(productRepository.findById(9L)).thenReturn(Optional.of(existing));
    when(productRepository.existsByPhotoUrlIgnoreCaseAndIdNot("/images/products/kefir.webp", 9L)).thenReturn(false);
    when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

    productService.update(9L, new ProductRequest(
        "Кефир",
        "Молочная продукция",
        "desc",
        "/images/products/kefir.webp",
        new BigDecimal("3.00"),
        9
    ));

    verify(stockMovementService, never()).record(eq(existing), eq(null), any(), anyInt(), eq("PRODUCT_UPDATED"));
  }

  @Test
  void deletePublishesAuditWhenSuccessful() {
    when(productRepository.existsById(11L)).thenReturn(true);
    doNothing().when(productRepository).deleteById(11L);
    doNothing().when(productRepository).flush();

    productService.delete(11L);

    verify(auditTrailPublisher).publish("PRODUCT_DELETED", "PRODUCT", "11", null);
  }
}
