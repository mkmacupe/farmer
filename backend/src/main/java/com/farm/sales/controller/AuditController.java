package com.farm.sales.controller;

import com.farm.sales.audit.AuditLogQueryService;
import com.farm.sales.dto.AuditLogResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
  private final AuditLogQueryService auditLogQueryService;

  public AuditController(AuditLogQueryService auditLogQueryService) {
    this.auditLogQueryService = auditLogQueryService;
  }

  @GetMapping("/logs")
  public ResponseEntity<List<AuditLogResponse>> latestLogs() {
    return ResponseEntity.ok(auditLogQueryService.latest());
  }
}
