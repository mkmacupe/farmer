package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.AutoAssignApproveItemRequest;
import com.farm.sales.dto.AutoAssignApproveRequest;
import com.farm.sales.dto.AutoAssignDriverRouteResponse;
import com.farm.sales.dto.AutoAssignItemResponse;
import com.farm.sales.dto.AutoAssignPreviewResponse;
import com.farm.sales.dto.AutoAssignRouteGeometryRequest;
import com.farm.sales.dto.AutoAssignResultResponse;
import com.farm.sales.dto.AutoAssignRoutePathPointResponse;
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
import java.util.LinkedHashSet;
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
  private static final double EARTH_RADIUS_KM = 6371.0088;
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
  
  private static final double VEHICLE_MAX_WEIGHT_KG = 1500.0;
  private static final double VEHICLE_MAX_VOLUME_M3 = 12.0;
  private static final double DEFAULT_PRODUCT_WEIGHT_KG = 1.0;
  private static final double DEFAULT_PRODUCT_VOLUME_M3 = 0.001;
  private static final double CROSS_ZONE_PENALTY_KM = 100.0;

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ProductRepository productRepository;
  private final UserRepository userRepository;
  private final DirectorProfileService directorProfileService;
  private final AuditTrailPublisher auditTrailPublisher;
  private final StockMovementService stockMovementService;
  private final OrderTimelineService orderTimelineService;
  private final NotificationStreamService notificationStreamService;
  private final RoadRoutingService roadRoutingService;

  @Autowired
  public OrderService(OrderRepository orderRepository,
                      OrderItemRepository orderItemRepository,
                      ProductRepository productRepository,
                      UserRepository userRepository,
                      DirectorProfileService directorProfileService,
                      AuditTrailPublisher auditTrailPublisher,
                      StockMovementService stockMovementService,
                      OrderTimelineService orderTimelineService,
                      NotificationStreamService notificationStreamService,
                      RoadRoutingService roadRoutingService) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.productRepository = productRepository;
    this.userRepository = userRepository;
    this.directorProfileService = directorProfileService;
    this.auditTrailPublisher = auditTrailPublisher;
    this.stockMovementService = stockMovementService;
    this.orderTimelineService = orderTimelineService;
    this.notificationStreamService = notificationStreamService;
    this.roadRoutingService = roadRoutingService;
  }

  public OrderService(OrderRepository orderRepository,
                      ProductRepository productRepository,
                      UserRepository userRepository,
                      DirectorProfileService directorProfileService,
                      AuditTrailPublisher auditTrailPublisher,
                      StockMovementService stockMovementService,
                      OrderTimelineService orderTimelineService,
                      NotificationStreamService notificationStreamService,
                      RoadRoutingService roadRoutingService) {
    this(
        orderRepository,
        null,
        productRepository,
        userRepository,
        directorProfileService,
        auditTrailPublisher,
        stockMovementService,
        orderTimelineService,
        notificationStreamService,
        roadRoutingService
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
    return approveOrderInternal(order, manager);
  }

  @Transactional
  public List<OrderResponse> approveAllOrders(Long managerId) {
    User manager = requireUserRole(managerId, Role.MANAGER, "Менеджер не найден");
    List<Order> ordersToApprove = orderRepository.findByStatus(OrderStatus.CREATED);
    return ordersToApprove.stream()
        .map(order -> approveOrderInternal(order, manager))
        .toList();
  }

  private OrderResponse approveOrderInternal(Order order, User manager) {
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
                point.stopSequence(),
                point.distanceFromPreviousKm()
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
              .map(driver -> new AutoAssignDriverRouteResponse(driver.getId(), driver.getFullName(), 0, 0.0, 0.0, 0.0, List.of(), List.of()))
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

    List<PreviewRouteDraft> routeDrafts = new ArrayList<>();
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
      routeDrafts.add(new PreviewRouteDraft(driverIndex, driverNode, route, points));
    }
    List<AutoAssignDriverRouteResponse> routes = routeDrafts.parallelStream()
        .map(routeDraft -> Map.entry(routeDraft.driverIndex(), toPreviewRouteResponse(routeDraft)))
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .toList();

    int plannedOrders = routes.stream()
        .mapToInt(route -> route.points().size())
        .sum();
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

  public List<AutoAssignRoutePathPointResponse> previewAutoAssignRouteGeometry(
      Long logisticianId,
      AutoAssignRouteGeometryRequest request
  ) {
    requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    if (request == null || request.points() == null || request.points().isEmpty()) {
      return List.of();
    }
    return buildPreviewRoutePathFromCoordinates(request.points());
  }

  @Transactional
  public AutoAssignResultResponse approveAutoAssignPlan(Long logisticianId, AutoAssignApproveRequest request) {
    User logistician = requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    List<AutoAssignApproveItemRequest> requestedAssignments = request == null ? List.of() : request.assignments();
    long approvedCount = orderRepository.countByStatus(OrderStatus.APPROVED);
    int totalApproved = approvedCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) approvedCount;

    if (requestedAssignments == null || requestedAssignments.isEmpty()) {
      return new AutoAssignResultResponse(totalApproved, 0, totalApproved, 0.0, List.of());
    }

    List<Long> requestedOrderIds = requestedAssignments.stream()
        .map(AutoAssignApproveItemRequest::orderId)
        .distinct()
        .toList();
    List<Order> approvedOrders = orderRepository.findAllByIdInForUpdate(requestedOrderIds);

    Map<Long, Order> approvedOrdersById = approvedOrders.stream()
        .filter(order -> order.getStatus() == OrderStatus.APPROVED)
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
        double legDistance = item.estimatedDistanceKm() == null
            ? distanceKm(previousPoint, targetPoint)
            : Math.max(0.0, item.estimatedDistanceKm());
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
    if (order.getStatus() != OrderStatus.APPROVED && order.getStatus() != OrderStatus.ASSIGNED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Назначать водителя можно только для заказов в статусе APPROVED или ASSIGNED");
    }

    OrderStatus previousStatus = order.getStatus();
    User previousDriver = order.getAssignedDriver();
    
    Instant now = Instant.now();
    order.setAssignedDriver(driver);
    order.setAssignedByLogistician(logistician);
    order.setAssignedAt(now);
    order.setStatus(OrderStatus.ASSIGNED);
    order.setUpdatedAt(now);
    Order saved = orderRepository.save(order);
    
    if (previousStatus != saved.getStatus()) {
      orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());
    }

    String auditDetails = "driverId=" + driver.getId() + ",logisticianId=" + logistician.getId();
    if (previousDriver != null && !previousDriver.getId().equals(driver.getId())) {
       auditDetails += ",previousDriverId=" + previousDriver.getId();
    }

    auditTrailPublisher.publish(
        "ORDER_DRIVER_ASSIGNED",
        "ORDER",
        String.valueOf(saved.getId()),
        auditDetails
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
            row -> {
              long total = row.getTotal() == null ? 0L : row.getTotal().longValue();
              return Math.toIntExact(Math.min(total, Integer.MAX_VALUE));
            }
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
      Coordinate zoneSeed = resolveDriverClusterSeed(driver, fallbackCoordinate);
      
      // Логика: если у водителя есть активные заказы, начинаем от последнего из них.
      // Если нет - от склада (или кластерного сида).
      Coordinate startCoordinate = orderRepository.findFirstByAssignedDriverIdAndStatusOrderByAssignedAtDescIdDesc(
              driver.getId(),
              OrderStatus.ASSIGNED
          )
          .map(lastOrder -> coordinateOrFallback(lastOrder, fallbackCoordinate))
          .orElse(zoneSeed);
          
      planNodes.add(new DriverPlanNode(driver, capacities[i], startCoordinate, zoneSeed));
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

  private List<AutoAssignRoutePathPointResponse> buildPreviewRoutePath(List<AutoAssignRoutePointResponse> points) {
    if (points == null || points.isEmpty()) {
      return List.of();
    }

    return buildPreviewRoutePathFromCoordinates(
        points.stream()
            .sorted(Comparator.comparingInt(AutoAssignRoutePointResponse::stopSequence))
            .map(point -> new AutoAssignRoutePathPointResponse(point.latitude(), point.longitude()))
            .toList()
    );
  }

  private List<AutoAssignRoutePathPointResponse> buildPreviewRoutePathFromCoordinates(
      List<AutoAssignRoutePathPointResponse> points
  ) {
    if (points == null || points.isEmpty()) {
      return List.of();
    }

    List<RoadRoutingService.RouteCoordinate> waypoints = new ArrayList<>(points.size() + 1);
    waypoints.add(new RoadRoutingService.RouteCoordinate(MOGILEV_SHARED_DEPOT.latitude(), MOGILEV_SHARED_DEPOT.longitude()));
    points.forEach(point -> waypoints.add(new RoadRoutingService.RouteCoordinate(point.latitude(), point.longitude())));

    try {
      return roadRoutingService.drivingRouteGeometry(waypoints).stream()
          .map(point -> new AutoAssignRoutePathPointResponse(point.latitude(), point.longitude()))
          .toList();
    } catch (RuntimeException exception) {
      log.warn("Road route geometry unavailable for preview, keeping stop markers only: {}", exception.getMessage());
      return List.of();
    }
  }

  private AutoAssignDriverRouteResponse toPreviewRouteResponse(PreviewRouteDraft routeDraft) {
    DriverPlanNode driverNode = routeDraft.driverNode();
    DriverRoutePlan route = routeDraft.route();
    List<AutoAssignRoutePointResponse> points = routeDraft.points();
    List<AutoAssignRoutePathPointResponse> path = points.isEmpty()
        ? List.of()
        : buildPreviewRoutePath(points);
    return new AutoAssignDriverRouteResponse(
        driverNode.driver().getId(),
        driverNode.driver().getFullName(),
        points.size(),
        roundDistance(route == null ? 0.0 : route.distanceKm()),
        roundDistance(route == null ? 0.0 : route.totalWeightKg()),
        roundDistance(route == null ? 0.0 : route.totalVolumeM3()),
        points,
        path
    );
  }

  private TransportPlan solveClusteredPlan(List<DriverPlanNode> drivers,
                                           List<Order> orders,
                                           Coordinate fallbackCoordinate) {
    if (drivers.isEmpty() || orders.isEmpty()) {
      return new TransportPlan(List.of(), List.of(), 0.0);
    }

    // Группируем заказы по координатам
    Map<Coordinate, List<Integer>> ordersByLocation = new HashMap<>();
    for (int i = 0; i < orders.size(); i++) {
      Coordinate coord = coordinateOrFallback(orders.get(i), fallbackCoordinate);
      ordersByLocation.computeIfAbsent(coord, k -> new ArrayList<>()).add(i);
    }

    List<DeliveryPoint> deliveryPoints = new ArrayList<>();
    for (Map.Entry<Coordinate, List<Integer>> entry : ordersByLocation.entrySet()) {
      List<Integer> orderIdxs = entry.getValue();
      double weight = 0.0;
      double volume = 0.0;
      for (int idx : orderIdxs) {
        Order order = orders.get(idx);
        List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
        for (OrderItem item : orderItems) {
          weight += estimateItemWeightKg(item);
          volume += estimateItemVolumeM3(item);
        }
      }
      deliveryPoints.add(new DeliveryPoint(entry.getKey(), orderIdxs, weight, volume));
    }

    List<TransportAssignment> assignments = new ArrayList<>();
    Map<Coordinate, Map<Integer, Double>> distanceMatrixBySource = buildPlanningDistanceMatrix(drivers, deliveryPoints);
    Map<Integer, Integer> preferredDriverByPoint = resolvePreferredDriverByPoint(drivers, deliveryPoints, distanceMatrixBySource);
    List<Integer> remainingPointIndexes = new ArrayList<>();
    for (int pointIndex = 0; pointIndex < deliveryPoints.size(); pointIndex++) {
      remainingPointIndexes.add(pointIndex);
    }

    Coordinate[] currentCoordinates = new Coordinate[drivers.size()];
    int[] assignedStopsByDriver = new int[drivers.size()];
    double[] assignedWeightByDriver = new double[drivers.size()];
    double[] assignedVolumeByDriver = new double[drivers.size()];
    List<List<AssignedPoint>> orderedPointsByDriver = new ArrayList<>(drivers.size());
    for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
      currentCoordinates[driverIndex] = drivers.get(driverIndex).startCoordinate();
      orderedPointsByDriver.add(new ArrayList<>());
    }

    for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
      while (true) {
        AssignmentCandidate candidate = findBestPointForDriver(
            drivers,
            driverIndex,
            currentCoordinates[driverIndex],
            remainingPointIndexes,
            deliveryPoints,
            distanceMatrixBySource,
            preferredDriverByPoint,
            assignedStopsByDriver,
            assignedWeightByDriver,
            assignedVolumeByDriver,
            true
        );
        if (candidate == null) {
          break;
        }
        assignPointToDriver(
            candidate,
            deliveryPoints,
            assignments,
            orderedPointsByDriver,
            currentCoordinates,
            assignedStopsByDriver,
            assignedWeightByDriver,
            assignedVolumeByDriver,
            remainingPointIndexes
        );
      }
    }

    while (!remainingPointIndexes.isEmpty()) {
      AssignmentCandidate bestCandidate = null;
      for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
        AssignmentCandidate candidate = findBestPointForDriver(
            drivers,
            driverIndex,
            currentCoordinates[driverIndex],
            remainingPointIndexes,
            deliveryPoints,
            distanceMatrixBySource,
            preferredDriverByPoint,
            assignedStopsByDriver,
            assignedWeightByDriver,
            assignedVolumeByDriver,
            false
        );
        if (candidate == null) {
          continue;
        }
        if (bestCandidate == null
            || candidate.score() < bestCandidate.score() - 1e-9
            || (Math.abs(candidate.score() - bestCandidate.score()) <= 1e-9
                && candidate.driverIndex() < bestCandidate.driverIndex())) {
          bestCandidate = candidate;
        }
      }

      if (bestCandidate == null) {
        break;
      }

      assignPointToDriver(
          bestCandidate,
          deliveryPoints,
          assignments,
          orderedPointsByDriver,
          currentCoordinates,
          assignedStopsByDriver,
          assignedWeightByDriver,
          assignedVolumeByDriver,
          remainingPointIndexes
      );
    }

    List<List<AssignedPoint>> optimizedPointsByDriver = reorderAssignedPointsByNearestNeighbor(
        orderedPointsByDriver,
        drivers,
        deliveryPoints,
        distanceMatrixBySource
    );
    List<DriverRoutePlan> routes = buildDriverRoutesForOrderedPoints(optimizedPointsByDriver, drivers, deliveryPoints);
    double totalDistanceKm = routes.stream().mapToDouble(DriverRoutePlan::distanceKm).sum();
    return new TransportPlan(assignments, routes, totalDistanceKm);
  }

  private List<List<AssignedPoint>> reorderAssignedPointsByNearestNeighbor(
      List<List<AssignedPoint>> assignedPointsByDriver,
      List<DriverPlanNode> drivers,
      List<DeliveryPoint> points,
      Map<Coordinate, Map<Integer, Double>> distanceMatrixBySource
  ) {
    List<List<AssignedPoint>> optimized = new ArrayList<>(assignedPointsByDriver.size());
    for (int driverIndex = 0; driverIndex < assignedPointsByDriver.size(); driverIndex++) {
      List<AssignedPoint> assignedPoints = assignedPointsByDriver.get(driverIndex);
      if (assignedPoints == null || assignedPoints.size() <= 1) {
        optimized.add(assignedPoints == null ? List.of() : List.copyOf(assignedPoints));
        continue;
      }

      List<Integer> remainingPointIndexes = assignedPoints.stream()
          .map(AssignedPoint::pointIndex)
          .collect(Collectors.toCollection(ArrayList::new));
      List<AssignedPoint> route = new ArrayList<>(assignedPoints.size());
      Coordinate currentCoordinate = drivers.get(driverIndex).startCoordinate();

      while (!remainingPointIndexes.isEmpty()) {
        int bestOffset = 0;
        int bestPointIndex = remainingPointIndexes.getFirst();
        double bestDistanceKm = distanceFromMatrix(distanceMatrixBySource, currentCoordinate, bestPointIndex, points);

        for (int offset = 1; offset < remainingPointIndexes.size(); offset++) {
          int candidatePointIndex = remainingPointIndexes.get(offset);
          double candidateDistanceKm = distanceFromMatrix(distanceMatrixBySource, currentCoordinate, candidatePointIndex, points);
          if (candidateDistanceKm < bestDistanceKm - 1e-9
              || (Math.abs(candidateDistanceKm - bestDistanceKm) <= 1e-9 && candidatePointIndex < bestPointIndex)) {
            bestOffset = offset;
            bestPointIndex = candidatePointIndex;
            bestDistanceKm = candidateDistanceKm;
          }
        }

        route.add(new AssignedPoint(bestPointIndex, bestDistanceKm));
        currentCoordinate = points.get(bestPointIndex).coordinate();
        remainingPointIndexes.remove(bestOffset);
      }

      optimized.add(List.copyOf(route));
    }
    return optimized;
  }

  private List<DriverRoutePlan> buildDriverRoutesForOrderedPoints(List<List<AssignedPoint>> pointIndexesByDriver,
                                                                  List<DriverPlanNode> drivers,
                                                                  List<DeliveryPoint> points) {
    List<DriverRoutePlan> routes = new ArrayList<>(drivers.size());
    for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
      List<AssignedPoint> orderedPoints = pointIndexesByDriver.get(driverIndex);
      if (orderedPoints == null || orderedPoints.isEmpty()) {
        routes.add(new DriverRoutePlan(driverIndex, List.of(), 0.0, 0.0, 0.0));
        continue;
      }

      List<RouteStop> stops = new ArrayList<>();
      int sequence = 1;
      double routeDistance = 0.0;
      double totalWeight = 0.0;
      double totalVolume = 0.0;

      for (AssignedPoint assignedPoint : orderedPoints) {
        DeliveryPoint point = points.get(assignedPoint.pointIndex());
        double legDistance = assignedPoint.distanceFromPreviousKm();
        routeDistance += legDistance;
        totalWeight += point.totalWeightKg();
        totalVolume += point.totalVolumeM3();

        boolean firstInPoint = true;
        for (int orderIdx : point.orderIndexes()) {
          stops.add(new RouteStop(orderIdx, sequence++, firstInPoint ? legDistance : 0.0));
          firstInPoint = false;
        }
      }

      routes.add(new DriverRoutePlan(driverIndex, stops, routeDistance, totalWeight, totalVolume));
    }
    return routes;
  }

  private Map<Coordinate, Map<Integer, Double>> buildPlanningDistanceMatrix(List<DriverPlanNode> drivers,
                                                                            List<DeliveryPoint> points) {
    if (points.isEmpty()) {
      return Map.of();
    }

    LinkedHashSet<Coordinate> uniqueSources = new LinkedHashSet<>();
    for (DriverPlanNode driver : drivers) {
      uniqueSources.add(driver.zoneSeed());
      uniqueSources.add(driver.startCoordinate());
    }
    for (DeliveryPoint point : points) {
      uniqueSources.add(point.coordinate());
    }

    List<Coordinate> sources = new ArrayList<>(uniqueSources);
    List<Coordinate> destinations = points.stream()
        .map(DeliveryPoint::coordinate)
        .toList();

    try {
      List<List<Double>> matrix = roadRoutingService.drivingDistanceMatrixKm(
          sources.stream().map(this::toRouteCoordinate).toList(),
          destinations.stream().map(this::toRouteCoordinate).toList()
      );
      Map<Coordinate, Map<Integer, Double>> bySource = new HashMap<>();
      for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
        Map<Integer, Double> rowByPointIndex = new HashMap<>();
        List<Double> row = matrix.get(sourceIndex);
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
          rowByPointIndex.put(pointIndex, row.get(pointIndex));
        }
        bySource.put(sources.get(sourceIndex), rowByPointIndex);
      }
      return bySource;
    } catch (RuntimeException exception) {
      log.debug("Road distance matrix unavailable for planning, falling back to haversine: {}", exception.getMessage());
      Map<Coordinate, Map<Integer, Double>> bySource = new HashMap<>();
      for (Coordinate source : sources) {
        Map<Integer, Double> rowByPointIndex = new HashMap<>();
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
          rowByPointIndex.put(pointIndex, haversineKm(source, points.get(pointIndex).coordinate()));
        }
        bySource.put(source, rowByPointIndex);
      }
      return bySource;
    }
  }

  private Map<Integer, Integer> resolvePreferredDriverByPoint(List<DriverPlanNode> drivers,
                                                              List<DeliveryPoint> points,
                                                              Map<Coordinate, Map<Integer, Double>> distanceMatrixBySource) {
    if (drivers.isEmpty() || points.isEmpty()) {
      return Map.of();
    }

    Map<Integer, Integer> preferredDriverByPoint = new HashMap<>();
    for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
      int bestDriverIndex = 0;
      double bestDistance = distanceFromMatrix(distanceMatrixBySource, drivers.getFirst().zoneSeed(), pointIndex, points);

      for (int driverIndex = 1; driverIndex < drivers.size(); driverIndex++) {
        double candidateDistance = distanceFromMatrix(
            distanceMatrixBySource,
            drivers.get(driverIndex).zoneSeed(),
            pointIndex,
            points
        );
        if (candidateDistance < bestDistance - 1e-9
            || (Math.abs(candidateDistance - bestDistance) <= 1e-9
                && drivers.get(driverIndex).driver().getId() < drivers.get(bestDriverIndex).driver().getId())) {
          bestDriverIndex = driverIndex;
          bestDistance = candidateDistance;
        }
      }
      preferredDriverByPoint.put(pointIndex, bestDriverIndex);
    }
    return preferredDriverByPoint;
  }

  private AssignmentCandidate findBestPointForDriver(List<DriverPlanNode> drivers,
                                                     int driverIndex,
                                                     Coordinate currentCoordinate,
                                                     List<Integer> candidatePointIndexes,
                                                     List<DeliveryPoint> points,
                                                     Map<Coordinate, Map<Integer, Double>> distanceMatrixBySource,
                                                     Map<Integer, Integer> preferredDriverByPoint,
                                                     int[] assignedStopsByDriver,
                                                     double[] assignedWeightByDriver,
                                                     double[] assignedVolumeByDriver,
                                                     boolean ownZoneOnly) {
    List<Integer> feasiblePointIndexes = new ArrayList<>();
    for (int pointIndex : candidatePointIndexes) {
      int preferredDriverIndex = preferredDriverByPoint.getOrDefault(pointIndex, driverIndex);
      if (ownZoneOnly && preferredDriverIndex != driverIndex) {
        continue;
      }

      DeliveryPoint point = points.get(pointIndex);
      if (!canAssignPointToDriver(
          drivers.get(driverIndex),
          point,
          assignedStopsByDriver[driverIndex],
          assignedWeightByDriver[driverIndex],
          assignedVolumeByDriver[driverIndex]
      )) {
        continue;
      }

      feasiblePointIndexes.add(pointIndex);
    }

    if (feasiblePointIndexes.isEmpty()) {
      return null;
    }

    AssignmentCandidate bestCandidate = null;
    for (int pointIndex : feasiblePointIndexes) {
      int preferredDriverIndex = preferredDriverByPoint.getOrDefault(pointIndex, driverIndex);
      double distanceKm = distanceFromMatrix(distanceMatrixBySource, currentCoordinate, pointIndex, points);
      double score = distanceKm;
      if (preferredDriverIndex != driverIndex) {
        score += CROSS_ZONE_PENALTY_KM;
      }

      if (bestCandidate == null
          || score < bestCandidate.score() - 1e-9
          || (Math.abs(score - bestCandidate.score()) <= 1e-9 && pointIndex < bestCandidate.pointIndex())) {
        bestCandidate = new AssignmentCandidate(driverIndex, pointIndex, distanceKm, score);
      }
    }
    return bestCandidate;
  }

  private boolean canAssignPointToDriver(DriverPlanNode driver,
                                         DeliveryPoint point,
                                         int assignedStops,
                                         double assignedWeightKg,
                                         double assignedVolumeM3) {
    int driverCapacity = Math.max(0, driver.capacity());
    return driverCapacity > 0
        && assignedStops + 1 <= driverCapacity
        && assignedWeightKg + point.totalWeightKg() <= VEHICLE_MAX_WEIGHT_KG
        && assignedVolumeM3 + point.totalVolumeM3() <= VEHICLE_MAX_VOLUME_M3;
  }

  private void assignPointToDriver(AssignmentCandidate candidate,
                                   List<DeliveryPoint> points,
                                   List<TransportAssignment> assignments,
                                   List<List<AssignedPoint>> orderedPointIndexesByDriver,
                                   Coordinate[] currentCoordinates,
                                   int[] assignedStopsByDriver,
                                   double[] assignedWeightByDriver,
                                   double[] assignedVolumeByDriver,
                                   List<Integer> remainingPointIndexes) {
    DeliveryPoint point = points.get(candidate.pointIndex());
    assignments.add(new TransportAssignment(candidate.pointIndex(), candidate.driverIndex()));
    orderedPointIndexesByDriver.get(candidate.driverIndex())
        .add(new AssignedPoint(candidate.pointIndex(), candidate.distanceKm()));
    currentCoordinates[candidate.driverIndex()] = point.coordinate();
    assignedStopsByDriver[candidate.driverIndex()] += 1;
    assignedWeightByDriver[candidate.driverIndex()] += point.totalWeightKg();
    assignedVolumeByDriver[candidate.driverIndex()] += point.totalVolumeM3();
    remainingPointIndexes.remove(Integer.valueOf(candidate.pointIndex()));
  }

  private record DeliveryPoint(Coordinate coordinate, List<Integer> orderIndexes, double totalWeightKg, double totalVolumeM3) {}

  private record AssignmentCandidate(int driverIndex, int pointIndex, double distanceKm, double score) {
  }

  private record AssignedPoint(int pointIndex, double distanceFromPreviousKm) {
  }

  private double distanceFromMatrix(Map<Coordinate, Map<Integer, Double>> distanceMatrixBySource,
                                    Coordinate source,
                                    int pointIndex,
                                    List<DeliveryPoint> points) {
    Map<Integer, Double> byPointIndex = distanceMatrixBySource.get(source);
    if (byPointIndex == null) {
      return haversineKm(source, points.get(pointIndex).coordinate());
    }
    return byPointIndex.getOrDefault(pointIndex, haversineKm(source, points.get(pointIndex).coordinate()));
  }

  private double distanceKm(Coordinate from, Coordinate to) {
    if (from.equals(to)) {
      return 0.0;
    }
    try {
      return roadRoutingService.drivingDistanceKm(toRouteCoordinate(from), toRouteCoordinate(to));
    } catch (RuntimeException exception) {
      log.debug("Road distance unavailable, falling back to haversine: {}", exception.getMessage());
      return haversineKm(from, to);
    }
  }

  private RoadRoutingService.RouteCoordinate toRouteCoordinate(Coordinate coordinate) {
    return new RoadRoutingService.RouteCoordinate(coordinate.latitude(), coordinate.longitude());
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

  private double estimateItemWeightKg(OrderItem item) {
    if (item == null) {
      return 0.0;
    }
    int quantity = item.getQuantity() == null ? 0 : Math.max(0, item.getQuantity());
    if (quantity == 0) {
      return 0.0;
    }
    Product product = item.getProduct();
    double weightKg = product == null || product.getWeightKg() == null || product.getWeightKg() <= 0
        ? DEFAULT_PRODUCT_WEIGHT_KG
        : product.getWeightKg();
    return weightKg * quantity;
  }

  private double estimateItemVolumeM3(OrderItem item) {
    if (item == null) {
      return 0.0;
    }
    int quantity = item.getQuantity() == null ? 0 : Math.max(0, item.getQuantity());
    if (quantity == 0) {
      return 0.0;
    }
    Product product = item.getProduct();
    double volumeM3 = product == null || product.getVolumeM3() == null || product.getVolumeM3() <= 0
        ? DEFAULT_PRODUCT_VOLUME_M3
        : product.getVolumeM3();
    return volumeM3 * quantity;
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
        order.getCustomer().getLegalEntityName(),
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

  private record DriverPlanNode(User driver, int capacity, Coordinate startCoordinate, Coordinate zoneSeed) {
  }

  private record TransportAssignment(int orderIndex, int driverIndex) {
  }

  private record RouteStop(int orderIndex, int sequence, double distanceFromPreviousKm) {
  }

  private record DriverRoutePlan(int driverIndex, List<RouteStop> stops, double distanceKm, double totalWeightKg, double totalVolumeM3) {
  }

  private record PreviewRouteDraft(int driverIndex,
                                   DriverPlanNode driverNode,
                                   DriverRoutePlan route,
                                   List<AutoAssignRoutePointResponse> points) {
  }

  private record TransportPlan(List<TransportAssignment> assignments,
                               List<DriverRoutePlan> routes,
                               double totalDistanceKm) {
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
}
