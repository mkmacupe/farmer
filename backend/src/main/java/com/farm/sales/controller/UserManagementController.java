package com.farm.sales.controller;

import com.farm.sales.dto.CreateDirectorRequest;
import com.farm.sales.dto.UserSummaryResponse;
import com.farm.sales.service.UserManagementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {
  private final UserManagementService userManagementService;

  public UserManagementController(UserManagementService userManagementService) {
    this.userManagementService = userManagementService;
  }

  @PostMapping("/directors")
  public ResponseEntity<UserSummaryResponse> createDirector(@Valid @RequestBody CreateDirectorRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userManagementService.createDirector(request));
  }

  @GetMapping("/directors")
  public ResponseEntity<List<UserSummaryResponse>> directors() {
    return ResponseEntity.ok(userManagementService.listDirectors());
  }

  @GetMapping("/drivers")
  public ResponseEntity<List<UserSummaryResponse>> drivers() {
    return ResponseEntity.ok(userManagementService.listDrivers());
  }
}
