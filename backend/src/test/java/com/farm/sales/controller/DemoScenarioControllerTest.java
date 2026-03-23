package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.DemoClearOrdersResponse;
import com.farm.sales.dto.DemoResetResponse;
import com.farm.sales.service.DemoScenarioService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DemoScenarioControllerTest {
  private DemoScenarioService demoScenarioService;
  private DemoScenarioController controller;

  @BeforeEach
  void setUp() {
    demoScenarioService = mock(DemoScenarioService.class);
    controller = new DemoScenarioController(demoScenarioService);
  }

  @Test
  void resetDelegatesToServiceAndReturnsOk() {
    DemoResetResponse response = new DemoResetResponse(
        "Транспортный учебный сценарий Farm Sales",
        Instant.parse("2026-03-06T20:00:00Z"),
        35L,
        30L,
        200L,
        30L,
        30L,
        30L,
        List.of("step-1", "step-2")
    );
    when(demoScenarioService.resetDemoScenario()).thenReturn(response);

    var httpResponse = controller.reset();

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(demoScenarioService).resetDemoScenario();
  }

  @Test
  void clearOrdersDelegatesToServiceAndReturnsOk() {
    DemoClearOrdersResponse response = new DemoClearOrdersResponse(
        "Заказы очищены, точки магазинов сохранены",
        Instant.parse("2026-03-06T20:05:00Z"),
        30L,
        0L,
        0L
    );
    when(demoScenarioService.clearOrdersKeepingStorePoints()).thenReturn(response);

    var httpResponse = controller.clearOrders();

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(demoScenarioService).clearOrdersKeepingStorePoints();
  }

  @Test
  void scenarioControllerUsesScenarioApiBasePath() {
    RequestMapping mapping = DemoScenarioController.class.getAnnotation(RequestMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(Arrays.asList(mapping.value())).containsExactly("/api/scenario");
  }

  @Test
  void clearOrdersKeepsClearOrdersSubpath() throws NoSuchMethodException {
    PostMapping mapping = DemoScenarioController.class
        .getDeclaredMethod("clearOrders")
        .getAnnotation(PostMapping.class);

    assertThat(mapping).isNotNull();
    assertThat(Arrays.asList(mapping.value())).containsExactly("/clear-orders");
  }
}
