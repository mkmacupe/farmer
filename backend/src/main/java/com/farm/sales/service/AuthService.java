package com.farm.sales.service;

import com.farm.sales.audit.AuditActor;
import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.config.JwtProperties;
import com.farm.sales.dto.AuthRequest;
import com.farm.sales.dto.AuthResponse;
import com.farm.sales.config.DataInitializer;
import com.farm.sales.model.User;
import com.farm.sales.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final Set<String> DEMO_USERNAMES = Set.of(
      "manager",
      "logistician",
      "driver1",
      "driver2",
      "driver3"
  );
  private static final Set<String> DEMO_LOGIN_USERNAMES = Stream.concat(
      DataInitializer.demoDirectorUsernames().stream(),
      DEMO_USERNAMES.stream()
  ).collect(Collectors.toUnmodifiableSet());

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;
  private final AuditTrailPublisher auditTrailPublisher;
  private final String dummyPasswordHash;
  private final boolean demoEnabled;
  @Autowired(required = false)
  private DataInitializer dataInitializer;

  public AuthService(UserRepository userRepository,
                     PasswordEncoder passwordEncoder,
                     JwtEncoder jwtEncoder,
                     JwtProperties jwtProperties,
                     AuditTrailPublisher auditTrailPublisher,
                     @Value("${app.demo.enabled:false}") boolean demoEnabled) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.jwtProperties = jwtProperties;
    this.auditTrailPublisher = auditTrailPublisher;
    this.dummyPasswordHash = passwordEncoder.encode("invalid-password-placeholder");
    this.demoEnabled = demoEnabled;
  }

  public AuthResponse login(AuthRequest request) {
    String normalizedUsername = normalizeUsername(request.username());
    User user = findUserWithLazyDemoSeed(normalizedUsername);

    // Normalize execution time by always checking a hash.
    String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
    boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
    if (user == null || !passwordMatches) {
      auditTrailPublisher.publishWithActor(
          "AUTH_LOGIN_FAILED",
          "AUTH",
          normalizedUsername,
          "reason=invalid_credentials",
          new AuditActor(normalizedUsername, null, null)
      );
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }

    String token = generateToken(user);
    auditTrailPublisher.publishWithActor(
        "AUTH_LOGIN_SUCCESS",
        "AUTH",
        user.getUsername(),
        "role=" + user.getRole().name(),
        new AuditActor(user.getUsername(), user.getId(), user.getRole().name())
    );
    return new AuthResponse(token, user.getUsername(), user.getFullName(), user.getRole().name());
  }

  public AuthResponse demoLogin(String username, String password) {
    if (!demoEnabled) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Вход по предзаполненным учетным записям отключён");
    }

    String normalizedUsername = username == null ? "" : username.trim();
    if (!DEMO_LOGIN_USERNAMES.contains(normalizedUsername)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }

    User user = findUserWithLazyDemoSeed(normalizedUsername);

    String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
    boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
    if (user == null || !passwordMatches) {
      auditTrailPublisher.publishWithActor(
          "AUTH_DEMO_LOGIN_FAILED",
          "AUTH",
          normalizedUsername,
          "reason=invalid_credentials",
          new AuditActor(normalizedUsername, null, null)
      );
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }

    String token = generateToken(user);
    auditTrailPublisher.publishWithActor(
        "AUTH_DEMO_LOGIN_SUCCESS",
        "AUTH",
        user.getUsername(),
        "role=" + user.getRole().name(),
        new AuditActor(user.getUsername(), user.getId(), user.getRole().name())
    );
    return new AuthResponse(token, user.getUsername(), user.getFullName(), user.getRole().name());
  }

  private String generateToken(User user) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(jwtProperties.getIssuer())
        .issuedAt(now)
        .expiresAt(now.plus(jwtProperties.getExpirationMinutes(), ChronoUnit.MINUTES))
        .subject(user.getUsername())
        .claim("roles", List.of(user.getRole().name()))
        .claim("userId", user.getId())
        .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  private User findUserWithLazyDemoSeed(String username) {
    User user = userRepository.findByUsername(username).orElse(null);
    if (user != null || dataInitializer == null) {
      return user;
    }

    String normalizedDemoUsername = username.toLowerCase(Locale.ROOT);
    if (!DEMO_LOGIN_USERNAMES.contains(normalizedDemoUsername)) {
      return null;
    }

    dataInitializer.seedDemoData();
    return userRepository.findByUsername(username).orElse(null);
  }

  private String normalizeUsername(String username) {
    return username == null ? "" : username.trim();
  }

}
