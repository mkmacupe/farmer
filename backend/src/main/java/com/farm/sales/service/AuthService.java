package com.farm.sales.service;

import com.farm.sales.audit.AuditActor;
import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.config.JwtProperties;
import com.farm.sales.dto.AuthRequest;
import com.farm.sales.dto.AuthResponse;
import com.farm.sales.model.User;
import com.farm.sales.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;
  private final AuditTrailPublisher auditTrailPublisher;
  private final String dummyPasswordHash;

  public AuthService(UserRepository userRepository,
                     PasswordEncoder passwordEncoder,
                     JwtEncoder jwtEncoder,
                     JwtProperties jwtProperties,
                     AuditTrailPublisher auditTrailPublisher) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.jwtProperties = jwtProperties;
    this.auditTrailPublisher = auditTrailPublisher;
    this.dummyPasswordHash = passwordEncoder.encode("invalid-password-placeholder");
  }

  public AuthResponse login(AuthRequest request) {
    User user = userRepository.findByUsername(request.username())
        .orElse(null);

    // Normalize execution time by always checking a hash.
    String passwordHash = user != null ? user.getPasswordHash() : dummyPasswordHash;
    boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
    if (user == null || !passwordMatches) {
      auditTrailPublisher.publishWithActor(
          "AUTH_LOGIN_FAILED",
          "AUTH",
          request.username(),
          "reason=invalid_credentials",
          new AuditActor(request.username(), null, null)
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

}
