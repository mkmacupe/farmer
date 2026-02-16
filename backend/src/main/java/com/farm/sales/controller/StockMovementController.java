package com.farm.sales.controller;

import com.farm.sales.dto.StockMovementResponse;
import com.farm.sales.service.StockMovementService;
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
@RequestMapping("/api/stock-movements")
public class StockMovementController {
  private final StockMovementService stockMovementService;

  public StockMovementController(StockMovementService stockMovementService) {
    this.stockMovementService = stockMovementService;
  }

  @GetMapping
  public ResponseEntity<List<StockMovementResponse>> list(
      @RequestParam(required = false) Long productId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false, defaultValue = "200") Integer limit
  ) {
    Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
    return ResponseEntity.ok(stockMovementService.list(productId, fromInstant, toInstant, limit));
  }
}
