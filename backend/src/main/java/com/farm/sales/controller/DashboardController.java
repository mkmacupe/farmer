package com.farm.sales.controller;

import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.service.DashboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/summary")
  public ResponseEntity<DashboardSummaryResponse> summary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);

    return ResponseEntity.ok(dashboardService.getSummary(fromInstant, toInstant));
  }
}
