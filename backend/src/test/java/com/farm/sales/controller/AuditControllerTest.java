package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditLogQueryService;
import com.farm.sales.dto.AuditLogResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuditControllerTest {
  private AuditLogQueryService auditLogQueryService;
  private AuditController controller;

  @BeforeEach
  void setUp() {
    auditLogQueryService = mock(AuditLogQueryService.class);
    controller = new AuditController(auditLogQueryService);
  }

  @Test
  void latestLogsReturnsOk() {
    List<AuditLogResponse> payload = List.of(
        new AuditLogResponse(
            1L,
            "ORDER_CREATED",
            "ORDER",
            "101",
            "customer",
            10L,
            "CUSTOMER",
            "items=1,total=90",
            Instant.now()
        )
    );
    when(auditLogQueryService.latest()).thenReturn(payload);

    var response = controller.latestLogs();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(payload);
    verify(auditLogQueryService).latest();
  }
}
