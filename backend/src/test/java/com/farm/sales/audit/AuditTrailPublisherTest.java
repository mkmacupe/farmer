package com.farm.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class AuditTrailPublisherTest {
  private ApplicationEventPublisher applicationEventPublisher;
  private AuditTrailPublisher publisher;

  @BeforeEach
  void setUp() {
    applicationEventPublisher = mock(ApplicationEventPublisher.class);
    publisher = new AuditTrailPublisher(applicationEventPublisher);
  }

  @Test
  void publishBuildsEventWithoutActorOverride() {
    publisher.publish("ORDER_CREATED", "ORDER", "101", "items=2");

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(AuditTrailEvent.class);
    AuditTrailEvent event = (AuditTrailEvent) captor.getValue();
    assertThat(event.actionType()).isEqualTo("ORDER_CREATED");
    assertThat(event.entityType()).isEqualTo("ORDER");
    assertThat(event.entityId()).isEqualTo("101");
    assertThat(event.details()).isEqualTo("items=2");
    assertThat(event.actorOverride()).isNull();
  }

  @Test
  void publishWithActorBuildsEventWithOverride() {
    AuditActor actor = new AuditActor("manager", 5L, "MANAGER");

    publisher.publishWithActor("AUTH_LOGIN_SUCCESS", "AUTH", "manager", "role=MANAGER", actor);

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    AuditTrailEvent event = (AuditTrailEvent) captor.getValue();
    assertThat(event.actionType()).isEqualTo("AUTH_LOGIN_SUCCESS");
    assertThat(event.actorOverride()).isEqualTo(actor);
  }

  @Test
  void staticFactoriesCreateExpectedEvents() {
    AuditTrailEvent base = AuditTrailEvent.of("A", "B", "C", "D");
    AuditActor actor = new AuditActor("user", 1L, "DIRECTOR");
    AuditTrailEvent withActor = AuditTrailEvent.withActor("A2", "B2", "C2", "D2", actor);

    assertThat(base.actorOverride()).isNull();
    assertThat(withActor.actorOverride()).isEqualTo(actor);
  }
}

