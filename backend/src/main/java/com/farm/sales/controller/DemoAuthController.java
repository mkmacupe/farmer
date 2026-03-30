package com.farm.sales.controller;

import com.farm.sales.dto.AuthResponse;
import com.farm.sales.dto.DemoLoginRequest;
import com.farm.sales.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoAuthController {
  private final AuthService authService;

  public DemoAuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/seed-login")
  public ResponseEntity<AuthResponse> demoLogin(@Valid @RequestBody DemoLoginRequest request) {
    return ResponseEntity.ok(authService.demoLogin(request.username(), request.password()));
  }
}
