package com.farm.sales.config;

import com.farm.sales.model.Order;
import com.farm.sales.model.OrderItem;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Product;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(200)
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoTransportScenarioInitializer implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(DemoTransportScenarioInitializer.class);
  private static final List<String> DIRECTOR_USERNAMES = List.of("berezka", "kvartal", "yantar");
  private static final int ORDERS_PER_POINT = 2;
  private static final List<DemoPoint> MOGILEV_POINTS = List.of(
      new DemoPoint("Точка 01", "Могилёв, ул. Первомайская 12", "53.9024300", "30.3358700"),
      new DemoPoint("Точка 02", "Могилёв, ул. Ленинская 31", "53.9011200", "30.3341000"),
      new DemoPoint("Точка 03", "Могилёв, ул. Миронова 26", "53.8949600", "30.3327800"),
      new DemoPoint("Точка 04", "Могилёв, ул. Болдина 11", "53.8894200", "30.3289400"),
      new DemoPoint("Точка 05", "Могилёв, ул. Крупской 74", "53.8786100", "30.3335500"),
      new DemoPoint("Точка 06", "Могилёв, ул. Гагарина 54", "53.9068100", "30.3187700"),
      new DemoPoint("Точка 07", "Могилёв, ул. Якубовского 42", "53.9152400", "30.3379400"),
      new DemoPoint("Точка 08", "Могилёв, Пушкинский пр-т 39", "53.9075200", "30.3521100"),
      new DemoPoint("Точка 09", "Могилёв, ул. Островского 5", "53.8987400", "30.3579200"),
      new DemoPoint("Точка 10", "Могилёв, ул. Симонова 15", "53.8849600", "30.3614800"),
      new DemoPoint("Точка 11", "Могилёв, ул. Терехина 8", "53.8745500", "30.3469200"),
      new DemoPoint("Точка 12", "Могилёв, ул. Космонавтов 22", "53.8689400", "30.3274300"),
      new DemoPoint("Точка 13", "Могилёв, ул. Челюскинцев 147", "53.8662200", "30.2899100"),
      new DemoPoint("Точка 14", "Могилёв, Витебский пр-т 2", "53.8721800", "30.2744300"),
      new DemoPoint("Точка 15", "Могилёв, ул. Габровская 47", "53.8842100", "30.2927800"),
      new DemoPoint("Точка 16", "Могилёв, ул. Мовчанского 36", "53.8927300", "30.3048800"),
      new DemoPoint("Точка 17", "Могилёв, ул. Бялыницкого-Бирули 28", "53.9058100", "30.3052100"),
      new DemoPoint("Точка 18", "Могилёв, Днепровский б-р 17", "53.9186200", "30.3159200"),
      new DemoPoint("Точка 19", "Могилёв, ул. Строителей 9", "53.9271500", "30.3294400"),
      new DemoPoint("Точка 20", "Могилёв, ул. Фатина 14", "53.9324800", "30.3458100"),
      new DemoPoint("Точка 21", "Могилёв, Минское шоссе 6", "53.9265400", "30.3667300"),
      new DemoPoint("Точка 22", "Могилёв, ул. Сурганова 19", "53.9147700", "30.3698800"),
      new DemoPoint("Точка 23", "Могилёв, ул. Кулешова 33", "53.9022100", "30.3734200"),
      new DemoPoint("Точка 24", "Могилёв, ул. Лазаренко 61", "53.8894300", "30.3720500"),
      new DemoPoint("Точка 25", "Могилёв, ул. Гришина 92", "53.8789400", "30.3601400"),
      new DemoPoint("Точка 26", "Могилёв, ул. Крупской 125", "53.8738400", "30.3524600"),
      new DemoPoint("Точка 27", "Могилёв, ул. Пионерская 5", "53.8883200", "30.3471500"),
      new DemoPoint("Точка 28", "Могилёв, ул. Ямницкая 71", "53.9122600", "30.2861700"),
      new DemoPoint("Точка 29", "Могилёв, ул. Фатина 38", "53.9368100", "30.3345200"),
      new DemoPoint("Точка 30", "Могилёв, ул. Белыницкого-Бирули 10", "53.9234400", "30.3016200")
  );

  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final OrderRepository orderRepository;
  private final Object seedLock = new Object();
  @Value("${app.demo.transport-seed-on-startup:false}")
  private boolean seedOnStartup;
  private volatile boolean scenarioSeeded;

  public DemoTransportScenarioInitializer(UserRepository userRepository,
                                          ProductRepository productRepository,
                                          StoreAddressRepository storeAddressRepository,
                                          OrderRepository orderRepository) {
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.orderRepository = orderRepository;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!seedOnStartup) {
      log.info("Startup demo transport seed skipped: app.demo.transport-seed-on-startup=false");
      return;
    }
    seedDemoScenario();
  }

  @Transactional
  public void seedDemoScenario() {
    if (scenarioSeeded) {
      return;
    }
    synchronized (seedLock) {
      if (scenarioSeeded) {
        return;
      }
      List<User> directors = resolveDemoDirectors();
      if (directors.size() != DIRECTOR_USERNAMES.size()) {
        log.warn("Пропуск demo-транспортного сида: не удалось найти всех demo-директоров.");
        return;
      }

      User manager = userRepository.findByUsername("manager").orElse(null);
      if (manager == null) {
        log.warn("Пропуск demo-транспортного сида: не найден пользователь manager.");
        return;
      }

      List<Product> products = productRepository.findAll().stream()
          .filter(product -> product.getPrice() != null)
          .sorted(Comparator.comparing(Product::getId, Comparator.nullsLast(Long::compareTo)))
          .toList();
      if (products.isEmpty()) {
        log.warn("Пропуск demo-транспортного сида: в каталоге нет товаров с ценой.");
        return;
      }

      if (orderRepository.count() > 0) {
        log.info("Demo transport scenario skipped: orders already exist, destructive reseed disabled.");
        scenarioSeeded = true;
        return;
      }
      seedOrdersForMogilev(directors, manager, products);
      scenarioSeeded = true;
    }
  }

  public void resetSeedState() {
    synchronized (seedLock) {
      scenarioSeeded = false;
    }
  }

  private List<User> resolveDemoDirectors() {
    List<User> directors = new ArrayList<>(DIRECTOR_USERNAMES.size());
    for (String username : DIRECTOR_USERNAMES) {
      User user = userRepository.findByUsername(username).orElse(null);
      if (user != null) {
        directors.add(user);
      }
    }
    return directors;
  }

  private void seedOrdersForMogilev(List<User> directors, User manager, List<Product> products) {
    Instant now = Instant.now();
    for (int i = 0; i < MOGILEV_POINTS.size(); i++) {
      DemoPoint point = MOGILEV_POINTS.get(i);
      User customer = directors.get(i % directors.size());
      Instant pointBaseCreatedAt = now.minusSeconds((long) (MOGILEV_POINTS.size() - i) * 240L);

      StoreAddress address = createPointAddress(customer, point, pointBaseCreatedAt.minusSeconds(900));
      for (int orderVariant = 0; orderVariant < ORDERS_PER_POINT; orderVariant++) {
        Instant createdAt = pointBaseCreatedAt.plusSeconds(orderVariant * 75L);
        int scenarioOrderIndex = i * ORDERS_PER_POINT + orderVariant;
        Order order = createApprovedOrder(customer, manager, address, products, scenarioOrderIndex, createdAt);
        orderRepository.save(order);
      }
    }

    log.info(
        "Demo transport scenario seeded: {} Mogilev points and {} approved orders created.",
        MOGILEV_POINTS.size(),
        MOGILEV_POINTS.size() * ORDERS_PER_POINT
    );
  }

  private StoreAddress createPointAddress(User customer, DemoPoint point, Instant timestamp) {
    StoreAddress address = new StoreAddress();
    address.setUser(customer);
    address.setLabel(point.label());
    address.setAddressLine(point.addressLine());
    address.setLatitude(new BigDecimal(point.latitude()));
    address.setLongitude(new BigDecimal(point.longitude()));
    address.setCreatedAt(timestamp);
    address.setUpdatedAt(timestamp);
    return storeAddressRepository.save(address);
  }

  private Order createApprovedOrder(User customer,
                                    User manager,
                                    StoreAddress address,
                                    List<Product> products,
                                    int pointIndex,
                                    Instant createdAt) {
    Order order = new Order();
    order.setCustomer(customer);
    order.setDeliveryAddress(address);
    order.setDeliveryAddressText(address.getAddressLine());
    order.setDeliveryLatitude(address.getLatitude());
    order.setDeliveryLongitude(address.getLongitude());
    order.setStatus(OrderStatus.APPROVED);
    order.setCreatedAt(createdAt);
    order.setUpdatedAt(createdAt.plusSeconds(120));
    order.setApprovedByManager(manager);
    order.setApprovedAt(createdAt.plusSeconds(120));

    List<OrderItem> items = buildOrderItems(order, products, pointIndex);
    order.setItems(items);
    order.setTotalAmount(items.stream()
        .map(OrderItem::getLineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add));

    return order;
  }

  private List<OrderItem> buildOrderItems(Order order, List<Product> products, int pointIndex) {
    int itemCount = 3 + (pointIndex % 3);
    List<OrderItem> items = new ArrayList<>(itemCount);

    for (int offset = 0; offset < itemCount; offset++) {
      Product product = products.get((pointIndex * 3 + offset) % products.size());
      int quantity = 2 + ((pointIndex + offset) % 4);
      BigDecimal price = product.getPrice();

      OrderItem item = new OrderItem();
      item.setOrder(order);
      item.setProduct(product);
      item.setQuantity(quantity);
      item.setPrice(price);
      item.setLineTotal(price.multiply(BigDecimal.valueOf(quantity)));
      items.add(item);
    }

    return items;
  }

  private record DemoPoint(String label, String addressLine, String latitude, String longitude) {
  }
}
