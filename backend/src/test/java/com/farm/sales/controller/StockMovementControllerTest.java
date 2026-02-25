package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.StockMovementResponse;
import com.farm.sales.service.StockMovementService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class StockMovementControllerTest {
  private StockMovementService stockMovementService;
  private StockMovementController controller;

  @BeforeEach
  void setUp() {
    stockMovementService = mock(StockMovementService.class);
    controller = new StockMovementController(stockMovementService);
  }

  @Test
  void listDelegatesToService() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to = Instant.parse("2026-01-31T23:59:59.999Z");
    List<StockMovementResponse> payload = List.of(
        new StockMovementResponse(
            1L,
            10L,
            "Молоко",
            100L,
            "OUTBOUND",
            -2,
            "ORDER_CREATED",
            "customer",
            7L,
            "CUSTOMER",
            Instant.now()
        )
    );
    when(stockMovementService.list(any(), any(), any(), anyInt())).thenReturn(payload);

    var response = controller.list(10L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 50);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactlyElementsOf(payload);
    ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
    verify(stockMovementService).list(org.mockito.ArgumentMatchers.eq(10L), fromCaptor.capture(), toCaptor.capture(), org.mockito.ArgumentMatchers.eq(50));
    assertThat(fromCaptor.getValue()).isEqualTo(from);
    assertThat(toCaptor.getValue()).isEqualTo(to);
  }

  @Test
  void listPassesNullDatesAsNullInstants() {
    when(stockMovementService.list(any(), any(), any(), anyInt())).thenReturn(List.of());

    var response = controller.list(null, null, null, 200);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEmpty();
    verify(stockMovementService).list(null, null, null, 200);
  }
}
