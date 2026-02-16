package com.farm.sales.audit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AuditTrailPublisher {
  private final ApplicationEventPublisher applicationEventPublisher;

  public AuditTrailPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public void publish(String actionType, String entityType, String entityId, String details) {
    applicationEventPublisher.publishEvent(AuditTrailEvent.of(actionType, entityType, entityId, details));
  }

  public void publishWithActor(String actionType,
                               String entityType,
                               String entityId,
                               String details,
                               AuditActor actor) {
    applicationEventPublisher.publishEvent(AuditTrailEvent.withActor(actionType, entityType, entityId, details, actor));
  }
}
