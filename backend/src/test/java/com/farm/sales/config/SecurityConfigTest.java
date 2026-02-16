package com.farm.sales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigTest {
  private final SecurityConfig securityConfig = new SecurityConfig();

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
}
