package com.farm.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class AuditActorResolverTest {
  private final AuditActorResolver resolver = new AuditActorResolver();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolveCurrentActorReturnsAnonymousForMissingOrUnauthenticatedContext() {
    SecurityContextHolder.clearContext();
    assertThat(resolver.resolveCurrentActor()).isEqualTo(new AuditActor("anonymous", null, null));

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    TestingAuthenticationToken unauthenticated = new TestingAuthenticationToken("name", null);
    unauthenticated.setAuthenticated(false);
    context.setAuthentication(unauthenticated);
    SecurityContextHolder.setContext(context);
    assertThat(resolver.resolveCurrentActor()).isEqualTo(new AuditActor("anonymous", null, null));
  }

  @Test
  void resolveCurrentActorExtractsJwtClaimsAndNormalizesValues() {
    Jwt jwt = Jwt.withTokenValue("token")
        .subject("  manager ")
        .claim("userId", " 44 ")
        .claim("roles", List.of(" director "))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
    authentication.setAuthenticated(true);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    AuditActor actor = resolver.resolveCurrentActor();
    assertThat(actor).isEqualTo(new AuditActor("manager", 44L, "DIRECTOR"));
  }

  @Test
  void resolveCurrentActorSupportsNumericUserIdAndMissingRoles() {
    Jwt jwt = Jwt.withTokenValue("token")
        .subject(null)
        .claim("userId", 77L)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(jwt, null);
    authentication.setAuthenticated(true);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);

    assertThat(resolver.resolveCurrentActor()).isEqualTo(new AuditActor("anonymous", 77L, null));
  }

  @Test
  void resolveCurrentActorHandlesInvalidJwtClaimsAndNonJwtPrincipal() {
    Jwt badJwt = Jwt.withTokenValue("token")
        .subject("   ")
        .claim("userId", "bad")
        .claim("roles", List.of("   "))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .header("alg", "HS256")
        .build();
    TestingAuthenticationToken badJwtAuth = new TestingAuthenticationToken(badJwt, null);
    badJwtAuth.setAuthenticated(true);
    SecurityContext jwtContext = SecurityContextHolder.createEmptyContext();
    jwtContext.setAuthentication(badJwtAuth);
    SecurityContextHolder.setContext(jwtContext);
    assertThat(resolver.resolveCurrentActor()).isEqualTo(new AuditActor("anonymous", null, null));

    TestingAuthenticationToken stringPrincipalAuth = new TestingAuthenticationToken("   ", null);
    stringPrincipalAuth.setAuthenticated(true);
    SecurityContext textContext = SecurityContextHolder.createEmptyContext();
    textContext.setAuthentication(stringPrincipalAuth);
    SecurityContextHolder.setContext(textContext);
    assertThat(resolver.resolveCurrentActor()).isEqualTo(new AuditActor("anonymous", null, null));
  }

  @Test
  void privateHelpersHandleUnsupportedUserIdAndEmptyRoles() throws Exception {
    Method extractUserId = AuditActorResolver.class.getDeclaredMethod("extractUserId", Object.class);
    extractUserId.setAccessible(true);
    assertThat(extractUserId.invoke(resolver, true)).isNull();

    Method extractRole = AuditActorResolver.class.getDeclaredMethod("extractRole", List.class);
    extractRole.setAccessible(true);
    assertThat(extractRole.invoke(resolver, List.of())).isNull();
    assertThat(extractRole.invoke(resolver, Arrays.asList((String) null))).isNull();
  }
}
