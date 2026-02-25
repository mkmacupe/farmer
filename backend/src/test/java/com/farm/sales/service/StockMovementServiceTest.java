package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditActor;
import com.farm.sales.audit.AuditActorResolver;
import com.farm.sales.dto.StockMovementResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.Product;
import com.farm.sales.model.StockMovement;
import com.farm.sales.model.StockMovementType;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StockMovementRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class StockMovementServiceTest {
  private StockMovementRepository stockMovementRepository;
  private ProductRepository productRepository;
  private OrderRepository orderRepository;
  private AuditActorResolver auditActorResolver;
  private StockMovementService service;

  @BeforeEach
  void setUp() {
    stockMovementRepository = mock(StockMovementRepository.class);
    productRepository = mock(ProductRepository.class);
    orderRepository = mock(OrderRepository.class);
    auditActorResolver = mock(AuditActorResolver.class);
    when(auditActorResolver.resolveCurrentActor()).thenReturn(new AuditActor("manager", 5L, "MANAGER"));
    service = new StockMovementService(stockMovementRepository, productRepository, orderRepository, auditActorResolver);
  }

  @Test
  void recordByIdsSkipsZeroQuantityAndValidatesNotFoundCases() {
    service.record(10L, null, StockMovementType.INBOUND, 0, "skip");
    verify(productRepository, never()).findById(any());

    when(productRepository.findById(10L)).thenReturn(Optional.empty());
    assertStatus(() -> service.record(10L, null, StockMovementType.INBOUND, 1, "create"), HttpStatus.NOT_FOUND, "Товар не найден");

    Product product = product(10L, "Молоко");
    when(productRepository.findById(10L)).thenReturn(Optional.of(product));
    when(orderRepository.findById(99L)).thenReturn(Optional.empty());
    assertStatus(() -> service.record(10L, 99L, StockMovementType.OUTBOUND, -2, "order"), HttpStatus.NOT_FOUND, "Заказ не найден");
  }

  @Test
  void recordByIdsAndByEntitiesPersistMovementWithActor() {
    Product product = product(20L, "Сыр");
    Order order = order(30L);
    when(productRepository.findById(20L)).thenReturn(Optional.of(product));
    when(orderRepository.findById(30L)).thenReturn(Optional.of(order));

    service.record(20L, 30L, StockMovementType.OUTBOUND, -3, "ORDER_CREATED");
    service.record(product, null, StockMovementType.ADJUSTMENT, 4, "PRODUCT_UPDATED");

    ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
    verify(stockMovementRepository, org.mockito.Mockito.times(2)).save(movementCaptor.capture());
    List<StockMovement> movements = movementCaptor.getAllValues();
    assertThat(movements.get(0).getOrder().getId()).isEqualTo(30L);
    assertThat(movements.get(0).getActorUsername()).isEqualTo("manager");
    assertThat(movements.get(0).getCreatedAt()).isNotNull();
    assertThat(movements.get(1).getOrder()).isNull();
    assertThat(movements.get(1).getQuantityChange()).isEqualTo(4);
  }

  @Test
  void recordByIdsAllowsNullOrderId() {
    Product product = product(66L, "Сметана");
    when(productRepository.findById(66L)).thenReturn(Optional.of(product));

    service.record(66L, null, StockMovementType.INBOUND, 5, "RESTOCK");

    ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
    verify(stockMovementRepository).save(movementCaptor.capture());
    assertThat(movementCaptor.getValue().getOrder()).isNull();
    assertThat(movementCaptor.getValue().getQuantityChange()).isEqualTo(5);
  }

  @Test
  void recordByEntitiesValidatesProductAndZeroQuantity() {
    service.record((Product) null, null, StockMovementType.INBOUND, 0, "skip");
    verify(stockMovementRepository, never()).save(any());

    assertStatus(
        () -> service.record((Product) null, null, StockMovementType.INBOUND, 2, "bad"),
        HttpStatus.BAD_REQUEST,
        "Товар обязателен"
    );
  }

  @Test
  void listMapsRowsAndNormalizesLimit() {
    Product product = product(44L, "Йогурт");
    Order order = order(77L);
    StockMovement movementWithOrder = movement(1L, product, order, StockMovementType.OUTBOUND, -2, "ORDER_CREATED");
    StockMovement movementWithoutOrder = movement(2L, product, null, StockMovementType.ADJUSTMENT, 3, "MANUAL");
    when(stockMovementRepository.findForList(eq(44L), any(), any(), eq(PageRequest.of(0, 1))))
        .thenReturn(List.of(movementWithOrder, movementWithoutOrder));
    when(stockMovementRepository.findForList(eq(null), any(), any(), eq(PageRequest.of(0, 500))))
        .thenReturn(List.of());

    List<StockMovementResponse> firstPage = service.list(44L, Instant.now().minusSeconds(3600), Instant.now(), 0);
    List<StockMovementResponse> capped = service.list(null, null, null, 9999);

    assertThat(firstPage).hasSize(2);
    assertThat(firstPage.get(0).orderId()).isEqualTo(77L);
    assertThat(firstPage.get(1).orderId()).isNull();
    assertThat(firstPage.get(1).movementType()).isEqualTo("ADJUSTMENT");
    assertThat(capped).isEmpty();
  }

  private void assertStatus(Runnable runnable, HttpStatus status, String reasonPart) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(status);
          assertThat(ex.getReason()).contains(reasonPart);
        });
  }

  private Product product(Long id, String name) {
    Product product = new Product();
    product.setId(id);
    product.setName(name);
    return product;
  }

  private Order order(Long id) {
    Order order = new Order();
    order.setId(id);
    return order;
  }

  private StockMovement movement(Long id,
                                 Product product,
                                 Order order,
                                 StockMovementType type,
                                 int quantity,
                                 String reason) {
    StockMovement movement = new StockMovement();
    movement.setId(id);
    movement.setProduct(product);
    movement.setOrder(order);
    movement.setMovementType(type);
    movement.setQuantityChange(quantity);
    movement.setReason(reason);
    movement.setActorUsername("manager");
    movement.setActorUserId(5L);
    movement.setActorRole("MANAGER");
    movement.setCreatedAt(Instant.now());
    return movement;
  }
}
