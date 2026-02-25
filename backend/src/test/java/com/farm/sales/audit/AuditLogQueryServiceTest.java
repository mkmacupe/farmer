package com.farm.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.AuditLogResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditLogQueryServiceTest {
  private AuditLogRepository auditLogRepository;
  private AuditLogQueryService service;

  @BeforeEach
  void setUp() {
    auditLogRepository = mock(AuditLogRepository.class);
    service = new AuditLogQueryService(auditLogRepository);
  }

  @Test
  void latestMapsEntityToResponse() {
    AuditLog log = new AuditLog();
    log.setId(1L);
    log.setActionType("ORDER_CREATED");
    log.setEntityType("ORDER");
    log.setEntityId("101");
    log.setActorUsername("manager");
    log.setActorUserId(5L);
    log.setActorRole("MANAGER");
    log.setDetails("ok");
    log.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    when(auditLogRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

    List<AuditLogResponse> response = service.latest();

    assertThat(response).containsExactly(new AuditLogResponse(
        1L,
        "ORDER_CREATED",
        "ORDER",
        "101",
        "manager",
        5L,
        "MANAGER",
        "ok",
        Instant.parse("2026-01-01T00:00:00Z")
    ));
  }
}

