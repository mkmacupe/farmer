package com.farm.sales.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                 JwtAuthenticationConverter jwtAuthenticationConverter,
                                                 CorsConfigurationSource corsConfigurationSource) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/seed-login").permitAll()
            .requestMatchers("/favicon.ico", "/error").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/notifications/stream")
            .hasAnyRole("DIRECTOR", "MANAGER", "LOGISTICIAN", "DRIVER")
            .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").authenticated()
            .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole("DIRECTOR", "MANAGER", "LOGISTICIAN", "DRIVER")
            .requestMatchers("/api/products/**").hasRole("MANAGER")
            .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("DIRECTOR")
            .requestMatchers(HttpMethod.POST, "/api/orders/*/repeat").hasRole("DIRECTOR")
            .requestMatchers(HttpMethod.GET, "/api/orders/my").hasRole("DIRECTOR")
            .requestMatchers(HttpMethod.GET, "/api/orders/my/page").hasRole("DIRECTOR")
            .requestMatchers(HttpMethod.GET, "/api/orders/assigned").hasRole("DRIVER")
            .requestMatchers(HttpMethod.GET, "/api/orders/assigned/page").hasRole("DRIVER")
            .requestMatchers(HttpMethod.GET, "/api/orders").hasAnyRole("MANAGER", "LOGISTICIAN")
            .requestMatchers(HttpMethod.GET, "/api/orders/page").hasAnyRole("MANAGER", "LOGISTICIAN")
            .requestMatchers(HttpMethod.POST, "/api/orders/auto-assign", "/api/orders/auto-assign/**").hasRole("LOGISTICIAN")
            .requestMatchers(HttpMethod.POST, "/api/orders/approve-all").hasRole("MANAGER")
            .requestMatchers(HttpMethod.POST, "/api/orders/*/approve").hasRole("MANAGER")
            .requestMatchers(HttpMethod.POST, "/api/orders/*/assign-driver").hasRole("LOGISTICIAN")
            .requestMatchers(HttpMethod.POST, "/api/orders/*/deliver").hasRole("DRIVER")
            .requestMatchers(HttpMethod.POST, "/api/scenario/reset", "/api/scenario/clear-orders").hasRole("MANAGER")
            .requestMatchers(HttpMethod.GET, "/api/orders/*/timeline")
            .hasAnyRole("DIRECTOR", "MANAGER", "LOGISTICIAN", "DRIVER")
            .requestMatchers("/api/director/**").hasRole("DIRECTOR")
            .requestMatchers(HttpMethod.POST, "/api/users/directors").hasRole("MANAGER")
            .requestMatchers(HttpMethod.GET, "/api/users/directors").hasRole("MANAGER")
            .requestMatchers(HttpMethod.GET, "/api/users/drivers").hasAnyRole("MANAGER", "LOGISTICIAN")
            .requestMatchers("/api/geo/**").hasAnyRole("DIRECTOR", "MANAGER", "LOGISTICIAN")
            .requestMatchers("/api/reports/**").hasRole("MANAGER")
            .requestMatchers("/api/audit/**").hasRole("MANAGER")
            .requestMatchers("/api/dashboard/**").hasRole("MANAGER")
            .requestMatchers("/api/stock-movements/**").hasRole("MANAGER")
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins}")
      String allowedOriginsValue
  ) {
    List<String> allowedOrigins = Arrays.stream(allowedOriginsValue.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();

    if (allowedOrigins.isEmpty()) {
      throw new IllegalStateException("Необходимо указать хотя бы одно значение app.cors.allowed-origins");
    }

    CorsConfiguration configuration = new CorsConfiguration();
    boolean hasWildcard = allowedOrigins.stream().anyMatch(value -> value.contains("*"));
    if (hasWildcard) {
      configuration.setAllowedOriginPatterns(allowedOrigins);
    } else {
      configuration.setAllowedOrigins(allowedOrigins);
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthoritiesClaimName("roles");
    converter.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(converter);
    return jwtAuthenticationConverter;
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    SecretKey secretKey = secretKey(properties);
    return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    SecretKey secretKey = secretKey(properties);
    return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  private SecretKey secretKey(JwtProperties properties) {
    String secret = properties.getSecret();
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Необходимо указать JWT secret");
    }

    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("JWT secret должен быть не менее 32 байт для HS256");
    }

    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }
}
