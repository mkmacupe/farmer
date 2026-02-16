package com.farm.sales.audit;

import com.farm.sales.dto.AuditLogResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditLogQueryService {
  private final AuditLogRepository auditLogRepository;

  public AuditLogQueryService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public List<AuditLogResponse> latest() {
    return auditLogRepository.findTop200ByOrderByCreatedAtDesc().stream()
        .map(log -> new AuditLogResponse(
            log.getId(),
            log.getActionType(),
            log.getEntityType(),
            log.getEntityId(),
            log.getActorUsername(),
            log.getActorUserId(),
            log.getActorRole(),
            log.getDetails(),
            log.getCreatedAt()
        ))
        .toList();
  }
}
