package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DemoTransportScenarioInitializerTest {
  private UserRepository userRepository;
  private ProductRepository productRepository;
  private StoreAddressRepository storeAddressRepository;
  private OrderRepository orderRepository;
  private DemoTransportScenarioInitializer initializer;

  @BeforeEach
  void setUp() {
    userRepository = org.mockito.Mockito.mock(UserRepository.class);
    productRepository = org.mockito.Mockito.mock(ProductRepository.class);
    storeAddressRepository = org.mockito.Mockito.mock(StoreAddressRepository.class);
    orderRepository = org.mockito.Mockito.mock(OrderRepository.class);
    initializer = new DemoTransportScenarioInitializer(
        userRepository,
        productRepository,
        storeAddressRepository,
        orderRepository
    );
  }

  @Test
  void seedDemoScenarioCreatesThirtyPointsAndSixtyApprovedOrders() {
    User berezka = user(1L, "berezka", Role.DIRECTOR);
    User kvartal = user(2L, "kvartal", Role.DIRECTOR);
    User yantar = user(3L, "yantar", Role.DIRECTOR);
    User manager = user(10L, "manager", Role.MANAGER);

    when(userRepository.findByUsername("berezka")).thenReturn(Optional.of(berezka));
    when(userRepository.findByUsername("kvartal")).thenReturn(Optional.of(kvartal));
    when(userRepository.findByUsername("yantar")).thenReturn(Optional.of(yantar));
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
    when(productRepository.findAll()).thenReturn(List.of(
        product(101L, "Молоко", "3.20"),
        product(102L, "Сыр", "7.40"),
        product(103L, "Картофель", "2.10"),
        product(104L, "Яблоки", "3.50"),
        product(105L, "Мёд", "9.90"),
        product(106L, "Кефир", "3.10")
    ));
    when(orderRepository.count()).thenReturn(0L);
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    initializer.seedDemoScenario();

    var addressCaptor = org.mockito.ArgumentCaptor.forClass(StoreAddress.class);
    var orderCaptor = org.mockito.ArgumentCaptor.forClass(Order.class);
    verify(storeAddressRepository, times(30)).save(addressCaptor.capture());
    verify(orderRepository, times(60)).save(orderCaptor.capture());

    assertThat(addressCaptor.getAllValues())
        .extracting(StoreAddress::getLabel)
        .contains("Точка 01", "Точка 15", "Точка 30");
    assertThat(orderCaptor.getAllValues())
        .allSatisfy(order -> {
          assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
          assertThat(order.getDeliveryAddress()).isNotNull();
          assertThat(order.getItems()).isNotEmpty();
          assertThat(order.getTotalAmount()).isNotNull();
        });
  }

  private static User user(Long id, String username, Role role) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setRole(role);
    user.setFullName(username);
    return user;
  }

  private static Product product(Long id, String name, String price) {
    Product product = new Product();
    product.setId(id);
    product.setName(name);
    product.setCategory("Категория");
    product.setDescription(name);
    product.setPrice(new BigDecimal(price));
    product.setStockQuantity(100);
    product.setWeightKg(1.0);
    product.setVolumeM3(0.001);
    return product;
  }
}
