package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditActor;
import com.farm.sales.audit.AuditActorResolver;
import com.farm.sales.dto.OrderTimelineEventResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.OrderTimelineEvent;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.OrderTimelineEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrderTimelineServiceTest {
  private OrderTimelineEventRepository timelineRepository;
  private OrderRepository orderRepository;
  private AuditActorResolver auditActorResolver;
  private OrderTimelineService service;

  @BeforeEach
  void setUp() {
    timelineRepository = mock(OrderTimelineEventRepository.class);
    orderRepository = mock(OrderRepository.class);
    auditActorResolver = mock(AuditActorResolver.class);
    when(auditActorResolver.resolveCurrentActor()).thenReturn(new AuditActor("manager", 8L, "MANAGER"));
    service = new OrderTimelineService(timelineRepository, orderRepository, auditActorResolver);
  }

  @Test
  void recordCreationAndStatusChangePersistEventWithActor() {
    Order order = order(101L, 15L, null);
    order.setStatus(OrderStatus.CREATED);

    service.recordCreation(order);
    service.recordStatusChange(order, OrderStatus.CREATED, OrderStatus.APPROVED);

    ArgumentCaptor<OrderTimelineEvent> eventCaptor = ArgumentCaptor.forClass(OrderTimelineEvent.class);
    verify(timelineRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
    List<OrderTimelineEvent> saved = eventCaptor.getAllValues();
    assertThat(saved.get(0).getFromStatus()).isNull();
    assertThat(saved.get(0).getToStatus()).isEqualTo("CREATED");
    assertThat(saved.get(0).getActorUsername()).isEqualTo("manager");
    assertThat(saved.get(0).getCreatedAt()).isNotNull();
    assertThat(saved.get(1).getFromStatus()).isEqualTo("CREATED");
    assertThat(saved.get(1).getToStatus()).isEqualTo("APPROVED");
  }

  @Test
  void getTimelineHandlesNotFoundAndRoleAccessRules() {
    when(orderRepository.findById(404L)).thenReturn(Optional.empty());
    assertStatus(() -> service.getTimeline(404L, Role.MANAGER, 1L), HttpStatus.NOT_FOUND, "Заказ не найден");

    Order directorOrder = order(11L, 2L, null);
    when(orderRepository.findById(11L)).thenReturn(Optional.of(directorOrder));
    assertStatus(
        () -> service.getTimeline(11L, Role.DIRECTOR, 99L),
        HttpStatus.FORBIDDEN,
        "только своих заказов"
    );

    Order unassignedOrder = order(12L, 2L, null);
    when(orderRepository.findById(12L)).thenReturn(Optional.of(unassignedOrder));
    assertStatus(
        () -> service.getTimeline(12L, Role.DRIVER, 5L),
        HttpStatus.FORBIDDEN,
        "назначенных ему"
    );

    Order assignedToOtherDriver = order(13L, 2L, user(44L, Role.DRIVER));
    when(orderRepository.findById(13L)).thenReturn(Optional.of(assignedToOtherDriver));
    assertStatus(
        () -> service.getTimeline(13L, Role.DRIVER, 5L),
        HttpStatus.FORBIDDEN,
        "назначенных ему"
    );
  }

  @Test
  void getTimelineReturnsMappedEventsForAuthorizedUsers() {
    Order order = order(20L, 2L, user(5L, Role.DRIVER));
    when(orderRepository.findById(20L)).thenReturn(Optional.of(order));

    OrderTimelineEvent event = new OrderTimelineEvent();
    event.setId(1L);
    event.setOrder(order);
    event.setFromStatus("CREATED");
    event.setToStatus("APPROVED");
    event.setActorUsername("manager");
    event.setActorUserId(8L);
    event.setActorRole("MANAGER");
    event.setDetails("ok");
    event.setCreatedAt(Instant.now());
    when(timelineRepository.findByOrderIdOrderByCreatedAtDesc(20L)).thenReturn(List.of(event));

    List<OrderTimelineEventResponse> managerView = service.getTimeline(20L, Role.MANAGER, 99L);
    List<OrderTimelineEventResponse> directorView = service.getTimeline(20L, Role.DIRECTOR, 2L);
    List<OrderTimelineEventResponse> driverView = service.getTimeline(20L, Role.DRIVER, 5L);

    assertThat(managerView).hasSize(1);
    assertThat(managerView.getFirst().toStatus()).isEqualTo("APPROVED");
    assertThat(directorView).hasSize(1);
    assertThat(driverView).hasSize(1);
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

  private Order order(Long id, Long customerId, User driver) {
    Order order = new Order();
    order.setId(id);
    User customer = user(customerId, Role.DIRECTOR);
    order.setCustomer(customer);
    order.setAssignedDriver(driver);
    return order;
  }

  private User user(Long id, Role role) {
    User user = new User();
    user.setId(id);
    user.setUsername("user-" + id);
    user.setRole(role);
    return user;
  }
}

