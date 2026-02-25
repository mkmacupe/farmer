package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.CreateDirectorRequest;
import com.farm.sales.dto.UserSummaryResponse;
import com.farm.sales.model.Role;
import com.farm.sales.model.User;
import com.farm.sales.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class UserManagementServiceTest {
  private UserRepository userRepository;
  private PasswordEncoder passwordEncoder;
  private AuditTrailPublisher auditTrailPublisher;
  private UserManagementService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    passwordEncoder = mock(PasswordEncoder.class);
    auditTrailPublisher = mock(AuditTrailPublisher.class);
    service = new UserManagementService(userRepository, passwordEncoder, auditTrailPublisher);
  }

  @Test
  void createDirectorRejectsDuplicateUsername() {
    CreateDirectorRequest request = new CreateDirectorRequest(
        "director-1",
        "secret",
        "Иван Петров",
        "+375291234567",
        "ООО Ферма"
    );
    when(userRepository.existsByUsername("director-1")).thenReturn(true);

    assertThatThrownBy(() -> service.createDirector(request))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.getReason()).contains("уже существует");
        });

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void createDirectorNormalizesInputAndPublishesAuditEvent() {
    CreateDirectorRequest request = new CreateDirectorRequest(
        "  director-2  ",
        "secret",
        "  Олег Курилин ",
        "  ",
        "  ОАО Могилевхимволокно  "
    );
    when(userRepository.existsByUsername("director-2")).thenReturn(false);
    when(passwordEncoder.encode("secret")).thenReturn("encoded");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(18L);
      return user;
    });

    UserSummaryResponse response = service.createDirector(request);

    assertThat(response.id()).isEqualTo(18L);
    assertThat(response.username()).isEqualTo("director-2");
    assertThat(response.fullName()).isEqualTo("Олег Курилин");
    assertThat(response.phone()).isNull();
    assertThat(response.legalEntityName()).isEqualTo("ОАО Могилевхимволокно");
    assertThat(response.role()).isEqualTo("DIRECTOR");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User saved = userCaptor.getValue();
    assertThat(saved.getUsername()).isEqualTo("director-2");
    assertThat(saved.getPasswordHash()).isEqualTo("encoded");
    assertThat(saved.getRole()).isEqualTo(Role.DIRECTOR);
    verify(auditTrailPublisher).publish("DIRECTOR_CREATED", "USER", "18", "username=director-2");
  }

  @Test
  void listDirectorsAndDriversReturnMappedResponses() {
    User director = user(1L, "director-a", "Директор А", "+375291", "ООО А", Role.DIRECTOR);
    User driver = user(2L, "driver-a", "Водитель А", null, null, Role.DRIVER);
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DIRECTOR)).thenReturn(List.of(director));
    when(userRepository.findAllByRoleOrderByFullNameAsc(Role.DRIVER)).thenReturn(List.of(driver));

    List<UserSummaryResponse> directors = service.listDirectors();
    List<UserSummaryResponse> drivers = service.listDrivers();

    assertThat(directors).containsExactly(new UserSummaryResponse(
        1L,
        "director-a",
        "Директор А",
        "+375291",
        "ООО А",
        "DIRECTOR"
    ));
    assertThat(drivers).containsExactly(new UserSummaryResponse(
        2L,
        "driver-a",
        "Водитель А",
        null,
        null,
        "DRIVER"
    ));
  }

  @Test
  void createDirectorSupportsNullPhone() {
    CreateDirectorRequest request = new CreateDirectorRequest(
        "director-3",
        "secret",
        "Директор 3",
        null,
        "ООО Три"
    );
    when(userRepository.existsByUsername("director-3")).thenReturn(false);
    when(passwordEncoder.encode("secret")).thenReturn("encoded");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(33L);
      return user;
    });

    UserSummaryResponse response = service.createDirector(request);

    assertThat(response.phone()).isNull();
    assertThat(response.id()).isEqualTo(33L);
  }

  @Test
  void createDirectorTrimsNonEmptyPhone() {
    CreateDirectorRequest request = new CreateDirectorRequest(
        "director-4",
        "secret",
        "Директор 4",
        "  +375291112233  ",
        "ООО Четыре"
    );
    when(userRepository.existsByUsername("director-4")).thenReturn(false);
    when(passwordEncoder.encode("secret")).thenReturn("encoded");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(34L);
      return user;
    });

    UserSummaryResponse response = service.createDirector(request);

    assertThat(response.phone()).isEqualTo("+375291112233");
  }

  private User user(Long id, String username, String fullName, String phone, String legalEntity, Role role) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setFullName(fullName);
    user.setPhone(phone);
    user.setLegalEntityName(legalEntity);
    user.setRole(role);
    return user;
  }
}
