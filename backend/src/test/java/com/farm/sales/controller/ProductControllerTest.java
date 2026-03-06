package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.ProductPageResponse;
import com.farm.sales.dto.ProductRequest;
import com.farm.sales.dto.ProductResponse;
import com.farm.sales.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductControllerTest {
  private ProductService productService;
  private ProductController controller;

  @BeforeEach
  void setUp() {
    productService = mock(ProductService.class);
    controller = new ProductController(productService);
  }

  @Test
  void createReturnsCreatedStatus() {
    ProductRequest request = new ProductRequest(
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "/images/products/milk.webp",
        new BigDecimal("45.00"),
        10,
        1.0,
        0.0012
    );
    ProductResponse response = new ProductResponse(
        1L,
        "Молоко 1 л",
        "Молочная продукция",
        "Свежее молоко",
        "/images/products/milk.webp",
        new BigDecimal("45.00"),
        10,
        1.0,
        0.0012
    );
    when(productService.create(request)).thenReturn(response);

    var httpResponse = controller.create(request);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(productService).create(request);
  }

  @Test
  void deleteReturnsNoContent() {
    var httpResponse = controller.delete(12L);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(productService).delete(12L);
  }

  @Test
  void listReturnsPagedPayload() {
    ProductPageResponse response = new ProductPageResponse(
        List.of(
            new ProductResponse(
                1L,
                "Молоко 1 л",
                "Молочная продукция",
                "Свежее молоко",
                "/images/products/milk.webp",
                new BigDecimal("45.00"),
                10,
                1.0,
                0.0012
            )
        ),
        0,
        12,
        1,
        1,
        false
    );
    when(productService.getPage("Молочная продукция", "мол", 0, 12)).thenReturn(response);

    var httpResponse = controller.list("Молочная продукция", "мол", 0, 12);

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(productService).getPage("Молочная продукция", "мол", 0, 12);
  }

  @Test
  void categoriesAndUpdateDelegateToService() {
    when(productService.getCategories()).thenReturn(List.of("Молочная продукция", "Овощи"));

    ProductRequest request = new ProductRequest(
        "Сыр 0.5 кг",
        "Молочная продукция",
        "Фермерский сыр",
        "/images/products/cheese.webp",
        new BigDecimal("13.42"),
        60,
        0.5,
        0.0006
    );
    ProductResponse updated = new ProductResponse(
        2L,
        "Сыр 0.5 кг",
        "Молочная продукция",
        "Фермерский сыр",
        "/images/products/cheese.webp",
        new BigDecimal("13.42"),
        60,
        0.5,
        0.0006
    );
    when(productService.update(2L, request)).thenReturn(updated);

    var categoriesResponse = controller.categories();
    var updateResponse = controller.update(2L, request);

    assertThat(categoriesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(categoriesResponse.getBody()).containsExactly("Молочная продукция", "Овощи");
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updateResponse.getBody()).isEqualTo(updated);
    verify(productService).getCategories();
    verify(productService).update(2L, request);
  }
}
