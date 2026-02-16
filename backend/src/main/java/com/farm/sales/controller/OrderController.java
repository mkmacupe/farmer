package com.farm.sales.controller;

import com.farm.sales.dto.DriverAssignRequest;
import com.farm.sales.dto.OrderCreateRequest;
import com.farm.sales.dto.OrderResponse;
import com.farm.sales.dto.OrderTimelineEventResponse;
import com.farm.sales.model.Role;
import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.OrderService;
import com.farm.sales.service.OrderTimelineService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orderService;
  private final OrderTimelineService orderTimelineService;
  private final JwtClaimsReader jwtClaimsReader;

  public OrderController(OrderService orderService,
                         OrderTimelineService orderTimelineService,
                         JwtClaimsReader jwtClaimsReader) {
    this.orderService = orderService;
    this.orderTimelineService = orderTimelineService;
    this.jwtClaimsReader = jwtClaimsReader;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody OrderCreateRequest request) {
    Long userId = jwtClaimsReader.requireUserId(jwt);
    OrderResponse response = orderService.createOrder(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping("/{id}/repeat")
  public ResponseEntity<OrderResponse> repeat(@PathVariable Long id,
                                              @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.status(HttpStatus.CREATED).body(orderService.repeatOrder(jwtClaimsReader.requireUserId(jwt), id));
  }

  @GetMapping("/my")
  public ResponseEntity<List<OrderResponse>> myOrders(@AuthenticationPrincipal Jwt jwt) {
    Long userId = jwtClaimsReader.requireUserId(jwt);
    return ResponseEntity.ok(orderService.getOrdersForRole(Role.DIRECTOR, userId));
  }

  @GetMapping("/assigned")
  public ResponseEntity<List<OrderResponse>> assignedOrders(@AuthenticationPrincipal Jwt jwt) {
    Long userId = jwtClaimsReader.requireUserId(jwt);
    return ResponseEntity.ok(orderService.getOrdersForRole(Role.DRIVER, userId));
  }

  @GetMapping
  public ResponseEntity<List<OrderResponse>> allOrders(@AuthenticationPrincipal Jwt jwt) {
    Role role = jwtClaimsReader.requireSingleRole(jwt);
    Long userId = jwtClaimsReader.requireUserId(jwt);
    return ResponseEntity.ok(orderService.getOrdersForRole(role, userId));
  }

  @PostMapping("/{id}/approve")
  public ResponseEntity<OrderResponse> approve(@PathVariable Long id,
                                               @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(orderService.approveOrder(id, jwtClaimsReader.requireUserId(jwt)));
  }

  @PostMapping("/{id}/assign-driver")
  public ResponseEntity<OrderResponse> assignDriver(@PathVariable Long id,
                                                    @AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody DriverAssignRequest request) {
    return ResponseEntity.ok(orderService.assignDriver(id, jwtClaimsReader.requireUserId(jwt), request.driverId()));
  }

  @PostMapping("/{id}/deliver")
  public ResponseEntity<OrderResponse> deliver(@PathVariable Long id,
                                               @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(orderService.markDelivered(id, jwtClaimsReader.requireUserId(jwt)));
  }

  @GetMapping("/{id}/timeline")
  public ResponseEntity<List<OrderTimelineEventResponse>> timeline(@PathVariable Long id,
                                                                   @AuthenticationPrincipal Jwt jwt) {
    Role role = jwtClaimsReader.requireSingleRole(jwt);
    Long userId = jwtClaimsReader.requireUserId(jwt);
    return ResponseEntity.ok(orderTimelineService.getTimeline(id, role, userId));
  }
}
