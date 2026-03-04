package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.AutoAssignApproveItemRequest;
import com.farm.sales.dto.AutoAssignApproveRequest;
import com.farm.sales.dto.AutoAssignDriverRouteResponse;
import com.farm.sales.dto.AutoAssignItemResponse;
import com.farm.sales.dto.AutoAssignPreviewResponse;
import com.farm.sales.dto.AutoAssignResultResponse;
import com.farm.sales.dto.AutoAssignRoutePointResponse;
import com.farm.sales.dto.OrderCreateRequest;
import com.farm.sales.dto.OrderItemRequest;
import com.farm.sales.dto.OrderItemResponse;
import com.farm.sales.dto.OrderPageResponse;
import com.farm.sales.dto.OrderResponse;
import com.farm.sales.dto.RealtimeNotificationResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderItem;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Product;
import com.farm.sales.model.Role;
import com.farm.sales.model.StockMovementType;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderItemRepository;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.UserRepository;
import io.micrometer.core.instrument.Metrics;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  private static final int MAX_ORDERS_IN_LIST = 200;
  private static final int DEFAULT_ORDERS_PAGE_SIZE = 50;
  private static final int MAX_ORDERS_PAGE_SIZE = 200;
  private static final int ROUTING_FETCH_PAGE_SIZE = 200;
  private static final int ROUTING_FETCH_MAX_PAGES = 50;
  private static final int TRANSPORT_DRIVER_LIMIT = 3;
  private static final int MAX_CLUSTER_REFINEMENT_ITERATIONS = 5;
  private static final double EARTH_RADIUS_KM = 6371.0088;
  private static final int DISTANCE_COST_SCALE = 1000;
  private static final String MOGILEV_SHARED_DEPOT_LABEL = "Могилёв, ул. Первомайская 31 (логистический хаб)";
  private static final Coordinate MOGILEV_SHARED_DEPOT = new Coordinate(53.8971270, 30.3320410);
  private static final List<Coordinate> MOGILEV_CLUSTER_RING = List.of(
      new Coordinate(53.9400000, 30.3400000), // north side
      new Coordinate(53.8700000, 30.4100000), // south-east side
      new Coordinate(53.8600000, 30.2600000)  // south-west side
  );
  private static final Map<String, Coordinate> MOGILEV_DRIVER_CLUSTER_SEEDS = Map.of(
      "driver1", MOGILEV_CLUSTER_RING.get(0),
      "driver2", MOGILEV_CLUSTER_RING.get(1),
      "driver3", MOGILEV_CLUSTER_RING.get(2)
  );
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final DirectorProfileService directorProfileService;
  private final AuditTrailPublisher auditTrailPublisher;
  private final StockMovementService stockMovementService;
  private final OrderTimelineService orderTimelineService;
  private final NotificationStreamService notificationStreamService;

  @Autowired
  public OrderService(OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository,
                      ProductRepository productRepository,
                      UserRepository userRepository,
                      DirectorProfileService directorProfileService,
                      AuditTrailPublisher auditTrailPublisher,
                      StockMovementService stockMovementService,
                      OrderTimelineService orderTimelineService,
                      NotificationStreamService notificationStreamService) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.productRepository = productRepository;
    this.userRepository = userRepository;
    this.directorProfileService = directorProfileService;
    this.auditTrailPublisher = auditTrailPublisher;
    this.stockMovementService = stockMovementService;
    this.orderTimelineService = orderTimelineService;
    this.notificationStreamService = notificationStreamService;
  }

  public OrderService(OrderRepository orderRepository,
                      ProductRepository productRepository,
                      UserRepository userRepository,
                      DirectorProfileService directorProfileService,
                      AuditTrailPublisher auditTrailPublisher,
                      StockMovementService stockMovementService,
                      OrderTimelineService orderTimelineService,
                      NotificationStreamService notificationStreamService) {
    this(
        orderRepository,
        null,
        productRepository,
        userRepository,
        directorProfileService,
        auditTrailPublisher,
        stockMovementService,
        orderTimelineService,
        notificationStreamService
    );
  }

  @Transactional
  public OrderResponse createOrder(Long directorId, OrderCreateRequest request) {
    User director = requireUserRole(directorId, Role.DIRECTOR, "Директор не найден");
    StoreAddress deliveryAddress = directorProfileService.getOwnedAddress(directorId, request.deliveryAddressId());

    List<Long> productIds = request.items().stream()
        .map(OrderItemRequest::productId)
        .distinct()
        .toList();
    Map<Long, Product> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
        .collect(Collectors.toMap(Product::getId, Function.identity()));
    if (productsById.size() != productIds.size()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
    }

    Order order = new Order();
    order.setCustomer(director);
    order.setDeliveryAddress(deliveryAddress);
    order.setDeliveryAddressText(deliveryAddress.getAddressLine());
    order.setDeliveryLatitude(deliveryAddress.getLatitude());
    order.setDeliveryLongitude(deliveryAddress.getLongitude());
    order.setStatus(OrderStatus.CREATED);
    Instant now = Instant.now();
    order.setCreatedAt(now);
    order.setUpdatedAt(now);

    List<OrderItem> items = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    List<StockReservation> reservations = new ArrayList<>();

    try {
      for (var itemRequest : request.items()) {
        Product product = productsById.get(itemRequest.productId());
        if (product == null) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Товар не найден");
        }

        if (product.getStockQuantity() < itemRequest.quantity()) {
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недостаточно остатка по товару: " + product.getName());
        }

        product.setStockQuantity(product.getStockQuantity() - itemRequest.quantity());
        reservations.add(new StockReservation(product, itemRequest.quantity()));

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(itemRequest.quantity());
        item.setPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.quantity())));

        items.add(item);
        total = total.add(item.getLineTotal());
      }

      order.setItems(items);
      order.setTotalAmount(total);

      Order saved = orderRepository.save(order);
      orderTimelineService.recordCreation(saved);
      for (OrderItem item : saved.getItems()) {
        stockMovementService.record(
            item.getProduct(),
            saved,
            StockMovementType.OUTBOUND,
            -item.getQuantity(),
            "ORDER_CREATED"
        );
      }

      auditTrailPublisher.publish(
          "ORDER_CREATED",
          "ORDER",
          String.valueOf(saved.getId()),
          "items=" + saved.getItems().size() + ",total=" + saved.getTotalAmount()
      );
      Metrics.counter("farm.sales.orders.created").increment();
      notificationStreamService.publishToRoles(
          Set.of("MANAGER"),
          new RealtimeNotificationResponse(
              "ORDER_CREATED",
              "Новая заявка на доставку",
              "Заказ №" + saved.getId() + " создан пользователем " + saved.getCustomer().getFullName(),
              saved.getId(),
              saved.getStatus().name(),
              Instant.now()
          )
      );

      return toResponse(saved);
    } catch (RuntimeException ex) {
      compensateStockReservations(reservations);
      throw ex;
    }
  }

  @Transactional
  public OrderResponse repeatOrder(Long directorId, Long sourceOrderId) {
    requireUserRole(directorId, Role.DIRECTOR, "Директор не найден");
    Order sourceOrder = orderRepository.findById(sourceOrderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    if (!sourceOrder.getCustomer().getId().equals(directorId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя повторить чужой заказ");
    }
    if (sourceOrder.getDeliveryAddress() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "В исходном заказе отсутствует адрес доставки");
    }

    List<OrderItemRequest> items = sourceOrder.getItems().stream()
        .map(item -> new OrderItemRequest(item.getProduct().getId(), item.getQuantity()))
        .toList();
    return createOrder(directorId, new OrderCreateRequest(sourceOrder.getDeliveryAddress().getId(), items));
  }

  @Transactional
  public List<OrderResponse> getOrdersForRole(Role role, Long userId) {
    return getOrdersPageForRole(role, userId, 0, MAX_ORDERS_IN_LIST).items();
  }

  @Transactional
  public OrderPageResponse getOrdersPageForRole(Role role, Long userId, Integer page, Integer size) {
    int normalizedPage = normalizePage(page);
    int normalizedSize = normalizeOrderPageSize(size);
    PageRequest pageRequest = PageRequest.of(normalizedPage, normalizedSize);

    Page<Order> ordersPage = switch (role) {
      case DIRECTOR -> orderRepository.findPageByCustomerIdOrderByCreatedAtDesc(userId, pageRequest);
      case DRIVER -> orderRepository.findPageByAssignedDriverIdOrderByCreatedAtDesc(userId, pageRequest);
      case MANAGER -> orderRepository.findPageAllByOrderByCreatedAtDesc(pageRequest);
      case LOGISTICIAN -> orderRepository.findPageByStatusOrderByCreatedAtDesc(OrderStatus.APPROVED, pageRequest);
    };
    if (ordersPage == null) {
      List<Order> fallbackOrders = switch (role) {
        case DIRECTOR -> orderRepository.findByCustomerIdOrderByCreatedAtDesc(userId, pageRequest);
        case DRIVER -> orderRepository.findByAssignedDriverIdOrderByCreatedAtDesc(userId, pageRequest);
        case MANAGER -> orderRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        case LOGISTICIAN -> orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.APPROVED, pageRequest);
      };
      List<Order> safeOrders = fallbackOrders == null ? List.of() : fallbackOrders;
      Map<Long, List<OrderItem>> fallbackItemsByOrderId = loadItemsByOrderId(safeOrders);
      List<OrderResponse> fallbackItems = safeOrders.stream()
          .map(order -> toResponse(order, fallbackItemsByOrderId.getOrDefault(order.getId(), List.of())))
          .toList();
      return new OrderPageResponse(
          fallbackItems,
          normalizedPage,
          normalizedSize,
          fallbackItems.size(),
          fallbackItems.isEmpty() ? 0 : 1,
          false
      );
    }

    Map<Long, List<OrderItem>> itemsByOrderId = loadItemsByOrderId(ordersPage.getContent());
    List<OrderResponse> items = ordersPage.getContent().stream()
        .map(order -> toResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
        .toList();

    return new OrderPageResponse(
        items,
        ordersPage.getNumber(),
        ordersPage.getSize(),
        ordersPage.getTotalElements(),
        ordersPage.getTotalPages(),
        ordersPage.hasNext()
    );
  }

  @Transactional
  public OrderResponse approveOrder(Long orderId, Long managerId) {
    User manager = requireUserRole(managerId, Role.MANAGER, "Менеджер не найден");
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    if (order.getStatus() != OrderStatus.CREATED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Можно одобрять только заказы в статусе CREATED");
    }

    OrderStatus previousStatus = order.getStatus();
    Instant now = Instant.now();
    order.setStatus(OrderStatus.APPROVED);
    order.setApprovedByManager(manager);
    order.setApprovedAt(now);
    order.setUpdatedAt(now);
    Order saved = orderRepository.save(order);
    orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());

    auditTrailPublisher.publish(
        "ORDER_APPROVED",
        "ORDER",
        String.valueOf(saved.getId()),
        "managerId=" + manager.getId()
    );
    Metrics.counter("farm.sales.orders.approved").increment();
    notificationStreamService.publishToRoles(
        Set.of("LOGISTICIAN"),
          new RealtimeNotificationResponse(
              "ORDER_APPROVED",
              "Заказ одобрен",
              "Заказ №" + saved.getId() + " одобрен менеджером",
              saved.getId(),
              saved.getStatus().name(),
              Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
          new RealtimeNotificationResponse(
              "ORDER_APPROVED",
              "Заказ одобрен",
              "Заказ №" + saved.getId() + " одобрен менеджером",
              saved.getId(),
              saved.getStatus().name(),
              Instant.now()
        )
    );

    return toResponse(saved);
  }

  @Transactional
  public OrderResponse assignDriver(Long orderId, Long logisticianId, Long driverId) {
    User logistician = requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    User driver = requireUserRole(driverId, Role.DRIVER, "Водитель не найден");
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    return toResponse(assignDriverInternal(order, logistician, driver));
  }

  @Transactional
  public AutoAssignResultResponse autoAssignApprovedOrders(Long logisticianId) {
    AutoAssignPreviewResponse preview = previewAutoAssignPlan(logisticianId);
    List<AutoAssignApproveItemRequest> assignments = preview.routes().stream()
        .flatMap(route -> route.points().stream()
            .map(point -> new AutoAssignApproveItemRequest(
                point.orderId(),
                route.driverId(),
                point.stopSequence()
            )))
        .toList();
    if (assignments.isEmpty()) {
      return new AutoAssignResultResponse(
          preview.totalApprovedOrders(),
          0,
          preview.totalApprovedOrders(),
          0.0,
          List.of()
      );
    }
    return approveAutoAssignPlan(logisticianId, new AutoAssignApproveRequest(assignments));
  }

  @Transactional
  public AutoAssignPreviewResponse previewAutoAssignPlan(Long logisticianId) {
    requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    List<Order> approvedOrders = loadApprovedOrdersForRouting(false);
    List<User> routingDrivers = resolveRoutingDrivers();
    if (approvedOrders.isEmpty()) {
      return new AutoAssignPreviewResponse(
          MOGILEV_SHARED_DEPOT_LABEL,
          MOGILEV_SHARED_DEPOT.latitude(),
          MOGILEV_SHARED_DEPOT.longitude(),
          0,
          0,
          0,
          0.0,
          routingDrivers.stream()
              .map(driver -> new AutoAssignDriverRouteResponse(driver.getId(), driver.getFullName(), 0, 0.0, List.of()))
              .toList()
      );
    }

    Map<Long, Integer> activeLoads = resolveActiveLoads(routingDrivers);
    List<DriverPlanNode> planDrivers = buildDriverPlanNodes(
        routingDrivers,
        activeLoads,
        approvedOrders.size(),
        MOGILEV_SHARED_DEPOT
    );
    TransportPlan plan = solveClusteredPlan(planDrivers, approvedOrders, MOGILEV_SHARED_DEPOT);

    List<AutoAssignDriverRouteResponse> routes = new ArrayList<>();
    Map<Integer, DriverRoutePlan> routeByDriverIndex = plan.routes().stream()
        .collect(Collectors.toMap(DriverRoutePlan::driverIndex, Function.identity()));
    for (int driverIndex = 0; driverIndex < planDrivers.size(); driverIndex++) {
      DriverPlanNode driverNode = planDrivers.get(driverIndex);
      DriverRoutePlan route = routeByDriverIndex.get(driverIndex);
      List<AutoAssignRoutePointResponse> points = route == null
          ? List.of()
          : route.stops().stream()
              .map(stop -> {
                Order order = approvedOrders.get(stop.orderIndex());
                Coordinate coordinate = coordinateOrFallback(order, MOGILEV_SHARED_DEPOT);
                String deliveryAddress = order.getDeliveryAddressText() == null || order.getDeliveryAddressText().isBlank()
                    ? "Заказ №" + order.getId()
                    : order.getDeliveryAddressText();
                return new AutoAssignRoutePointResponse(
                    order.getId(),
                    deliveryAddress,
                    coordinate.latitude(),
                    coordinate.longitude(),
                    stop.sequence(),
                    roundDistance(stop.distanceFromPreviousKm())
                );
              })
              .toList();
      routes.add(new AutoAssignDriverRouteResponse(
          driverNode.driver().getId(),
          driverNode.driver().getFullName(),
          points.size(),
          roundDistance(route == null ? 0.0 : route.distanceKm()),
          points
      ));
    }

    int plannedOrders = plan.assignments().size();
    int totalApproved = approvedOrders.size();
    return new AutoAssignPreviewResponse(
        MOGILEV_SHARED_DEPOT_LABEL,
        MOGILEV_SHARED_DEPOT.latitude(),
        MOGILEV_SHARED_DEPOT.longitude(),
        totalApproved,
        plannedOrders,
        Math.max(0, totalApproved - plannedOrders),
        roundDistance(plan.totalDistanceKm()),
        routes
    );
  }

  @Transactional
  public AutoAssignResultResponse approveAutoAssignPlan(Long logisticianId, AutoAssignApproveRequest request) {
    User logistician = requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    List<AutoAssignApproveItemRequest> requestedAssignments = request == null ? List.of() : request.assignments();

    List<Order> approvedOrders = loadApprovedOrdersForRouting(true);
    int totalApproved = approvedOrders.size();
    if (requestedAssignments == null || requestedAssignments.isEmpty() || totalApproved == 0) {
      return new AutoAssignResultResponse(totalApproved, 0, totalApproved, 0.0, List.of());
    }

    Map<Long, Order> approvedOrdersById = approvedOrders.stream()
        .collect(Collectors.toMap(Order::getId, Function.identity()));
    Map<Long, User> driversById = resolveRoutingDrivers().stream()
        .collect(Collectors.toMap(User::getId, Function.identity()));

    Set<Long> uniqueOrderIds = new HashSet<>();
    for (AutoAssignApproveItemRequest item : requestedAssignments) {
      if (!uniqueOrderIds.add(item.orderId())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "План содержит дублирующийся заказ #" + item.orderId());
      }
      if (!approvedOrdersById.containsKey(item.orderId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Заказ #" + item.orderId() + " больше недоступен для назначения, обновите план"
        );
      }
      if (!driversById.containsKey(item.driverId())) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "В плане указан водитель вне транспортной группы из 3 водителей"
        );
      }
    }

    Map<Long, List<AutoAssignApproveItemRequest>> assignmentsByDriver = new LinkedHashMap<>();
    for (AutoAssignApproveItemRequest item : requestedAssignments) {
      assignmentsByDriver.computeIfAbsent(item.driverId(), ignored -> new ArrayList<>()).add(item);
    }
    for (List<AutoAssignApproveItemRequest> items : assignmentsByDriver.values()) {
      items.sort(Comparator
          .comparing((AutoAssignApproveItemRequest item) -> item.stopSequence() == null ? Integer.MAX_VALUE : item.stopSequence())
          .thenComparing(AutoAssignApproveItemRequest::orderId));
    }

    List<AutoAssignItemResponse> resultAssignments = new ArrayList<>();
    double totalDistanceKm = 0.0;
    for (Map.Entry<Long, List<AutoAssignApproveItemRequest>> entry : assignmentsByDriver.entrySet()) {
      User driver = driversById.get(entry.getKey());
      Coordinate previousPoint = MOGILEV_SHARED_DEPOT;
      for (AutoAssignApproveItemRequest item : entry.getValue()) {
        Order order = approvedOrdersById.get(item.orderId());
        Coordinate targetPoint = coordinateOrFallback(order, MOGILEV_SHARED_DEPOT);
        double legDistance = haversineKm(previousPoint, targetPoint);
        previousPoint = targetPoint;

        Order saved = assignDriverInternal(order, logistician, driver);
        resultAssignments.add(new AutoAssignItemResponse(
            saved.getId(),
            driver.getId(),
            driver.getFullName(),
            roundDistance(legDistance)
        ));
        totalDistanceKm += legDistance;
      }
    }

    int assignedCount = resultAssignments.size();
    return new AutoAssignResultResponse(
        totalApproved,
        assignedCount,
        Math.max(0, totalApproved - assignedCount),
        roundDistance(totalDistanceKm),
        resultAssignments
    );
  }

  @Transactional
  public OrderResponse markDelivered(Long orderId, Long driverId) {
    User driver = requireUserRole(driverId, Role.DRIVER, "Водитель не найден");
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    if (order.getAssignedDriver() == null || !order.getAssignedDriver().getId().equals(driver.getId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Водитель может обновлять только назначенные ему заказы");
    }
    if (order.getStatus() != OrderStatus.ASSIGNED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отметить доставку можно только для заказов в статусе ASSIGNED");
    }

    OrderStatus previousStatus = order.getStatus();
    Instant now = Instant.now();
    order.setStatus(OrderStatus.DELIVERED);
    order.setDeliveredAt(now);
    order.setUpdatedAt(now);
    Order saved = orderRepository.save(order);
    orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());

    auditTrailPublisher.publish(
        "ORDER_DELIVERED",
        "ORDER",
        String.valueOf(saved.getId()),
        "driverId=" + driver.getId()
    );
    Metrics.counter("farm.sales.orders.delivered").increment();
    notificationStreamService.publishToRoles(
        Set.of("MANAGER", "LOGISTICIAN"),
        new RealtimeNotificationResponse(
            "ORDER_DELIVERED",
            "Заказ доставлен",
            "Заказ №" + saved.getId() + " отмечен как доставленный",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
        new RealtimeNotificationResponse(
            "ORDER_DELIVERED",
            "Заказ доставлен",
            "Заказ №" + saved.getId() + " отмечен как доставленный",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );

    return toResponse(saved);
  }

  private List<Order> loadApprovedOrdersForRouting(boolean forUpdate) {
    List<Order> approvedOrders = new ArrayList<>();
    boolean reachedPageLimit = true;

    for (int page = 0; page < ROUTING_FETCH_MAX_PAGES; page++) {
      PageRequest pageRequest = PageRequest.of(page, ROUTING_FETCH_PAGE_SIZE);
      List<Order> chunk = forUpdate
          ? orderRepository.findByStatusOrderByCreatedAtDescForUpdate(OrderStatus.APPROVED, pageRequest)
          : orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.APPROVED, pageRequest);
      if (chunk.isEmpty()) {
        reachedPageLimit = false;
        break;
      }
      approvedOrders.addAll(chunk);
      if (chunk.size() < ROUTING_FETCH_PAGE_SIZE) {
        reachedPageLimit = false;
        break;
      }
    }

    if (reachedPageLimit) {
      log.warn(
          "Auto-assign approved orders list was truncated at {} records ({} pages x {}).",
          approvedOrders.size(),
          ROUTING_FETCH_MAX_PAGES,
          ROUTING_FETCH_PAGE_SIZE
      );
    }
    return approvedOrders;
  }

  private Order assignDriverInternal(Order order, User logistician, User driver) {
    if (order.getStatus() != OrderStatus.APPROVED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Назначать водителя можно только для заказов в статусе APPROVED");
    }

    OrderStatus previousStatus = order.getStatus();
    Instant now = Instant.now();
    order.setAssignedDriver(driver);
    order.setAssignedByLogistician(logistician);
    order.setAssignedAt(now);
    order.setStatus(OrderStatus.ASSIGNED);
    order.setUpdatedAt(now);
    Order saved = orderRepository.save(order);
    orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());

    auditTrailPublisher.publish(
        "ORDER_DRIVER_ASSIGNED",
        "ORDER",
        String.valueOf(saved.getId()),
        "driverId=" + driver.getId() + ",logisticianId=" + logistician.getId()
    );
    Metrics.counter("farm.sales.orders.assigned").increment();
    notificationStreamService.publishToRoles(
        Set.of("MANAGER"),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DRIVER"),
        Set.of(driver.getId()),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );

    return saved;
  }

  private List<User> resolveRoutingDrivers() {
    List<User> drivers = userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER);
    if (drivers.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Невозможно выполнить автоназначение: водители не найдены"
      );
    }
    if (drivers.size() <= TRANSPORT_DRIVER_LIMIT) {
      return drivers;
    }
    return drivers.subList(0, TRANSPORT_DRIVER_LIMIT);
  }

  private Map<Long, Integer> resolveActiveLoads(List<User> drivers) {
    if (drivers.isEmpty()) {
      return Map.of();
    }

    List<Long> driverIds = drivers.stream()
        .map(User::getId)
        .filter(id -> id != null)
        .toList();
    if (driverIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, Integer> aggregatedLoads = orderRepository.countByAssignedDriverIdsAndStatus(driverIds, OrderStatus.ASSIGNED).stream()
        .collect(Collectors.toMap(
            OrderRepository.DriverLoadAggregate::getDriverId,
            row -> Math.toIntExact(Math.min(row.getTotal(), Integer.MAX_VALUE))
        ));

    Map<Long, Integer> activeLoads = new HashMap<>();
    for (User driver : drivers) {
      activeLoads.put(driver.getId(), aggregatedLoads.getOrDefault(driver.getId(), 0));
    }
    return activeLoads;
  }

  private List<DriverPlanNode> buildDriverPlanNodes(List<User> drivers,
                                                     Map<Long, Integer> activeLoads,
                                                     int pendingOrdersCount,
                                                     Coordinate fallbackCoordinate) {
    if (drivers.isEmpty()) {
      return List.of();
    }

    List<User> sortedDrivers = new ArrayList<>(drivers);
    sortedDrivers.sort(Comparator
        .comparingInt((User driver) -> activeLoads.getOrDefault(driver.getId(), 0))
        .thenComparing(User::getId));

    int[] capacities = new int[sortedDrivers.size()];
    if (pendingOrdersCount > 0) {
      Arrays.fill(capacities, 1);
      int remainingCapacity = Math.max(0, pendingOrdersCount - sortedDrivers.size());
      for (int orderIndex = 0; orderIndex < remainingCapacity; orderIndex++) {
        int bestIndex = 0;
        for (int i = 1; i < sortedDrivers.size(); i++) {
          User bestDriver = sortedDrivers.get(bestIndex);
          User candidateDriver = sortedDrivers.get(i);
          int bestProjectedLoad = activeLoads.getOrDefault(bestDriver.getId(), 0) + capacities[bestIndex];
          int candidateProjectedLoad = activeLoads.getOrDefault(candidateDriver.getId(), 0) + capacities[i];
          if (candidateProjectedLoad < bestProjectedLoad
              || (candidateProjectedLoad == bestProjectedLoad && candidateDriver.getId() < bestDriver.getId())) {
            bestIndex = i;
          }
        }
        capacities[bestIndex]++;
      }
    }

    List<DriverPlanNode> planNodes = new ArrayList<>();
    for (int i = 0; i < sortedDrivers.size(); i++) {
      User driver = sortedDrivers.get(i);
      Coordinate coordinate = resolveDriverClusterSeed(driver, fallbackCoordinate);
      planNodes.add(new DriverPlanNode(driver, capacities[i], coordinate));
    }
    return planNodes;
  }

  private Coordinate resolveDriverClusterSeed(User driver, Coordinate fallbackCoordinate) {
    if (driver == null) {
      return fallbackCoordinate;
    }

    if (driver.getUsername() != null) {
      Coordinate mappedByUsername = MOGILEV_DRIVER_CLUSTER_SEEDS.get(driver.getUsername().toLowerCase(Locale.ROOT));
      if (mappedByUsername != null) {
        return mappedByUsername;
      }
    }

    if (driver.getId() != null) {
      int baseIndex = (int) Math.floorMod(driver.getId() - 1, MOGILEV_CLUSTER_RING.size());
      return MOGILEV_CLUSTER_RING.get(baseIndex);
    }
    return fallbackCoordinate;
  }

  private Coordinate extractCoordinate(Order order) {
    if (order.getDeliveryLatitude() == null || order.getDeliveryLongitude() == null) {
      return null;
    }
    return new Coordinate(order.getDeliveryLatitude().doubleValue(), order.getDeliveryLongitude().doubleValue());
  }

  private Coordinate coordinateOrFallback(Order order, Coordinate fallbackCoordinate) {
    Coordinate coordinate = extractCoordinate(order);
    if (coordinate == null) {
      return fallbackCoordinate;
    }
    return coordinate;
  }

  private TransportPlan solveClusteredPlan(List<DriverPlanNode> drivers,
                                           List<Order> orders,
                                           Coordinate fallbackCoordinate) {
    if (drivers.isEmpty() || orders.isEmpty()) {
      return new TransportPlan(List.of(), List.of(), 0.0);
    }

    List<DriverPlanNode> workingDrivers = new ArrayList<>(drivers);
    List<TransportAssignment> assignments = List.of();
    for (int iteration = 0; iteration < MAX_CLUSTER_REFINEMENT_ITERATIONS; iteration++) {
      List<TransportAssignment> nextAssignments = solveTransportationPlan(workingDrivers, orders, fallbackCoordinate);
      if (!assignments.isEmpty() && sameAssignments(assignments, nextAssignments)) {
        assignments = nextAssignments;
        break;
      }
      assignments = nextAssignments;
      workingDrivers = refineDriverCoordinates(workingDrivers, assignments, orders, fallbackCoordinate);
    }

    List<DriverRoutePlan> routes = buildDriverRoutes(assignments, drivers.size(), orders, fallbackCoordinate);
    double totalDistanceKm = routes.stream().mapToDouble(DriverRoutePlan::distanceKm).sum();
    return new TransportPlan(assignments, routes, totalDistanceKm);
  }

  private List<DriverPlanNode> refineDriverCoordinates(List<DriverPlanNode> drivers,
                                                       List<TransportAssignment> assignments,
                                                       List<Order> orders,
                                                       Coordinate fallbackCoordinate) {
    Map<Integer, List<TransportAssignment>> assignmentsByDriver = assignments.stream()
        .collect(Collectors.groupingBy(TransportAssignment::driverIndex));
    List<DriverPlanNode> refinedDrivers = new ArrayList<>(drivers.size());
    for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
      DriverPlanNode driver = drivers.get(driverIndex);
      List<TransportAssignment> driverAssignments = assignmentsByDriver.getOrDefault(driverIndex, List.of());
      if (driverAssignments.isEmpty()) {
        refinedDrivers.add(driver);
        continue;
      }
      double latitudeSum = 0.0;
      double longitudeSum = 0.0;
      for (TransportAssignment assignment : driverAssignments) {
        Coordinate coordinate = coordinateOrFallback(orders.get(assignment.orderIndex()), fallbackCoordinate);
        latitudeSum += coordinate.latitude();
        longitudeSum += coordinate.longitude();
      }
      double divisor = driverAssignments.size();
      Coordinate centroid = new Coordinate(latitudeSum / divisor, longitudeSum / divisor);
      refinedDrivers.add(new DriverPlanNode(driver.driver(), driver.capacity(), centroid));
    }
    return refinedDrivers;
  }

  private boolean sameAssignments(List<TransportAssignment> left, List<TransportAssignment> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      TransportAssignment leftItem = left.get(i);
      TransportAssignment rightItem = right.get(i);
      if (leftItem.orderIndex() != rightItem.orderIndex() || leftItem.driverIndex() != rightItem.driverIndex()) {
        return false;
      }
    }
    return true;
  }

  private List<DriverRoutePlan> buildDriverRoutes(List<TransportAssignment> assignments,
                                                  int driverCount,
                                                  List<Order> orders,
                                                  Coordinate fallbackCoordinate) {
    Map<Integer, List<Integer>> orderIndexesByDriver = new HashMap<>();
    for (TransportAssignment assignment : assignments) {
      orderIndexesByDriver.computeIfAbsent(assignment.driverIndex(), ignored -> new ArrayList<>())
          .add(assignment.orderIndex());
    }

    List<DriverRoutePlan> routes = new ArrayList<>(driverCount);
    for (int driverIndex = 0; driverIndex < driverCount; driverIndex++) {
      List<Integer> assignedOrderIndexes = orderIndexesByDriver.getOrDefault(driverIndex, List.of());
      routes.add(buildRouteForDriver(driverIndex, assignedOrderIndexes, orders, fallbackCoordinate));
    }
    return routes;
  }

  private DriverRoutePlan buildRouteForDriver(int driverIndex,
                                              List<Integer> assignedOrderIndexes,
                                              List<Order> orders,
                                              Coordinate fallbackCoordinate) {
    if (assignedOrderIndexes.isEmpty()) {
      return new DriverRoutePlan(driverIndex, List.of(), 0.0);
    }

    List<Integer> remaining = new ArrayList<>(assignedOrderIndexes);
    List<RouteStop> stops = new ArrayList<>(remaining.size());
    Coordinate previousPoint = MOGILEV_SHARED_DEPOT;
    int sequence = 1;
    double routeDistance = 0.0;

    while (!remaining.isEmpty()) {
      int bestPosition = 0;
      int bestOrderIndex = remaining.getFirst();
      Coordinate bestCoordinate = coordinateOrFallback(orders.get(bestOrderIndex), fallbackCoordinate);
      double bestDistance = haversineKm(previousPoint, bestCoordinate);

      for (int i = 1; i < remaining.size(); i++) {
        int candidateOrderIndex = remaining.get(i);
        Coordinate candidateCoordinate = coordinateOrFallback(orders.get(candidateOrderIndex), fallbackCoordinate);
        double candidateDistance = haversineKm(previousPoint, candidateCoordinate);
        if (candidateDistance < bestDistance
            || (candidateDistance == bestDistance && orders.get(candidateOrderIndex).getId() < orders.get(bestOrderIndex).getId())) {
          bestPosition = i;
          bestOrderIndex = candidateOrderIndex;
          bestCoordinate = candidateCoordinate;
          bestDistance = candidateDistance;
        }
      }

      remaining.remove(bestPosition);
      stops.add(new RouteStop(bestOrderIndex, sequence, bestDistance));
      routeDistance += bestDistance;
      previousPoint = bestCoordinate;
      sequence++;
    }

    return new DriverRoutePlan(driverIndex, stops, routeDistance);
  }

  private List<TransportAssignment> solveTransportationPlan(List<DriverPlanNode> drivers,
                                                            List<Order> orders,
                                                            Coordinate fallbackCoordinate) {
    int driverCount = drivers.size();
    int orderCount = orders.size();
    int sourceNode = 0;
    int firstDriverNode = 1;
    int firstOrderNode = firstDriverNode + driverCount;
    int sinkNode = firstOrderNode + orderCount;

    @SuppressWarnings("unchecked")
    List<FlowEdge>[] graph = new List[sinkNode + 1];
    Arrays.setAll(graph, ignored -> new ArrayList<>());

    for (int driverIndex = 0; driverIndex < driverCount; driverIndex++) {
      DriverPlanNode driverNode = drivers.get(driverIndex);
      int driverGraphNode = firstDriverNode + driverIndex;
      addEdge(graph, sourceNode, driverGraphNode, driverNode.capacity(), 0L);

      for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
        int orderGraphNode = firstOrderNode + orderIndex;
        Coordinate orderCoordinate = coordinateOrFallback(orders.get(orderIndex), fallbackCoordinate);
        double distanceKm = haversineKm(driverNode.coordinate(), orderCoordinate);
        long cost = Math.max(0L, Math.round(distanceKm * DISTANCE_COST_SCALE));
        addEdge(graph, driverGraphNode, orderGraphNode, 1, cost);
      }
    }

    for (int orderIndex = 0; orderIndex < orderCount; orderIndex++) {
      addEdge(graph, firstOrderNode + orderIndex, sinkNode, 1, 0L);
    }

    MinCostFlowResult flowResult = runMinCostMaxFlow(graph, sourceNode, sinkNode, orderCount);
    if (flowResult.flow() < orderCount) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Не удалось автоматически распределить заказы: недостаточно доступных водителей"
      );
    }

    List<TransportAssignment> assignments = new ArrayList<>();
    for (int driverIndex = 0; driverIndex < driverCount; driverIndex++) {
      int driverGraphNode = firstDriverNode + driverIndex;
      for (FlowEdge edge : graph[driverGraphNode]) {
        if (edge.flow <= 0 || edge.to < firstOrderNode || edge.to >= firstOrderNode + orderCount) {
          continue;
        }
        int orderIndex = edge.to - firstOrderNode;
        assignments.add(new TransportAssignment(orderIndex, driverIndex));
      }
    }

    assignments.sort(Comparator.comparingInt(TransportAssignment::orderIndex));
    return assignments;
  }

  private MinCostFlowResult runMinCostMaxFlow(List<FlowEdge>[] graph,
                                               int sourceNode,
                                               int sinkNode,
                                               int requiredFlow) {
    int nodeCount = graph.length;
    int totalFlow = 0;
    long totalCost = 0L;

    long[] potential = new long[nodeCount];
    long[] distance = new long[nodeCount];
    int[] previousNode = new int[nodeCount];
    int[] previousEdge = new int[nodeCount];
    final long infinity = Long.MAX_VALUE / 4;

    while (totalFlow < requiredFlow) {
      Arrays.fill(distance, infinity);
      Arrays.fill(previousNode, -1);
      Arrays.fill(previousEdge, -1);
      distance[sourceNode] = 0L;

      PriorityQueue<QueueState> queue = new PriorityQueue<>(Comparator.comparingLong(QueueState::distance));
      queue.add(new QueueState(sourceNode, 0L));

      while (!queue.isEmpty()) {
        QueueState state = queue.poll();
        if (state.distance() != distance[state.node()]) {
          continue;
        }

        List<FlowEdge> edges = graph[state.node()];
        for (int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
          FlowEdge edge = edges.get(edgeIndex);
          if (edge.remainingCapacity() <= 0) {
            continue;
          }
          long reducedCost = edge.cost + potential[state.node()] - potential[edge.to];
          long candidateDistance = state.distance() + reducedCost;
          if (candidateDistance < distance[edge.to]) {
            distance[edge.to] = candidateDistance;
            previousNode[edge.to] = state.node();
            previousEdge[edge.to] = edgeIndex;
            queue.add(new QueueState(edge.to, candidateDistance));
          }
        }
      }

      if (previousNode[sinkNode] == -1) {
        break;
      }

      for (int node = 0; node < nodeCount; node++) {
        if (distance[node] < infinity) {
          potential[node] += distance[node];
        }
      }

      int augment = requiredFlow - totalFlow;
      for (int node = sinkNode; node != sourceNode; node = previousNode[node]) {
        FlowEdge edge = graph[previousNode[node]].get(previousEdge[node]);
        augment = Math.min(augment, edge.remainingCapacity());
      }

      for (int node = sinkNode; node != sourceNode; node = previousNode[node]) {
        FlowEdge edge = graph[previousNode[node]].get(previousEdge[node]);
        edge.flow += augment;
        FlowEdge reverse = graph[node].get(edge.reverseEdgeIndex);
        reverse.flow -= augment;
        totalCost += (long) augment * edge.cost;
      }

      totalFlow += augment;
    }

    return new MinCostFlowResult(totalFlow, totalCost);
  }

  private void addEdge(List<FlowEdge>[] graph, int fromNode, int toNode, int capacity, long cost) {
    FlowEdge forward = new FlowEdge(toNode, graph[toNode].size(), capacity, cost);
    FlowEdge reverse = new FlowEdge(fromNode, graph[fromNode].size(), 0, -cost);
    graph[fromNode].add(forward);
    graph[toNode].add(reverse);
  }

  private double haversineKm(Coordinate from, Coordinate to) {
    double deltaLatitude = Math.toRadians(to.latitude() - from.latitude());
    double deltaLongitude = Math.toRadians(to.longitude() - from.longitude());
    double fromLatitude = Math.toRadians(from.latitude());
    double toLatitude = Math.toRadians(to.latitude());

    double a = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
        + Math.cos(fromLatitude) * Math.cos(toLatitude)
        * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
    double normalizedA = Math.max(0.0, Math.min(1.0, a));
    double c = 2 * Math.atan2(Math.sqrt(normalizedA), Math.sqrt(1 - normalizedA));
    return EARTH_RADIUS_KM * c;
  }

  private double roundDistance(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private User requireUserRole(Long userId, Role role, String missingMessage) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, missingMessage));
    if (user.getRole() != role) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Роль пользователя не подходит для этой операции");
    }
    return user;
  }

  private Map<Long, List<OrderItem>> loadItemsByOrderId(List<Order> orders) {
    if (orders == null || orders.isEmpty()) {
      return Map.of();
    }
    if (orderItemRepository == null) {
      return orders.stream().collect(Collectors.toMap(
          Order::getId,
          order -> order.getItems() == null ? List.of() : order.getItems()
      ));
    }
    List<Long> orderIds = orders.stream()
        .map(Order::getId)
        .filter(id -> id != null)
        .toList();
    if (orderIds.isEmpty()) {
      return Map.of();
    }

    Map<Long, List<OrderItem>> byOrderId = new HashMap<>();
    for (OrderItem item : orderItemRepository.findByOrderIdInWithProduct(orderIds)) {
      Long orderId = item.getOrder() == null ? null : item.getOrder().getId();
      if (orderId == null) {
        continue;
      }
      byOrderId.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(item);
    }
    return byOrderId;
  }

  private List<OrderItem> loadItemsByOrderId(Long orderId) {
    if (orderId == null) {
      return List.of();
    }
    if (orderItemRepository == null) {
      return List.of();
    }
    return orderItemRepository.findByOrderIdWithProduct(orderId);
  }

  private OrderResponse toResponse(Order order) {
    List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
    return toResponse(order, orderItems);
  }

  private OrderResponse toResponse(Order order, List<OrderItem> orderItems) {
    List<OrderItemResponse> items = orderItems.stream()
        .map(item -> new OrderItemResponse(
            item.getProduct().getId(),
            item.getProduct().getName(),
            item.getQuantity(),
            item.getPrice(),
            item.getLineTotal()
        ))
        .collect(Collectors.toList());

    return new OrderResponse(
        order.getId(),
        order.getCustomer().getId(),
        order.getCustomer().getFullName(),
        order.getDeliveryAddress() == null ? null : order.getDeliveryAddress().getId(),
        order.getDeliveryAddressText(),
        order.getDeliveryLatitude(),
        order.getDeliveryLongitude(),
        order.getAssignedDriver() == null ? null : order.getAssignedDriver().getId(),
        order.getAssignedDriver() == null ? null : order.getAssignedDriver().getFullName(),
        order.getStatus().name(),
        order.getCreatedAt(),
        order.getUpdatedAt(),
        order.getApprovedAt(),
        order.getAssignedAt(),
        order.getDeliveredAt(),
        order.getTotalAmount(),
        items
    );
  }

  private void compensateStockReservations(List<StockReservation> reservations) {
    for (StockReservation reservation : reservations) {
      Product product = reservation.product();
      product.setStockQuantity(product.getStockQuantity() + reservation.quantity());
      productRepository.save(product);
    }
  }

  private record StockReservation(Product product, int quantity) {
  }

  private record Coordinate(double latitude, double longitude) {
  }

  private record DriverPlanNode(User driver, int capacity, Coordinate coordinate) {
  }

  private record TransportAssignment(int orderIndex, int driverIndex) {
  }

  private record RouteStop(int orderIndex, int sequence, double distanceFromPreviousKm) {
  }

  private record DriverRoutePlan(int driverIndex, List<RouteStop> stops, double distanceKm) {
  }

  private record TransportPlan(List<TransportAssignment> assignments,
                               List<DriverRoutePlan> routes,
                               double totalDistanceKm) {
  }

  private record MinCostFlowResult(int flow, long cost) {
  }

  private record QueueState(int node, long distance) {
  }

  private int normalizePage(Integer rawPage) {
    if (rawPage == null || rawPage < 0) {
      return 0;
    }
    return rawPage;
  }

  private int normalizeOrderPageSize(Integer rawSize) {
    if (rawSize == null || rawSize <= 0) {
      return DEFAULT_ORDERS_PAGE_SIZE;
    }
    return Math.min(rawSize, MAX_ORDERS_PAGE_SIZE);
  }

  private static final class FlowEdge {
    private final int to;
    private final int reverseEdgeIndex;
    private final int capacity;
    private final long cost;
    private int flow;

    private FlowEdge(int to, int reverseEdgeIndex, int capacity, long cost) {
      this.to = to;
      this.reverseEdgeIndex = reverseEdgeIndex;
      this.capacity = capacity;
      this.cost = cost;
      this.flow = 0;
    }

    private int remainingCapacity() {
      return capacity - flow;
    }
  }
}
