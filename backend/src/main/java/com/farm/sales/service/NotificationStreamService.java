package com.farm.sales.service;

import com.farm.sales.dto.RealtimeNotificationResponse;
import com.farm.sales.model.RealtimeNotification;
import com.farm.sales.repository.RealtimeNotificationRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationStreamService {
  private static final Logger logger = LoggerFactory.getLogger(NotificationStreamService.class);
  private static final long STREAM_TIMEOUT_MS = 0L;
  private static final int MAX_ACTIVE_SUBSCRIBERS = 1000;
  private static final int HEARTBEAT_SECONDS = 25;
  private static final int DISPATCH_SECONDS = 1;
  private static final int DISPATCH_BATCH_SIZE = 200;
  private final CopyOnWriteArrayList<Subscriber> subscribers = new CopyOnWriteArrayList<>();
  private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
  private final ScheduledExecutorService dispatchExecutor = Executors.newSingleThreadScheduledExecutor();
  private final RealtimeNotificationRepository notificationRepository;
  private final Clock clock;
  private final Duration notificationRetention;
  private final Duration cleanupInterval;
  private volatile Instant nextCleanupAt;

  @Autowired
  public NotificationStreamService(
      RealtimeNotificationRepository notificationRepository,
      @Value("${app.notifications.sse.retention:PT24H}") Duration notificationRetention,
      @Value("${app.notifications.sse.cleanup-interval:PT5M}") Duration cleanupInterval
  ) {
    this(notificationRepository, Clock.systemUTC(), notificationRetention, cleanupInterval);
  }

  NotificationStreamService(RealtimeNotificationRepository notificationRepository,
                            Clock clock,
                            Duration notificationRetention,
                            Duration cleanupInterval) {
    this.notificationRepository = notificationRepository;
    this.clock = clock;
    this.notificationRetention = sanitizeDuration(notificationRetention, Duration.ofHours(24));
    this.cleanupInterval = sanitizeDuration(cleanupInterval, Duration.ofMinutes(5));
  }

  @PostConstruct
  void initSchedulers() {
    nextCleanupAt = clock.instant();
    heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    dispatchExecutor.scheduleWithFixedDelay(
        this::dispatchPendingNotificationsSafely,
        DISPATCH_SECONDS,
        DISPATCH_SECONDS,
        TimeUnit.SECONDS
    );
  }

  public SseEmitter subscribe(Long userId, Set<String> roles) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    Subscriber subscriber = new Subscriber(userId, normalizeRoles(roles), emitter, new AtomicLong(initialCursor()));
    subscribers.add(subscriber);
    enforceSubscriberLimit();

    emitter.onCompletion(() -> subscribers.remove(subscriber));
    emitter.onTimeout(() -> {
      emitter.complete();
      subscribers.remove(subscriber);
    });
    emitter.onError(error -> subscribers.remove(subscriber));

    try {
      emitter.send(SseEmitter.event()
          .name("connected")
          .data(new RealtimeNotificationResponse(
              "CONNECTED",
              "Подключено",
              "Канал уведомлений подключён",
              null,
              null,
              Instant.now()
          )));
    } catch (IOException ex) {
      emitter.completeWithError(ex);
      subscribers.remove(subscriber);
    }

    return emitter;
  }

  private void enforceSubscriberLimit() {
    while (subscribers.size() > MAX_ACTIVE_SUBSCRIBERS) {
      if (subscribers.isEmpty()) {
        return;
      }
      Subscriber dropped = subscribers.remove(0);
      if (dropped == null) {
        return;
      }
      try {
        dropped.emitter().complete();
      } catch (Exception ignored) {
        // Ignore emitter completion issues for dropped subscribers.
      }
    }
  }

  public void publishToRoles(Set<String> targetRoles, RealtimeNotificationResponse notification) {
    publishToRolesAndUsers(targetRoles, Set.of(), notification);
  }

  public void publishToRolesAndUsers(Set<String> targetRoles,
                                     Set<Long> targetUserIds,
                                     RealtimeNotificationResponse notification) {
    Set<String> normalizedRoles = normalizeRoles(targetRoles);
    if (normalizedRoles.isEmpty()) {
      return;
    }

    RealtimeNotification event = new RealtimeNotification();
    event.setEventType(defaultIfBlank(notification.type(), "NOTIFICATION"));
    event.setTitle(defaultIfBlank(notification.title(), "Уведомление"));
    event.setMessage(defaultIfBlank(notification.message(), ""));
    event.setOrderId(notification.orderId());
    event.setOrderStatus(normalizeNullable(notification.status()));
    event.setTargetRoles(String.join(",", normalizedRoles));
    event.setTargetUserIds(encodeUserIds(targetUserIds));
    event.setCreatedAt(notification.createdAt() == null ? Instant.now() : notification.createdAt());
    notificationRepository.save(event);
  }

  private void dispatchPendingNotificationsSafely() {
    try {
      dispatchPendingNotifications();
    } catch (Exception ex) {
      logger.warn("Failed to dispatch realtime notifications: {}", ex.getMessage());
      logger.debug("Realtime notification dispatcher failure", ex);
    }
    try {
      cleanupExpiredNotificationsIfDue();
    } catch (Exception ex) {
      logger.warn("Failed to cleanup realtime notifications: {}", ex.getMessage());
      logger.debug("Realtime notification cleanup failure", ex);
    }
  }

  private void dispatchPendingNotifications() {
    if (subscribers.isEmpty()) {
      return;
    }

    while (true) {
      var batch = notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(oldestSubscriberCursor());
      if (batch.isEmpty()) {
        return;
      }

      for (RealtimeNotification event : batch) {
        dispatchEvent(event);
      }

      if (batch.size() < DISPATCH_BATCH_SIZE) {
        return;
      }
    }
  }

  private void dispatchEvent(RealtimeNotification event) {
    Long eventId = event.getId();
    if (eventId == null) {
      return;
    }

    Set<String> targetRoles = decodeRoles(event.getTargetRoles());
    if (targetRoles.isEmpty()) {
      markEventSeen(eventId);
      return;
    }
    Set<Long> targetUserIds = decodeUserIds(event.getTargetUserIds());
    RealtimeNotificationResponse notification = toResponse(event);

    for (Subscriber subscriber : subscribers) {
      if (subscriber.lastSeenNotificationId().get() >= eventId) {
        continue;
      }
      if (!isRoleMatched(subscriber.roles(), targetRoles)) {
        subscriber.lastSeenNotificationId().set(eventId);
        continue;
      }
      if (!isUserMatched(subscriber.userId(), targetUserIds)) {
        subscriber.lastSeenNotificationId().set(eventId);
        continue;
      }
      try {
        subscriber.emitter().send(SseEmitter.event().name("notification").data(notification));
        subscriber.lastSeenNotificationId().set(eventId);
      } catch (IOException ex) {
        subscriber.emitter().completeWithError(ex);
        subscribers.remove(subscriber);
      }
    }
  }

  private void cleanupExpiredNotificationsIfDue() {
    Instant now = clock.instant();
    if (nextCleanupAt != null && now.isBefore(nextCleanupAt)) {
      return;
    }
    cleanupExpiredNotifications();
    nextCleanupAt = now.plus(cleanupInterval);
  }

  private void cleanupExpiredNotifications() {
    long cleanupUpperBound = subscribers.isEmpty()
        ? notificationRepository.findMaxId().orElse(0L)
        : oldestSubscriberCursor();
    if (cleanupUpperBound <= 0L) {
      return;
    }
    notificationRepository.deleteByCreatedAtBeforeAndIdLessThanEqual(retentionCutoff(), cleanupUpperBound);
  }

  private long initialCursor() {
    return notificationRepository.findEarliestRetainedId(retentionCutoff())
        .map(id -> Math.max(0L, id - 1L))
        .orElseGet(() -> notificationRepository.findMaxId().orElse(0L));
  }

  private long oldestSubscriberCursor() {
    long cursor = Long.MAX_VALUE;
    for (Subscriber subscriber : subscribers) {
      cursor = Math.min(cursor, subscriber.lastSeenNotificationId().get());
    }
    return cursor == Long.MAX_VALUE ? 0L : cursor;
  }

  private void markEventSeen(Long eventId) {
    for (Subscriber subscriber : subscribers) {
      subscriber.lastSeenNotificationId().updateAndGet(current -> Math.max(current, eventId));
    }
  }

  private RealtimeNotificationResponse toResponse(RealtimeNotification event) {
    return new RealtimeNotificationResponse(
        event.getEventType(),
        event.getTitle(),
        event.getMessage(),
        event.getOrderId(),
        event.getOrderStatus(),
        event.getCreatedAt()
    );
  }

  private boolean isUserMatched(Long subscriberUserId, Set<Long> targetUserIds) {
    if (targetUserIds == null || targetUserIds.isEmpty()) {
      return true;
    }
    return subscriberUserId != null && targetUserIds.contains(subscriberUserId);
  }

  private boolean isRoleMatched(Set<String> subscriberRoles, Set<String> targetRoles) {
    for (String role : subscriberRoles) {
      if (targetRoles.contains(role)) {
        return true;
      }
    }
    return false;
  }

  private void sendHeartbeat() {
    Instant now = Instant.now();
    for (Subscriber subscriber : subscribers) {
      try {
        subscriber.emitter().send(SseEmitter.event()
            .name("heartbeat")
            .data(now.toString()));
      } catch (IOException ex) {
        subscriber.emitter().completeWithError(ex);
        subscribers.remove(subscriber);
      }
    }
  }

  private Set<String> normalizeRoles(Set<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return Set.of();
    }
    return roles.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.toUpperCase(Locale.ROOT))
        .distinct()
        .sorted()
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private Set<String> decodeRoles(String encodedRoles) {
    if (encodedRoles == null || encodedRoles.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(encodedRoles.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(value -> value.toUpperCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String encodeUserIds(Set<Long> targetUserIds) {
    if (targetUserIds == null || targetUserIds.isEmpty()) {
      return null;
    }
    String encoded = targetUserIds.stream()
        .filter(Objects::nonNull)
        .sorted()
        .map(String::valueOf)
        .collect(Collectors.joining(","));
    return encoded.isBlank() ? null : encoded;
  }

  private Set<Long> decodeUserIds(String encodedUserIds) {
    if (encodedUserIds == null || encodedUserIds.isBlank()) {
      return Set.of();
    }

    Set<Long> decoded = new LinkedHashSet<>();
    for (String value : encodedUserIds.split(",")) {
      String candidate = value.trim();
      if (candidate.isEmpty()) {
        continue;
      }
      try {
        decoded.add(Long.parseLong(candidate));
      } catch (NumberFormatException ignored) {
        // Ignore malformed values, continue with valid user ids.
      }
    }
    return decoded;
  }

  private String normalizeNullable(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String defaultIfBlank(String raw, String fallback) {
    String normalized = normalizeNullable(raw);
    return normalized == null ? fallback : normalized;
  }

  private Instant retentionCutoff() {
    return clock.instant().minus(notificationRetention);
  }

  private Duration sanitizeDuration(Duration candidate, Duration fallback) {
    if (candidate == null || candidate.isNegative() || candidate.isZero()) {
      return fallback;
    }
    return candidate;
  }

  @PreDestroy
  void shutdown() {
    heartbeatExecutor.shutdownNow();
    dispatchExecutor.shutdownNow();
  }

  private record Subscriber(Long userId,
                            Set<String> roles,
                            SseEmitter emitter,
                            AtomicLong lastSeenNotificationId) {
  }
}
