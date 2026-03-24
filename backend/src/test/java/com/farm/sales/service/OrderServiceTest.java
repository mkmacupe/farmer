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
import com.farm.sales.dto.AutoAssignRouteGeometryRequest;
import com.farm.sales.dto.AutoAssignRoutePathPointResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
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
    when(roadRoutingService.drivingDistanceMatrixKm(any(), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      List<RoadRoutingService.RouteCoordinate> sources = invocation.getArgument(0);
      @SuppressWarnings("unchecked")
      List<RoadRoutingService.RouteCoordinate> destinations = invocation.getArgument(1);
      return sources.stream()
          .map(source -> destinations.stream()
              .map(destination -> testDistance(source, destination))
              .toList())
          .toList();
    });
    when(roadRoutingService.drivingDistanceKm(any(), any())).thenAnswer(invocation -> {
      RoadRoutingService.RouteCoordinate from = invocation.getArgument(0);
      RoadRoutingService.RouteCoordinate to = invocation.getArgument(1);
      return testDistance(from, to);
    });
    when(roadRoutingService.drivingRouteGeometry(any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      List<RoadRoutingService.RouteCoordinate> waypoints = invocation.getArgument(0);
      return waypoints;
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
    order.setRouteTripNumber(1);
    order.setRouteStopSequence(1);
    when(orderRepository.findByAssignedDriverIdOrderByRouteOrder(eq(3L), any(Pageable.class)))
        .thenReturn(List.of(order));

    List<OrderResponse> responses = orderService.getOrdersForRole(Role.DRIVER, 3L);

    assertThat(responses).hasSize(1);
    assertThat(responses.getFirst().routeTripNumber()).isEqualTo(1);
    assertThat(responses.getFirst().routeStopSequence()).isEqualTo(1);
    verify(orderRepository).findByAssignedDriverIdOrderByRouteOrder(eq(3L), any(Pageable.class));
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
    assertThat(preview.routes())
        .filteredOn(route -> !route.points().isEmpty())
        .allSatisfy(route -> assertThat(route.path()).hasSizeGreaterThanOrEqualTo(2));
    AutoAssignRoutePathPointResponse depot = new AutoAssignRoutePathPointResponse(53.8971270, 30.3320410);
    assertThat(preview.routes())
        .filteredOn(route -> !route.trips().isEmpty())
        .allSatisfy(route -> assertThat(route.trips().getFirst().path().getFirst()).isEqualTo(depot));

    assertThat(firstApproved.getStatus()).isEqualTo(OrderStatus.APPROVED);
    assertThat(secondApproved.getStatus()).isEqualTo(OrderStatus.APPROVED);
    verify(orderRepository, never()).save(any(Order.class));
    verify(orderTimelineService, never()).recordStatusChange(any(Order.class), eq(OrderStatus.APPROVED), eq(OrderStatus.ASSIGNED));
    verify(roadRoutingService, never()).drivingRouteGeometry(any());
  }


  @Test
  void previewAutoAssignPlanHandlesApprovedOrdersWithoutItems() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driver = user(3L, "Driver A", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);

    Order approvedWithoutItems = orderWithCoordinates(401L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    approvedWithoutItems.setItems(null);

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(approvedWithoutItems));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of(driver));
    when(orderRepository.countByAssignedDriverIdAndStatus(3L, OrderStatus.ASSIGNED)).thenReturn(0L);

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    assertThat(preview.totalApprovedOrders()).isEqualTo(1);
    assertThat(preview.plannedOrders()).isEqualTo(1);
    assertThat(preview.routes()).isNotEmpty();
  }

  @Test
  void previewAutoAssignPlanSkipsRoadMatrixForLargePreviewSets() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    User driverSouthWest = user(5L, "Driver 3", Role.DRIVER);
    driverNorth.setUsername("driver1");
    driverSouthEast.setUsername("driver2");
    driverSouthWest.setUsername("driver3");
    User director = user(7L, "Director", Role.DIRECTOR);

    List<Order> approvedOrders = new ArrayList<>();
    for (int index = 0; index < 61; index++) {
      Order order = order(900L + index, director, OrderStatus.APPROVED);
      order.setDeliveryLatitude(BigDecimal.valueOf(53.7000000 + index * 0.002));
      order.setDeliveryLongitude(BigDecimal.valueOf(30.1000000 + index * 0.002));
      approvedOrders.add(order);
    }

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(approvedOrders);
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast, driverSouthWest));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    assertThat(preview.totalApprovedOrders()).isEqualTo(61);
    assertThat(preview.plannedOrders()).isEqualTo(61);
    assertThat(preview.approximatePlanningDistances()).isTrue();
    assertThat(preview.planningHighlights())
        .anySatisfy(item -> assertThat(item).contains("следующий рейс"));
    assertThat(preview.routes())
        .flatExtracting(route -> route.points())
        .hasSize(61);
    verify(roadRoutingService, never()).drivingDistanceMatrixKm(any(), any());
  }

  @Test
  void previewAutoAssignPlanAssignsMultipleOrdersFromSameLocation() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverA = user(3L, "Driver A", Role.DRIVER);
    User driverB = user(4L, "Driver B", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);

    Order firstApproved = orderWithCoordinates(501L, director, OrderStatus.APPROVED, "53.8654000", "30.2905000");
    Order secondApproved = orderWithCoordinates(502L, director, OrderStatus.APPROVED, "53.8654000", "30.2905000");
    firstApproved.setItems(List.of(item(firstApproved, product(1L, "Горох", "2.60", 100), 8)));
    secondApproved.setItems(List.of(item(secondApproved, product(2L, "Гречка", "3.00", 100), 6)));

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(firstApproved, secondApproved));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of(driverA, driverB));
    when(orderRepository.countByAssignedDriverIdAndStatus(3L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.countByAssignedDriverIdAndStatus(4L, OrderStatus.ASSIGNED)).thenReturn(0L);

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    assertThat(preview.totalApprovedOrders()).isEqualTo(2);
    assertThat(preview.plannedOrders()).isEqualTo(2);
    assertThat(preview.unplannedOrders()).isEqualTo(0);
    assertThat(preview.routes())
        .flatExtracting(route -> route.points())
        .extracting(point -> point.orderId())
        .containsExactlyInAnyOrder(501L, 502L);
  }

  @Test
  void previewAutoAssignPlanKeepsAllSeparatedOrdersInSingleOptimalPlan() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    User driverSouthWest = user(5L, "Driver 3", Role.DRIVER);
    driverNorth.setUsername("driver1");
    driverSouthEast.setUsername("driver2");
    driverSouthWest.setUsername("driver3");
    User director = user(7L, "Director", Role.DIRECTOR);

    Order northOrder = orderWithCoordinates(601L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    Order southEastOrder = orderWithCoordinates(602L, director, OrderStatus.APPROVED, "53.8710000", "30.4095000");
    Order southWestOrder = orderWithCoordinates(603L, director, OrderStatus.APPROVED, "53.8600000", "30.2605000");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(northOrder, southEastOrder, southWestOrder));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast, driverSouthWest));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    java.util.Map<Long, Long> driverByOrderId = preview.routes().stream()
        .flatMap(route -> route.points().stream().map(point -> java.util.Map.entry(point.orderId(), route.driverId())))
        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));

    assertThat(driverByOrderId.keySet()).containsExactlyInAnyOrder(601L, 602L, 603L);
    assertThat(new java.util.HashSet<>(driverByOrderId.values())).hasSizeBetween(1, 3);
    assertThat(preview.estimatedTotalDistanceKm()).isGreaterThan(0.0);
  }

  @Test
  void previewAutoAssignPlanAssignsBorderlinePointToNearestDriverFromDepot() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    driverNorth.setUsername("driver1");
    driverSouthEast.setUsername("driver2");
    User director = user(7L, "Director", Role.DIRECTOR);

    Order borderlineOrder = orderWithCoordinates(604L, director, OrderStatus.APPROVED, "53.9150000", "30.3600000");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(borderlineOrder));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    java.util.Map<Long, Long> driverByOrderId = preview.routes().stream()
        .flatMap(route -> route.points().stream().map(point -> java.util.Map.entry(point.orderId(), route.driverId())))
        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));

    assertThat(driverByOrderId).containsEntry(604L, 3L);
  }

  @Test
  void previewAutoAssignPlanReordersDriverStopsByPureNearestDistanceAfterAssignment() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    driverNorth.setUsername("driver1");
    driverSouthEast.setUsername("driver2");
    User director = user(7L, "Director", Role.DIRECTOR);

    Order northButFarther = orderWithCoordinates(701L, director, OrderStatus.APPROVED, "54.0100000", "30.3400000");
    Order northButCloser = orderWithCoordinates(702L, director, OrderStatus.APPROVED, "53.9450000", "30.3420000");
    Order southEastAnchor = orderWithCoordinates(703L, director, OrderStatus.APPROVED, "53.8700000", "30.4150000");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(northButFarther, northButCloser, southEastAnchor));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    var northRoute = preview.routes().stream()
        .filter(route -> route.driverId().equals(3L))
        .findFirst()
        .orElseThrow();

    assertThat(northRoute.points())
        .extracting(point -> point.orderId())
        .startsWith(702L, 701L);
    assertThat(northRoute.points())
        .extracting(point -> point.tripNumber())
        .startsWith(1, 1);
    assertThat(northRoute.points())
        .extracting(point -> point.stopSequence())
        .startsWith(1, 2);
    assertThat(northRoute.points().get(0).distanceFromPreviousKm())
        .isLessThan(northRoute.points().get(1).distanceFromPreviousKm());
  }

  @Test
  void previewAutoAssignPlanSwapsLaterTripPointIntoEarlierNearbyTripWhenCapacityIsTight() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverOne = user(3L, "Driver 1", Role.DRIVER);
    User driverTwo = user(4L, "Driver 2", Role.DRIVER);
    User driverThree = user(5L, "Driver 3", Role.DRIVER);
    driverOne.setUsername("driver1");
    driverTwo.setUsername("driver2");
    driverThree.setUsername("driver3");
    User director = user(7L, "Director", Role.DIRECTOR);

    Product load800 = product(21L, "Груз 800", "5.00", 100);
    load800.setWeightKg(800.0);
    load800.setVolumeM3(1.0);
    Product load700 = product(22L, "Груз 700", "5.00", 100);
    load700.setWeightKg(700.0);
    load700.setVolumeM3(1.0);
    Product load1500 = product(23L, "Груз 1500", "5.00", 100);
    load1500.setWeightKg(1500.0);
    load1500.setVolumeM3(1.0);

    Order eastAnchor = orderWithCoordinates(901L, director, OrderStatus.APPROVED, "53.9100000", "30.3600000");
    eastAnchor.setItems(List.of(item(eastAnchor, load800, 1)));
    Order eastOuter = orderWithCoordinates(902L, director, OrderStatus.APPROVED, "53.9120000", "30.3680000");
    eastOuter.setItems(List.of(item(eastOuter, load700, 1)));
    Order southHeavy = orderWithCoordinates(903L, director, OrderStatus.APPROVED, "53.8500000", "30.3320000");
    southHeavy.setItems(List.of(item(southHeavy, load1500, 1)));
    Order westInner = orderWithCoordinates(904L, director, OrderStatus.APPROVED, "53.9000000", "30.2700000");
    westInner.setItems(List.of(item(westInner, load700, 1)));
    Order westMiddle = orderWithCoordinates(905L, director, OrderStatus.APPROVED, "53.9000000", "30.2500000");
    westMiddle.setItems(List.of(item(westMiddle, load800, 1)));
    Order westOuter = orderWithCoordinates(906L, director, OrderStatus.APPROVED, "53.9000000", "30.2300000");
    westOuter.setItems(List.of(item(westOuter, load700, 1)));

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(eastAnchor, eastOuter, southHeavy, westInner, westMiddle, westOuter));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverOne, driverTwo, driverThree));
    doReturn(List.of(
        List.of(5.0, 7.0, 6.0, 8.0, 10.0, 12.0, 0.0),
        List.of(0.0, 2.0, 20.0, 20.0, 22.0, 24.0, 5.0),
        List.of(2.0, 0.0, 18.0, 22.0, 24.0, 26.0, 7.0),
        List.of(20.0, 18.0, 0.0, 20.0, 22.0, 24.0, 6.0),
        List.of(20.0, 22.0, 20.0, 0.0, 2.0, 4.0, 8.0),
        List.of(22.0, 24.0, 22.0, 2.0, 0.0, 2.0, 10.0),
        List.of(24.0, 26.0, 24.0, 4.0, 2.0, 0.0, 12.0)
    )).when(roadRoutingService).drivingDistanceMatrixKm(any(), any());

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L, List.of(3L, 4L, 5L));

    var assignmentByOrderId = preview.routes().stream()
        .flatMap(route -> route.points().stream().map(point -> java.util.Map.entry(point.orderId(), java.util.Map.entry(route.driverId(), point.tripNumber()))))
        .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));

    assertThat(assignmentByOrderId.get(906L)).isEqualTo(java.util.Map.entry(5L, 1));
    assertThat(assignmentByOrderId.get(904L)).isEqualTo(java.util.Map.entry(3L, 2));

    var driverThreeRoute = preview.routes().stream()
        .filter(route -> route.driverId().equals(5L))
        .findFirst()
        .orElseThrow();
    assertThat(driverThreeRoute.points())
        .extracting(point -> point.orderId())
        .containsExactly(905L, 906L);
    assertThat(driverThreeRoute.points())
        .extracting(point -> point.tripNumber())
        .containsExactly(1, 1);
    assertThat(driverThreeRoute.trips()).singleElement().satisfies(trip -> {
      assertThat(trip.totalWeightKg()).isLessThanOrEqualTo(1500.01);
      assertThat(trip.totalVolumeM3()).isLessThanOrEqualTo(12.01);
    });
  }

  @Test
  void previewAutoAssignPlanStartsEveryTripFromDepotAndReturnsOnlyBetweenTrips() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    User driverSouthWest = user(5L, "Driver 3", Role.DRIVER);
    driverNorth.setUsername("driver1");
    driverSouthEast.setUsername("driver2");
    driverSouthWest.setUsername("driver3");
    User director = user(7L, "Director", Role.DIRECTOR);

    Product heavyLoad = product(10L, "Тяжелый груз", "8.00", 100);
    heavyLoad.setWeightKg(1200.0);
    heavyLoad.setVolumeM3(1.0);

    Order northFirst = orderWithCoordinates(801L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    northFirst.setItems(List.of(item(northFirst, heavyLoad, 1)));
    Order southEast = orderWithCoordinates(802L, director, OrderStatus.APPROVED, "53.8710000", "30.4095000");
    southEast.setItems(List.of(item(southEast, heavyLoad, 1)));
    Order southWest = orderWithCoordinates(803L, director, OrderStatus.APPROVED, "53.8600000", "30.2605000");
    southWest.setItems(List.of(item(southWest, heavyLoad, 1)));
    Order northSecond = orderWithCoordinates(804L, director, OrderStatus.APPROVED, "53.9325000", "30.3460000");
    northSecond.setItems(List.of(item(northSecond, heavyLoad, 1)));
    Order activeNorth = orderWithCoordinates(805L, director, OrderStatus.ASSIGNED, "53.9450000", "30.3455000");
    activeNorth.setAssignedDriver(driverNorth);
    activeNorth.setAssignedAt(Instant.now().minusSeconds(30));
    activeNorth.setDeliveryAddressText("Активная точка Севера");

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(northFirst, southEast, southWest, northSecond));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast, driverSouthWest));
    when(orderRepository.findFirstByAssignedDriverIdAndStatusOrderByAssignedAtDescIdDesc(3L, OrderStatus.ASSIGNED))
        .thenReturn(Optional.of(activeNorth));
    when(orderRepository.countByAssignedDriverIdsAndStatus(any(), eq(OrderStatus.ASSIGNED)))
        .thenReturn(List.of(driverLoadAggregate(3L, 1L)));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);

    AutoAssignRoutePathPointResponse depot = new AutoAssignRoutePathPointResponse(53.8971270, 30.3320410);
    assertThat(preview.plannedOrders()).isEqualTo(4);
    assertThat(preview.unplannedOrders()).isEqualTo(0);

    var northRoute = preview.routes().stream()
        .filter(route -> route.driverId().equals(3L))
        .findFirst()
        .orElseThrow();

    assertThat(northRoute.trips()).hasSize(2);
    assertThat(northRoute.insights()).isNotEmpty();
    assertThat(northRoute.trips().getFirst().path().getFirst()).isEqualTo(depot);
    assertThat(northRoute.trips().getFirst().returnsToDepot()).isTrue();
    assertThat(northRoute.trips().getFirst().insights()).isNotEmpty();
    assertThat(northRoute.trips().getFirst().path().getLast()).isEqualTo(depot);
    assertThat(northRoute.trips().get(1).returnsToDepot()).isTrue();
    assertThat(northRoute.trips().get(1).path().getFirst()).isEqualTo(depot);
    assertThat(northRoute.trips().get(1).path().getLast()).isEqualTo(depot);
    assertThat(northRoute.points())
        .extracting(point -> point.tripNumber())
        .containsExactly(1, 2);
    assertThat(northRoute.points())
        .extracting(point -> point.stopSequence())
        .containsExactly(1, 2);
    assertThat(northRoute.points())
        .allSatisfy(point -> assertThat(point.selectionReason()).isNotBlank());
    verify(roadRoutingService, times(4)).drivingDistanceKm(any(), any());
  }

  void previewAutoAssignPlanMaintainsCapacityAndExplainsRoutesAcrossRandomizedScenarios() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driverNorth = user(3L, "Driver 1", Role.DRIVER);
    User driverSouthEast = user(4L, "Driver 2", Role.DRIVER);
    User driverSouthWest = user(5L, "Driver 3", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNorth, driverSouthEast, driverSouthWest));

    Random random = new Random(42L);
    AutoAssignRoutePathPointResponse depot = new AutoAssignRoutePathPointResponse(53.8971270, 30.3320410);

    for (int scenario = 0; scenario < 30; scenario++) {
      List<Order> approvedOrders = new ArrayList<>();
      int orderCount = 7 + random.nextInt(8);
      double anchorLatitude = 53.82 + random.nextDouble() * 0.03;
      double anchorLongitude = 30.24 + random.nextDouble() * 0.03;

      for (int index = 0; index < orderCount; index++) {
        double latitude = index > 0 && index % 4 == 0
            ? approvedOrders.get(index - 1).getDeliveryLatitude().doubleValue()
            : 53.82 + random.nextDouble() * 0.14;
        double longitude = index > 0 && index % 4 == 0
            ? approvedOrders.get(index - 1).getDeliveryLongitude().doubleValue()
            : 30.24 + random.nextDouble() * 0.18;

        if (index == orderCount - 1) {
          latitude = anchorLatitude;
          longitude = anchorLongitude;
        }

        Order order = orderWithCoordinates(
            5000L + scenario * 100L + index,
            director,
            OrderStatus.APPROVED,
            String.format(java.util.Locale.US, "%.7f", latitude),
            String.format(java.util.Locale.US, "%.7f", longitude)
        );
        Product cargo = product(9000L + scenario * 100L + index, "Сборный груз", "5.00", 10_000);
        cargo.setWeightKg(12.0);
        cargo.setVolumeM3(0.06);
        int quantity = 18 + random.nextInt(22);
        order.setItems(List.of(item(order, cargo, quantity)));
        approvedOrders.add(order);
      }

      when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
          .thenReturn(approvedOrders);

      AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L, List.of(3L, 4L, 5L));

      assertThat(preview.totalApprovedOrders()).isEqualTo(orderCount);
      assertThat(preview.plannedOrders()).isEqualTo(orderCount);
      assertThat(preview.unplannedOrders()).isEqualTo(0);
      assertThat(preview.planningHighlights()).hasSizeGreaterThanOrEqualTo(2);
      assertThat(preview.planningHighlights())
          .anySatisfy(item -> assertThat(item).contains("водителями"));
      assertThat(preview.planningHighlights())
          .anySatisfy(item -> assertThat(item).contains("следующий рейс"));

      Set<Long> plannedOrderIds = new HashSet<>();
      preview.routes().forEach(route -> {
        if (!route.points().isEmpty()) {
          assertThat(route.insights()).isNotEmpty();
        }
        route.points().forEach(point -> {
          assertThat(plannedOrderIds.add(point.orderId())).isTrue();
          assertThat(point.selectionReason()).isNotBlank();
        });
        route.trips().forEach(trip -> {
          assertThat(trip.totalWeightKg()).isLessThanOrEqualTo(1500.01);
          assertThat(trip.totalVolumeM3()).isLessThanOrEqualTo(12.01);
          assertThat(trip.weightUtilizationPercent()).isBetween(0.0, 100.01);
          assertThat(trip.volumeUtilizationPercent()).isBetween(0.0, 100.01);
          assertThat(trip.insights()).isNotEmpty();
          if (!trip.path().isEmpty()) {
            assertThat(trip.path().getFirst()).isEqualTo(depot);
            assertThat(trip.returnsToDepot()).isTrue();
            assertThat(trip.path().getLast()).isEqualTo(depot);
          }
        });
      });

      assertThat(plannedOrderIds)
          .containsExactlyInAnyOrderElementsOf(approvedOrders.stream().map(Order::getId).toList());
    }
  }

  @Test
  void previewAutoAssignPlanRejectsProductsWithoutTransportMetrics() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    User driver = user(3L, "Driver A", Role.DRIVER);
    User director = user(7L, "Director", Role.DIRECTOR);

    Product legacyProduct = product(9L, "Старый товар", "7.50", 20);
    legacyProduct.setWeightKg(null);
    legacyProduct.setVolumeM3(null);

    Order approved = orderWithCoordinates(402L, director, OrderStatus.APPROVED, "53.9395000", "30.3410000");
    approved.setItems(List.of(item(approved, legacyProduct, 2)));

    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));
    when(orderRepository.findByStatusOrderByCreatedAtDesc(eq(OrderStatus.APPROVED), any(Pageable.class)))
        .thenReturn(List.of(approved));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of(driver));

    assertThatThrownBy(() -> orderService.previewAutoAssignPlan(2L))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(ex.getReason()).contains("weightKg");
          assertThat(ex.getReason()).contains("volumeM3");
          assertThat(ex.getReason()).contains("Старый товар");
        });
  }

  @Test
  void previewAutoAssignRouteGeometryBuildsPathFromProvidedPoints() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));

    List<AutoAssignRoutePathPointResponse> geometry = orderService.previewAutoAssignRouteGeometry(
        2L,
        new AutoAssignRouteGeometryRequest(List.of(new AutoAssignRoutePathPointResponse(53.9395, 30.3410)))
    );

    assertThat(geometry).hasSizeGreaterThanOrEqualTo(2);
    assertThat(geometry.getLast()).isEqualTo(new AutoAssignRoutePathPointResponse(53.9395, 30.3410));
    verify(roadRoutingService).drivingRouteGeometry(any());
  }

  @Test
  void previewAutoAssignRouteGeometryStartsFromDepot() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));

    List<AutoAssignRoutePathPointResponse> geometry = orderService.previewAutoAssignRouteGeometry(
        2L,
        new AutoAssignRouteGeometryRequest(List.of(new AutoAssignRoutePathPointResponse(53.9395, 30.3410)))
    );

    assertThat(geometry).hasSizeGreaterThanOrEqualTo(2);
    assertThat(geometry.getFirst()).isEqualTo(new AutoAssignRoutePathPointResponse(53.8971270, 30.3320410));
    assertThat(geometry.getLast()).isEqualTo(new AutoAssignRoutePathPointResponse(53.9395, 30.3410));
    verify(roadRoutingService).drivingRouteGeometry(any());
  }

  @Test
  void previewAutoAssignRouteGeometryReturnsToDepotWhenRequested() {
    User logistician = user(2L, "Logistician", Role.LOGISTICIAN);
    when(userRepository.findById(2L)).thenReturn(Optional.of(logistician));

    List<AutoAssignRoutePathPointResponse> geometry = orderService.previewAutoAssignRouteGeometry(
        2L,
        new AutoAssignRouteGeometryRequest(
            List.of(new AutoAssignRoutePathPointResponse(53.9395, 30.3410)),
            true
        )
    );

    assertThat(geometry).hasSizeGreaterThanOrEqualTo(3);
    assertThat(geometry.getFirst()).isEqualTo(geometry.getLast());
    verify(roadRoutingService).drivingRouteGeometry(any());
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
    when(orderRepository.findAllByIdInForUpdate(any()))
        .thenReturn(List.of(firstApproved, secondApproved));
    when(orderRepository.countByStatus(OrderStatus.APPROVED)).thenReturn(2L);
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER))
        .thenReturn(List.of(driverNearFirst, driverNearSecond));
    when(orderRepository.countByAssignedDriverIdAndStatus(3L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.countByAssignedDriverIdAndStatus(4L, OrderStatus.ASSIGNED)).thenReturn(0L);
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    AutoAssignPreviewResponse preview = orderService.previewAutoAssignPlan(2L);
    List<AutoAssignApproveItemRequest> requestedAssignments = preview.routes().stream()
        .flatMap(route -> route.points().stream()
        .map(point -> new AutoAssignApproveItemRequest(
            point.orderId(),
            route.driverId(),
            point.tripNumber(),
            point.stopSequence(),
            point.distanceFromPreviousKm()
        )))
        .toList();
    AutoAssignResultResponse result = orderService.approveAutoAssignPlan(2L, new AutoAssignApproveRequest(requestedAssignments));

    assertThat(result.totalApprovedOrders()).isEqualTo(2);
    assertThat(result.assignedOrders()).isEqualTo(2);
    assertThat(result.unassignedOrders()).isEqualTo(0);
    assertThat(result.assignments()).hasSize(2);
    assertThat(result.estimatedTotalDistanceKm()).isCloseTo(preview.estimatedTotalDistanceKm(), org.assertj.core.data.Offset.offset(0.01));
    assertThat(firstApproved.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(secondApproved.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(firstApproved.getRouteTripNumber()).isNotNull();
    assertThat(firstApproved.getRouteStopSequence()).isNotNull();
    assertThat(secondApproved.getRouteTripNumber()).isNotNull();
    assertThat(secondApproved.getRouteStopSequence()).isNotNull();
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
    product.setWeightKg(1.0);
    product.setVolumeM3(0.001);
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
    order.setRouteTripNumber(null);
    order.setRouteStopSequence(null);
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
        director.getLegalEntityName(),
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
        null,
        null,
        new BigDecimal("10.00"),
        List.of()
    );
  }

  private OrderRepository.DriverLoadAggregate driverLoadAggregate(Long driverId, long total) {
    return new OrderRepository.DriverLoadAggregate() {
      @Override
      public Long getDriverId() {
        return driverId;
      }

      @Override
      public Number getTotal() {
        return total;
      }
    };
  }

  private double testDistance(RoadRoutingService.RouteCoordinate from, RoadRoutingService.RouteCoordinate to) {
    double latitudeDelta = from.latitude() - to.latitude();
    double longitudeDelta = from.longitude() - to.longitude();
    return Math.sqrt(latitudeDelta * latitudeDelta + longitudeDelta * longitudeDelta) * 111.0;
  }
}
