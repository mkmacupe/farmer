package com.farm.sales.controller;

import com.farm.sales.dto.DashboardCategoryInsightResponse;
import com.farm.sales.dto.DashboardSummaryResponse;
import com.farm.sales.dto.DashboardTrendResponse;
import com.farm.sales.service.DashboardService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
    DateRange range = toInstantRange(from, to);
    return ResponseEntity.ok(dashboardService.getSummary(range.from(), range.to()));
  }

  @GetMapping("/trends")
  public ResponseEntity<DashboardTrendResponse> trends(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    DateRange range = toInstantRange(from, to);
    return ResponseEntity.ok(dashboardService.getTrends(range.from(), range.to()));
  }

  @GetMapping("/categories")
  public ResponseEntity<List<DashboardCategoryInsightResponse>> categories(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
  ) {
    DateRange range = toInstantRange(from, to);
    return ResponseEntity.ok(dashboardService.getCategoryInsights(range.from(), range.to()));
  }

  private DateRange toInstantRange(LocalDate from, LocalDate to) {
    Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    return new DateRange(fromInstant, toInstant);
  }

  private record DateRange(Instant from, Instant to) {
  }
}
