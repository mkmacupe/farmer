package com.farm.sales.controller;

import com.farm.sales.dto.GeoLookupResponse;
import com.farm.sales.service.GeocodingService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/geo")
public class GeoController {
  private final GeocodingService geocodingService;

  public GeoController(GeocodingService geocodingService) {
    this.geocodingService = geocodingService;
  }

  @GetMapping("/lookup")
  public ResponseEntity<List<GeoLookupResponse>> lookup(
      @RequestParam @NotBlank(message = "Поисковая строка не должна быть пустой") String q,
      @RequestParam(defaultValue = "5")
      @Min(value = 1, message = "Параметр limit должен быть не меньше 1")
      @Max(value = 10, message = "Параметр limit должен быть не больше 10")
      int limit
  ) {
    return ResponseEntity.ok(geocodingService.search(q, limit));
  }

  @GetMapping("/reverse")
  public ResponseEntity<GeoLookupResponse> reverse(
      @RequestParam
      @DecimalMin(value = "-90.0", inclusive = true, message = "Широта должна быть не меньше -90")
      @DecimalMax(value = "90.0", inclusive = true, message = "Широта должна быть не больше 90")
      BigDecimal lat,
      @RequestParam
      @DecimalMin(value = "-180.0", inclusive = true, message = "Долгота должна быть не меньше -180")
      @DecimalMax(value = "180.0", inclusive = true, message = "Долгота должна быть не больше 180")
      BigDecimal lon
  ) {
    return ResponseEntity.ok(geocodingService.reverse(lat, lon));
  }
}
