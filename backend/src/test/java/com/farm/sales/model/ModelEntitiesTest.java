package com.farm.sales.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelEntitiesTest {
  @Test
  void userProductAndAddressExposeConstructorsAndAccessors() {
    User user = new User("director", "hash", "Олег", "+37529", "ООО", Role.DIRECTOR);
    user.setId(1L);
    user.setUsername("director-1");
    user.setPasswordHash("hash-2");
    user.setFullName("Олег Курилин");
    user.setPhone(null);
    user.setLegalEntityName("ОАО");
    user.setRole(Role.MANAGER);
    assertThat(user.getId()).isEqualTo(1L);
    assertThat(user.getUsername()).isEqualTo("director-1");
    assertThat(user.getPasswordHash()).isEqualTo("hash-2");
    assertThat(user.getFullName()).isEqualTo("Олег Курилин");
    assertThat(user.getPhone()).isNull();
    assertThat(user.getLegalEntityName()).isEqualTo("ОАО");
    assertThat(user.getRole()).isEqualTo(Role.MANAGER);

    Product product = new Product("Молоко", "Молочная продукция", "desc", "/images/products/milk.webp",
        new BigDecimal("2.98"), 10);
    product.setId(2L);
    product.setName("Сыр");
    product.setCategory("Молочная продукция");
    product.setDescription("Новый");
    product.setPhotoUrl("/images/products/cheese.webp");
    product.setPrice(new BigDecimal("10.50"));
    product.setStockQuantity(7);
    assertThat(product.getId()).isEqualTo(2L);
    assertThat(product.getName()).isEqualTo("Сыр");
    assertThat(product.getCategory()).isEqualTo("Молочная продукция");
    assertThat(product.getDescription()).isEqualTo("Новый");
    assertThat(product.getPhotoUrl()).isEqualTo("/images/products/cheese.webp");
    assertThat(product.getPrice()).isEqualByComparingTo("10.50");
    assertThat(product.getStockQuantity()).isEqualTo(7);

    StoreAddress address = new StoreAddress();
    address.setId(3L);
    address.setUser(user);
    address.setLabel("Основной склад");
    address.setAddressLine("Могилёв, ул. Челюскинцев 105");
    address.setLatitude(new BigDecimal("53.8654000"));
    address.setLongitude(new BigDecimal("30.2905000"));
    address.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    address.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
    assertThat(address.getId()).isEqualTo(3L);
    assertThat(address.getUser()).isSameAs(user);
    assertThat(address.getLabel()).isEqualTo("Основной склад");
    assertThat(address.getAddressLine()).isEqualTo("Могилёв, ул. Челюскинцев 105");
    assertThat(address.getLatitude()).isEqualByComparingTo("53.8654000");
    assertThat(address.getLongitude()).isEqualByComparingTo("30.2905000");
    assertThat(address.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(address.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
  }

  @Test
  void orderAndRelatedEntitiesExposeAccessors() {
    User customer = new User();
    customer.setId(7L);
    customer.setFullName("Customer");
    StoreAddress deliveryAddress = new StoreAddress();
    deliveryAddress.setId(5L);
    deliveryAddress.setAddressLine("Адрес");
    Product product = new Product();
    product.setId(8L);
    product.setName("Товар");

    OrderItem item = new OrderItem();
    item.setId(11L);
    item.setProduct(product);
    item.setQuantity(2);
    item.setPrice(new BigDecimal("3.50"));
    item.setLineTotal(new BigDecimal("7.00"));

    Order order = new Order();
    order.setId(10L);
    order.setCustomer(customer);
    order.setDeliveryAddress(deliveryAddress);
    order.setDeliveryAddressText("Адрес");
    order.setDeliveryLatitude(new BigDecimal("53.9000000"));
    order.setDeliveryLongitude(new BigDecimal("30.3000000"));
    order.setStatus(OrderStatus.ASSIGNED);
    order.setTotalAmount(new BigDecimal("15.00"));
    order.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    order.setUpdatedAt(Instant.parse("2026-01-01T01:00:00Z"));
    order.setApprovedByManager(customer);
    order.setApprovedAt(Instant.parse("2026-01-01T00:30:00Z"));
    order.setAssignedDriver(customer);
    order.setAssignedByLogistician(customer);
    order.setAssignedAt(Instant.parse("2026-01-01T00:40:00Z"));
    order.setDeliveredAt(Instant.parse("2026-01-01T02:00:00Z"));
    order.setItems(List.of(item));
    item.setOrder(order);

    assertThat(order.getId()).isEqualTo(10L);
    assertThat(order.getCustomer()).isSameAs(customer);
    assertThat(order.getDeliveryAddress()).isSameAs(deliveryAddress);
    assertThat(order.getDeliveryAddressText()).isEqualTo("Адрес");
    assertThat(order.getDeliveryLatitude()).isEqualByComparingTo("53.9000000");
    assertThat(order.getDeliveryLongitude()).isEqualByComparingTo("30.3000000");
    assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
    assertThat(order.getTotalAmount()).isEqualByComparingTo("15.00");
    assertThat(order.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(order.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-01T01:00:00Z"));
    assertThat(order.getApprovedByManager()).isSameAs(customer);
    assertThat(order.getApprovedAt()).isEqualTo(Instant.parse("2026-01-01T00:30:00Z"));
    assertThat(order.getAssignedDriver()).isSameAs(customer);
    assertThat(order.getAssignedByLogistician()).isSameAs(customer);
    assertThat(order.getAssignedAt()).isEqualTo(Instant.parse("2026-01-01T00:40:00Z"));
    assertThat(order.getDeliveredAt()).isEqualTo(Instant.parse("2026-01-01T02:00:00Z"));
    assertThat(order.getItems()).containsExactly(item);

    assertThat(item.getId()).isEqualTo(11L);
    assertThat(item.getOrder()).isSameAs(order);
    assertThat(item.getProduct()).isSameAs(product);
    assertThat(item.getQuantity()).isEqualTo(2);
    assertThat(item.getPrice()).isEqualByComparingTo("3.50");
    assertThat(item.getLineTotal()).isEqualByComparingTo("7.00");
  }

  @Test
  void stockAndRealtimeEntitiesExposeAccessors() {
    Product product = new Product();
    product.setId(20L);
    product.setName("Кефир");
    Order order = new Order();
    order.setId(21L);

    StockMovement movement = new StockMovement();
    movement.setId(1L);
    movement.setProduct(product);
    movement.setOrder(order);
    movement.setMovementType(StockMovementType.OUTBOUND);
    movement.setQuantityChange(-2);
    movement.setReason("ORDER_CREATED");
    movement.setActorUsername("manager");
    movement.setActorUserId(5L);
    movement.setActorRole("MANAGER");
    movement.setCreatedAt(Instant.parse("2026-01-01T03:00:00Z"));
    assertThat(movement.getId()).isEqualTo(1L);
    assertThat(movement.getProduct()).isSameAs(product);
    assertThat(movement.getOrder()).isSameAs(order);
    assertThat(movement.getMovementType()).isEqualTo(StockMovementType.OUTBOUND);
    assertThat(movement.getQuantityChange()).isEqualTo(-2);
    assertThat(movement.getReason()).isEqualTo("ORDER_CREATED");
    assertThat(movement.getActorUsername()).isEqualTo("manager");
    assertThat(movement.getActorUserId()).isEqualTo(5L);
    assertThat(movement.getActorRole()).isEqualTo("MANAGER");
    assertThat(movement.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T03:00:00Z"));

    RealtimeNotification notification = new RealtimeNotification();
    notification.setId(2L);
    notification.setEventType("ORDER_CREATED");
    notification.setTitle("Новый заказ");
    notification.setMessage("Заказ создан");
    notification.setOrderId(100L);
    notification.setOrderStatus("CREATED");
    notification.setTargetRoles("MANAGER,DIRECTOR");
    notification.setTargetUserIds("1,2");
    notification.setCreatedAt(Instant.parse("2026-01-01T03:10:00Z"));
    assertThat(notification.getId()).isEqualTo(2L);
    assertThat(notification.getEventType()).isEqualTo("ORDER_CREATED");
    assertThat(notification.getTitle()).isEqualTo("Новый заказ");
    assertThat(notification.getMessage()).isEqualTo("Заказ создан");
    assertThat(notification.getOrderId()).isEqualTo(100L);
    assertThat(notification.getOrderStatus()).isEqualTo("CREATED");
    assertThat(notification.getTargetRoles()).isEqualTo("MANAGER,DIRECTOR");
    assertThat(notification.getTargetUserIds()).isEqualTo("1,2");
    assertThat(notification.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T03:10:00Z"));

    OrderTimelineEvent timeline = new OrderTimelineEvent();
    timeline.setId(3L);
    timeline.setOrder(order);
    timeline.setFromStatus("CREATED");
    timeline.setToStatus("APPROVED");
    timeline.setActorUsername("manager");
    timeline.setActorUserId(5L);
    timeline.setActorRole("MANAGER");
    timeline.setDetails("Статус обновлён");
    timeline.setCreatedAt(Instant.parse("2026-01-01T03:20:00Z"));
    assertThat(timeline.getId()).isEqualTo(3L);
    assertThat(timeline.getOrder()).isSameAs(order);
    assertThat(timeline.getFromStatus()).isEqualTo("CREATED");
    assertThat(timeline.getToStatus()).isEqualTo("APPROVED");
    assertThat(timeline.getActorUsername()).isEqualTo("manager");
    assertThat(timeline.getActorUserId()).isEqualTo(5L);
    assertThat(timeline.getActorRole()).isEqualTo("MANAGER");
    assertThat(timeline.getDetails()).isEqualTo("Статус обновлён");
    assertThat(timeline.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T03:20:00Z"));
  }

  @Test
  void enumsExposeAllExpectedValues() {
    assertThat(Role.values()).containsExactly(Role.DIRECTOR, Role.MANAGER, Role.LOGISTICIAN, Role.DRIVER);
    assertThat(OrderStatus.values()).containsExactly(
        OrderStatus.CREATED,
        OrderStatus.APPROVED,
        OrderStatus.ASSIGNED,
        OrderStatus.DELIVERED
    );
    assertThat(StockMovementType.values()).containsExactly(
        StockMovementType.INBOUND,
        StockMovementType.OUTBOUND,
        StockMovementType.ADJUSTMENT
    );
  }
}
