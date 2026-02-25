package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class SecurityConfigTest {
  private SecurityConfig securityConfig;

  @BeforeEach
  void setUp() {
    securityConfig = new SecurityConfig();
  }

  @Test
  void jwtBeansRejectTooShortSecret() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("short-secret");

    assertThatThrownBy(() -> securityConfig.jwtEncoder(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("не менее 32 байт");

    assertThatThrownBy(() -> securityConfig.jwtDecoder(properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("не менее 32 байт");
  }

  @Test
  void jwtBeansAcceptStrongSecret() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret("0123456789abcdef0123456789abcdef");

    assertThat(securityConfig.jwtEncoder(properties)).isNotNull();
    assertThat(securityConfig.jwtDecoder(properties)).isNotNull();
  }

  @Test
  void corsConfigurationSupportsExactOriginsAndPatterns() {
    CorsConfigurationSource exactSource = securityConfig.corsConfigurationSource(
        "https://example.com, https://app.example.com "
    );
    CorsConfiguration exactConfig = ((UrlBasedCorsConfigurationSource) exactSource)
        .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/products"));
    assertThat(exactConfig.getAllowedOrigins()).containsExactly("https://example.com", "https://app.example.com");
    assertThat(exactConfig.getAllowedOriginPatterns()).isNull();
    assertThat(exactConfig.getAllowedMethods()).contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    assertThat(exactConfig.getAllowCredentials()).isTrue();

    CorsConfigurationSource patternSource = securityConfig.corsConfigurationSource("https://*.example.com");
    CorsConfiguration patternConfig = ((UrlBasedCorsConfigurationSource) patternSource)
        .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/orders"));
    assertThat(patternConfig.getAllowedOriginPatterns()).containsExactly("https://*.example.com");
    assertThat(patternConfig.getAllowedOrigins()).isNull();
  }

  @Test
  void corsConfigurationRejectsEmptyOriginsAndJwtConverterIsConfigured() {
    assertThatThrownBy(() -> securityConfig.corsConfigurationSource(" ,  "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("app.cors.allowed-origins");

    JwtAuthenticationConverter converter = securityConfig.jwtAuthenticationConverter();
    assertThat(converter).isNotNull();
  }

  @Test
  void jwtBeansRejectNullOrBlankSecret() {
    JwtProperties nullSecret = new JwtProperties();
    nullSecret.setSecret(null);
    assertThatThrownBy(() -> securityConfig.jwtEncoder(nullSecret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT secret");

    JwtProperties blankSecret = new JwtProperties();
    blankSecret.setSecret("   ");
    assertThatThrownBy(() -> securityConfig.jwtDecoder(blankSecret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT secret");
  }
}
