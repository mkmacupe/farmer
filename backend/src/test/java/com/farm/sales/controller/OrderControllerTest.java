package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.AutoAssignApproveItemRequest;
import com.farm.sales.dto.AutoAssignApproveRequest;
import com.farm.sales.dto.AutoAssignDriverRouteResponse;
import com.farm.sales.dto.AutoAssignItemResponse;
import com.farm.sales.dto.AutoAssignPreviewResponse;
import com.farm.sales.dto.AutoAssignResultResponse;
import com.farm.sales.dto.AutoAssignRoutePointResponse;
import com.farm.sales.dto.DriverAssignRequest;
import com.farm.sales.dto.OrderCreateRequest;
import com.farm.sales.dto.OrderItemRequest;
import com.farm.sales.dto.OrderResponse;
import com.farm.sales.dto.OrderTimelineEventResponse;
import com.farm.sales.model.Role;
import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.OrderService;
import com.farm.sales.service.OrderTimelineService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class OrderControllerTest {
  private OrderService orderService;
  private OrderTimelineService orderTimelineService;
  private JwtClaimsReader jwtClaimsReader;
  private OrderController controller;

  @BeforeEach
  void setUp() {
    orderService = mock(OrderService.class);
    orderTimelineService = mock(OrderTimelineService.class);
    jwtClaimsReader = mock(JwtClaimsReader.class);
    controller = new OrderController(orderService, orderTimelineService, jwtClaimsReader);
  }

  @Test
  void createUsesUserIdFromStringClaim() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(42L);

    OrderCreateRequest request = new OrderCreateRequest(7L, List.of(new OrderItemRequest(1L, 2)));
    when(orderService.createOrder(eq(42L), any(OrderCreateRequest.class))).thenReturn(sampleOrder());

    var response = controller.create(jwt, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(orderService).createOrder(42L, request);
  }

  @Test
  void createReturnsUnauthorizedWhenUserIdClaimIsInvalid() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "В токене отсутствует или некорректен userId"));

    OrderCreateRequest request = new OrderCreateRequest(7L, List.of(new OrderItemRequest(1L, 1)));

    assertThatThrownBy(() -> controller.create(jwt, request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

  @Test
  void allOrdersReturnsUnauthorizedWhenRoleClaimIsUnknown() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(7L);
    when(jwtClaimsReader.requireSingleRole(jwt))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректная роль в токене"));

    assertThatThrownBy(() -> controller.allOrders(jwt))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

  @Test
  void allOrdersReturnsUnauthorizedWhenMultipleRolesPresent() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(7L);
    when(jwtClaimsReader.requireSingleRole(jwt))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "В токене должна быть ровно одна роль"));

    assertThatThrownBy(() -> controller.allOrders(jwt))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

  @Test
  void assignDriverDelegatesToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(18L);
    when(orderService.assignDriver(101L, 18L, 31L)).thenReturn(sampleOrder());

    var response = controller.assignDriver(101L, jwt, new DriverAssignRequest(31L));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(orderService).assignDriver(101L, 18L, 31L);
  }

  @Test
  void autoAssignDelegatesToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(18L);
    AutoAssignResultResponse result = new AutoAssignResultResponse(
        2,
        2,
        0,
        11.3,
        List.of(new AutoAssignItemResponse(1L, 31L, "Driver One", 4.5))
    );
    when(orderService.autoAssignApprovedOrders(18L)).thenReturn(result);

    var response = controller.autoAssign(jwt);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(result);
    verify(orderService).autoAssignApprovedOrders(18L);
  }

  @Test
  void autoAssignPreviewAndApproveDelegateToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(18L);

    AutoAssignPreviewResponse preview = new AutoAssignPreviewResponse(
        "Могилёв, тестовая точка",
        53.89,
        30.33,
        2,
        2,
        0,
        12.1,
        List.of(new AutoAssignDriverRouteResponse(
            31L,
            "Driver One",
            2,
            12.1,
            10.5,
            0.05,
            List.of(new AutoAssignRoutePointResponse(1L, "Address", 53.91, 30.34, 1, 4.5))
        ))
    );
    when(orderService.previewAutoAssignPlan(18L)).thenReturn(preview);

    AutoAssignApproveRequest request = new AutoAssignApproveRequest(
        List.of(new AutoAssignApproveItemRequest(1L, 31L, 1))
    );
    AutoAssignResultResponse approved = new AutoAssignResultResponse(
        2,
        2,
        0,
        12.1,
        List.of(new AutoAssignItemResponse(1L, 31L, "Driver One", 4.5))
    );
    when(orderService.approveAutoAssignPlan(18L, request)).thenReturn(approved);

    var previewResponse = controller.autoAssignPreview(jwt);
    var approveResponse = controller.approveAutoAssign(jwt, request);

    assertThat(previewResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(previewResponse.getBody()).isEqualTo(preview);
    assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(approveResponse.getBody()).isEqualTo(approved);
    verify(orderService).previewAutoAssignPlan(18L);
    verify(orderService).approveAutoAssignPlan(18L, request);
  }

  @Test
  void timelineDelegatesToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(42L);
    when(jwtClaimsReader.requireSingleRole(jwt)).thenReturn(Role.MANAGER);

    List<OrderTimelineEventResponse> events = List.of(
        new OrderTimelineEventResponse(1L, 101L, "CREATED", "APPROVED", "manager", 8L, "MANAGER", "", Instant.now())
    );
    when(orderTimelineService.getTimeline(101L, Role.MANAGER, 42L)).thenReturn(events);

    var response = controller.timeline(101L, jwt);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactlyElementsOf(events);
    verify(orderTimelineService).getTimeline(101L, Role.MANAGER, 42L);
  }

  @Test
  void repeatDelegatesToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(42L);
    when(orderService.repeatOrder(42L, 77L)).thenReturn(sampleOrder());

    var response = controller.repeat(77L, jwt);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(orderService).repeatOrder(42L, 77L);
  }

  @Test
  void myOrdersAndAssignedOrdersDelegateToService() {
    Jwt directorJwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(directorJwt)).thenReturn(42L);
    when(orderService.getOrdersForRole(Role.DIRECTOR, 42L)).thenReturn(List.of(sampleOrder()));

    var myOrders = controller.myOrders(directorJwt);

    assertThat(myOrders.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(myOrders.getBody()).hasSize(1);
    verify(orderService).getOrdersForRole(Role.DIRECTOR, 42L);

    Jwt driverJwt = mock(Jwt.class);
    when(jwtClaimsReader.requireUserId(driverJwt)).thenReturn(55L);
    when(orderService.getOrdersForRole(Role.DRIVER, 55L)).thenReturn(List.of(sampleOrder()));

    var assigned = controller.assignedOrders(driverJwt);

    assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(assigned.getBody()).hasSize(1);
    verify(orderService).getOrdersForRole(Role.DRIVER, 55L);
  }

  @Test
  void allOrdersApproveAndDeliverDelegateToService() {
    Jwt jwt = mock(Jwt.class);
    when(jwtClaimsReader.requireSingleRole(jwt)).thenReturn(Role.MANAGER);
    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(10L);
    when(orderService.getOrdersForRole(Role.MANAGER, 10L)).thenReturn(List.of(sampleOrder()));
    when(orderService.approveOrder(101L, 10L)).thenReturn(sampleOrder());
    when(orderService.markDelivered(101L, 10L)).thenReturn(sampleOrder());

    var allOrders = controller.allOrders(jwt);
    var approved = controller.approve(101L, jwt);
    var delivered = controller.deliver(101L, jwt);

    assertThat(allOrders.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(allOrders.getBody()).hasSize(1);
    assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(delivered.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(orderService).getOrdersForRole(Role.MANAGER, 10L);
    verify(orderService).approveOrder(101L, 10L);
    verify(orderService).markDelivered(101L, 10L);
  }

  private OrderResponse sampleOrder() {
    Instant now = Instant.now();
    return new OrderResponse(
        101L,
        42L,
        "Director",
        7L,
        "Kyiv, Khreshchatyk 1",
        new BigDecimal("50.4501000"),
        new BigDecimal("30.5234000"),
        31L,
        "Driver One",
        "ASSIGNED",
        now,
        now,
        now,
        now,
        null,
        new BigDecimal("99.99"),
        List.of()
    );
  }
}
