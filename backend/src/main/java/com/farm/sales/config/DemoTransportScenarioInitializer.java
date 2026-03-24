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
  private static final List<String> DIRECTOR_USERNAMES = DataInitializer.demoDirectorUsernames();
  private static final int TARGET_ORDER_COUNT = 30;
  private static final int TARGET_TOTAL_WEIGHT_KG = 4498;
  private static final int TARGET_CLUSTER_LOAD_KG = 1500;
  private static final double PREFERRED_SCENARIO_PRODUCT_WEIGHT_KG = 1.0;
  private static final List<DemoPoint> MOGILEV_POINTS = List.of(
      new DemoPoint("Сценарий 01", "Могилёв, ул. Челюскинцев 105", "53.8654000", "30.2905000"),
      new DemoPoint("Сценарий 02", "Могилёв, ул. Челюскинцев 123", "53.8676400", "30.2943600"),
      new DemoPoint("Сценарий 03", "Могилёв, ул. Прямая 10", "53.8733754", "30.2784216"),
      new DemoPoint("Сценарий 04", "Могилёв, ул. Терехина 16", "53.8761200", "30.3504400"),
      new DemoPoint("Сценарий 05", "Могилёв, ул. Гришина 92", "53.8789400", "30.3601400"),
      new DemoPoint("Сценарий 06", "Могилёв, ул. Златоустовского 24", "53.8822252", "30.3632077"),
      new DemoPoint("Сценарий 07", "Могилёв, пер. Вагнера 30", "53.8842917", "30.3492661"),
      new DemoPoint("Сценарий 08", "Могилёв, ул. Габровская 63", "53.8859200", "30.2968100"),
      new DemoPoint("Сценарий 09", "Могилёв, ул. Чигринова 11", "53.8836878", "30.3385820"),
      new DemoPoint("Сценарий 10", "Могилёв, ул. Романова 1", "53.8941372", "30.3087819"),
      new DemoPoint("Сценарий 11", "Могилёв, ул. Комсомольская 10", "53.8972780", "30.3351973"),
      new DemoPoint("Сценарий 12", "Могилёв, ул. Островского 21", "53.8993200", "30.3601100"),
      new DemoPoint("Сценарий 13", "Могилёв, ул. Космонавтов 41", "53.8997146", "30.2948063"),
      new DemoPoint("Сценарий 14", "Могилёв, ул. Ленинская 44", "53.9005400", "30.3364800"),
      new DemoPoint("Сценарий 15", "Могилёв, ул. Первомайская 18", "53.9019502", "30.3341576"),
      new DemoPoint("Сценарий 16", "Могилёв, ул. Бялыницкого-Бирули 44", "53.9069800", "30.3099400"),
      new DemoPoint("Сценарий 17", "Могилёв, ул. Гагарина 33", "53.9074600", "30.3206500"),
      new DemoPoint("Сценарий 18", "Могилёв, ул. Миколуцкого 30", "53.9080094", "30.3562839"),
      new DemoPoint("Сценарий 19", "Могилёв, ул. Алексея Пысина 21", "53.9134022", "30.2851870"),
      new DemoPoint("Сценарий 20", "Могилёв, ул. Якубовского 18", "53.9146200", "30.3421800"),
      new DemoPoint("Сценарий 21", "Могилёв, ул. Сурганова 21А", "53.9196949", "30.3183777"),
      new DemoPoint("Сценарий 22", "Могилёв, ул. Сурганова 19", "53.9201640", "30.3127560"),
      new DemoPoint("Сценарий 23", "Могилёв, ул. Якубовского 67", "53.9203246", "30.3035017"),
      new DemoPoint("Сценарий 24", "Могилёв, ул. Строителей 14", "53.9283600", "30.3331200"),
      new DemoPoint("Сценарий 25", "Могилёв, ул. Академика Павлова 3", "53.9342000", "30.2941000"),
      new DemoPoint("Сценарий 26", "Могилёв, ул. Фатина 27", "53.9342200", "30.3484600"),
      new DemoPoint("Сценарий 27", "Могилёв, пр-т Мира 42", "53.8948000", "30.3312000"),
      new DemoPoint("Сценарий 28", "Могилёв, Брамный пер. 2", "53.8954066", "30.3299772"),
      new DemoPoint("Сценарий 29", "Могилёв, ул. Павлова 12", "53.9316000", "30.3008000"),
      new DemoPoint("Сценарий 30", "Могилёв, ул. Мовчанского 53", "53.9119000", "30.3661000")
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
      validateScenarioProducts(products);

      if (orderRepository.count() > 0) {
        log.info("Demo transport scenario skipped: orders already exist, destructive reseed disabled.");
        scenarioSeeded = true;
        return;
      }

      storeAddressRepository.deleteAllInBatch();
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
      User customer = directors.get(i);
      Instant createdAt = now.minusSeconds((long) (MOGILEV_POINTS.size() - i) * 180L);

      StoreAddress address = createPointAddress(customer, point, createdAt.minusSeconds(600));
      Order order = createApprovedOrder(customer, manager, address, products, i, createdAt);
      orderRepository.save(order);
    }

    log.info(
        "Demo transport scenario seeded: {} Mogilev points and {} approved orders created with total weight {} kg.",
        MOGILEV_POINTS.size(),
        TARGET_ORDER_COUNT,
        TARGET_TOTAL_WEIGHT_KG
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
    order.setAssignedDriver(null);
    order.setAssignedByLogistician(null);
    order.setAssignedAt(null);
    order.setDeliveredAt(null);

    List<OrderItem> items = buildOrderItems(order, products, pointIndex);
    order.setItems(items);
    order.setTotalAmount(items.stream()
        .map(OrderItem::getLineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add));

    return order;
  }

  private List<OrderItem> buildOrderItems(Order order, List<Product> products, int pointIndex) {
    Product product = selectScenarioProduct(products, pointIndex);
    double productWeightKg = requireScenarioProductWeightKg(product);
    int targetWeightKg = targetOrderWeightKg(pointIndex);
    int quantity = Math.max(1, (int) Math.round(targetWeightKg / productWeightKg));
    BigDecimal price = product.getPrice();

    OrderItem item = new OrderItem();
    item.setOrder(order);
    item.setProduct(product);
    item.setQuantity(quantity);
    item.setPrice(price);
    item.setLineTotal(price.multiply(BigDecimal.valueOf(quantity)));

    return List.of(item);
  }

  private int targetOrderWeightKg(int pointIndex) {
    int clusterIndex = pointIndex / 10;
    int orderIndexWithinCluster = pointIndex % 10;
    if (clusterIndex < 2 && orderIndexWithinCluster == 0) {
      return TARGET_CLUSTER_LOAD_KG - 1 - (9 * 150);
    }
    return 150;
  }

  private double requireScenarioProductWeightKg(Product product) {
    return product.getWeightKg();
  }

  private void validateScenarioProducts(List<Product> products) {
    List<String> invalidProducts = products.stream()
        .filter(product -> !hasValidTransportMetrics(product))
        .map(product -> product == null || product.getName() == null || product.getName().isBlank()
            ? "Товар без названия"
            : product.getName())
        .distinct()
        .toList();
    if (!invalidProducts.isEmpty()) {
      throw new IllegalStateException(
          "Demo transport scenario requires explicit positive weightKg and volumeM3 for all products: "
              + String.join(", ", invalidProducts)
      );
    }
  }

  private Product selectScenarioProduct(List<Product> products, int pointIndex) {
    List<Product> validProducts = products.stream()
        .filter(this::hasValidTransportMetrics)
        .toList();
    if (validProducts.isEmpty()) {
      throw new IllegalStateException(
          "Demo transport scenario requires at least one product with explicit positive weightKg and volumeM3"
      );
    }
    List<Product> oneKilogramProducts = validProducts.stream()
        .filter(product -> Math.abs(product.getWeightKg() - PREFERRED_SCENARIO_PRODUCT_WEIGHT_KG) < 0.0001)
        .toList();
    if (!oneKilogramProducts.isEmpty()) {
      return oneKilogramProducts.get(pointIndex % oneKilogramProducts.size());
    }
    return validProducts.get(pointIndex % validProducts.size());
  }

  private boolean hasValidTransportMetrics(Product product) {
    return product != null
        && product.getWeightKg() != null
        && product.getWeightKg() > 0.0
        && product.getVolumeM3() != null
        && product.getVolumeM3() > 0.0;
  }

  private record DemoPoint(String label, String addressLine, String latitude, String longitude) {
  }
}
