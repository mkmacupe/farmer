package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.config.JwtProperties;
import com.farm.sales.dto.AuthRequest;
import com.farm.sales.dto.AuthResponse;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
import com.farm.sales.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;

class AuthServiceTest {
  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private JwtEncoder jwtEncoder;
  private JwtProperties jwtProperties;
  private AuditTrailPublisher auditTrailPublisher;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    userRepository = org.mockito.Mockito.mock(UserRepository.class);
    passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
    jwtEncoder = org.mockito.Mockito.mock(JwtEncoder.class);
    auditTrailPublisher = org.mockito.Mockito.mock(AuditTrailPublisher.class);
    jwtProperties = new JwtProperties();
    jwtProperties.setIssuer("farm-sales");
    jwtProperties.setExpirationMinutes(60);
    jwtProperties.setSecret("0123456789abcdef0123456789abcdef");

    when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
    authService = new AuthService(userRepository, passwordEncoder, jwtEncoder, jwtProperties, auditTrailPublisher);
  }

  @Test
  void loginReturnsTokenWhenCredentialsAreValid() {
    User user = new User();
    user.setId(7L);
    user.setUsername("manager");
    user.setFullName("Manager");
    user.setRole(Role.MANAGER);
    user.setPasswordHash("stored-hash");

    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("secret123", "stored-hash")).thenReturn(true);
    Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
    when(jwt.getTokenValue()).thenReturn("token-value");
    when(jwtEncoder.encode(any())).thenReturn(jwt);

    AuthResponse response = authService.login(new AuthRequest("manager", "secret123"));

    assertThat(response.token()).isEqualTo("token-value");
    assertThat(response.role()).isEqualTo("MANAGER");
  }

  @Test
  void loginStillPerformsPasswordCheckWhenUserDoesNotExist() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("secret123", "dummy-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new AuthRequest("ghost", "secret123")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });

    verify(passwordEncoder).matches("secret123", "dummy-hash");
  }

  @Test
  void loginFailsWhenPasswordDoesNotMatchExistingUser() {
    User user = new User();
    user.setId(9L);
    user.setUsername("manager");
    user.setRole(Role.MANAGER);
    user.setPasswordHash("stored-hash");
    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new AuthRequest("manager", "wrong")))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });

    verify(passwordEncoder).matches("wrong", "stored-hash");
  }

  @Test
  void demoLoginReturnsTokenWhenDemoEnabledAndUserIsAllowed() {
    User user = new User();
    user.setId(7L);
    user.setUsername("manager");
    user.setFullName("Manager");
    user.setRole(Role.MANAGER);
    user.setPasswordHash("stored-hash");

    when(userRepository.findByUsername("manager")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("MgrD5v8cN4", "stored-hash")).thenReturn(true);
    Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
    when(jwt.getTokenValue()).thenReturn("demo-token");
    when(jwtEncoder.encode(any())).thenReturn(jwt);

    AuthResponse response = authService.demoLogin("manager", "MgrD5v8cN4", true);

    assertThat(response.token()).isEqualTo("demo-token");
    assertThat(response.role()).isEqualTo("MANAGER");
  }

  @Test
  void demoLoginFailsWhenDemoModeDisabled() {
    assertThatThrownBy(() -> authService.demoLogin("manager", "secret", false))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        });
  }

  @Test
  void demoLoginFailsForUnknownOrNotAllowedUser() {
    when(userRepository.findByUsername("intruder")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("secret", "dummy-hash")).thenReturn(false);

    assertThatThrownBy(() -> authService.demoLogin("intruder", "secret", true))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

}
