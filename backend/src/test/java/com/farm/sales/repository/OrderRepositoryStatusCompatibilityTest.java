package com.farm.sales.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OrderRepositoryStatusCompatibilityTest {
  @Autowired
  private OrderRepository orderRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private StoreAddressRepository storeAddressRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @PersistenceContext
  private EntityManager entityManager;

  @Test
  void findPageAllByOrderByCreatedAtDescHandlesLegacyOrderStatusValues() {
    String suffix = Long.toUnsignedString(System.nanoTime());

    User customer = userRepository.save(new User(
        "legacy-status-" + suffix,
        "password-hash",
        "Legacy Customer",
        null,
        null,
        Role.DIRECTOR
    ));

    Instant now = Instant.now();
    StoreAddress address = new StoreAddress();
    address.setUser(customer);
    address.setLabel("Основной");
    address.setAddressLine("Тестовый адрес 1");
    address.setLatitude(BigDecimal.valueOf(53.9));
    address.setLongitude(BigDecimal.valueOf(30.33));
    address.setCreatedAt(now);
    address.setUpdatedAt(now);
    StoreAddress savedAddress = storeAddressRepository.save(address);

    Order order = new Order();
    order.setCustomer(customer);
    order.setDeliveryAddress(savedAddress);
    order.setDeliveryAddressText(savedAddress.getAddressLine());
    order.setDeliveryLatitude(savedAddress.getLatitude());
    order.setDeliveryLongitude(savedAddress.getLongitude());
    order.setStatus(OrderStatus.CREATED);
    order.setTotalAmount(BigDecimal.valueOf(10.00));
    order.setCreatedAt(now);
    order.setUpdatedAt(now);
    Order savedOrder = orderRepository.saveAndFlush(order);

    jdbcTemplate.update("update orders set status = ? where id = ?", "PENDING", savedOrder.getId());
    entityManager.clear();

    Page<Order> page = orderRepository.findPageAllByOrderByCreatedAtDesc(PageRequest.of(0, 50));
    Order loaded = page.getContent().stream()
        .filter(item -> savedOrder.getId().equals(item.getId()))
        .findFirst()
        .orElseThrow();

    assertThat(loaded.getStatus()).isEqualTo(OrderStatus.CREATED);
  }
}
