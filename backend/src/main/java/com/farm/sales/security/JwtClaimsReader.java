package com.farm.sales.security;

import com.farm.sales.model.Role;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtClaimsReader {
  public Long requireUserId(Jwt jwt) {
    if (jwt == null) {
      throw unauthorized("Требуется аутентификация");
    }

    Object value = jwt.getClaim("userId");
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String string) {
      try {
        return Long.parseLong(string.trim());
      } catch (NumberFormatException ignored) {
        // fall through to exception
      }
    }
    throw unauthorized("В токене отсутствует или некорректен userId");
  }

  public Role requireSingleRole(Jwt jwt) {
    Set<String> roles = requireRoles(jwt);
    if (roles.size() != 1) {
      throw unauthorized("В токене должна быть ровно одна роль");
    }

    String roleName = roles.iterator().next();
    try {
      return Role.valueOf(roleName);
    } catch (IllegalArgumentException ignored) {
      throw unauthorized("Некорректная роль в токене");
    }
  }

  public Set<String> requireRoles(Jwt jwt) {
    if (jwt == null) {
      throw unauthorized("Требуется аутентификация");
    }

    Set<String> roles = normalizeRoles(jwt.getClaimAsStringList("roles"));
    if (roles.isEmpty()) {
      throw unauthorized("Токен без ролей недопустим");
    }
    return roles;
  }

  private Set<String> normalizeRoles(List<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return Set.of();
    }

    return roles.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .map(value -> value.toUpperCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private ResponseStatusException unauthorized(String reason) {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
  }
}
