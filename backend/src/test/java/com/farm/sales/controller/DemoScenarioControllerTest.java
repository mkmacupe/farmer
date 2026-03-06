package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.DemoResetResponse;
import com.farm.sales.service.DemoScenarioService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
        "Демо-сценарий защиты Farm Sales",
        Instant.parse("2026-03-06T20:00:00Z"),
        8L,
        3L,
        20L,
        28L,
        25L,
        25L,
        List.of("step-1", "step-2")
    );
    when(demoScenarioService.resetDemoScenario()).thenReturn(response);

    var httpResponse = controller.reset();

    assertThat(httpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(httpResponse.getBody()).isEqualTo(response);
    verify(demoScenarioService).resetDemoScenario();
  }
}
