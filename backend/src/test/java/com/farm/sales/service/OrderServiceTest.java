package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.OrderCreateRequest;
import com.farm.sales.dto.OrderItemRequest;
import com.farm.sales.dto.OrderResponse;
import com.farm.sales.model.Order;
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
    orderService = new OrderService(
        orderRepository,
        productRepository,
        userRepository,
        directorProfileService,
        auditTrailPublisher,
        stockMovementService,
        orderTimelineService,
        notificationStreamService
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
}
