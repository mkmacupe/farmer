package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.AutoAssignApproveItemRequest;
import com.farm.sales.dto.AutoAssignApproveRequest;
import com.farm.sales.dto.AutoAssignPreviewResponse;
import com.farm.sales.dto.AutoAssignResultResponse;
import com.farm.sales.dto.OrderCreateRequest;
import com.farm.sales.dto.OrderItemRequest;
import com.farm.sales.dto.OrderResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderItem;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class OrderServiceTest {
  private OrderRepository orderRepository;
  private ProductRepository productRepository;
  private UserRepository userRepository;
  private DirectorProfileService directorProfileService;
  private AuditTrailPublisher auditTrailPublisher;
  private StockMovementService stockMovementService;
  private OrderTimelineService orderTimelineService;
  private NotificationStreamService notificationStreamService;
  private RoadRoutingService roadRoutingService;
  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderRepository = org.mockito.Mockito.mock(OrderRepository.class);
    productRepository = org.mockito.Mockito.mock(ProductRepository.class);
    userRepository = org.mockito.Mockito.mock(UserRepository.class);
    directorProfileService = org.mockito.Mockito.mock(DirectorProfileService.class);
    auditTrailPublisher = org.mockito.Mockito.mock(AuditTrailPublisher.class);
    stockMovementService = org.mockito.Mockito.mock(StockMovementService.class);
    orderTimelineService = org.mockito.Mockito.mock(OrderTimelineService.class);
    notificationStreamService = org.mockito.Mockito.mock(NotificationStreamService.class);
    roadRoutingService = org.mockito.Mockito.mock(RoadRoutingService.class);
    when(roadRoutingService.drivingDistancesKm(any(), any())).thenAnswer(invocation -> {
      RoadRoutingService.RouteCoordinate source = invocation.getArgument(0);
      @SuppressWarnings("unchecked")
      List<RoadRoutingService.RouteCoordinate> destinations = invocation.getArgument(1);
      return destinations.stream()
          .map(destination -> testDistance(source, destination))
          .toList();
    });
    when(roadRoutingService.drivingDistanceKm(any(), any())).thenAnswer(invocation -> {
      RoadRoutingService.RouteCoordinate from = invocation.getArgument(0);
      RoadRoutingService.RouteCoordinate to = invocation.getArgument(1);
      return testDistance(from, to);
    });
    orderService = new OrderService(
        orderRepository,
        productRepository,
        userRepository,
        directorProfileService,
        auditTrailPublisher,
        stockMovementService,
        orderTimelineService,
        notificationStreamService,
        roadRoutingService
    );
  }

  @Test
  void createOrderBuildsOrderAndDecrementsStock() {
    User director = user(7L, "Director One", Role.DIRECTOR);
    StoreAddress address = address(5L, director);
    Product milk = product(1L, "Молоко", "5.00", 10);
    Product cheese = product(2L, "Сыр", "12.00", 3);

    when(userRepository.findById(7L)).thenReturn(Optional.of(director));
    when(directorProfileService.getOwnedAddress(7L, 5L)).thenReturn(address);
    when(productRepository.findAllByIdInForUpdate(List.of(1L, 2L))).thenReturn(List.of(milk, cheese));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
      Order order = invocation.getArgument(0);
      order.setId(101L);
      return order;
    });

    OrderCreateRequest request = new OrderCreateRequest(5L, List.of(
        new OrderItemRequest(1L, 2),
        new OrderItemRequest(2L, 1)
    ));

    OrderResponse response = orderService.createOrder(7L, request);

    assertThat(response.id()).isEqualTo(101L);
    assertThat(response.status()).isEqualTo(OrderStatus.CREATED.name());
    assertThat(response.totalAmount()).isEqualByComparingTo("22.00");
    assertThat(response.deliveryAddressId()).isEqualTo(5L);
    assertThat(response.items()).hasSize(2);
    assertThat(milk.getStockQuantity()).isEqualTo(8);
    assertThat(cheese.getStockQuantity()).isEqualTo(2);
  }

  @Test
  void createOrderReturnsBadRequestWhenStockIsNotEnough() {
    User director = user(7L, "Director One", Role.DIRECTOR);
    StoreAddress address = address(5L, director);
    Product milk = product(1L, "Молоко", "5.00", 1);

    when(userRepository.findById(7L)).thenReturn(Optional.of(director));
    when(directorProfileService.getOwnedAddress(7L, 5L)).thenReturn(address);
    when(productRepository.findAllByIdInForUpdate(List.of(1L))).thenReturn(List.of(milk));

    OrderCreateRequest request = new OrderCreateRequest(5L, List.of(
        new OrderItemRequest(1L, 2)
    ));

    assertThatThrownBy(() -> orderService.createOrder(7L, request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("Недостаточно остатка");
        });

    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  void approveOrderMovesCreatedToApproved() {
    User manager = user(1L, "Manager", Role.MANAGER);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(10L, director, OrderStatus.CREATED);

    when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
    when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = orderService.approveOrder(10L, 1L);

    assertThat(response.status()).isEqualTo(OrderStatus.APPROVED.name());
    assertThat(response.approvedAt()).isNotNull();
  }

  @Test
  void assignDriverFailsWhenOrderIsNotApproved() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driver = user(3L, "Driver", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(20L, director, OrderStatus.CREATED);

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(userRepository.findById(3L)).thenReturn(Optional.of(driver));
    when(orderRepository.findById(20L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.assignDriver(20L, 2L, 3L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("статусе APPROVED");
        });
  }

  @Test
  void markDeliveredAllowsOnlyAssignedDriver() {
    User assignedDriver = user(3L, "Driver", Role.DRIVER);
    User anotherDriver = user(4L, "Other Driver", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(30L, director, OrderStatus.ASSIGNED);
    order.setAssignedDriver(assignedDriver);

    when(userRepository.findById(4L)).thenReturn(Optional.of(anotherDriver));
    when(orderRepository.findById(30L)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.markDelivered(30L, 4L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        });
  }

  @Test
  void getOrdersForRoleUsesDriverScopedQuery() {
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(40L, director, OrderStatus.ASSIGNED);
    order.setAssignedDriver(user(3L, "Driver", Role.DRIVER));
    when(orderRepository.findByAssignedDriverIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class)))
        .thenReturn(List.of(order));

    List<OrderResponse> responses = orderService.getOrdersForRole(Role.DRIVER, 3L);

    assertThat(responses).hasSize(1);
    verify(orderRepository).findByAssignedDriverIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class));
    verify(orderRepository, never()).findAllByOrderByCreatedAtDesc(any(Pageable.class));
  }

  @Test
  void getOrdersForRoleUsesApprovedOrdersForLogistician() {
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(41L, director, OrderStatus.APPROVED);
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(order));

    List<OrderResponse> responses = orderService.getOrdersForRole(Role.LOGISTICIAN, 2L);

    assertThat(responses).hasSize(1);
    verify(orderRepository).findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class));
    verify(orderRepository, never()).findAllByOrderByCreatedAtDesc(any(Pageable.class));
  }

  @Test
  void createOrderRestoresStockWhenPersistenceFails() {
    User director = user(7L, "Director One", Role.DIRECTOR);
    StoreAddress address = address(5L, director);
    Product milk = product(1L, "Молоко", "5.00", 10);
    when(userRepository.findById(7L)).thenReturn(Optional.of(director));
    when(directorProfileService.getOwnedAddress(7L, 5L)).thenReturn(address);
    when(productRepository.findAllByIdInForUpdate(List.of(1L))).thenReturn(List.of(milk));
    when(orderRepository.save(any(Order.class))).thenThrow(new IllegalStateException("db-failure"));

    assertThatThrownBy(() -> orderService.createOrder(
        7L,
        new OrderCreateRequest(5L, List.of(new OrderItemRequest(1L, 2)))
    )).isInstanceOf(IllegalStateException.class);

    assertThat(milk.getStockQuantity()).isEqualTo(10);
    verify(productRepository).save(milk);
    verify(orderTimelineService, never()).recordCreation(any());
  }

  @Test
  void createOrderFailsWhenProductIsMissingOrDirectorRoleIsInvalid() {
    User manager = user(7L, "Manager", Role.MANAGER);
    when(userRepository.findById(7L)).thenReturn(Optional.of(manager));

    assertThatThrownBy(() -> orderService.createOrder(
        7L,
        new OrderCreateRequest(5L, List.of(new OrderItemRequest(1L, 1)))
    )).isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        });

    User director = user(8L, "Director", Role.DIRECTOR);
    StoreAddress address = address(5L, director);
    when(userRepository.findById(8L)).thenReturn(Optional.of(director));
    when(directorProfileService.getOwnedAddress(8L, 5L)).thenReturn(address);
    when(productRepository.findAllByIdInForUpdate(List.of(99L))).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.createOrder(
        8L,
        new OrderCreateRequest(5L, List.of(new OrderItemRequest(99L, 1)))
    )).isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getReason()).contains("Товар не найден");
        });
  }

  @Test
  void repeatOrderRejectsMissingForbiddenAndAddresslessSourceOrders() {
    User director = user(7L, "Director", Role.DIRECTOR);
    User anotherDirector = user(8L, "Another", Role.DIRECTOR);
    when(userRepository.findById(7L)).thenReturn(Optional.of(director));
    when(orderRepository.findById(404L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.repeatOrder(7L, 404L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    Order foreignOrder = order(405L, anotherDirector, OrderStatus.CREATED);
    when(orderRepository.findById(405L)).thenReturn(Optional.of(foreignOrder));
    assertThatThrownBy(() -> orderService.repeatOrder(7L, 405L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

    Order noAddress = order(406L, director, OrderStatus.CREATED);
    noAddress.setDeliveryAddress(null);
    when(orderRepository.findById(406L)).thenReturn(Optional.of(noAddress));
    assertThatThrownBy(() -> orderService.repeatOrder(7L, 406L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void repeatOrderBuildsCreateRequestFromSourceItems() {
    OrderService spyService = spy(orderService);
    User director = user(7L, "Director", Role.DIRECTOR);
    Product milk = product(1L, "Молоко", "5.00", 10);
    StoreAddress sourceAddress = address(15L, director);
    Order source = order(500L, director, OrderStatus.DELIVERED);
    source.setDeliveryAddress(sourceAddress);
    source.setItems(List.of(item(source, milk, 3)));
    when(userRepository.findById(7L)).thenReturn(Optional.of(director));
    when(orderRepository.findById(500L)).thenReturn(Optional.of(source));
    doReturn(sampleResponse(700L, director, OrderStatus.CREATED)).when(spyService)
        .createOrder(eq(7L), any(OrderCreateRequest.class));

    OrderResponse response = spyService.repeatOrder(7L, 500L);

    assertThat(response.id()).isEqualTo(700L);
    ArgumentCaptor<OrderCreateRequest> requestCaptor = ArgumentCaptor.forClass(OrderCreateRequest.class);
    verify(spyService).createOrder(eq(7L), requestCaptor.capture());
    assertThat(requestCaptor.getValue().deliveryAddressId()).isEqualTo(15L);
    assertThat(requestCaptor.getValue().items()).containsExactly(new OrderItemRequest(1L, 3));
  }

  @Test
  void getOrdersForRoleUsesDirectorAndManagerQueries() {
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(88L, director, OrderStatus.CREATED);
    when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class))).thenReturn(List.of(order));
    when(orderRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of(order));

    List<OrderResponse> directorOrders = orderService.getOrdersForRole(Role.DIRECTOR, 7L);
    List<OrderResponse> managerOrders = orderService.getOrdersForRole(Role.MANAGER, 1L);

    assertThat(directorOrders).hasSize(1);
    assertThat(managerOrders).hasSize(1);
    verify(orderRepository).findByCustomerIdOrderByCreatedAtDesc(eq(7L), any(Pageable.class));
    verify(orderRepository).findAllByOrderByCreatedAtDesc(any(Pageable.class));
  }

  @Test
  void approveOrderRejectsWrongStatusAndMissingEntities() {
    when(userRepository.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.approveOrder(10L, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    User manager = user(1L, "Manager", Role.MANAGER);
    when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
    when(orderRepository.findById(10L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.approveOrder(10L, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    User director = user(7L, "Director", Role.DIRECTOR);
    Order approved = order(11L, director, OrderStatus.APPROVED);
    when(orderRepository.findById(11L)).thenReturn(Optional.of(approved));
    assertThatThrownBy(() -> orderService.approveOrder(11L, 1L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void assignDriverSuccessAndRoleValidation() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driver = user(3L, "Driver", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(120L, director, OrderStatus.APPROVED);
    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(userRepository.findById(3L)).thenReturn(Optional.of(driver));
    when(orderRepository.findById(120L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = orderService.assignDriver(120L, 2L, 3L);

    assertThat(response.status()).isEqualTo(OrderStatus.ASSIGNED.name());
    assertThat(response.assignedDriverId()).isEqualTo(3L);

    User wrongRole = user(4L, "Wrong", Role.MANAGER);
    when(userRepository.findById(4L)).thenReturn(Optional.of(wrongRole));
    assertThatThrownBy(() -> orderService.assignDriver(120L, 4L, 3L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void markDeliveredValidatesStateAndCanSucceed() {
    User driver = user(3L, "Driver", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order order = order(130L, director, OrderStatus.ASSIGNED);
    order.setAssignedDriver(driver);
    when(userRepository.findById(3L)).thenReturn(Optional.of(driver));
    when(orderRepository.findById(130L)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse delivered = orderService.markDelivered(130L, 3L);

    assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED.name());
    assertThat(delivered.deliveredAt()).isNotNull();

    Order wrongStatus = order(131L, director, OrderStatus.APPROVED);
    wrongStatus.setAssignedDriver(driver);
    when(orderRepository.findById(131L)).thenReturn(Optional.of(wrongStatus));
    assertThatThrownBy(() -> orderService.markDelivered(131L, 3L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

    when(orderRepository.findById(999L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.markDelivered(999L, 3L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void createOrderHandlesUnexpectedMissingProductInMap() {
    User director = user(99L, "Director", Role.DIRECTOR);
    StoreAddress address = address(5L, director);
    Product corrupted = product(null, "Corrupted", "1.00", 10);
    when(userRepository.findById(99L)).thenReturn(Optional.of(director));
    when(directorProfileService.getOwnedAddress(99L, 5L)).thenReturn(address);
    when(productRepository.findAllByIdInForUpdate(List.of(1L))).thenReturn(List.of(corrupted));

    assertThatThrownBy(() -> orderService.createOrder(
        99L,
        new OrderCreateRequest(5L, List.of(new OrderItemRequest(1L, 1)))
    )).isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.getReason()).contains("Товар не найден");
        });
  }

  @Test
  void assignDriverFailsWhenOrderNotFoundAndMarkDeliveredWhenDriverNotAssigned() {
    User logistician = user(20L, "Logistician", Role.LOGISTICIAN);
    User driver = user(21L, "Driver", Role.DRIVER);
    when(userRepository.findById(20L)).thenReturn(Optional.of(logistician));
    when(userRepository.findById(21L)).thenReturn(Optional.of(driver));
    when(orderRepository.findById(777L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> orderService.assignDriver(777L, 20L, 21L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    User director = user(30L, "Director", Role.DIRECTOR);
    Order withoutAssignedDriver = order(778L, director, OrderStatus.ASSIGNED);
    withoutAssignedDriver.setAssignedDriver(null);
    when(userRepository.findById(21L)).thenReturn(Optional.of(driver));
    when(orderRepository.findById(778L)).thenReturn(Optional.of(withoutAssignedDriver));
    assertThatThrownBy(() -> orderService.markDelivered(778L, 21L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void previewAutoAssignPlanBuildsRoutesWithoutChangingOrderStatus() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNearFirst = user(3L, "Driver A", Role.DRIVER);
    User driverNearSecond = user(4L, "Driver B", Role.DRIVER);
    driverNearFirst.setUsername("driver1");
    driverNearSecond.setUsername("driver2");
    User director = user(7L, "Director", Role.DIRECTOR);

    Order firstApproved = orderWithCoordinates(201L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    Order secondApproved = orderWithCoordinates(202L, director, OrderStatus.APPROVED, "53.8710000", "30.4095000");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(firstApproved, secondApproved));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of(driverNearFirst, driverNearSecond));
    when(orderRepository.countByAssignedDriverIdAndStatus(3L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.countByAssignedDriverIdAndStatus(4L, OrderStatus.ASSIGNED)).thenReturn(0L);

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    assertThat(preview.totalApprovedOrders()).isEqualTo(2);
    assertThat(preview.plannedOrders()).isEqualTo(2);
    assertThat(preview.unplannedOrders()).isEqualTo(0);
    assertThat(preview.routes())
        .flatExtracting(route -> route.points())
        .extracting(point -> point.orderId())
        .containsExactlyInAnyOrder(201L, 202L);

    assertThat(firstApproved.getStatus()).isEqualTo(OrderStatus.APPROVED);
    assertThat(secondApproved.getStatus()).isEqualTo(OrderStatus.APPROVED);
    verify(orderRepository, never()).save(any(Order.class));
    verify(orderTimelineService, never()).recordStatusChange(any(Order.class), eq(OrderStatus.APPROVED), eq(OrderStatus.ASSIGNED));
  }

  @Test
  void approveAutoAssignPlanAssignsOrdersFromProvidedPlan() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNearFirst = user(3L, "Driver 1", Role.DRIVER);
    User driverNearSecond = user(4L, "Driver 2", Role.DRIVER);
    driverNearFirst.setUsername("driver1");
    driverNearSecond.setUsername("driver2");
    User director = user(7L, "Director", Role.DIRECTOR);

    Order firstApproved = orderWithCoordinates(301L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    Order secondApproved = orderWithCoordinates(302L, director, OrderStatus.APPROVED, "53.8709000", "30.4098000");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(firstApproved, secondApproved));
    when(orderRepository.findByStatusOrderByCreatedAtDescForUpdate(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(firstApproved, secondApproved));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNearFirst, driverNearSecond));
    when(orderRepository.countByAssignedDriverIdAndStatus(3L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.countByAssignedDriverIdAndStatus(4L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);
    List<AutoAssignApproveItemRequest> requestedAssignments = preview.routes().stream()
        .flatMap(route -> route.points().stream()
            .map(point -> new AutoAssignApproveItemRequest(point.orderId(), route.driverId(), point.stopSequence())))
        .toList();
    AutoAssignResultResponse result = orderService.approveAutoAssignPlan(2L, new AutoAssignApproveRequest(requestedAssignments));

    assertThat(result.totalApprovedOrders()).isEqualTo(2);
    assertThat(result.assignedOrders()).isEqualTo(2);
    assertThat(result.unassignedOrders()).isEqualTo(0);
    assertThat(result.assignments()).hasSize(2);
    assertThat(firstApproved.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(secondApproved.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    verify(orderTimelineService, times(2)).recordStatusChange(any(Order.class), eq(OrderStatus.APPROVED), eq(OrderStatus.ASSIGNED));
  }

  @Test
  void autoAssignApprovedOrdersFailsWhenNoDriversAvailable() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User director = user(7L, "Director", Role.DIRECTOR);
    Order approved = order(777L, director, OrderStatus.APPROVED);

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(approved));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.autoAssignApprovedOrders(2L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("водители не найдены");
        });
  }

  @Test
  void toResponseHandlesNullDeliveryAddressThroughListView() {
    User director = user(44L, "Director", Role.DIRECTOR);
    Order order = order(900L, director, OrderStatus.CREATED);
    order.setDeliveryAddress(null);
    order.setDeliveryAddressText("Текстовый адрес");
    when(orderRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of(order));

    List<OrderResponse> responses = orderService.getOrdersForRole(Role.MANAGER, 1L);

    assertThat(responses).hasSize(1);
    assertThat(responses.getFirst().deliveryAddressId()).isNull();
  }

  private User user(Long id, String fullName, Role role) {
    User user = new User();
    user.setId(id);
    user.setUsername("user-" + id);
    user.setPasswordHash("hash");
    user.setFullName(fullName);
    user.setRole(role);
    user.setLegalEntityName(role == Role.DIRECTOR ? "Retail LLC" : null);
    return user;
  }

  private Product product(Long id, String name, String price, int stock) {
    Product product = new Product();
    product.setId(id);
    product.setName(name);
    product.setCategory("Молочная продукция");
    product.setDescription(name + " desc");
    product.setPrice(new BigDecimal(price));
    product.setStockQuantity(stock);
    return product;
  }

  private StoreAddress address(Long id, User director) {
    StoreAddress address = new StoreAddress();
    address.setId(id);
    address.setUser(director);
    address.setLabel("Main Store");
    address.setAddressLine("Могилёв, ул. Челюскинцев 105");
    address.setLatitude(new BigDecimal("53.8654000"));
    address.setLongitude(new BigDecimal("30.2905000"));
    address.setCreatedAt(Instant.now());
    address.setUpdatedAt(Instant.now());
    return address;
  }

  private Order order(Long id, User director, OrderStatus status) {
    Order order = new Order();
    order.setId(id);
    order.setCustomer(director);
    StoreAddress address = address(5L, director);
    order.setDeliveryAddress(address);
    order.setDeliveryAddressText(address.getAddressLine());
    order.setDeliveryLatitude(address.getLatitude());
    order.setDeliveryLongitude(address.getLongitude());
    order.setStatus(status);
    order.setTotalAmount(new BigDecimal("10.00"));
    order.setCreatedAt(Instant.now().minusSeconds(300));
    order.setUpdatedAt(Instant.now().minusSeconds(60));
    order.setItems(new ArrayList<>());
    return order;
  }

  private OrderItem item(Order order, Product product, int quantity) {
    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProduct(product);
    item.setQuantity(quantity);
    item.setPrice(product.getPrice());
    item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
    return item;
  }

  private Order orderWithCoordinates(Long id, User director, OrderStatus status, String latitude, String longitude) {
    Order order = order(id, director, status);
    order.setDeliveryLatitude(new BigDecimal(latitude));
    order.setDeliveryLongitude(new BigDecimal(longitude));
    return order;
  }

  private OrderResponse sampleResponse(Long id, User director, OrderStatus status) {
    return new OrderResponse(
        id,
        director.getId(),
        director.getFullName(),
        5L,
        "Address",
        new BigDecimal("53.1"),
        new BigDecimal("30.1"),
        null,
        null,
        status.name(),
        Instant.now(),
        Instant.now(),
        null,
        null,
        null,
        new BigDecimal("10.00"),
        List.of()
    );
  }

  private double testDistance(RoadRoutingService.RouteCoordinate from, RoadRoutingService.RouteCoordinate to) {
    double latitudeDelta = from.latitude() - to.latitude();
    double longitudeDelta = from.longitude() - to.longitude();
    return Math.sqrt(latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta) * 111.0;
  }
}
