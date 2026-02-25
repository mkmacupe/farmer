package com.farm.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditEventListenerTest {
  private AuditLogRepository auditLogRepository;
  private AuditActorResolver auditActorResolver;
  private AuditEventListener listener;

  @BeforeEach
  void setUp() {
    auditLogRepository = mock(AuditLogRepository.class);
    auditActorResolver = mock(AuditActorResolver.class);
    listener = new AuditEventListener(auditLogRepository, auditActorResolver);
  }

  @Test
  void onAuditEventUsesResolvedActorAndNormalizesValues() {
    when(auditActorResolver.resolveCurrentActor()).thenReturn(new AuditActor("  manager  ", 7L, "  manager "));
    AuditTrailEvent event = new AuditTrailEvent(
        "  ",
        null,
        "  ",
        "x".repeat(2200),
        null
    );

    listener.onAuditEvent(event);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog log = captor.getValue();
    assertThat(log.getActionType()).isEqualTo("UNKNOWN");
    assertThat(log.getEntityType()).isEqualTo("UNKNOWN");
    assertThat(log.getEntityId()).isNull();
    assertThat(log.getActorUsername()).isEqualTo("manager");
    assertThat(log.getActorUserId()).isEqualTo(7L);
    assertThat(log.getActorRole()).isEqualTo("manager");
    assertThat(log.getDetails()).hasSize(2000);
    assertThat(log.getCreatedAt()).isNotNull();
  }

  @Test
  void onAuditEventPrefersActorOverrideAndTrimsDetails() {
    when(auditActorResolver.resolveCurrentActor()).thenReturn(new AuditActor("resolver", 1L, "MANAGER"));
    AuditTrailEvent event = AuditTrailEvent.withActor(
        "ORDER_CREATED",
        "ORDER",
        "  101  ",
        " details ",
        new AuditActor("  ", null, "  ")
    );

    listener.onAuditEvent(event);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog log = captor.getValue();
    assertThat(log.getActionType()).isEqualTo("ORDER_CREATED");
    assertThat(log.getEntityType()).isEqualTo("ORDER");
    assertThat(log.getEntityId()).isEqualTo("101");
    assertThat(log.getActorUsername()).isEqualTo("anonymous");
    assertThat(log.getActorUserId()).isNull();
    assertThat(log.getActorRole()).isNull();
    assertThat(log.getDetails()).isEqualTo("details");
  }

  @Test
  void onAuditEventKeepsNullDetailsWhenNotProvided() {
    when(auditActorResolver.resolveCurrentActor()).thenReturn(new AuditActor("manager", 1L, null));
    AuditTrailEvent event = new AuditTrailEvent("ACTION", "ENTITY", null, null, null);

    listener.onAuditEvent(event);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(captor.capture());
    AuditLog log = captor.getValue();
    assertThat(log.getEntityId()).isNull();
    assertThat(log.getDetails()).isNull();
  }
}
