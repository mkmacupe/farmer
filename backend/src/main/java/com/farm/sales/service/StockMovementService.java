package com.farm.sales.service;

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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockMovementService {
  private final StockMovementRepository stockMovementRepository;
  private final ProductRepository productRepository;
  private final OrderRepository orderRepository;
  private final AuditActorResolver auditActorResolver;

  public StockMovementService(StockMovementRepository stockMovementRepository,
                              ProductRepository productRepository,
                              OrderRepository orderRepository,
                              AuditActorResolver auditActorResolver) {
    this.stockMovementRepository = stockMovementRepository;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.auditActorResolver = auditActorResolver;
  }

  @Transactional
  public void record(Long productId, Long orderId, StockMovementType type, int quantityChange, String reason) {
    if (quantityChange == 0) {
      return;
    }

    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден"));
    Order order = null;
    if (orderId != null) {
      order = orderRepository.findById(orderId)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    }

    saveMovement(product, order, type, quantityChange, reason);
  }

  @Transactional
  public void record(Product product, Order order, StockMovementType type, int quantityChange, String reason) {
    if (quantityChange == 0) {
      return;
    }
    if (product == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Товар обязателен для движения остатков");
    }

    saveMovement(product, order, type, quantityChange, reason);
  }

  private void saveMovement(Product product, Order order, StockMovementType type, int quantityChange, String reason) {
    AuditActor actor = auditActorResolver.resolveCurrentActor();

    StockMovement movement = new StockMovement();
    movement.setProduct(product);
    movement.setOrder(order);
    movement.setMovementType(type);
    movement.setQuantityChange(quantityChange);
    movement.setReason(reason);
    movement.setActorUsername(actor.username());
    movement.setActorUserId(actor.userId());
    movement.setActorRole(actor.role());
    movement.setCreatedAt(Instant.now());

    stockMovementRepository.save(movement);
  }

  @Transactional(readOnly = true)
  public List<StockMovementResponse> list(Long productId, Instant from, Instant to, int limit) {
    int resolvedLimit = Math.min(Math.max(limit, 1), 500);
    return stockMovementRepository.findForList(productId, from, to, PageRequest.of(0, resolvedLimit)).stream()
        .map(movement -> new StockMovementResponse(
            movement.getId(),
            movement.getProduct().getId(),
            movement.getProduct().getName(),
            movement.getOrder() == null ? null : movement.getOrder().getId(),
            movement.getMovementType().name(),
            movement.getQuantityChange(),
            movement.getReason(),
            movement.getActorUsername(),
            movement.getActorUserId(),
            movement.getActorRole(),
            movement.getCreatedAt()
        ))
        .toList();
  }
}
