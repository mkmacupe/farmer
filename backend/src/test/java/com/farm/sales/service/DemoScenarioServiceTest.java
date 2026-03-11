package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditLogRepository;
import com.farm.sales.config.DataInitializer;
import com.farm.sales.config.DemoTransportScenarioInitializer;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderItemRepository;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.OrderTimelineEventRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.RealtimeNotificationRepository;
import com.farm.sales.repository.StockMovementRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DemoScenarioServiceTest {
  private OrderItemRepository orderItemRepository;
  private OrderTimelineEventRepository orderTimelineEventRepository;
  private StockMovementRepository stockMovementRepository;
  private RealtimeNotificationRepository realtimeNotificationRepository;
  private OrderRepository orderRepository;
  private AuditLogRepository auditLogRepository;
  private StoreAddressRepository storeAddressRepository;
  private UserRepository userRepository;
  private ProductRepository productRepository;
  private DataInitializer dataInitializer;
  private DemoTransportScenarioInitializer demoTransportScenarioInitializer;
  private EntityManager entityManager;
  private DemoScenarioService service;

  @BeforeEach
  void setUp() {
    orderItemRepository = mock(OrderItemRepository.class);
    orderTimelineEventRepository = mock(OrderTimelineEventRepository.class);
    stockMovementRepository = mock(StockMovementRepository.class);
    realtimeNotificationRepository = mock(RealtimeNotificationRepository.class);
    orderRepository = mock(OrderRepository.class);
    auditLogRepository = mock(AuditLogRepository.class);
    storeAddressRepository = mock(StoreAddressRepository.class);
    userRepository = mock(UserRepository.class);
    productRepository = mock(ProductRepository.class);
    dataInitializer = mock(DataInitializer.class);
    demoTransportScenarioInitializer = mock(DemoTransportScenarioInitializer.class);
    entityManager = mock(EntityManager.class);
    service = new DemoScenarioService(
        orderItemRepository,
        orderTimelineEventRepository,
        stockMovementRepository,
        realtimeNotificationRepository,
        orderRepository,
        auditLogRepository,
        storeAddressRepository,
        userRepository,
        productRepository,
        dataInitializer,
        demoTransportScenarioInitializer,
        entityManager
    );
  }

  @Test
  void resetDemoScenarioClearsMutableDataAndReseedsCanonicalScenario() {
    User director = new User();
    director.setId(1L);
    director.setRole(Role.DIRECTOR);
    director.setFullName("Олег Курилин");
    when(userRepository.count()).thenReturn(8L);
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DIRECTOR)).thenReturn(List.of(director, director, director));
    when(productRepository.count()).thenReturn(20L);
    when(storeAddressRepository.count()).thenReturn(30L);
    when(orderRepository.count()).thenReturn(60L);
    when(orderRepository.countByStatus(OrderStatus.APPROVED)).thenReturn(60L);

    var response = service.resetDemoScenario();

    InOrder inOrder = inOrder(
        orderItemRepository,
        orderTimelineEventRepository,
        stockMovementRepository,
        realtimeNotificationRepository,
        orderRepository,
        auditLogRepository,
        storeAddressRepository,
        userRepository,
        productRepository,
        entityManager,
        dataInitializer,
        demoTransportScenarioInitializer
    );
    inOrder.verify(orderItemRepository).deleteAllInBatch();
    inOrder.verify(orderTimelineEventRepository).deleteAllInBatch();
    inOrder.verify(stockMovementRepository).deleteAllInBatch();
    inOrder.verify(realtimeNotificationRepository).deleteAllInBatch();
    inOrder.verify(orderRepository).deleteAllInBatch();
    inOrder.verify(auditLogRepository).deleteAllInBatch();
    inOrder.verify(storeAddressRepository).deleteAllInBatch();
    inOrder.verify(userRepository).deleteAllInBatch();
    inOrder.verify(productRepository).deleteAllInBatch();
    inOrder.verify(entityManager).flush();
    inOrder.verify(entityManager).clear();
    inOrder.verify(dataInitializer).resetDemoSeedState();
    inOrder.verify(demoTransportScenarioInitializer).resetSeedState();
    inOrder.verify(dataInitializer).seedDemoDataWithoutAddresses();
    inOrder.verify(demoTransportScenarioInitializer).seedDemoScenario();

    assertThat(response.scenarioName()).contains("Демо-сценарий");
    assertThat(response.totalUsers()).isEqualTo(8L);
    assertThat(response.directors()).isEqualTo(3L);
    assertThat(response.products()).isEqualTo(20L);
    assertThat(response.storeAddresses()).isEqualTo(30L);
    assertThat(response.totalOrders()).isEqualTo(60L);
    assertThat(response.approvedOrders()).isEqualTo(60L);
    assertThat(response.defenseFlow()).hasSize(4);
    verify(orderRepository).countByStatus(OrderStatus.APPROVED);
  }
}
