package com.farm.sales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.NotificationStreamService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationControllerTest {
  private NotificationStreamService notificationStreamService;
  private JwtClaimsReader jwtClaimsReader;
  private NotificationController controller;

  @BeforeEach
  void setUp() {
    notificationStreamService = mock(NotificationStreamService.class);
    jwtClaimsReader = mock(JwtClaimsReader.class);
    controller = new NotificationController(notificationStreamService, jwtClaimsReader);
  }

  @Test
  void streamDelegatesToNotificationService() {
    Jwt jwt = jwt(1L, "manager", List.of("MANAGER"));
    SseEmitter emitter = new SseEmitter();

    when(jwtClaimsReader.requireUserId(jwt)).thenReturn(1L);
    when(jwtClaimsReader.requireRoles(jwt)).thenReturn(Set.of("MANAGER"));
    when(notificationStreamService.subscribe(1L, java.util.Set.of("MANAGER"))).thenReturn(emitter);

    SseEmitter response = controller.stream(jwt);

    assertThat(response).isEqualTo(emitter);
    verify(notificationStreamService).subscribe(1L, java.util.Set.of("MANAGER"));
  }

  @Test
  void streamRejectsTokenWithoutRoles() {
    Jwt jwtWithoutRoles = jwt(1L, "manager", List.of());
    when(jwtClaimsReader.requireUserId(jwtWithoutRoles)).thenReturn(1L);
    when(jwtClaimsReader.requireRoles(jwtWithoutRoles))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Токен без ролей недопустим"));

    assertThatThrownBy(() -> controller.stream(jwtWithoutRoles))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

  @Test
  void streamRejectsTokenWithoutUserId() {
    Jwt jwtWithoutUserId = Jwt.withTokenValue("token")
        .subject("manager")
        .claim("roles", List.of("MANAGER"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    when(jwtClaimsReader.requireUserId(jwtWithoutUserId))
        .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "В токене отсутствует или некорректен userId"));

    assertThatThrownBy(() -> controller.stream(jwtWithoutUserId))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        });
  }

  private Jwt jwt(Long userId, String subject, List<String> roles) {
    return Jwt.withTokenValue("token")
        .subject(subject)
        .claim("userId", userId)
        .claim("roles", roles)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
  }
}
