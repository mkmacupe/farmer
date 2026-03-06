package com.farm.sales.controller;

import com.farm.sales.dto.DemoResetResponse;
import com.farm.sales.service.DemoScenarioService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoScenarioController {
  // Dedicated endpoint for returning the coursework demo to a known-good defense state.
  private final DemoScenarioService demoScenarioService;

  public DemoScenarioController(DemoScenarioService demoScenarioService) {
    this.demoScenarioService = demoScenarioService;
  }

  @PostMapping("/reset")
  public ResponseEntity<DemoResetResponse> reset() {
    return ResponseEntity.ok(demoScenarioService.resetDemoScenario());
  }
}
