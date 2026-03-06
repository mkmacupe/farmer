package com.farm.sales.controller;

import com.farm.sales.dto.AuthRequest;
import com.farm.sales.dto.AuthResponse;
import com.farm.sales.dto.DemoLoginRequest;
import com.farm.sales.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final boolean demoEnabled;

  public AuthController(AuthService authService,
                        @Value("${app.demo.enabled:false}") boolean demoEnabled) {
    this.authService = authService;
    this.demoEnabled = demoEnabled;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/demo-login")
  public ResponseEntity<AuthResponse> demoLogin(@Valid @RequestBody DemoLoginRequest request) {
    return ResponseEntity.ok(authService.demoLogin(request.username(), request.password(), demoEnabled));
  }
}
