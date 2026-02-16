package com.farm.sales.service;

import com.farm.sales.audit.AuditActor;
import com.farm.sales.audit.AuditActorResolver;
import com.farm.sales.dto.OrderTimelineEventResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.OrderTimelineEvent;
import com.farm.sales.model.Role;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.OrderTimelineEventRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderTimelineService {
  private final OrderTimelineEventRepository timelineRepository;
  private final OrderRepository orderRepository;
  private final AuditActorResolver auditActorResolver;

  public OrderTimelineService(OrderTimelineEventRepository timelineRepository,
                              OrderRepository orderRepository,
                              AuditActorResolver auditActorResolver) {
    this.timelineRepository = timelineRepository;
    this.orderRepository = orderRepository;
    this.auditActorResolver = auditActorResolver;
  }

  @Transactional
  public void recordCreation(Order order) {
    AuditActor actor = auditActorResolver.resolveCurrentActor();
    save(order, null, order.getStatus(), "Заказ создан", actor);
  }

  @Transactional
  public void recordStatusChange(Order order, OrderStatus fromStatus, OrderStatus toStatus) {
    AuditActor actor = auditActorResolver.resolveCurrentActor();
    save(order, fromStatus, toStatus, "Статус обновлён", actor);
  }

  @Transactional(readOnly = true)
  public List<OrderTimelineEventResponse> getTimeline(Long orderId, Role role, Long userId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));

    if (role == Role.DIRECTOR && !order.getCustomer().getId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Директор может просматривать таймлайн только своих заказов");
    }
    if (role == Role.DRIVER
        && (order.getAssignedDriver() == null || !order.getAssignedDriver().getId().equals(userId))) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Водитель может просматривать таймлайн только назначенных ему заказов");
    }

    return timelineRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
        .map(event -> new OrderTimelineEventResponse(
            event.getId(),
            event.getOrder().getId(),
            event.getFromStatus(),
            event.getToStatus(),
            event.getActorUsername(),
            event.getActorUserId(),
            event.getActorRole(),
            event.getDetails(),
            event.getCreatedAt()
        ))
        .toList();
  }

  private void save(Order order, OrderStatus fromStatus, OrderStatus toStatus, String details, AuditActor actor) {
    OrderTimelineEvent event = new OrderTimelineEvent();
    event.setOrder(order);
    event.setFromStatus(fromStatus == null ? null : fromStatus.name());
    event.setToStatus(toStatus.name());
    event.setActorUsername(actor.username());
    event.setActorUserId(actor.userId());
    event.setActorRole(actor.role());
    event.setDetails(details);
    event.setCreatedAt(Instant.now());
    timelineRepository.save(event);
  }
}
