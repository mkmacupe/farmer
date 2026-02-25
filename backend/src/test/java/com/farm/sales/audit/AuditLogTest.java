package com.farm.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditLogTest {
  @Test
  void accessorsReadAndWriteAllFields() {
    AuditLog log = new AuditLog();
    log.setId(10L);
    log.setActionType("ORDER_CREATED");
    log.setEntityType("ORDER");
    log.setEntityId("101");
    log.setActorUsername("manager");
    log.setActorUserId(8L);
    log.setActorRole("MANAGER");
    log.setDetails("details");
    log.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

    assertThat(log.getId()).isEqualTo(10L);
    assertThat(log.getActionType()).isEqualTo("ORDER_CREATED");
    assertThat(log.getEntityType()).isEqualTo("ORDER");
    assertThat(log.getEntityId()).isEqualTo("101");
    assertThat(log.getActorUsername()).isEqualTo("manager");
    assertThat(log.getActorUserId()).isEqualTo(8L);
    assertThat(log.getActorRole()).isEqualTo("MANAGER");
    assertThat(log.getDetails()).isEqualTo("details");
    assertThat(log.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
  }
}

