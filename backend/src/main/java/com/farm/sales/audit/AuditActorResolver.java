package com.farm.sales.audit;

import java.util.List;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuditActorResolver {
  public AuditActor resolveCurrentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return new AuditActor("anonymous", null, null);
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof Jwt jwt) {
      String username = normalizeText(jwt.getSubject(), "anonymous");
      Long userId = extractUserId(jwt.getClaim("userId"));
      String role = extractRole(jwt.getClaimAsStringList("roles"));
      return new AuditActor(username, userId, role);
    }

    String username = normalizeText(authentication.getName(), "anonymous");
    return new AuditActor(username, null, null);
  }

  private Long extractUserId(Object claimValue) {
    if (claimValue instanceof Number number) {
      return number.longValue();
    }
    if (claimValue instanceof String value) {
      try {
        return Long.parseLong(value.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private String extractRole(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return null;
    }
    String firstRole = roles.getFirst();
    if (firstRole == null) {
      return null;
    }
    String normalized = firstRole.trim().toUpperCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeText(String value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? fallback : normalized;
  }
}
