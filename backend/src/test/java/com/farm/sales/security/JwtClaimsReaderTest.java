package com.farm.sales.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.farm.sales.model.Role;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class JwtClaimsReaderTest {
  private final JwtClaimsReader reader = new JwtClaimsReader();

  @Test
  void requireUserIdSupportsNumberAndStringClaims() {
    Jwt numericJwt = jwtWithClaims(15L, List.of("DIRECTOR"));
    Jwt stringJwt = Jwt.withTokenValue("token")
        .subject("director")
        .claim("userId", " 42 ")
        .claim("roles", List.of("DIRECTOR"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();

    assertThat(reader.requireUserId(numericJwt)).isEqualTo(15L);
    assertThat(reader.requireUserId(stringJwt)).isEqualTo(42L);
  }

  @Test
  void requireUserIdRejectsNullAndInvalidClaims() {
    Jwt invalidJwt = Jwt.withTokenValue("token")
        .subject("director")
        .claim("userId", "abc")
        .claim("roles", List.of("DIRECTOR"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    Jwt missingJwt = jwtWithClaims(null, List.of("DIRECTOR"));

    assertUnauthorized(() -> reader.requireUserId(null), "Требуется аутентификация");
    assertUnauthorized(() -> reader.requireUserId(invalidJwt), "В токене отсутствует или некорректен userId");
    assertUnauthorized(() -> reader.requireUserId(missingJwt), "В токене отсутствует или некорректен userId");
  }

  @Test
  void requireRolesNormalizesValuesAndRejectsEmpty() {
    Jwt jwt = Jwt.withTokenValue("token")
        .subject("manager")
        .claim("userId", 1L)
        .claim("roles", Arrays.asList(" manager ", "", "MANAGER", null, "driver"))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    Jwt noRolesJwt = Jwt.withTokenValue("token")
        .subject("manager")
        .claim("userId", 1L)
        .claim("roles", List.of(" ", ""))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    Jwt missingRolesClaim = Jwt.withTokenValue("token")
        .subject("manager")
        .claim("userId", 1L)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    Jwt emptyRoles = Jwt.withTokenValue("token")
        .subject("manager")
        .claim("userId", 1L)
        .claim("roles", List.of())
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();

    assertThat(reader.requireRoles(jwt)).containsExactly("MANAGER", "DRIVER");
    assertUnauthorized(() -> reader.requireRoles(null), "Требуется аутентификация");
    assertUnauthorized(() -> reader.requireRoles(noRolesJwt), "Токен без ролей недопустим");
    assertUnauthorized(() -> reader.requireRoles(missingRolesClaim), "Токен без ролей недопустим");
    assertUnauthorized(() -> reader.requireRoles(emptyRoles), "Токен без ролей недопустим");
  }

  @Test
  void requireSingleRoleValidatesSingleKnownRole() {
    Jwt oneRoleJwt = jwtWithClaims(9L, List.of("manager"));
    Jwt multiRoleJwt = jwtWithClaims(9L, List.of("MANAGER", "DIRECTOR"));
    Jwt badRoleJwt = jwtWithClaims(9L, List.of("UNKNOWN_ROLE"));

    assertThat(reader.requireSingleRole(oneRoleJwt)).isEqualTo(Role.MANAGER);
    assertUnauthorized(() -> reader.requireSingleRole(multiRoleJwt), "В токене должна быть ровно одна роль");
    assertUnauthorized(() -> reader.requireSingleRole(badRoleJwt), "Некорректная роль в токене");
  }

  @Test
  void normalizeRolesHandlesNullElementsViaReflection() throws Exception {
    Method method = JwtClaimsReader.class.getDeclaredMethod("normalizeRoles", List.class);
    method.setAccessible(true);

    @SuppressWarnings("unchecked")
    var normalized = (java.util.Set<String>) method.invoke(reader, Arrays.asList(null, " ", " director "));
    assertThat(normalized).containsExactly("DIRECTOR");
  }

  private Jwt jwtWithClaims(Object userId, List<String> roles) {
    Jwt.Builder builder = Jwt.withTokenValue("token")
        .subject("user")
        .claim("roles", roles)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256");
    if (userId != null) {
      builder.claim("userId", userId);
    }
    return builder.build();
  }

  private void assertUnauthorized(Runnable runnable, String reasonPart) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(ex.getReason()).contains(reasonPart);
        });
  }
}
