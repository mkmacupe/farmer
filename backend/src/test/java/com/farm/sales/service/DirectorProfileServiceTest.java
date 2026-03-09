package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.GeoLookupResponse;
import com.farm.sales.dto.ProfileResponse;
import com.farm.sales.dto.ProfileUpdateRequest;
import com.farm.sales.dto.StoreAddressRequest;
import com.farm.sales.dto.StoreAddressResponse;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.Role;
import com.farm.sales.model.StoreAddress;
import com.farm.sales.model.User;
import com.farm.sales.repository.OrderRepository;
import com.farm.sales.repository.StoreAddressRepository;
import com.farm.sales.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class DirectorProfileServiceTest {
  private UserRepository userRepository;
  private StoreAddressRepository storeAddressRepository;
  private OrderRepository orderRepository;
  private GeocodingService geocodingService;
  private AuditTrailPublisher auditTrailPublisher;
  private DirectorProfileService service;

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    storeAddressRepository = mock(StoreAddressRepository.class);
    orderRepository = mock(OrderRepository.class);
    geocodingService = mock(GeocodingService.class);
    auditTrailPublisher = mock(AuditTrailPublisher.class);
    service = new DirectorProfileService(
        userRepository,
        storeAddressRepository,
        orderRepository,
        geocodingService,
        auditTrailPublisher
    );
  }

  @Test
  void getProfileRejectsMissingAndNonDirectorUsers() {
    when(userRepository.findById(77L)).thenReturn(Optional.empty());
    assertStatus(() -> service.getProfile(77L), HttpStatus.NOT_FOUND, "Пользователь не найден");

    User manager = user(1L, "manager", Role.MANAGER);
    when(userRepository.findById(1L)).thenReturn(Optional.of(manager));
    assertStatus(() -> service.getProfile(1L), HttpStatus.FORBIDDEN, "только директорам");
  }

  @Test
  void getAndUpdateProfileUseDirectorEntityAndNormalizePhone() {
    User director = user(10L, "director", Role.DIRECTOR);
    director.setPhone("+375291111111");
    director.setLegalEntityName("ООО Точка");
    when(userRepository.findById(10L)).thenReturn(Optional.of(director));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ProfileResponse profile = service.getProfile(10L);
    assertThat(profile.id()).isEqualTo(10L);
    assertThat(profile.username()).isEqualTo("director");

    ProfileResponse updated = service.updateProfile(10L, new ProfileUpdateRequest("  Иван Директор  ", "  "));
    assertThat(updated.fullName()).isEqualTo("Иван Директор");
    assertThat(updated.phone()).isNull();
    verify(auditTrailPublisher).publish("DIRECTOR_PROFILE_UPDATED", "USER", "10", null);

    ProfileResponse updatedWithNullPhone = service.updateProfile(10L, new ProfileUpdateRequest("Иван Директор", null));
    assertThat(updatedWithNullPhone.phone()).isNull();

    ProfileResponse updatedWithPhone = service.updateProfile(10L, new ProfileUpdateRequest("Иван Директор", "  +375291234567  "));
    assertThat(updatedWithPhone.phone()).isEqualTo("+375291234567");
  }

  @Test
  void addressesFlowSupportsReadCreateUpdateDeleteAndOwnershipCheck() {
    User director = user(15L, "director", Role.DIRECTOR);
    when(userRepository.findById(15L)).thenReturn(Optional.of(director));

    StoreAddress existing = address(3L, director, "Точка", "Могилёв, 1",
        new BigDecimal("53.9000000"), new BigDecimal("30.3000000"));
    when(storeAddressRepository.findByUserIdOrderByCreatedAtDesc(15L)).thenReturn(List.of(existing));
    when(storeAddressRepository.findByIdAndUserId(3L, 15L)).thenReturn(Optional.of(existing));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> invocation.getArgument(0));

    List<StoreAddressResponse> addresses = service.getAddresses(15L);
    assertThat(addresses).hasSize(1);
    assertThat(addresses.getFirst().deletable()).isTrue();
    assertThat(addresses.getFirst().label()).isEqualTo("Точка");

    StoreAddressRequest createWithCoords = new StoreAddressRequest(
        "  Новый склад ",
        "  Могилёв, 2  ",
        new BigDecimal("53.8100000"),
        new BigDecimal("30.1200000")
    );
    StoreAddressResponse created = service.createAddress(15L, createWithCoords);
    assertThat(created.label()).isEqualTo("Новый склад");
    assertThat(created.addressLine()).isEqualTo("Могилёв, 2");
    assertThat(created.latitude()).isEqualByComparingTo("53.8100000");
    assertThat(created.deletable()).isTrue();
    verify(geocodingService, never()).geocodeFirst(any());

    when(geocodingService.geocodeFirst("Могилёв, 3")).thenReturn(Optional.of(
        new GeoLookupResponse("place", "Могилёв, 3", new BigDecimal("53.9100000"), new BigDecimal("30.4100000"))
    ));
    StoreAddressRequest updateWithoutCoords = new StoreAddressRequest(" Обновлено ", "Могилёв, 3", null, null);
    StoreAddressResponse updated = service.updateAddress(15L, 3L, updateWithoutCoords);
    assertThat(updated.label()).isEqualTo("Обновлено");
    assertThat(updated.latitude()).isEqualByComparingTo("53.9100000");
    assertThat(updated.longitude()).isEqualByComparingTo("30.4100000");
    assertThat(updated.deletable()).isTrue();

    when(orderRepository.existsByDeliveryAddressIdAndStatusIn(
        3L,
        EnumSet.of(OrderStatus.CREATED, OrderStatus.APPROVED, OrderStatus.ASSIGNED)
    )).thenReturn(false);
    service.deleteAddress(15L, 3L);
    verify(orderRepository, atLeastOnce()).existsByDeliveryAddressIdAndStatusIn(
        3L,
        EnumSet.of(OrderStatus.CREATED, OrderStatus.APPROVED, OrderStatus.ASSIGNED)
    );
    verify(orderRepository).clearDeliveryAddressReference(3L);
    verify(storeAddressRepository).delete(existing);

    StoreAddress owned = service.getOwnedAddress(15L, 3L);
    assertThat(owned).isSameAs(existing);
  }

  @Test
  void deleteAddressReturnsConflictWhenAddressUsedInOrders() {
    User director = user(16L, "director-used", Role.DIRECTOR);
    when(userRepository.findById(16L)).thenReturn(Optional.of(director));
    StoreAddress existing = address(4L, director, "Склад", "Минск, 1",
        new BigDecimal("53.9000000"), new BigDecimal("27.5667000"));
    when(storeAddressRepository.findByIdAndUserId(4L, 16L)).thenReturn(Optional.of(existing));
    when(orderRepository.existsByDeliveryAddressIdAndStatusIn(
        4L,
        EnumSet.of(OrderStatus.CREATED, OrderStatus.APPROVED, OrderStatus.ASSIGNED)
    )).thenReturn(true);

    assertStatus(() -> service.deleteAddress(16L, 4L),
        HttpStatus.CONFLICT, "есть активные заказы");

    verify(orderRepository, never()).clearDeliveryAddressReference(4L);
    verify(storeAddressRepository, never()).delete(any(StoreAddress.class));
  }

  @Test
  void createAndOwnedAddressHandleNotFoundBranches() {
    User director = user(20L, "director-2", Role.DIRECTOR);
    when(userRepository.findById(20L)).thenReturn(Optional.of(director));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
      StoreAddress value = invocation.getArgument(0);
      value.setId(8L);
      return value;
    });
    when(geocodingService.geocodeFirst("Unknown")).thenReturn(Optional.empty());

    StoreAddressResponse created = service.createAddress(
        20L,
        new StoreAddressRequest("Label", "Unknown", null, null)
    );
    assertThat(created.id()).isEqualTo(8L);
    assertThat(created.latitude()).isNull();
    assertThat(created.longitude()).isNull();
    assertThat(created.deletable()).isTrue();

    when(storeAddressRepository.findByIdAndUserId(404L, 20L)).thenReturn(Optional.empty());
    assertStatus(() -> service.updateAddress(20L, 404L, new StoreAddressRequest("L", "A", null, null)),
        HttpStatus.NOT_FOUND, "Адрес не найден");
    assertStatus(() -> service.deleteAddress(20L, 404L),
        HttpStatus.NOT_FOUND, "Адрес не найден");
    assertStatus(() -> service.getOwnedAddress(20L, 404L),
        HttpStatus.BAD_REQUEST, "не принадлежит");

    StoreAddressResponse createdWithSingleCoordinate = service.createAddress(
        20L,
        new StoreAddressRequest("Label", "Unknown", new BigDecimal("53.5"), null)
    );
    assertThat(createdWithSingleCoordinate.latitude()).isEqualByComparingTo("53.5");
    assertThat(createdWithSingleCoordinate.longitude()).isNull();
    assertThat(createdWithSingleCoordinate.deletable()).isTrue();
  }

  @Test
  void createAddressPublishesAuditData() {
    User director = user(55L, "dir55", Role.DIRECTOR);
    when(userRepository.findById(55L)).thenReturn(Optional.of(director));
    when(storeAddressRepository.save(any(StoreAddress.class))).thenAnswer(invocation -> {
      StoreAddress value = invocation.getArgument(0);
      value.setId(99L);
      return value;
    });

    service.createAddress(
        55L,
        new StoreAddressRequest("Store", "Addr", new BigDecimal("53.1"), new BigDecimal("30.1"))
    );

    verify(auditTrailPublisher).publish("DIRECTOR_ADDRESS_CREATED", "STORE_ADDRESS", "99", "userId=55");

    ArgumentCaptor<StoreAddress> captor = ArgumentCaptor.forClass(StoreAddress.class);
    verify(storeAddressRepository).save(captor.capture());
    assertThat(captor.getValue().getCreatedAt()).isNotNull();
    assertThat(captor.getValue().getUpdatedAt()).isNotNull();
  }

  private void assertStatus(Runnable runnable, HttpStatus status, String reasonPart) {
    assertThatThrownBy(runnable::run)
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(error -> {
          ResponseStatusException ex = (ResponseStatusException) error;
          assertThat(ex.getStatusCode()).isEqualTo(status);
          assertThat(ex.getReason()).contains(reasonPart);
        });
  }

  private User user(Long id, String username, Role role) {
    User user = new User();
    user.setId(id);
    user.setUsername(username);
    user.setFullName("Director " + id);
    user.setRole(role);
    return user;
  }

  private StoreAddress address(Long id,
                               User user,
                               String label,
                               String line,
                               BigDecimal latitude,
                               BigDecimal longitude) {
    StoreAddress address = new StoreAddress();
    address.setId(id);
    address.setUser(user);
    address.setLabel(label);
    address.setAddressLine(line);
    address.setLatitude(latitude);
    address.setLongitude(longitude);
    address.setCreatedAt(Instant.now().minusSeconds(60));
    address.setUpdatedAt(Instant.now().minusSeconds(5));
    return address;
  }
}
