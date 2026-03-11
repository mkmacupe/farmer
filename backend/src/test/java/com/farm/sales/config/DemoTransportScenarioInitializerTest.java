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
  void seedDemoScenarioCreatesThirtyPointsAndSixtyApprovedOrders() {
    User berezka = user(1L, "berezka", "Берёзка");
    User kvartal = user(2L, "kvartal", "Квартал");
    User yantar = user(3L, "yantar", "Янтарь");
    User manager = user(4L, "manager", "Менеджер");

    when(userRepository.findByUsername("berezka")).thenReturn(Optional.of(berezka));
    when(userRepository.findByUsername("kvartal")).thenReturn(Optional.of(kvartal));
    when(userRepository.findByUsername("yantar")).thenReturn(Optional.of(yantar));
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
    when(productRepository.findAll()).thenReturn(List.of(
        product(101L, "Товар 1", "10.50"),
        product(102L, "Товар 2", "11.20"),
        product(103L, "Товар 3", "12.00"),
        product(104L, "Товар 4", "13.40"),
        product(105L, "Товар 5", "14.10"),
        product(106L, "Товар 6", "9.90")
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

    ArgumentCaptor<StoreAddress> addressCaptor = ArgumentCaptor.forClass(StoreAddress.class);
    verify(storeAddressRepository, times(30)).save(addressCaptor.capture());
    assertThat(addressCaptor.getAllValues())
        .extracting(StoreAddress::getLabel)
        .contains("Точка 01", "Точка 15", "Точка 30");

    ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository, times(60)).save(orderCaptor.capture());
    assertThat(orderCaptor.getAllValues())
        .allSatisfy(order -> {
          assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
          assertThat(order.getDeliveryAddress()).isNotNull();
          assertThat(order.getApprovedByManager()).isSameAs(manager);
          assertThat(order.getItems()).hasSize(2);
          assertThat(order.getTotalAmount()).isNotNull();
        });
    assertThat(orderCaptor.getAllValues().stream()
        .collect(java.util.stream.Collectors.groupingBy(Order::getDeliveryAddressText))
        .values())
        .allSatisfy(ordersAtPoint -> {
          double pointWeightKg = ordersAtPoint.stream().mapToDouble(this::orderWeightKg).sum();
          assertThat(pointWeightKg).isBetween(480.0, 520.0);
        });
  }

  private User user(Long id, String username, String fullName) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setFullName(fullName);
    user.setRole("manager".equals(username) ? Role.MANAGER : Role.DIRECTOR);
    user.setPasswordHash("encoded");
    return user;
  }

  private Product product(Long id, String name, String price) {
    Product product = new Product();
    product.setId(id);
    product.setName(name);
    product.setCategory("Категория");
    product.setPrice(new BigDecimal(price));
    product.setStockQuantity(100);
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
