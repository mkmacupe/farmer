package com.farm.sales.controller;

import com.farm.sales.service.ReportService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/orders")
  public ResponseEntity<byte[]> ordersReport(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) String status) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Параметр 'from' должен быть меньше или равен 'to'");
    }

    Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1) : null;

    byte[] report = reportService.buildOrdersReport(fromInstant, toInstant, status);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=otchet-zakazy.xlsx")
        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(report);
  }
}
