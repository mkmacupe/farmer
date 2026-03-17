package com.farm.sales.service;

import com.farm.sales.audit.AuditLogRepository;
import com.farm.sales.config.DataInitializer;
import com.farm.sales.config.DemoTransportScenarioInitializer;
import com.farm.sales.dto.DemoResetResponse;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Role;
import com.farm.sales.repository.OrderItemRepository;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.OrderTimelineEventRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.RealtimeNotificationRepository;
import com.farm.sales.repository.StockMovementRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoScenarioService {
  private static final List<String> DEFENSE_FLOW = List.of(
      "Менеджер: выполнить demo reset и показать, что система загрузила 25 торговых точек и пакет одобренных заявок для логистики.",
      "Логист: открыть список APPROVED заказов, запустить preview автоназначения и показать распределение по 3 водителям.",
      "Логист: подтвердить план и показать маршрутные линии, вес, объём и последовательность остановок.",
      "Водитель driver1: открыть назначенные заказы и отметить одну доставку завершённой."
  );

  private final OrderItemRepository orderItemRepository;
  private final OrderTimelineEventRepository orderTimelineEventRepository;
  private final StockMovementRepository stockMovementRepository;
  private final RealtimeNotificationRepository realtimeNotificationRepository;
  private final OrderRepository orderRepository;
  private final AuditLogRepository auditLogRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final DataInitializer dataInitializer;
  private final DemoTransportScenarioInitializer demoTransportScenarioInitializer;
  private final EntityManager entityManager;

  public DemoScenarioService(OrderItemRepository orderItemRepository,
                             OrderTimelineEventRepository orderTimelineEventRepository,
                             StockMovementRepository stockMovementRepository,
                             RealtimeNotificationRepository realtimeNotificationRepository,
                             OrderRepository orderRepository,
                             AuditLogRepository auditLogRepository,
                             StoreAddressRepository storeAddressRepository,
                             UserRepository userRepository,
                             ProductRepository productRepository,
                             DataInitializer dataInitializer,
                             DemoTransportScenarioInitializer demoTransportScenarioInitializer,
                             EntityManager entityManager) {
    this.orderItemRepository = orderItemRepository;
    this.orderTimelineEventRepository = orderTimelineEventRepository;
    this.stockMovementRepository = stockMovementRepository;
    this.realtimeNotificationRepository = realtimeNotificationRepository;
    this.orderRepository = orderRepository;
    this.auditLogRepository = auditLogRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.userRepository = userRepository;
    this.productRepository = productRepository;
    this.dataInitializer = dataInitializer;
    this.demoTransportScenarioInitializer = demoTransportScenarioInitializer;
    this.entityManager = entityManager;
  }

  @Transactional
  @CacheEvict(value = "users-by-role", allEntries = true)
  public DemoResetResponse resetDemoScenario() {
    realtimeNotificationRepository.deleteAllInBatch();
    orderTimelineEventRepository.deleteAllInBatch();
    stockMovementRepository.deleteAllInBatch();
    auditLogRepository.deleteAllInBatch();
    orderItemRepository.deleteAllInBatch();
    orderRepository.deleteAllInBatch();
    storeAddressRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
    productRepository.deleteAllInBatch();
    entityManager.flush();
    entityManager.clear();

    dataInitializer.resetDemoSeedState();
    demoTransportScenarioInitializer.resetSeedState();
    dataInitializer.seedDemoDataWithoutAddresses();
    demoTransportScenarioInitializer.seedDemoScenario();

    return new DemoResetResponse(
        "Транспортный demo-сценарий Farm Sales: 25 точек / 35 заказов",
        Instant.now(),
        userRepository.count(),
        userRepository.findAllByRoleOrderByFullNameAsc(Role.DIRECTOR).size(),
        productRepository.count(),
        storeAddressRepository.count(),
        orderRepository.count(),
        orderRepository.countByStatus(OrderStatus.APPROVED),
        DEFENSE_FLOW
    );
  }
}
