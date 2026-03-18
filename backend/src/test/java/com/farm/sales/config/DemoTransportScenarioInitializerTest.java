package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DemoTransportScenarioInitializerTest {
  private UserRepository userRepository;
  private ProductRepository productRepository;
  private StoreAddressRepository storeAddressRepository;
  private OrderRepository orderRepository;
  private DemoTransportScenarioInitializer initializer;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    productRepository = mock(ProductRepository.class);
    storeAddressRepository = mock(StoreAddressRepository.class);
    orderRepository = mock(OrderRepository.class);
    initializer = new DemoTransportScenarioInitializer(
        userRepository,
        productRepository,
        storeAddressRepository,
        orderRepository
    );
  }

  @Test
  void seedDemoScenarioCreatesThirtyPointsAndThirtyApprovedOrdersWith4498KgTotalWeight() {
    List<String> directorUsernames = DataInitializer.demoDirectorUsernames();
    for (int index = 0; index < directorUsernames.size(); index++) {
      String username = directorUsernames.get(index);
      when(userRepository.findByUsername(username)).thenReturn(Optional.of(user((long) index + 1, username, "Директор " + (index + 1))));
    }
    User manager = user(100L, "manager", "Менеджер");
    manager.setRole(Role.MANAGER);
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
    when(productRepository.findAll()).thenReturn(List.of(
        product(101L, "Товар 1", "10.50", 1.0),
        product(102L, "Товар 2", "11.20", 1.0),
        product(103L, "Товар 3", "12.00", 1.0)
    ));
    when(orderRepository.count()).thenReturn(0L);

    AtomicLong addressIds = new AtomicLong(10);
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
      StoreAddress address = invocation.getArgument(0);
      if (address.getId() == null) {
        address.setId(addressIds.incrementAndGet());
      }
      return address;
    });

    AtomicLong orderIds = new AtomicLong(100);
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
      Order order = invocation.getArgument(0);
      if (order.getId() == null) {
        order.setId(orderIds.incrementAndGet());
      }
      return order;
    });

    initializer.seedDemoScenario();

    verify(storeAddressRepository).deleteAllInBatch();

    ArgumentCaptor<StoreAddress> addressCaptor = ArgumentCaptor.forClass(StoreAddress.class);
    verify(storeAddressRepository, times(30)).save(addressCaptor.capture());
    assertThat(addressCaptor.getAllValues())
        .extracting(StoreAddress::getLabel)
        .contains("Сценарий 01", "Сценарий 15", "Сценарий 30");

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, times(30)).save(orderCaptor.capture());
    assertThat(orderCaptor.getAllValues())
        .allSatisfy(order -> {
          assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
          assertThat(order.getDeliveryAddress()).isNotNull();
          assertThat(order.getApprovedByManager()).isSameAs(manager);
          assertThat(order.getItems()).hasSize(1);
          assertThat(order.getTotalAmount()).isNotNull();
        });

    var ordersByPoint = orderCaptor.getAllValues().stream()
        .collect(java.util.stream.Collectors.groupingBy(Order::getDeliveryAddressText));
    assertThat(ordersByPoint).hasSize(30);
    assertThat(ordersByPoint.values()).allSatisfy(ordersAtPoint -> assertThat(ordersAtPoint).hasSize(1));

    double totalWeightKg = orderCaptor.getAllValues().stream().mapToDouble(this::orderWeightKg).sum();
    assertThat(totalWeightKg).isEqualTo(4498.0);
  }

  private User user(Long id, String username, String fullName) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setFullName(fullName);
    user.setRole(Role.DIRECTOR);
    user.setPasswordHash("encoded");
    return user;
  }

  private Product product(Long id, String name, String price, double weightKg) {
    Product product = new Product();
    product.setId(id);
    product.setName(name);
    product.setCategory("Категория");
    product.setPrice(new BigDecimal(price));
    product.setStockQuantity(100);
    product.setWeightKg(weightKg);
    return product;
  }

  private double orderWeightKg(Order order) {
    if (order.getItems() == null) {
      return 0.0;
    }
    return order.getItems().stream()
        .mapToDouble(item -> {
          Product product = item.getProduct();
          double weightKg = product == null || product.getWeightKg() == null ? 1.0 : product.getWeightKg();
          return weightKg * item.getQuantity();
        })
        .sum();
  }
}
