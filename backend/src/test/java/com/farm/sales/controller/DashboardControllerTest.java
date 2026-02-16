package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.service.DashboardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class DashboardControllerTest {
  private DashboardService dashboardService;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    dashboardService = mock(DashboardService.class);
    controller = new DashboardController(dashboardService);
  }

  @Test
  void summaryDelegatesToService() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-31T23:59:59.999Z");
    DashboardSummaryResponse response = new DashboardSummaryResponse(
        from,
        to,
        5,
        2,
        new BigDecimal("150.00"),
        new BigDecimal("30.00"),
        List.of()
    );
    when(dashboardService.getSummary(any(), any())).thenReturn(response);

    var http = controller.summary(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

    assertThat(http.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(http.getBody()).isEqualTo(response);
    ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(dashboardService).getSummary(fromCaptor.capture(), toCaptor.capture());
    assertThat(fromCaptor.getValue()).isEqualTo(from);
    assertThat(toCaptor.getValue()).isEqualTo(to);
  }
}
