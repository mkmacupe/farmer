package com.farm.sales.controller;

import com.farm.sales.dto.ProfileResponse;
import com.farm.sales.dto.ProfileUpdateRequest;
import com.farm.sales.dto.StoreAddressRequest;
import com.farm.sales.dto.StoreAddressResponse;
import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.DirectorProfileService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/director")
public class DirectorProfileController {
  private final DirectorProfileService directorProfileService;
  private final JwtClaimsReader jwtClaimsReader;

  public DirectorProfileController(DirectorProfileService directorProfileService,
                                   JwtClaimsReader jwtClaimsReader) {
    this.directorProfileService = directorProfileService;
    this.jwtClaimsReader = jwtClaimsReader;
  }

  @GetMapping("/profile")
  public ResponseEntity<ProfileResponse> profile(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(directorProfileService.getProfile(jwtClaimsReader.requireUserId(jwt)));
  }

  @PatchMapping("/profile")
  public ResponseEntity<ProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody ProfileUpdateRequest request) {
    return ResponseEntity.ok(directorProfileService.updateProfile(jwtClaimsReader.requireUserId(jwt), request));
  }

  @GetMapping("/addresses")
  public ResponseEntity<List<StoreAddressResponse>> addresses(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(directorProfileService.getAddresses(jwtClaimsReader.requireUserId(jwt)));
  }

  @PostMapping("/addresses")
  public ResponseEntity<StoreAddressResponse> createAddress(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody StoreAddressRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(directorProfileService.createAddress(jwtClaimsReader.requireUserId(jwt), request));
  }

  @PutMapping("/addresses/{id}")
  public ResponseEntity<StoreAddressResponse> updateAddress(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody StoreAddressRequest request) {
    return ResponseEntity.ok(directorProfileService.updateAddress(jwtClaimsReader.requireUserId(jwt), id, request));
  }

  @DeleteMapping("/addresses/{id}")
  public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
    directorProfileService.deleteAddress(jwtClaimsReader.requireUserId(jwt), id);
    return ResponseEntity.noContent().build();
  }
}
