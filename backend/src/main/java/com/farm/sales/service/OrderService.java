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
import com.farm.sales.dto.AutoAssignRouteTripResponse;
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
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderItemRepository;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.ProductRepository;
import com.farm.sales.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private static final int ROAD_MATRIX_DELIVERY_POINT_THRESHOLD = 60;
  private static final int TRANSPORT_DRIVER_LIMIT = 3;
  private static final double EARTH_RADIUS_KM = 6371.0088;
  private static final String MOGILEV_SHARED_DEPOT_LABEL = "Могилёв, ул. Первомайская 31 (логистический хаб)";
  private static final Coordinate MOGILEV_SHARED_DEPOT = new Coordinate(53.8971270, 30.3320410);
  private static final double VEHICLE_MAX_WEIGHT_KG = 1500.0;
  private static final double VEHICLE_MAX_VOLUME_M3 = 12.0;
  private static final int MIN_POINTS_FOR_TWO_OPT = 3;

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
  private final OrderLifecyclePublisher orderLifecyclePublisher;
  private final OrderResponseMapper orderResponseMapper;
  private final OrderRouteGeometrySupport routeGeometrySupport;
  private final OrderTransportTextSupport transportTextSupport;

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
    this.orderLifecyclePublisher = new OrderLifecyclePublisher(
        auditTrailPublisher,
        stockMovementService,
        orderTimelineService,
        notificationStreamService
    );
    this.orderResponseMapper = new OrderResponseMapper();
    this.routeGeometrySupport = new OrderRouteGeometrySupport(
        roadRoutingService,
        log,
        MOGILEV_SHARED_DEPOT.latitude(),
        MOGILEV_SHARED_DEPOT.longitude()
    );
    this.transportTextSupport = new OrderTransportTextSupport(VEHICLE_MAX_WEIGHT_KG, VEHICLE_MAX_VOLUME_M3);
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
      orderLifecyclePublisher.publishCreated(saved);
      return orderResponseMapper.toResponse(saved);
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
      case DRIVER -> orderRepository.findPageByAssignedDriverIdOrderByRouteOrder(userId, pageRequest);
      case MANAGER -> orderRepository.findPageAllByOrderByCreatedAtDesc(pageRequest);
      case LOGISTICIAN -> orderRepository.findPageByStatusOrderByCreatedAtDesc(OrderStatus.APPROVED, pageRequest);
    };
    if (ordersPage == null) {
      List<Order> fallbackOrders = switch (role) {
        case DIRECTOR -> orderRepository.findByCustomerIdOrderByCreatedAtDesc(userId, pageRequest);
        case DRIVER -> orderRepository.findByAssignedDriverIdOrderByRouteOrder(userId, pageRequest);
        case MANAGER -> orderRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        case LOGISTICIAN -> orderRepository.findByStatusOrderByCreatedAtDesc(OrderStatus.APPROVED, pageRequest);
      };
      List<Order> safeOrders = fallbackOrders == null ? List.of() : fallbackOrders;
      Map<Long, List<OrderItem>> fallbackItemsByOrderId = loadItemsByOrderId(safeOrders);
      List<OrderResponse> fallbackItems = safeOrders.stream()
          .map(order -> orderResponseMapper.toResponse(order, fallbackItemsByOrderId.getOrDefault(order.getId(), List.of())))
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
        .map(order -> orderResponseMapper.toResponse(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
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
    orderLifecyclePublisher.publishApproved(saved, manager, previousStatus);
    return orderResponseMapper.toResponse(saved);
  }

  @Transactional
  public OrderResponse assignDriver(Long orderId, Long logisticianId, Long driverId) {
    User logistician = requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    User driver = requireUserRole(driverId, Role.DRIVER, "Водитель не найден");
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    return orderResponseMapper.toResponse(assignDriverInternal(order, logistician, driver));
  }

  @Transactional
  public AutoAssignResultResponse autoAssignApprovedOrders(Long logisticianId) {
    AutoAssignPreviewResponse preview = previewAutoAssignPlan(logisticianId, null);
    List<AutoAssignApproveItemRequest> assignments = preview.routes().stream()
        .flatMap(route -> route.points().stream()
            .map(point -> new AutoAssignApproveItemRequest(
                point.orderId(),
                route.driverId(),
                point.tripNumber(),
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
    return previewAutoAssignPlan(logisticianId, null);
  }

  @Transactional
  public AutoAssignPreviewResponse previewAutoAssignPlan(Long logisticianId, List<Long> selectedDriverIds) {
    requireUserRole(logisticianId, Role.LOGISTICIAN, "Логист не найден");
    List<Order> approvedOrders = loadApprovedOrdersForRouting(false);
    validateTransportMetrics(approvedOrders);
    List<User> routingDrivers = resolveRoutingDrivers(selectedDriverIds);
    List<DriverPlanNode> planDrivers = buildDriverPlanNodes(routingDrivers, MOGILEV_SHARED_DEPOT);
    List<String> planningHighlights = transportTextSupport.buildPlanningHighlights(routingDrivers.size(), false);
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
              .map(driver -> new AutoAssignDriverRouteResponse(
                  driver.getId(),
                  driver.getFullName(),
                  0,
                  0.0,
                  0.0,
                  0.0,
                  List.of(),
                  List.of(),
                  List.of(),
                  List.of("В текущем плане этому водителю не назначено ни одной точки.")
              ))
              .toList(),
          false,
          planningHighlights
      );
    }
    TransportPlan plan = solveClusteredPlan(planDrivers, approvedOrders, MOGILEV_SHARED_DEPOT);

    List<PreviewRouteDraft> routeDrafts = new ArrayList<>();
    Map<Integer, DriverRoutePlan> routeByDriverIndex = plan.routes().stream()
        .collect(Collectors.toMap(DriverRoutePlan::driverIndex, Function.identity()));
    for (int driverIndex = 0; driverIndex < planDrivers.size(); driverIndex++) {
      DriverPlanNode driverNode = planDrivers.get(driverIndex);
      DriverRoutePlan route = routeByDriverIndex.get(driverIndex);
      List<AutoAssignRoutePointResponse> points = buildPreviewRoutePoints(route, approvedOrders);
      routeDrafts.add(new PreviewRouteDraft(driverIndex, driverNode, route, points));
    }
    List<AutoAssignDriverRouteResponse> routes = routeDrafts.stream()
        .map(routeDraft -> Map.entry(routeDraft.driverIndex(), toPreviewRouteResponse(routeDraft)))
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .toList();

    int plannedOrders = routes.stream()
        .mapToInt(route -> route.points().size())
        .sum();
    int totalApproved = approvedOrders.size();
    planningHighlights = transportTextSupport.buildPlanningHighlights(
        routingDrivers.size(),
        plan.approximatePlanningDistances()
    );
    return new AutoAssignPreviewResponse(
        MOGILEV_SHARED_DEPOT_LABEL,
        MOGILEV_SHARED_DEPOT.latitude(),
        MOGILEV_SHARED_DEPOT.longitude(),
        totalApproved,
        plannedOrders,
        Math.max(0, totalApproved - plannedOrders),
        roundDistance(plan.totalDistanceKm()),
        routes,
        plan.approximatePlanningDistances(),
        planningHighlights
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
    return routeGeometrySupport.buildPreviewRoutePathFromCoordinates(request.points(), request.returnsToDepot());
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
    validateTransportMetrics(approvedOrders);

    Map<Long, Order> approvedOrdersById = approvedOrders.stream()
        .filter(order -> order.getStatus() == OrderStatus.APPROVED)
        .collect(Collectors.toMap(Order::getId, Function.identity()));
    Set<Long> requestedDriverIds = requestedAssignments.stream()
        .map(AutoAssignApproveItemRequest::driverId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    List<User> routingDrivers = resolveRoutingDrivers(new ArrayList<>(requestedDriverIds));
    Map<Long, User> driversById = routingDrivers.stream()
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
            "В плане указан водитель вне выбранной транспортной группы"
        );
      }
    }

    Map<Long, List<AutoAssignApproveItemRequest>> assignmentsByDriver = new LinkedHashMap<>();
    for (AutoAssignApproveItemRequest item : requestedAssignments) {
      assignmentsByDriver.computeIfAbsent(item.driverId(), ignored -> new ArrayList<>()).add(item);
    }
    for (List<AutoAssignApproveItemRequest> items : assignmentsByDriver.values()) {
      items.sort(Comparator
          .comparing((AutoAssignApproveItemRequest item) -> item.tripNumber() == null ? Integer.MAX_VALUE : item.tripNumber())
          .thenComparing(item -> item.stopSequence() == null ? Integer.MAX_VALUE : item.stopSequence())
          .thenComparing(AutoAssignApproveItemRequest::orderId));
    }

    List<AutoAssignItemResponse> resultAssignments = new ArrayList<>();
    double totalDistanceKm = 0.0;
    for (Map.Entry<Long, List<AutoAssignApproveItemRequest>> entry : assignmentsByDriver.entrySet()) {
      User driver = driversById.get(entry.getKey());
      Coordinate previousPoint = MOGILEV_SHARED_DEPOT;
      Integer activeTripNumber = null;
      for (AutoAssignApproveItemRequest item : entry.getValue()) {
        int itemTripNumber = item.tripNumber() == null ? 1 : item.tripNumber();
        if (activeTripNumber != null && itemTripNumber != activeTripNumber && !previousPoint.equals(MOGILEV_SHARED_DEPOT)) {
          totalDistanceKm += distanceKm(previousPoint, MOGILEV_SHARED_DEPOT);
          previousPoint = MOGILEV_SHARED_DEPOT;
        }

        Order order = approvedOrdersById.get(item.orderId());
        Coordinate targetPoint = coordinateOrFallback(order, MOGILEV_SHARED_DEPOT);
        double legDistance = item.estimatedDistanceKm() == null
            ? distanceKm(previousPoint, targetPoint)
            : Math.max(0.0, item.estimatedDistanceKm());
        previousPoint = targetPoint;
        activeTripNumber = itemTripNumber;

        Order saved = assignDriverInternal(order, logistician, driver, itemTripNumber, item.stopSequence());
        resultAssignments.add(new AutoAssignItemResponse(
            saved.getId(),
            driver.getId(),
            driver.getFullName(),
            roundDistance(legDistance)
        ));
        totalDistanceKm += legDistance;
      }
      if (activeTripNumber != null && !previousPoint.equals(MOGILEV_SHARED_DEPOT)) {
        totalDistanceKm += distanceKm(previousPoint, MOGILEV_SHARED_DEPOT);
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
    orderLifecyclePublisher.publishDelivered(saved, driver, previousStatus);
    return orderResponseMapper.toResponse(saved);
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
    return assignDriverInternal(order, logistician, driver, null, null);
  }

  private Order assignDriverInternal(Order order,
                                     User logistician,
                                     User driver,
                                     Integer routeTripNumber,
                                     Integer routeStopSequence) {
    if (order.getStatus() != OrderStatus.APPROVED && order.getStatus() != OrderStatus.ASSIGNED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Назначать водителя можно только для заказов в статусе APPROVED или ASSIGNED");
    }

    OrderStatus previousStatus = order.getStatus();
    User previousDriver = order.getAssignedDriver();
    
    Instant now = Instant.now();
    order.setAssignedDriver(driver);
    order.setAssignedByLogistician(logistician);
    order.setAssignedAt(now);
    order.setRouteTripNumber(routeTripNumber);
    order.setRouteStopSequence(routeStopSequence);
    order.setStatus(OrderStatus.ASSIGNED);
    order.setUpdatedAt(now);
    Order saved = orderRepository.save(order);
    orderLifecyclePublisher.publishAssigned(saved, logistician, driver, previousDriver, previousStatus);
    return saved;
  }

  private List<User> resolveRoutingDrivers() {
    return resolveRoutingDrivers(null);
  }

  private List<User> resolveRoutingDrivers(List<Long> selectedDriverIds) {
    List<User> drivers = userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER);
    if (drivers.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Невозможно выполнить автоназначение: водители не найдены"
      );
    }
    if (selectedDriverIds == null || selectedDriverIds.isEmpty()) {
      if (drivers.size() <= TRANSPORT_DRIVER_LIMIT) {
        return drivers;
      }
      return drivers.subList(0, TRANSPORT_DRIVER_LIMIT);
    }
    List<Long> normalizedIds = selectedDriverIds.stream()
        .filter(id -> id != null)
        .distinct()
        .toList();
    if (normalizedIds.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите хотя бы одного водителя");
    }
    if (normalizedIds.size() > TRANSPORT_DRIVER_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Можно выбрать не более " + TRANSPORT_DRIVER_LIMIT + " водителей для транспортной задачи"
      );
    }

    Map<Long, User> driversById = drivers.stream()
        .filter(driver -> driver.getId() != null)
        .collect(Collectors.toMap(User::getId, Function.identity()));
    List<User> selectedDrivers = new ArrayList<>(normalizedIds.size());
    for (Long driverId : normalizedIds) {
      User driver = driversById.get(driverId);
      if (driver == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Выбранный водитель #" + driverId + " не найден или недоступен"
        );
      }
      selectedDrivers.add(driver);
    }
    return selectedDrivers;
  }

  private List<DriverPlanNode> buildDriverPlanNodes(List<User> drivers,
                                                    Coordinate fallbackCoordinate) {
    if (drivers.isEmpty()) {
      return List.of();
    }

    List<DriverPlanNode> planNodes = new ArrayList<>();
    for (User driver : drivers) {
      planNodes.add(new DriverPlanNode(driver, fallbackCoordinate));
    }
    return planNodes;
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

  private AutoAssignDriverRouteResponse toPreviewRouteResponse(PreviewRouteDraft routeDraft) {
    DriverPlanNode driverNode = routeDraft.driverNode();
    DriverRoutePlan route = routeDraft.route();
    List<AutoAssignRoutePointResponse> points = routeDraft.points();
    List<AutoAssignRouteTripResponse> trips = route == null
        ? List.of()
        : buildPreviewRouteTrips(route, points);
    List<AutoAssignRoutePathPointResponse> path = routeGeometrySupport.flattenPreviewRoutePaths(trips);
    List<String> insights = transportTextSupport.buildRouteInsights(trips);
    return new AutoAssignDriverRouteResponse(
        driverNode.driver().getId(),
        driverNode.driver().getFullName(),
        points.size(),
        roundDistance(route == null ? 0.0 : route.distanceKm()),
        roundDistance(route == null ? 0.0 : route.totalWeightKg()),
        roundDistance(route == null ? 0.0 : route.totalVolumeM3()),
        points,
        path,
        trips,
        insights
    );
  }

  private List<AutoAssignRoutePointResponse> buildPreviewRoutePoints(DriverRoutePlan route, List<Order> approvedOrders) {
    if (route == null || route.stops() == null || route.stops().isEmpty()) {
      return List.of();
    }

    List<AutoAssignRoutePointResponse> points = new ArrayList<>(route.stops().size());
    AutoAssignRoutePointResponse previousPoint = null;
    for (RouteStop stop : route.stops()) {
      Order order = approvedOrders.get(stop.orderIndex());
      Coordinate coordinate = coordinateOrFallback(order, MOGILEV_SHARED_DEPOT);
      String deliveryAddress = order.getDeliveryAddressText() == null || order.getDeliveryAddressText().isBlank()
          ? "Заказ №" + order.getId()
          : order.getDeliveryAddressText();
      AutoAssignRoutePointResponse point = new AutoAssignRoutePointResponse(
          order.getId(),
          deliveryAddress,
          coordinate.latitude(),
          coordinate.longitude(),
          stop.tripNumber(),
          stop.sequence(),
          roundDistance(stop.distanceFromPreviousKm()),
          buildStopSelectionReason(stop, coordinate, previousPoint),
          buildPreviewOrderItems(order)
      );
      points.add(point);
      previousPoint = point;
    }
    return List.copyOf(points);
  }

  private String buildStopSelectionReason(RouteStop stop,
                                          Coordinate coordinate,
                                          AutoAssignRoutePointResponse previousPoint) {
    if (previousPoint == null || previousPoint.tripNumber() != stop.tripNumber()) {
      return stop.tripNumber() <= 1
          ? "Первая точка рейса: выбрана как ближайшая подходящая доставка от склада."
          : "Новый рейс после повторной загрузки: первая точка снова выбрана от склада.";
    }

    boolean sameCoordinate = Double.compare(previousPoint.latitude(), coordinate.latitude()) == 0
        && Double.compare(previousPoint.longitude(), coordinate.longitude()) == 0;
    if (sameCoordinate) {
      return "Тот же адрес, объединён с предыдущей точкой без дополнительного пробега.";
    }
    return "Следующая точка рейса: выбрана как ближайшая подходящая доставка от предыдущей остановки.";
  }

  private List<OrderItemResponse> buildPreviewOrderItems(Order order) {
    if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
      return List.of();
    }

    return order.getItems().stream()
        .map(item -> new OrderItemResponse(
            item.getProduct() == null ? null : item.getProduct().getId(),
            item.getProduct() == null || item.getProduct().getName() == null || item.getProduct().getName().isBlank()
                ? "Без названия"
                : item.getProduct().getName(),
            item.getQuantity(),
            item.getPrice(),
            item.getLineTotal()
        ))
        .toList();
  }

  private List<AutoAssignRouteTripResponse> buildPreviewRouteTrips(
      DriverRoutePlan route,
      List<AutoAssignRoutePointResponse> points
  ) {
    if (route == null || points == null || points.isEmpty() || route.trips().isEmpty()) {
      return List.of();
    }

    Map<Integer, List<AutoAssignRoutePointResponse>> pointsByTrip = points.stream()
        .collect(Collectors.groupingBy(
            AutoAssignRoutePointResponse::tripNumber,
            LinkedHashMap::new,
            Collectors.collectingAndThen(Collectors.toList(), tripPoints -> tripPoints.stream()
                .sorted(Comparator.comparingInt(AutoAssignRoutePointResponse::stopSequence))
                .toList())
        ));

    List<AutoAssignRouteTripResponse> trips = new ArrayList<>(route.trips().size());
    for (DriverTripPlan trip : route.trips()) {
      List<AutoAssignRoutePointResponse> tripPoints = pointsByTrip.getOrDefault(trip.tripNumber(), List.of());
      List<AutoAssignRoutePathPointResponse> tripPath = tripPoints.isEmpty()
          ? List.of()
          : routeGeometrySupport.buildPreviewTripPath(
              tripPoints.stream()
                  .map(point -> new AutoAssignRoutePathPointResponse(point.latitude(), point.longitude()))
                  .distinct()
                  .toList(),
              routeGeometrySupport.depotPoint(),
              trip.returnsToDepot()
          );
      double weightUtilizationPercent = utilizationPercent(trip.totalWeightKg(), VEHICLE_MAX_WEIGHT_KG);
      double volumeUtilizationPercent = utilizationPercent(trip.totalVolumeM3(), VEHICLE_MAX_VOLUME_M3);
      trips.add(new AutoAssignRouteTripResponse(
          trip.tripNumber(),
          trip.assignedOrders(),
          roundDistance(trip.distanceKm()),
          roundDistance(trip.totalWeightKg()),
          roundDistance(trip.totalVolumeM3()),
          trip.returnsToDepot(),
          tripPoints,
          tripPath,
          transportTextSupport.buildTripInsights(
              trip.tripNumber(),
              tripPoints,
              weightUtilizationPercent,
              volumeUtilizationPercent
          ),
          roundDistance(weightUtilizationPercent),
          roundDistance(volumeUtilizationPercent)
      ));
    }
    return trips;
  }

  private TransportPlan solveClusteredPlan(List<DriverPlanNode> drivers,
                                           List<Order> orders,
                                           Coordinate fallbackCoordinate) {
    if (drivers.isEmpty() || orders.isEmpty()) {
      return new TransportPlan(List.of(), 0.0, false);
    }

    double[] orderWeightKg = new double[orders.size()];
    double[] orderVolumeM3 = new double[orders.size()];
    for (int orderIndex = 0; orderIndex < orders.size(); orderIndex++) {
      Order order = orders.get(orderIndex);
      double totalWeightKg = 0.0;
      double totalVolumeM3 = 0.0;
      List<OrderItem> orderItems = order.getItems() == null ? List.of() : order.getItems();
      for (OrderItem item : orderItems) {
        totalWeightKg += estimateItemWeightKg(item);
        totalVolumeM3 += estimateItemVolumeM3(item);
      }
      orderWeightKg[orderIndex] = totalWeightKg;
      orderVolumeM3[orderIndex] = totalVolumeM3;
    }

    Map<Coordinate, List<Integer>> ordersByLocation = new LinkedHashMap<>();
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
        weight += orderWeightKg[idx];
        volume += orderVolumeM3[idx];
      }
      deliveryPoints.add(new DeliveryPoint(entry.getKey(), orderIdxs, weight, volume));
    }

    PlanningDistanceTable distanceTable = buildPlanningDistanceTable(drivers, deliveryPoints);
    boolean[] remainingPointIndexes = new boolean[deliveryPoints.size()];
    int remainingPointCount = deliveryPoints.size();
    for (int pointIndex = 0; pointIndex < deliveryPoints.size(); pointIndex++) {
      remainingPointIndexes[pointIndex] = true;
    }

    List<List<List<AssignedPoint>>> tripsByDriver = new ArrayList<>(drivers.size());
    for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
      tripsByDriver.add(new ArrayList<>());
    }

    record PointAngle(int pointIndex, double angle, double distance) {}
    List<PointAngle> pointAngles = new ArrayList<>();
    for (int i = 0; i < deliveryPoints.size(); i++) {
      Coordinate c = deliveryPoints.get(i).coordinate();
      double angle = Math.atan2(c.longitude() - fallbackCoordinate.longitude(), c.latitude() - fallbackCoordinate.latitude());
      double dist = Math.pow(c.longitude() - fallbackCoordinate.longitude(), 2) + Math.pow(c.latitude() - fallbackCoordinate.latitude(), 2);
      pointAngles.add(new PointAngle(i, angle, dist));
    }
    pointAngles.sort(Comparator.comparingDouble(PointAngle::angle).thenComparingDouble(PointAngle::distance));
    int[] sweepOrderedIndexes = pointAngles.stream().mapToInt(PointAngle::pointIndex).toArray();

    while (remainingPointCount > 0) {
      double[] assignedWeightByDriver = new double[drivers.size()];
      double[] assignedVolumeByDriver = new double[drivers.size()];
      List<List<AssignedPoint>> currentTripPointsByDriver = new ArrayList<>(drivers.size());
      for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
        currentTripPointsByDriver.add(new ArrayList<>());
      }

      boolean assignedAnyInTripWave = false;
      int currentDriverIndex = 0;

      for (int pointIndex : sweepOrderedIndexes) {
        if (!remainingPointIndexes[pointIndex]) {
          continue;
        }
        DeliveryPoint point = deliveryPoints.get(pointIndex);

        for (int attempts = 0; attempts < drivers.size(); attempts++) {
          int dIdx = (currentDriverIndex + attempts) % drivers.size();
          if (canAssignPointToDriver(point, assignedWeightByDriver[dIdx], assignedVolumeByDriver[dIdx])) {
            currentTripPointsByDriver.get(dIdx).add(new AssignedPoint(pointIndex, 0.0));
            assignedWeightByDriver[dIdx] += point.totalWeightKg();
            assignedVolumeByDriver[dIdx] += point.totalVolumeM3();
            remainingPointIndexes[pointIndex] = false;
            remainingPointCount -= 1;
            assignedAnyInTripWave = true;
            currentDriverIndex = dIdx;
            break;
          }
        }
      }

      for (int driverIndex = 0; driverIndex < drivers.size(); driverIndex++) {
        List<AssignedPoint> tripPoints = currentTripPointsByDriver.get(driverIndex);
        if (!tripPoints.isEmpty()) {
          tripsByDriver.get(driverIndex).add(List.copyOf(tripPoints));
        }
      }

      if (!assignedAnyInTripWave) {
        break;
      }
    }

    List<List<List<AssignedPoint>>> optimizedTripsByDriver = reorderTripsByNearestNeighbor(
        tripsByDriver,
        deliveryPoints,
        distanceTable,
        fallbackCoordinate
    );
    List<List<List<AssignedPoint>>> rebalancedTripsByDriver = rebalanceTripsAcrossExistingTrips(
        optimizedTripsByDriver,
        deliveryPoints,
        distanceTable,
        fallbackCoordinate
    );
    List<DriverRoutePlan> routes = buildDriverRoutesForTrips(rebalancedTripsByDriver, deliveryPoints);
    double totalDistanceKm = routes.stream().mapToDouble(DriverRoutePlan::distanceKm).sum();
    return new TransportPlan(routes, totalDistanceKm, distanceTable.approximatePlanningDistances());
  }

  private List<List<List<AssignedPoint>>> reorderTripsByNearestNeighbor(
      List<List<List<AssignedPoint>>> assignedTripsByDriver,
      List<DeliveryPoint> points,
      PlanningDistanceTable distanceTable,
      Coordinate tripStartCoordinate
  ) {
    List<List<List<AssignedPoint>>> optimizedTripsByDriver = new ArrayList<>(assignedTripsByDriver.size());
    for (List<List<AssignedPoint>> driverTrips : assignedTripsByDriver) {
      if (driverTrips == null || driverTrips.isEmpty()) {
        optimizedTripsByDriver.add(List.of());
        continue;
      }

      List<List<AssignedPoint>> optimizedDriverTrips = new ArrayList<>(driverTrips.size());
      for (int tripOffset = 0; tripOffset < driverTrips.size(); tripOffset++) {
        optimizedDriverTrips.add(optimizeTripPoints(driverTrips.get(tripOffset), distanceTable, points, tripStartCoordinate));
      }

      optimizedTripsByDriver.add(List.copyOf(optimizedDriverTrips));
    }
    return optimizedTripsByDriver;
  }

  private List<List<List<AssignedPoint>>> rebalanceTripsAcrossExistingTrips(
      List<List<List<AssignedPoint>>> tripsByDriver,
      List<DeliveryPoint> points,
      PlanningDistanceTable distanceTable,
      Coordinate tripStartCoordinate
  ) {
    if (tripsByDriver == null || tripsByDriver.isEmpty()) {
      return List.of();
    }

    List<List<List<AssignedPoint>>> workingTripsByDriver = copyTripsByDriver(tripsByDriver);
    int maxAcceptedMoves = Math.max(1, points.size() * Math.max(1, workingTripsByDriver.size()));

    for (int acceptedMoveCount = 0; acceptedMoveCount < maxAcceptedMoves; acceptedMoveCount++) {
      TripRebalanceMove bestMove = findBestTripRebalanceMove(
          workingTripsByDriver,
          points,
          distanceTable,
          tripStartCoordinate
      );
      if (bestMove == null) {
        break;
      }
      applyTripRebalanceMove(workingTripsByDriver, bestMove);
    }

    return freezeTripsByDriver(workingTripsByDriver);
  }

  private TripRebalanceMove findBestTripRebalanceMove(
      List<List<List<AssignedPoint>>> tripsByDriver,
      List<DeliveryPoint> points,
      PlanningDistanceTable distanceTable,
      Coordinate tripStartCoordinate
  ) {
    TripRebalanceMove bestMove = null;

    for (int sourceDriverIndex = 0; sourceDriverIndex < tripsByDriver.size(); sourceDriverIndex++) {
      List<List<AssignedPoint>> sourceDriverTrips = tripsByDriver.get(sourceDriverIndex);
      for (int sourceTripIndex = 0; sourceTripIndex < sourceDriverTrips.size(); sourceTripIndex++) {
        List<AssignedPoint> sourceTrip = sourceDriverTrips.get(sourceTripIndex);
        if (sourceTrip == null || sourceTrip.isEmpty()) {
          continue;
        }

        double currentSourceTripDistanceKm = computeAssignedTripDistance(
            sourceTrip,
            tripStartCoordinate,
            distanceTable,
            points
        );
        double currentSourceTripWeightKg = computeAssignedTripWeightKg(sourceTrip, points);
        double currentSourceTripVolumeM3 = computeAssignedTripVolumeM3(sourceTrip, points);

        for (int sourcePointOffset = 0; sourcePointOffset < sourceTrip.size(); sourcePointOffset++) {
          AssignedPoint movedPoint = sourceTrip.get(sourcePointOffset);
          DeliveryPoint deliveryPoint = points.get(movedPoint.pointIndex());
          double sourceWeightWithoutMovedKg = currentSourceTripWeightKg - deliveryPoint.totalWeightKg();
          double sourceVolumeWithoutMovedM3 = currentSourceTripVolumeM3 - deliveryPoint.totalVolumeM3();
          List<Integer> sourceRemainingPointIndexes = new ArrayList<>(Math.max(0, sourceTrip.size() - 1));
          for (int currentPointOffset = 0; currentPointOffset < sourceTrip.size(); currentPointOffset++) {
            if (currentPointOffset == sourcePointOffset) {
              continue;
            }
            sourceRemainingPointIndexes.add(sourceTrip.get(currentPointOffset).pointIndex());
          }
          List<AssignedPoint> optimizedSourceTrip = optimizeTripPointIndexes(
              sourceRemainingPointIndexes,
              distanceTable,
              points,
              tripStartCoordinate
          );
          double optimizedSourceTripDistanceKm = computeAssignedTripDistance(
              optimizedSourceTrip,
              tripStartCoordinate,
              distanceTable,
              points
          );

          for (int targetDriverIndex = 0; targetDriverIndex < tripsByDriver.size(); targetDriverIndex++) {
            List<List<AssignedPoint>> targetDriverTrips = tripsByDriver.get(targetDriverIndex);
            for (int targetTripIndex = 0; targetTripIndex < targetDriverTrips.size(); targetTripIndex++) {
              if (sourceDriverIndex == targetDriverIndex && sourceTripIndex == targetTripIndex) {
                continue;
              }

              List<AssignedPoint> targetTrip = targetDriverTrips.get(targetTripIndex);
              if (targetTrip == null || targetTrip.isEmpty()) {
                continue;
              }

              double currentTargetTripDistanceKm = computeAssignedTripDistance(
                  targetTrip,
                  tripStartCoordinate,
                  distanceTable,
                  points
              );
              double currentTargetTripWeightKg = computeAssignedTripWeightKg(targetTrip, points);
              double currentTargetTripVolumeM3 = computeAssignedTripVolumeM3(targetTrip, points);

              if (!canAssignPointToTrip(deliveryPoint, targetTrip, points)) {
              } else {
                List<Integer> targetPointIndexes = extractPointIndexes(targetTrip);
                targetPointIndexes.add(movedPoint.pointIndex());
                List<AssignedPoint> optimizedTargetTrip = optimizeTripPointIndexes(
                    targetPointIndexes,
                    distanceTable,
                    points,
                    tripStartCoordinate
                );
                double optimizedTargetTripDistanceKm = computeAssignedTripDistance(
                    optimizedTargetTrip,
                    tripStartCoordinate,
                    distanceTable,
                    points
                );

                double gainKm = (currentSourceTripDistanceKm + currentTargetTripDistanceKm)
                    - (optimizedSourceTripDistanceKm + optimizedTargetTripDistanceKm);
                if (gainKm > 1e-6) {
                  TripRebalanceMove candidateMove = new TripRebalanceMove(
                      sourceDriverIndex,
                      sourceTripIndex,
                      targetDriverIndex,
                      targetTripIndex,
                      movedPoint.pointIndex(),
                      optimizedSourceTrip,
                      optimizedTargetTrip,
                      gainKm
                  );
                  if (isBetterTripRebalanceMove(candidateMove, bestMove)) {
                    bestMove = candidateMove;
                  }
                }
              }

              for (int targetPointOffset = 0; targetPointOffset < targetTrip.size(); targetPointOffset++) {
                AssignedPoint swappedOutPoint = targetTrip.get(targetPointOffset);
                DeliveryPoint swappedOutDeliveryPoint = points.get(swappedOutPoint.pointIndex());
                boolean targetCanAcceptMovedPoint = canAssignPointToDriver(
                    deliveryPoint,
                    currentTargetTripWeightKg - swappedOutDeliveryPoint.totalWeightKg(),
                    currentTargetTripVolumeM3 - swappedOutDeliveryPoint.totalVolumeM3()
                );
                boolean sourceCanAcceptSwappedOutPoint = canAssignPointToDriver(
                    swappedOutDeliveryPoint,
                    sourceWeightWithoutMovedKg,
                    sourceVolumeWithoutMovedM3
                );
                if (!targetCanAcceptMovedPoint || !sourceCanAcceptSwappedOutPoint) {
                  continue;
                }

                List<Integer> targetPointIndexes = extractPointIndexes(targetTrip);

                List<Integer> swappedSourcePointIndexes = new ArrayList<>(sourceRemainingPointIndexes);
                swappedSourcePointIndexes.add(swappedOutPoint.pointIndex());
                List<AssignedPoint> optimizedSwappedSourceTrip = optimizeTripPointIndexes(
                    swappedSourcePointIndexes,
                    distanceTable,
                    points,
                    tripStartCoordinate
                );
                double optimizedSwappedSourceTripDistanceKm = computeAssignedTripDistance(
                    optimizedSwappedSourceTrip,
                    tripStartCoordinate,
                    distanceTable,
                    points
                );

                List<Integer> swappedTargetPointIndexes = new ArrayList<>(targetPointIndexes);
                swappedTargetPointIndexes.set(targetPointOffset, movedPoint.pointIndex());
                List<AssignedPoint> optimizedSwappedTargetTrip = optimizeTripPointIndexes(
                    swappedTargetPointIndexes,
                    distanceTable,
                    points,
                    tripStartCoordinate
                );
                double optimizedSwappedTargetTripDistanceKm = computeAssignedTripDistance(
                    optimizedSwappedTargetTrip,
                    tripStartCoordinate,
                    distanceTable,
                    points
                );

                double gainKm = (currentSourceTripDistanceKm + currentTargetTripDistanceKm)
                    - (optimizedSwappedSourceTripDistanceKm + optimizedSwappedTargetTripDistanceKm);
                if (gainKm <= 1e-6) {
                  continue;
                }

                TripRebalanceMove candidateMove = new TripRebalanceMove(
                    sourceDriverIndex,
                    sourceTripIndex,
                    targetDriverIndex,
                    targetTripIndex,
                    movedPoint.pointIndex(),
                    optimizedSwappedSourceTrip,
                    optimizedSwappedTargetTrip,
                    gainKm
                );
                if (isBetterTripRebalanceMove(candidateMove, bestMove)) {
                  bestMove = candidateMove;
                }
              }
            }
          }
        }
      }
    }

    return bestMove;
  }

  private boolean isBetterTripRebalanceMove(TripRebalanceMove candidateMove, TripRebalanceMove bestMove) {
    if (candidateMove == null) {
      return false;
    }
    if (bestMove == null) {
      return true;
    }
    if (candidateMove.gainKm() > bestMove.gainKm() + 1e-6) {
      return true;
    }
    if (Math.abs(candidateMove.gainKm() - bestMove.gainKm()) > 1e-6) {
      return false;
    }
    if (candidateMove.targetTripIndex() != bestMove.targetTripIndex()) {
      return candidateMove.targetTripIndex() < bestMove.targetTripIndex();
    }
    if (candidateMove.sourceTripIndex() != bestMove.sourceTripIndex()) {
      return candidateMove.sourceTripIndex() > bestMove.sourceTripIndex();
    }
    return candidateMove.pointIndex() < bestMove.pointIndex();
  }

  private void applyTripRebalanceMove(
      List<List<List<AssignedPoint>>> tripsByDriver,
      TripRebalanceMove move
  ) {
    List<List<AssignedPoint>> sourceDriverTrips = tripsByDriver.get(move.sourceDriverIndex());
    List<List<AssignedPoint>> targetDriverTrips = tripsByDriver.get(move.targetDriverIndex());

    if (move.optimizedSourceTrip().isEmpty()) {
      sourceDriverTrips.remove(move.sourceTripIndex());
      if (move.sourceDriverIndex() == move.targetDriverIndex() && move.targetTripIndex() > move.sourceTripIndex()) {
        targetDriverTrips = sourceDriverTrips;
      }
    } else {
      sourceDriverTrips.set(move.sourceTripIndex(), move.optimizedSourceTrip());
    }

    int targetTripIndex = move.targetTripIndex();
    if (move.sourceDriverIndex() == move.targetDriverIndex()
        && move.optimizedSourceTrip().isEmpty()
        && move.targetTripIndex() > move.sourceTripIndex()) {
      targetTripIndex -= 1;
    }
    targetDriverTrips.set(targetTripIndex, move.optimizedTargetTrip());
  }

  private List<List<List<AssignedPoint>>> copyTripsByDriver(List<List<List<AssignedPoint>>> tripsByDriver) {
    List<List<List<AssignedPoint>>> copiedTripsByDriver = new ArrayList<>(tripsByDriver.size());
    for (List<List<AssignedPoint>> driverTrips : tripsByDriver) {
      List<List<AssignedPoint>> copiedDriverTrips = new ArrayList<>();
      if (driverTrips != null) {
        for (List<AssignedPoint> tripPoints : driverTrips) {
          copiedDriverTrips.add(tripPoints == null ? List.of() : new ArrayList<>(tripPoints));
        }
      }
      copiedTripsByDriver.add(copiedDriverTrips);
    }
    return copiedTripsByDriver;
  }

  private List<List<List<AssignedPoint>>> freezeTripsByDriver(List<List<List<AssignedPoint>>> tripsByDriver) {
    List<List<List<AssignedPoint>>> frozenTripsByDriver = new ArrayList<>(tripsByDriver.size());
    for (List<List<AssignedPoint>> driverTrips : tripsByDriver) {
      List<List<AssignedPoint>> frozenDriverTrips = new ArrayList<>();
      if (driverTrips != null) {
        for (List<AssignedPoint> tripPoints : driverTrips) {
          frozenDriverTrips.add(tripPoints == null ? List.of() : List.copyOf(tripPoints));
        }
      }
      frozenTripsByDriver.add(List.copyOf(frozenDriverTrips));
    }
    return List.copyOf(frozenTripsByDriver);
  }

  private List<AssignedPoint> optimizeTripPoints(List<AssignedPoint> tripPoints,
                                                 PlanningDistanceTable distanceTable,
                                                 List<DeliveryPoint> points,
                                                 Coordinate tripStartCoordinate) {
    if (tripPoints == null || tripPoints.isEmpty()) {
      return List.of();
    }
    return optimizeTripPointIndexes(
        extractPointIndexes(tripPoints),
        distanceTable,
        points,
        tripStartCoordinate
    );
  }

  private List<AssignedPoint> optimizeTripPointIndexes(List<Integer> pointIndexes,
                                                       PlanningDistanceTable distanceTable,
                                                       List<DeliveryPoint> points,
                                                       Coordinate tripStartCoordinate) {
    if (pointIndexes == null || pointIndexes.isEmpty()) {
      return List.of();
    }
    if (pointIndexes.size() == 1) {
      return rebuildAssignedPoints(List.copyOf(pointIndexes), tripStartCoordinate, distanceTable, points);
    }

    Set<Integer> remainingTripPointIndexes = new LinkedHashSet<>(pointIndexes);
    List<Integer> orderedPointIndexes = new ArrayList<>(pointIndexes.size());
    Coordinate currentCoordinate = tripStartCoordinate;

    while (!remainingTripPointIndexes.isEmpty()) {
      int bestPointIndex = findNearestPointWithinSubset(currentCoordinate, remainingTripPointIndexes, distanceTable, points);
      orderedPointIndexes.add(bestPointIndex);
      currentCoordinate = points.get(bestPointIndex).coordinate();
      remainingTripPointIndexes.remove(bestPointIndex);
    }

    List<Integer> optimizedPointIndexes = orderedPointIndexes.size() >= MIN_POINTS_FOR_TWO_OPT
        ? optimizeTripByTwoOpt(orderedPointIndexes, distanceTable, points, tripStartCoordinate)
        : List.copyOf(orderedPointIndexes);
    return rebuildAssignedPoints(optimizedPointIndexes, tripStartCoordinate, distanceTable, points);
  }

  private List<Integer> extractPointIndexes(List<AssignedPoint> tripPoints) {
    if (tripPoints == null || tripPoints.isEmpty()) {
      return new ArrayList<>();
    }

    List<Integer> pointIndexes = new ArrayList<>(tripPoints.size());
    for (AssignedPoint tripPoint : tripPoints) {
      pointIndexes.add(tripPoint.pointIndex());
    }
    return pointIndexes;
  }

  private boolean canAssignPointToTrip(DeliveryPoint point,
                                       List<AssignedPoint> tripPoints,
                                       List<DeliveryPoint> points) {
    return canAssignPointToDriver(
        point,
        computeAssignedTripWeightKg(tripPoints, points),
        computeAssignedTripVolumeM3(tripPoints, points)
    );
  }

  private double computeAssignedTripWeightKg(List<AssignedPoint> tripPoints,
                                             List<DeliveryPoint> points) {
    if (tripPoints == null || tripPoints.isEmpty()) {
      return 0.0;
    }

    double assignedWeightKg = 0.0;
    for (AssignedPoint tripPoint : tripPoints) {
      assignedWeightKg += points.get(tripPoint.pointIndex()).totalWeightKg();
    }
    return assignedWeightKg;
  }

  private double computeAssignedTripVolumeM3(List<AssignedPoint> tripPoints,
                                             List<DeliveryPoint> points) {
    if (tripPoints == null || tripPoints.isEmpty()) {
      return 0.0;
    }

    double assignedVolumeM3 = 0.0;
    for (AssignedPoint tripPoint : tripPoints) {
      assignedVolumeM3 += points.get(tripPoint.pointIndex()).totalVolumeM3();
    }
    return assignedVolumeM3;
  }

  private double computeAssignedTripDistance(List<AssignedPoint> tripPoints,
                                             Coordinate tripStartCoordinate,
                                             PlanningDistanceTable distanceTable,
                                             List<DeliveryPoint> points) {
    return computeClosedTripDistance(
        extractPointIndexes(tripPoints),
        tripStartCoordinate,
        distanceTable,
        points
    );
  }

  private List<DriverRoutePlan> buildDriverRoutesForTrips(List<List<List<AssignedPoint>>> tripsByDriver,
                                                          List<DeliveryPoint> points) {
    List<DriverRoutePlan> routes = new ArrayList<>(tripsByDriver.size());
    for (int driverIndex = 0; driverIndex < tripsByDriver.size(); driverIndex++) {
      List<List<AssignedPoint>> driverTrips = tripsByDriver.get(driverIndex);
      if (driverTrips == null || driverTrips.isEmpty()) {
        routes.add(new DriverRoutePlan(driverIndex, List.of(), List.of(), 0.0, 0.0, 0.0));
        continue;
      }

      List<RouteStop> stops = new ArrayList<>();
      List<DriverTripPlan> trips = new ArrayList<>();
      int sequence = 1;
      double routeDistance = 0.0;
      double totalWeight = 0.0;
      double totalVolume = 0.0;

      for (int tripOffset = 0; tripOffset < driverTrips.size(); tripOffset++) {
        List<AssignedPoint> tripPoints = driverTrips.get(tripOffset);
        if (tripPoints == null || tripPoints.isEmpty()) {
          continue;
        }

        int tripNumber = tripOffset + 1;
        double tripDistance = 0.0;
        double tripWeight = 0.0;
        double tripVolume = 0.0;
        int tripAssignedOrders = 0;

        for (AssignedPoint assignedPoint : tripPoints) {
          DeliveryPoint point = points.get(assignedPoint.pointIndex());
          double legDistance = assignedPoint.distanceFromPreviousKm();
          tripDistance += legDistance;
          routeDistance += legDistance;
          tripWeight += point.totalWeightKg();
          tripVolume += point.totalVolumeM3();
          totalWeight += point.totalWeightKg();
          totalVolume += point.totalVolumeM3();

          boolean firstInPoint = true;
          for (int orderIdx : point.orderIndexes()) {
            stops.add(new RouteStop(orderIdx, sequence++, tripNumber, firstInPoint ? legDistance : 0.0));
            tripAssignedOrders++;
            firstInPoint = false;
          }
        }

        Coordinate lastCoordinate = points.get(tripPoints.getLast().pointIndex()).coordinate();
        boolean returnsToDepot = true;
        double returnDistance = distanceKm(lastCoordinate, MOGILEV_SHARED_DEPOT);
        tripDistance += returnDistance;
        routeDistance += returnDistance;

        trips.add(new DriverTripPlan(
            tripNumber,
            tripAssignedOrders,
            tripDistance,
            tripWeight,
            tripVolume,
            returnsToDepot
        ));
      }

      routes.add(new DriverRoutePlan(driverIndex, List.copyOf(stops), List.copyOf(trips), routeDistance, totalWeight, totalVolume));
    }
    return routes;
  }

  private PlanningDistanceTable buildPlanningDistanceTable(List<DriverPlanNode> drivers,
                                                           List<DeliveryPoint> points) {
    if (points.isEmpty()) {
      return new PlanningDistanceTable(List.of(), Map.of(), new double[0][0], new double[0], new int[0][0], false);
    }

    LinkedHashSet<Coordinate> uniqueSources = new LinkedHashSet<>();
    for (DriverPlanNode driver : drivers) {
      uniqueSources.add(driver.startCoordinate());
    }
    for (DeliveryPoint point : points) {
      uniqueSources.add(point.coordinate());
    }

    List<Coordinate> sources = new ArrayList<>(uniqueSources);
      List<Coordinate> destinations = new ArrayList<>(points.stream()
          .map(DeliveryPoint::coordinate)
          .toList());
      destinations.add(MOGILEV_SHARED_DEPOT);
      Map<Coordinate, Integer> sourceIndexByCoordinate = new HashMap<>();
      for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
        sourceIndexByCoordinate.put(sources.get(sourceIndex), sourceIndex);
      }

    if (points.size() > ROAD_MATRIX_DELIVERY_POINT_THRESHOLD) {
      log.info(
          "Auto-assign preview is using haversine planning distances for {} delivery points to avoid slow road-matrix calls.",
          points.size()
      );
      return buildApproximatePlanningDistanceTable(sources, sourceIndexByCoordinate, points);
    }

      try {
      List<List<Double>> matrix = roadRoutingService.drivingDistanceMatrixKm(
          sources.stream().map(this::toRouteCoordinate).toList(),
          destinations.stream().map(this::toRouteCoordinate).toList()
      );
      double[][] distancesBySource = new double[sources.size()][points.size()];
      double[] distanceToDepotBySource = new double[sources.size()];
      for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
        List<Double> row = matrix.get(sourceIndex);
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
          distancesBySource[sourceIndex][pointIndex] = row.get(pointIndex);
        }
        distanceToDepotBySource[sourceIndex] = row.get(points.size());
      }
      return new PlanningDistanceTable(
          List.copyOf(sources),
          Map.copyOf(sourceIndexByCoordinate),
          distancesBySource,
          distanceToDepotBySource,
          buildSortedPointIndexesBySource(distancesBySource),
          false
      );
    } catch (RuntimeException exception) {
      log.debug("Road distance matrix unavailable for planning, falling back to haversine: {}", exception.getMessage());
      return buildApproximatePlanningDistanceTable(sources, sourceIndexByCoordinate, points);
    }
  }

  private PlanningDistanceTable buildApproximatePlanningDistanceTable(
      List<Coordinate> sources,
      Map<Coordinate, Integer> sourceIndexByCoordinate,
      List<DeliveryPoint> points
  ) {
    double[][] distancesBySource = new double[sources.size()][points.size()];
    double[] distanceToDepotBySource = new double[sources.size()];
    for (int sourceIndex = 0; sourceIndex < sources.size(); sourceIndex++) {
      Coordinate source = sources.get(sourceIndex);
      for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
        distancesBySource[sourceIndex][pointIndex] = haversineKm(source, points.get(pointIndex).coordinate());
      }
      distanceToDepotBySource[sourceIndex] = haversineKm(source, MOGILEV_SHARED_DEPOT);
    }
    return new PlanningDistanceTable(
        List.copyOf(sources),
        Map.copyOf(sourceIndexByCoordinate),
        distancesBySource,
        distanceToDepotBySource,
        buildSortedPointIndexesBySource(distancesBySource),
        true
    );
  }

  private AssignmentCandidate findNearestFeasiblePoint(int driverIndex,
                                                       Coordinate currentCoordinate,
                                                       boolean[] candidatePointIndexes,
                                                       List<DeliveryPoint> points,
                                                       PlanningDistanceTable distanceTable,
                                                       Coordinate depotCoordinate,
                                                       double assignedWeightKg,
                                                       double assignedVolumeM3) {
    int sourceIndex = distanceTable.sourceIndexByCoordinate().getOrDefault(currentCoordinate, -1);
    if (sourceIndex < 0) {
      return null;
    }

    AssignmentCandidate bestCandidate = null;
    double currentReturnDistanceKm = distanceToDepot(distanceTable, currentCoordinate, depotCoordinate);
    for (int pointIndex : distanceTable.sortedPointIndexesBySource()[sourceIndex]) {
      if (!candidatePointIndexes[pointIndex]) {
        continue;
      }
      DeliveryPoint point = points.get(pointIndex);
      if (!canAssignPointToDriver(point, assignedWeightKg, assignedVolumeM3)) {
        continue;
      }

      Coordinate pointCoordinate = point.coordinate();
      double legDistanceKm = distanceTable.distancesBySource()[sourceIndex][pointIndex];
      double candidateReturnDistanceKm = distanceToDepot(distanceTable, pointCoordinate, depotCoordinate);
      double totalDeltaKm = legDistanceKm + candidateReturnDistanceKm - currentReturnDistanceKm;
      if (bestCandidate == null
          || totalDeltaKm < bestCandidate.totalDeltaKm() - 1e-9
          || (Math.abs(totalDeltaKm - bestCandidate.totalDeltaKm()) <= 1e-9
              && legDistanceKm < bestCandidate.legDistanceKm() - 1e-9)
          || (Math.abs(totalDeltaKm - bestCandidate.totalDeltaKm()) <= 1e-9
              && Math.abs(legDistanceKm - bestCandidate.legDistanceKm()) <= 1e-9
              && pointIndex < bestCandidate.pointIndex())) {
        bestCandidate = new AssignmentCandidate(driverIndex, pointIndex, legDistanceKm, totalDeltaKm);
      }
    }
    return bestCandidate;
  }

  private int findNearestPointWithinSubset(Coordinate currentCoordinate,
                                           Set<Integer> candidatePointIndexes,
                                           PlanningDistanceTable distanceTable,
                                           List<DeliveryPoint> points) {
    int sourceIndex = distanceTable.sourceIndexByCoordinate().getOrDefault(currentCoordinate, -1);
    if (sourceIndex >= 0) {
      for (int pointIndex : distanceTable.sortedPointIndexesBySource()[sourceIndex]) {
        if (candidatePointIndexes.contains(pointIndex)) {
          return pointIndex;
        }
      }
    }

    int bestPointIndex = candidatePointIndexes.iterator().next();
    double bestDistanceKm = haversineKm(currentCoordinate, points.get(bestPointIndex).coordinate());
    for (int pointIndex : candidatePointIndexes) {
      double candidateDistanceKm = haversineKm(currentCoordinate, points.get(pointIndex).coordinate());
      if (candidateDistanceKm < bestDistanceKm - 1e-9
          || (Math.abs(candidateDistanceKm - bestDistanceKm) <= 1e-9 && pointIndex < bestPointIndex)) {
        bestPointIndex = pointIndex;
        bestDistanceKm = candidateDistanceKm;
      }
    }
    return bestPointIndex;
  }

  private boolean canAssignPointToDriver(DeliveryPoint point,
                                         double assignedWeightKg,
                                         double assignedVolumeM3) {
    return assignedWeightKg + point.totalWeightKg() <= VEHICLE_MAX_WEIGHT_KG
        && assignedVolumeM3 + point.totalVolumeM3() <= VEHICLE_MAX_VOLUME_M3;
  }

  private List<Integer> optimizeTripByTwoOpt(List<Integer> orderedPointIndexes,
                                             PlanningDistanceTable distanceTable,
                                             List<DeliveryPoint> points,
                                             Coordinate tripStartCoordinate) {
    if (orderedPointIndexes == null || orderedPointIndexes.size() <= 2) {
      return orderedPointIndexes == null ? List.of() : List.copyOf(orderedPointIndexes);
    }

    List<Integer> optimized = new ArrayList<>(orderedPointIndexes);
    double optimizedDistanceKm = computeClosedTripDistance(optimized, tripStartCoordinate, distanceTable, points);
    boolean improved = true;
    while (improved) {
      improved = false;
      for (int left = 0; left < optimized.size() - 1; left++) {
        for (int right = left + 1; right < optimized.size(); right++) {
          List<Integer> candidate = new ArrayList<>(optimized);
          Collections.reverse(candidate.subList(left, right + 1));
          double candidateDistanceKm = computeClosedTripDistance(candidate, tripStartCoordinate, distanceTable, points);
          if (candidateDistanceKm < optimizedDistanceKm - 1e-9) {
            optimized = candidate;
            optimizedDistanceKm = candidateDistanceKm;
            improved = true;
          }
        }
      }
    }
    return List.copyOf(optimized);
  }

  private List<AssignedPoint> rebuildAssignedPoints(List<Integer> orderedPointIndexes,
                                                    Coordinate tripStartCoordinate,
                                                    PlanningDistanceTable distanceTable,
                                                    List<DeliveryPoint> points) {
    if (orderedPointIndexes == null || orderedPointIndexes.isEmpty()) {
      return List.of();
    }

    List<AssignedPoint> rebuilt = new ArrayList<>(orderedPointIndexes.size());
    Coordinate currentCoordinate = tripStartCoordinate;
    for (int pointIndex : orderedPointIndexes) {
      double legDistanceKm = distanceFromMatrix(distanceTable, currentCoordinate, pointIndex, points);
      rebuilt.add(new AssignedPoint(pointIndex, legDistanceKm));
      currentCoordinate = points.get(pointIndex).coordinate();
    }
    return List.copyOf(rebuilt);
  }

  private double computeClosedTripDistance(List<Integer> orderedPointIndexes,
                                           Coordinate tripStartCoordinate,
                                           PlanningDistanceTable distanceTable,
                                           List<DeliveryPoint> points) {
    if (orderedPointIndexes == null || orderedPointIndexes.isEmpty()) {
      return 0.0;
    }

    double totalDistanceKm = 0.0;
    Coordinate currentCoordinate = tripStartCoordinate;
    for (int pointIndex : orderedPointIndexes) {
      totalDistanceKm += distanceFromMatrix(distanceTable, currentCoordinate, pointIndex, points);
      currentCoordinate = points.get(pointIndex).coordinate();
    }
    totalDistanceKm += distanceToDepot(distanceTable, currentCoordinate, tripStartCoordinate);
    return totalDistanceKm;
  }

  private double distanceToDepot(PlanningDistanceTable distanceTable,
                                 Coordinate source,
                                 Coordinate depotCoordinate) {
    Integer sourceIndex = distanceTable.sourceIndexByCoordinate().get(source);
    if (sourceIndex == null) {
      return haversineKm(source, depotCoordinate);
    }
    return distanceTable.distanceToDepotBySource()[sourceIndex];
  }

  private record DeliveryPoint(Coordinate coordinate, List<Integer> orderIndexes, double totalWeightKg, double totalVolumeM3) {}

  private record AssignmentCandidate(int driverIndex, int pointIndex, double legDistanceKm, double totalDeltaKm) {
  }

  private record AssignedPoint(int pointIndex, double distanceFromPreviousKm) {
  }

  private record TripRebalanceMove(int sourceDriverIndex,
                                   int sourceTripIndex,
                                   int targetDriverIndex,
                                   int targetTripIndex,
                                   int pointIndex,
                                   List<AssignedPoint> optimizedSourceTrip,
                                   List<AssignedPoint> optimizedTargetTrip,
                                   double gainKm) {
  }

  private record PlanningDistanceTable(List<Coordinate> sources,
                                       Map<Coordinate, Integer> sourceIndexByCoordinate,
                                       double[][] distancesBySource,
                                       double[] distanceToDepotBySource,
                                       int[][] sortedPointIndexesBySource,
                                       boolean approximatePlanningDistances) {
  }

  private int[][] buildSortedPointIndexesBySource(double[][] distancesBySource) {
    int[][] sortedPointIndexesBySource = new int[distancesBySource.length][];
    for (int sourceIndex = 0; sourceIndex < distancesBySource.length; sourceIndex++) {
      Integer[] indexes = new Integer[distancesBySource[sourceIndex].length];
      for (int pointIndex = 0; pointIndex < indexes.length; pointIndex++) {
        indexes[pointIndex] = pointIndex;
      }
      final int currentSourceIndex = sourceIndex;
      java.util.Arrays.sort(indexes, (left, right) -> {
        double leftDistance = distancesBySource[currentSourceIndex][left];
        double rightDistance = distancesBySource[currentSourceIndex][right];
        if (Math.abs(leftDistance - rightDistance) <= 1e-9) {
          return Integer.compare(left, right);
        }
        return Double.compare(leftDistance, rightDistance);
      });

      int[] sortedIndexes = new int[indexes.length];
      for (int pointIndex = 0; pointIndex < indexes.length; pointIndex++) {
        sortedIndexes[pointIndex] = indexes[pointIndex];
      }
      sortedPointIndexesBySource[sourceIndex] = sortedIndexes;
    }
    return sortedPointIndexesBySource;
  }

  private double distanceFromMatrix(PlanningDistanceTable distanceTable,
                                    Coordinate source,
                                    int pointIndex,
                                    List<DeliveryPoint> points) {
    Integer sourceIndex = distanceTable.sourceIndexByCoordinate().get(source);
    if (sourceIndex == null) {
      return haversineKm(source, points.get(pointIndex).coordinate());
    }
    return distanceTable.distancesBySource()[sourceIndex][pointIndex];
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
    return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
  }

  private double utilizationPercent(double value, double capacity) {
    if (capacity <= 0.0) {
      return 0.0;
    }
    return Math.max(0.0, (value / capacity) * 100.0);
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
    return product.getWeightKg() * quantity;
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
    return product.getVolumeM3() * quantity;
  }

  private void validateTransportMetrics(List<Order> orders) {
    if (orders == null || orders.isEmpty()) {
      return;
    }

    Set<String> invalidProducts = new LinkedHashSet<>();
    for (Order order : orders) {
      List<OrderItem> items = order.getItems() == null ? List.of() : order.getItems();
      for (OrderItem item : items) {
        Product product = item.getProduct();
        if (hasValidTransportMetrics(product)) {
          continue;
        }
        String productName = product == null || product.getName() == null || product.getName().isBlank()
            ? "Товар без названия"
            : product.getName();
        invalidProducts.add(productName);
      }
    }

    if (!invalidProducts.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Невозможно построить транспортный план: для товаров не заданы обязательные метрики weightKg и volumeM3: "
              + String.join(", ", invalidProducts)
      );
    }
  }

  private boolean hasValidTransportMetrics(Product product) {
    return product != null
        && product.getWeightKg() != null
        && product.getWeightKg() > 0.0
        && product.getVolumeM3() != null
        && product.getVolumeM3() > 0.0;
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

  private record DriverPlanNode(User driver,
                                Coordinate startCoordinate) {
  }

  private record RouteStop(int orderIndex, int sequence, int tripNumber, double distanceFromPreviousKm) {
  }

  private record DriverTripPlan(int tripNumber,
                                int assignedOrders,
                                double distanceKm,
                                double totalWeightKg,
                                double totalVolumeM3,
                                boolean returnsToDepot) {
  }

  private record DriverRoutePlan(int driverIndex,
                                 List<RouteStop> stops,
                                 List<DriverTripPlan> trips,
                                 double distanceKm,
                                 double totalWeightKg,
                                 double totalVolumeM3) {
  }

  private record PreviewRouteDraft(int driverIndex,
                                   DriverPlanNode driverNode,
                                   DriverRoutePlan route,
                                   List<AutoAssignRoutePointResponse> points) {
  }

  private record TransportPlan(List<DriverRoutePlan> routes,
                               double totalDistanceKm,
                               boolean approximatePlanningDistances) {
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
