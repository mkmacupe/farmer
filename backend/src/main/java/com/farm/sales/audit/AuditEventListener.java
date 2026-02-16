package com.farm.sales.audit;

import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventListener {
  private static final int DETAILS_MAX_LENGTH = 2000;
  private final AuditLogRepository auditLogRepository;
  private final AuditActorResolver auditActorResolver;

  public AuditEventListener(AuditLogRepository auditLogRepository, AuditActorResolver auditActorResolver) {
    this.auditLogRepository = auditLogRepository;
    this.auditActorResolver = auditActorResolver;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAuditEvent(AuditTrailEvent event) {
    AuditActor actor = event.actorOverride() != null
        ? event.actorOverride()
        : auditActorResolver.resolveCurrentActor();

    AuditLog log = new AuditLog();
    log.setActionType(nonBlank(event.actionType(), "UNKNOWN"));
    log.setEntityType(nonBlank(event.entityType(), "UNKNOWN"));
    log.setEntityId(blankToNull(event.entityId()));
    log.setActorUsername(nonBlank(actor.username(), "anonymous"));
    log.setActorUserId(actor.userId());
    log.setActorRole(blankToNull(actor.role()));
    log.setDetails(limitLength(blankToNull(event.details()), DETAILS_MAX_LENGTH));
    log.setCreatedAt(Instant.now());

    auditLogRepository.save(log);
  }

  private String nonBlank(String value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? fallback : normalized;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String limitLength(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
