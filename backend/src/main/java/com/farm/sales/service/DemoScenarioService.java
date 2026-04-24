package com.farm.sales.service;

import com.farm.sales.audit.AuditLogRepository;
import com.farm.sales.config.DataInitializer;
import com.farm.sales.config.DemoTransportScenarioInitializer;
import com.farm.sales.dto.DemoClearOrdersResponse;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoScenarioService {
  private static final List<String> DEFENSE_FLOW = List.of(
      "Менеджер: выполнить scenario reset и показать, что система загрузила 30 торговых точек и 30 одобренных заказов.",
      "Логист: открыть список APPROVED заказов, запустить preview автоназначения и показать, что все 3 водителя укладываются в один рейс.",
      "Логист: добавить ещё один заказ весом больше 2 кг и показать, что система отправляет одного из водителей на второй рейс.",
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
        "Транспортный учебный сценарий Farm Sales: 30 точек / 30 заказов / 4498 кг",
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

  @Transactional
  @CacheEvict(value = "users-by-role", allEntries = true)
  public DemoClearOrdersResponse clearOrdersKeepingStorePoints() {
    realtimeNotificationRepository.deleteAllInBatch();
    orderTimelineEventRepository.deleteAllInBatch();
    stockMovementRepository.deleteAllInBatch();
    auditLogRepository.deleteAllInBatch();
    orderItemRepository.deleteAllInBatch();
    orderRepository.deleteAllInBatch();
    entityManager.flush();
    entityManager.clear();
    demoTransportScenarioInitializer.resetSeedState();

    return new DemoClearOrdersResponse(
        "Заказы очищены, точки магазинов сохранены",
        Instant.now(),
        storeAddressRepository.count(),
        orderRepository.count(),
        orderRepository.countByStatus(OrderStatus.APPROVED)
    );
  }
}
