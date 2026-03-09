package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
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
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DirectorProfileService {
  private static final EnumSet<OrderStatus> ACTIVE_ADDRESS_LOCK_STATUSES =
      EnumSet.of(OrderStatus.CREATED, OrderStatus.APPROVED, OrderStatus.ASSIGNED);
  private final UserRepository userRepository;
  private final StoreAddressRepository storeAddressRepository;
  private final OrderRepository orderRepository;
  private final GeocodingService geocodingService;
  private final AuditTrailPublisher auditTrailPublisher;

  public DirectorProfileService(UserRepository userRepository,
                                StoreAddressRepository storeAddressRepository,
                                OrderRepository orderRepository,
                                GeocodingService geocodingService,
                                AuditTrailPublisher auditTrailPublisher) {
    this.userRepository = userRepository;
    this.storeAddressRepository = storeAddressRepository;
    this.orderRepository = orderRepository;
    this.geocodingService = geocodingService;
    this.auditTrailPublisher = auditTrailPublisher;
  }

  @Transactional
  public ProfileResponse getProfile(Long directorId) {
    User director = loadDirector(directorId);
    return toProfileResponse(director);
  }

  @Transactional
  public ProfileResponse updateProfile(Long directorId, ProfileUpdateRequest request) {
    User director = loadDirector(directorId);
    director.setFullName(request.fullName().trim());
    director.setPhone(normalizeNullable(request.phone()));
    User saved = userRepository.save(director);
    auditTrailPublisher.publish("DIRECTOR_PROFILE_UPDATED", "USER", String.valueOf(saved.getId()), null);
    return toProfileResponse(saved);
  }

  @Transactional
  public List<StoreAddressResponse> getAddresses(Long directorId) {
    loadDirector(directorId);
    return storeAddressRepository.findByUserIdOrderByCreatedAtDesc(directorId).stream()
        .map(this::toAddressResponse)
        .toList();
  }

  @Transactional
  public StoreAddressResponse createAddress(Long directorId, StoreAddressRequest request) {
    User director = loadDirector(directorId);
    Instant now = Instant.now();
    StoreAddress address = new StoreAddress();
    address.setUser(director);
    address.setLabel(request.label().trim());
    address.setAddressLine(request.addressLine().trim());
    Coordinates coordinates = resolveCoordinates(request.latitude(), request.longitude(), address.getAddressLine());
    address.setLatitude(coordinates.latitude());
    address.setLongitude(coordinates.longitude());
    address.setCreatedAt(now);
    address.setUpdatedAt(now);
    StoreAddress saved = storeAddressRepository.save(address);
    auditTrailPublisher.publish(
        "DIRECTOR_ADDRESS_CREATED",
        "STORE_ADDRESS",
        String.valueOf(saved.getId()),
        "userId=" + directorId
    );
    return toAddressResponse(saved);
  }

  @Transactional
  public StoreAddressResponse updateAddress(Long directorId, Long addressId, StoreAddressRequest request) {
    loadDirector(directorId);
    StoreAddress address = storeAddressRepository.findByIdAndUserId(addressId, directorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес не найден"));
    address.setLabel(request.label().trim());
    address.setAddressLine(request.addressLine().trim());
    Coordinates coordinates = resolveCoordinates(request.latitude(), request.longitude(), address.getAddressLine());
    address.setLatitude(coordinates.latitude());
    address.setLongitude(coordinates.longitude());
    address.setUpdatedAt(Instant.now());
    StoreAddress saved = storeAddressRepository.save(address);
    auditTrailPublisher.publish(
        "DIRECTOR_ADDRESS_UPDATED",
        "STORE_ADDRESS",
        String.valueOf(saved.getId()),
        "userId=" + directorId
    );
    return toAddressResponse(saved);
  }

  @Transactional
  public void deleteAddress(Long directorId, Long addressId) {
    loadDirector(directorId);
    StoreAddress address = storeAddressRepository.findByIdAndUserId(addressId, directorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Адрес не найден"));
    if (orderRepository.existsByDeliveryAddressIdAndStatusIn(addressId, ACTIVE_ADDRESS_LOCK_STATUSES)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Нельзя удалить адрес, пока по нему есть активные заказы"
      );
    }
    orderRepository.clearDeliveryAddressReference(addressId);
    storeAddressRepository.delete(address);
    auditTrailPublisher.publish(
        "DIRECTOR_ADDRESS_DELETED",
        "STORE_ADDRESS",
        String.valueOf(addressId),
        "userId=" + directorId
    );
  }

  @Transactional
  public StoreAddress getOwnedAddress(Long directorId, Long addressId) {
    loadDirector(directorId);
    return storeAddressRepository.findByIdAndUserId(addressId, directorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выбранный адрес не принадлежит пользователю"));
  }

  private User loadDirector(Long directorId) {
    User user = userRepository.findById(directorId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    if (user.getRole() != Role.DIRECTOR) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Этот маршрут доступен только директорам");
    }
    return user;
  }

  private ProfileResponse toProfileResponse(User user) {
    return new ProfileResponse(
        user.getId(),
        user.getUsername(),
        user.getFullName(),
        user.getPhone(),
        user.getLegalEntityName()
    );
  }

  private StoreAddressResponse toAddressResponse(StoreAddress address) {
    return new StoreAddressResponse(
        address.getId(),
        address.getLabel(),
        address.getAddressLine(),
        address.getLatitude(),
        address.getLongitude(),
        isAddressDeletable(address.getId())
    );
  }

  private boolean isAddressDeletable(Long addressId) {
    return addressId == null
        || !orderRepository.existsByDeliveryAddressIdAndStatusIn(addressId, ACTIVE_ADDRESS_LOCK_STATUSES);
  }

  private Coordinates resolveCoordinates(BigDecimal latitude, BigDecimal longitude, String addressLine) {
    if (latitude != null && longitude != null) {
      return new Coordinates(latitude, longitude);
    }
    return geocodingService.geocodeFirst(addressLine)
        .map(value -> new Coordinates(value.latitude(), value.longitude()))
        .orElse(new Coordinates(latitude, longitude));
  }

  private String normalizeNullable(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private record Coordinates(BigDecimal latitude, BigDecimal longitude) {
  }
}
