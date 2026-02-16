package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.CreateDirectorRequest;
import com.farm.sales.dto.UserSummaryResponse;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
import com.farm.sales.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserManagementService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final AuditTrailPublisher auditTrailPublisher;

  public UserManagementService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               AuditTrailPublisher auditTrailPublisher) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.auditTrailPublisher = auditTrailPublisher;
  }

  public UserSummaryResponse createDirector(CreateDirectorRequest request) {
    String username = request.username().trim();
    if (userRepository.existsByUsername(username)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким логином уже существует");
    }

    User director = new User(
        username,
        passwordEncoder.encode(request.password()),
        request.fullName().trim(),
        normalizeNullable(request.phone()),
        request.legalEntityName().trim(),
        Role.DIRECTOR
    );

    User saved = userRepository.save(director);
    auditTrailPublisher.publish(
        "DIRECTOR_CREATED",
        "USER",
        String.valueOf(saved.getId()),
        "username=" + saved.getUsername()
    );
    return toResponse(saved);
  }

  public List<UserSummaryResponse> listDirectors() {
    return userRepository.findAllByRoleOrderByFullNameAsc(Role.DIRECTOR).stream()
        .map(this::toResponse)
        .toList();
  }

  public List<UserSummaryResponse> listDrivers() {
    return userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER).stream()
        .map(this::toResponse)
        .toList();
  }

  private UserSummaryResponse toResponse(User user) {
    return new UserSummaryResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getPhone(),
        user.getLegalEntityName(),
        user.getRole().name()
    );
  }

  private String normalizeNullable(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
