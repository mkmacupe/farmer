package com.farm.sales.audit;

public record AuditTrailEvent(
    String actionType,
    String entityType,
    String entityId,
    String details,
    AuditActor actorOverride
) {
  public static AuditTrailEvent of(String actionType, String entityType, String entityId, String details) {
    return new AuditTrailEvent(actionType, entityType, entityId, details, null);
  }

  public static AuditTrailEvent withActor(String actionType,
                                          String entityType,
                                          String entityId,
                                          String details,
                                          AuditActor actor) {
    return new AuditTrailEvent(actionType, entityType, entityId, details, actor);
  }
}
