package com.farm.sales.controller;

import com.farm.sales.security.JwtClaimsReader;
import com.farm.sales.service.NotificationStreamService;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationStreamService notificationStreamService;
  private final JwtClaimsReader jwtClaimsReader;

  public NotificationController(NotificationStreamService notificationStreamService,
                                JwtClaimsReader jwtClaimsReader) {
    this.notificationStreamService = notificationStreamService;
    this.jwtClaimsReader = jwtClaimsReader;
  }

  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
    Long userId = jwtClaimsReader.requireUserId(jwt);
    Set<String> roles = jwtClaimsReader.requireRoles(jwt);
    return notificationStreamService.subscribe(userId, roles);
  }
}
