package com.farm.sales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.farm.sales.dto.RealtimeNotificationResponse;
import com.farm.sales.model.RealtimeNotification;
import com.farm.sales.repository.RealtimeNotificationRepository;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationStreamServiceTest {
  private static final Instant TEST_NOW = Instant.parse("2026-04-01T09:00:00Z");
  private static final Duration DEFAULT_RETENTION = Duration.ofHours(24);
  private static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(5);
  private RealtimeNotificationRepository notificationRepository;
  private NotificationStreamService service;

  @BeforeEach
  void setUp() {
    notificationRepository = mock(RealtimeNotificationRepository.class);
    when(notificationRepository.findEarliestRetainedId(any(Instant.class))).thenReturn(Optional.empty());
    when(notificationRepository.findMaxId()).thenReturn(Optional.of(0L));
    service = new NotificationStreamService(
        notificationRepository,
        Clock.fixed(TEST_NOW, ZoneOffset.UTC),
        DEFAULT_RETENTION,
        DEFAULT_CLEANUP_INTERVAL
    );
  }

  @Test
  void subscribeRegistersCallbacksAndRemovesSubscribersOnLifecycleEvents() throws Exception {
    try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
      SseEmitter first = service.subscribe(5L, Set.of("MANAGER"));
      assertThat(first).isSameAs(mocked.constructed().get(0));
      assertThat(subscribers()).hasSize(1);

      ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
      verify(first).onCompletion(completion.capture());
      completion.getValue().run();
      assertThat(subscribers()).isEmpty();

      SseEmitter second = service.subscribe(5L, Set.of("MANAGER"));
      assertThat(second).isSameAs(mocked.constructed().get(1));
      ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
      verify(second).onTimeout(timeout.capture());
      timeout.getValue().run();
      verify(second).complete();
      assertThat(subscribers()).isEmpty();

      SseEmitter third = service.subscribe(5L, Set.of("MANAGER"));
      assertThat(third).isSameAs(mocked.constructed().get(2));
      ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);
      verify(third).onError(errorCaptor.capture());
      errorCaptor.getValue().accept(new RuntimeException("boom"));
      assertThat(subscribers()).isEmpty();
    }
  }

  @Test
  void subscribeCompletesWithErrorWhenConnectedEventCannotBeSent() throws Exception {
    try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class, (emitter, context) ->
        doThrow(new IOException("io")).when(emitter).send(any(SseEmitter.SseEventBuilder.class))
    )) {
      service.subscribe(7L, Set.of("DIRECTOR"));

      SseEmitter emitter = mocked.constructed().getFirst();
      verify(emitter).completeWithError(any(IOException.class));
      assertThat(subscribers()).isEmpty();
    }
  }

  @Test
  void publishToRolesAndUsersNormalizesAndPersistsPayload() {
    RealtimeNotificationResponse notification = new RealtimeNotificationResponse(
        " ",
        " ",
        null,
        101L,
        "  ASSIGNED ",
        null
    );

    Set<Long> targetUserIds = new LinkedHashSet<>(Arrays.asList(10L, null, 3L));
    service.publishToRolesAndUsers(Set.of(" manager ", "", "DIRECTOR"), targetUserIds, notification);

    ArgumentCaptor<RealtimeNotification> captor = ArgumentCaptor.forClass(RealtimeNotification.class);
    verify(notificationRepository).save(captor.capture());
    RealtimeNotification saved = captor.getValue();
    assertThat(saved.getEventType()).isEqualTo("NOTIFICATION");
    assertThat(saved.getTitle()).isEqualTo("Уведомление");
    assertThat(saved.getMessage()).isEqualTo("");
    assertThat(saved.getOrderId()).isEqualTo(101L);
    assertThat(saved.getOrderStatus()).isEqualTo("ASSIGNED");
    assertThat(saved.getTargetRoles()).isEqualTo("DIRECTOR,MANAGER");
    assertThat(saved.getTargetUserIds()).isEqualTo("3,10");
    assertThat(saved.getCreatedAt()).isNotNull();

    service.publishToRoles(Set.of("LOGISTICIAN"), new RealtimeNotificationResponse(
        "TYPE", "Title", "Msg", null, null, Instant.now()
    ));
    verify(notificationRepository, org.mockito.Mockito.times(2)).save(any(RealtimeNotification.class));

    service.publishToRolesAndUsers(Set.of(), Set.of(1L), notification);
    verify(notificationRepository, org.mockito.Mockito.times(2)).save(any(RealtimeNotification.class));
  }

  @Test
  void dispatchPendingNotificationsSafelyHandlesRepositoryExceptions() throws Exception {
    when(notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(anyLong()))
        .thenThrow(new RuntimeException("fail"));
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), mock(SseEmitter.class)));

    invoke(service, "dispatchPendingNotificationsSafely", new Class<?>[] {});
  }

  @Test
  void dispatchPendingNotificationsReplaysRetainedEventsForLateSubscriber() throws Exception {
    RealtimeNotification event = new RealtimeNotification();
    event.setId(10L);
    event.setEventType("ORDER_CREATED");
    event.setTitle("Новый заказ");
    event.setMessage("msg");
    event.setTargetRoles("MANAGER");
    event.setCreatedAt(Instant.now());

    SseEmitter firstEmitter = mock(SseEmitter.class);
    SseEmitter lateEmitter = mock(SseEmitter.class);
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), firstEmitter));

    when(notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(anyLong()))
        .thenAnswer(invocation -> ((Long) invocation.getArgument(0)) >= 10L ? List.of() : List.of(event));

    invoke(service, "dispatchPendingNotifications", new Class<?>[] {});
    subscribers().add(newSubscriber(2L, Set.of("MANAGER"), lateEmitter));
    invoke(service, "dispatchPendingNotifications", new Class<?>[] {});

    verify(firstEmitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(lateEmitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void cleanupExpiredNotificationsUsesRetentionWindowAndOldestActiveCursor() throws Exception {
    when(notificationRepository.findMaxId()).thenReturn(Optional.of(80L));

    invoke(service, "cleanupExpiredNotifications", new Class<?>[] {});
    verify(notificationRepository)
        .deleteByCreatedAtBeforeAndIdLessThanEqual(TEST_NOW.minus(DEFAULT_RETENTION), 80L);

    org.mockito.Mockito.clearInvocations(notificationRepository);
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), mock(SseEmitter.class), 40L));

    invoke(service, "cleanupExpiredNotifications", new Class<?>[] {});
    verify(notificationRepository)
        .deleteByCreatedAtBeforeAndIdLessThanEqual(TEST_NOW.minus(DEFAULT_RETENTION), 40L);
  }

  @Test
  void dispatchPendingNotificationsLoopsUntilBatchShorterThan200() throws Exception {
    List<RealtimeNotification> fullBatch = new ArrayList<>();
    for (long id = 1; id <= 200; id++) {
      RealtimeNotification event = new RealtimeNotification();
      event.setId(id);
      event.setTargetRoles("");
      fullBatch.add(event);
    }
    when(notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(anyLong()))
        .thenReturn(fullBatch)
        .thenReturn(List.of());
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), mock(SseEmitter.class)));

    invoke(service, "dispatchPendingNotifications", new Class<?>[] {});

    verify(notificationRepository, org.mockito.Mockito.times(2))
        .findTop200ByIdGreaterThanOrderByIdAsc(anyLong());
    assertThat(lastSeenNotificationId(subscribers().get(0))).isEqualTo(200L);
  }

  @Test
  void dispatchPendingNotificationsReturnsImmediatelyForEmptyBatch() throws Exception {
    when(notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(anyLong())).thenReturn(List.of());
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), mock(SseEmitter.class)));

    invoke(service, "dispatchPendingNotifications", new Class<?>[] {});

    verify(notificationRepository).findTop200ByIdGreaterThanOrderByIdAsc(anyLong());
  }

  @Test
  void dispatchPendingNotificationsStopsWhenBatchSmallerThanLimit() throws Exception {
    RealtimeNotification event = new RealtimeNotification();
    event.setId(55L);
    event.setTargetRoles("");
    when(notificationRepository.findTop200ByIdGreaterThanOrderByIdAsc(anyLong())).thenReturn(List.of(event));
    subscribers().add(newSubscriber(1L, Set.of("MANAGER"), mock(SseEmitter.class)));

    invoke(service, "dispatchPendingNotifications", new Class<?>[] {});

    verify(notificationRepository).findTop200ByIdGreaterThanOrderByIdAsc(anyLong());
    assertThat(lastSeenNotificationId(subscribers().get(0))).isEqualTo(55L);
  }

  @Test
  void dispatchEventFiltersByRoleAndUserAndRemovesBrokenEmitters() throws Exception {
    SseEmitter matchedEmitter = mock(SseEmitter.class);
    SseEmitter wrongUserEmitter = mock(SseEmitter.class);
    SseEmitter wrongRoleEmitter = mock(SseEmitter.class);
    SseEmitter brokenEmitter = mock(SseEmitter.class);
    doThrow(new IOException("broken")).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));

    subscribers().add(newSubscriber(10L, Set.of("MANAGER"), matchedEmitter));
    subscribers().add(newSubscriber(99L, Set.of("MANAGER"), wrongUserEmitter));
    subscribers().add(newSubscriber(10L, Set.of("DRIVER"), wrongRoleEmitter));
    subscribers().add(newSubscriber(20L, Set.of("MANAGER"), brokenEmitter));

    RealtimeNotification event = new RealtimeNotification();
    event.setId(1L);
    event.setEventType("ORDER_CREATED");
    event.setTitle("Новый заказ");
    event.setMessage("msg");
    event.setOrderId(101L);
    event.setOrderStatus("CREATED");
    event.setTargetRoles("manager,director");
    event.setTargetUserIds("10,20,bad");
    event.setCreatedAt(Instant.now());

    invoke(service, "dispatchEvent", new Class<?>[] {RealtimeNotification.class}, event);

    verify(matchedEmitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(brokenEmitter).completeWithError(any(IOException.class));
    assertThat(subscribers()).hasSize(3);

    RealtimeNotification noRoles = new RealtimeNotification();
    noRoles.setTargetRoles("   ");
    invoke(service, "dispatchEvent", new Class<?>[] {RealtimeNotification.class}, noRoles);
    verify(wrongRoleEmitter, org.mockito.Mockito.never()).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  void utilityMethodsAndHeartbeatCoverAllBranchesAndShutdown() throws Exception {
    assertThat((Set<String>) invoke(service, "normalizeRoles", new Class<?>[] {Set.class}, (Object) null)).isEmpty();
    assertThat((Set<String>) invoke(service, "normalizeRoles", new Class<?>[] {Set.class}, Set.of(" manager ", "DRIVER")))
        .containsExactly("DRIVER", "MANAGER");

    assertThat((Set<String>) invoke(service, "decodeRoles", new Class<?>[] {String.class}, (Object) null)).isEmpty();
    assertThat((Set<String>) invoke(service, "decodeRoles", new Class<?>[] {String.class}, " manager, ,driver "))
        .containsExactly("MANAGER", "DRIVER");

    assertThat(invoke(service, "encodeUserIds", new Class<?>[] {Set.class}, (Object) null)).isNull();
    Set<Long> idsWithNull = new LinkedHashSet<>(Arrays.asList(4L, null, 1L));
    assertThat(invoke(service, "encodeUserIds", new Class<?>[] {Set.class}, idsWithNull)).isEqualTo("1,4");
    Set<Long> onlyNull = new LinkedHashSet<>(Arrays.asList((Long) null));
    assertThat(invoke(service, "encodeUserIds", new Class<?>[] {Set.class}, onlyNull)).isNull();
    assertThat((Set<Long>) invoke(service, "decodeUserIds", new Class<?>[] {String.class}, " , 3, bad, 7"))
        .containsExactly(3L, 7L);
    assertThat((Set<Long>) invoke(service, "decodeUserIds", new Class<?>[] {String.class}, (Object) null)).isEmpty();
    assertThat((Set<Long>) invoke(service, "decodeUserIds", new Class<?>[] {String.class}, "   ")).isEmpty();

    assertThat(invoke(service, "normalizeNullable", new Class<?>[] {String.class}, (Object) null)).isNull();
    assertThat(invoke(service, "normalizeNullable", new Class<?>[] {String.class}, "   ")).isNull();
    assertThat(invoke(service, "normalizeNullable", new Class<?>[] {String.class}, " value ")).isEqualTo("value");
    assertThat(invoke(service, "defaultIfBlank", new Class<?>[] {String.class, String.class}, " ", "fallback"))
        .isEqualTo("fallback");
    assertThat(invoke(service, "defaultIfBlank", new Class<?>[] {String.class, String.class}, "ok", "fallback"))
        .isEqualTo("ok");

    assertThat((Boolean) invoke(service, "isUserMatched", new Class<?>[] {Long.class, Set.class}, null, Set.of()))
        .isTrue();
    assertThat((Boolean) invoke(service, "isUserMatched", new Class<?>[] {Long.class, Set.class}, 1L, null))
        .isTrue();
    assertThat((Boolean) invoke(service, "isUserMatched", new Class<?>[] {Long.class, Set.class}, null, Set.of(1L)))
        .isFalse();
    assertThat((Boolean) invoke(service, "isUserMatched", new Class<?>[] {Long.class, Set.class}, 1L, Set.of(1L)))
        .isTrue();

    assertThat((Boolean) invoke(service, "isRoleMatched", new Class<?>[] {Set.class, Set.class}, Set.of("MANAGER"), Set.of("MANAGER")))
        .isTrue();
    assertThat((Boolean) invoke(service, "isRoleMatched", new Class<?>[] {Set.class, Set.class}, Set.of("DRIVER"), Set.of("MANAGER")))
        .isFalse();

    SseEmitter okEmitter = mock(SseEmitter.class);
    SseEmitter failedEmitter = mock(SseEmitter.class);
    doThrow(new IOException("hb")).when(failedEmitter).send(any(SseEmitter.SseEventBuilder.class));
    Object okSubscriber = newSubscriber(1L, Set.of("MANAGER"), okEmitter);
    Object failedSubscriber = newSubscriber(2L, Set.of("MANAGER"), failedEmitter);
    subscribers().add(okSubscriber);
    subscribers().add(failedSubscriber);
    invoke(service, "sendHeartbeat", new Class<?>[] {});
    verify(okEmitter).send(any(SseEmitter.SseEventBuilder.class));
    verify(failedEmitter).completeWithError(any(IOException.class));
    assertThat(subscribers()).contains(okSubscriber);
    assertThat(subscribers()).doesNotContain(failedSubscriber);

    Class<?> subscriberClass = Class.forName("com.farm.sales.service.NotificationStreamService$Subscriber");
    Method userIdAccessor = subscriberClass.getDeclaredMethod("userId");
    Method rolesAccessor = subscriberClass.getDeclaredMethod("roles");
    Method emitterAccessor = subscriberClass.getDeclaredMethod("emitter");
    assertThat(userIdAccessor.invoke(okSubscriber)).isEqualTo(1L);
    assertThat(rolesAccessor.invoke(okSubscriber)).isEqualTo(Set.of("MANAGER"));
    assertThat(emitterAccessor.invoke(okSubscriber)).isSameAs(okEmitter);

    invoke(service, "shutdown", new Class<?>[] {});
    assertThat(((java.util.concurrent.ScheduledExecutorService) field("heartbeatExecutor").get(service)).isShutdown()).isTrue();
    assertThat(((java.util.concurrent.ScheduledExecutorService) field("dispatchExecutor").get(service)).isShutdown()).isTrue();
  }

  @SuppressWarnings("unchecked")
  private CopyOnWriteArrayList<Object> subscribers() throws Exception {
    return (CopyOnWriteArrayList<Object>) field("subscribers").get(service);
  }

  private Field field(String name) throws Exception {
    Field field = NotificationStreamService.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private Object invoke(Object target, String methodName, Class<?>[] types, Object... args) throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, types);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private Object newSubscriber(Long userId, Set<String> roles, SseEmitter emitter) throws Exception {
    return newSubscriber(userId, roles, emitter, 0L);
  }

  private Object newSubscriber(Long userId,
                               Set<String> roles,
                               SseEmitter emitter,
                               long lastSeenNotificationId) throws Exception {
    Class<?> subscriberClass = Class.forName("com.farm.sales.service.NotificationStreamService$Subscriber");
    Constructor<?> constructor = subscriberClass.getDeclaredConstructor(Long.class, Set.class, SseEmitter.class, AtomicLong.class);
    constructor.setAccessible(true);
    return constructor.newInstance(userId, roles, emitter, new AtomicLong(lastSeenNotificationId));
  }

  private long lastSeenNotificationId(Object subscriber) throws Exception {
    Class<?> subscriberClass = Class.forName("com.farm.sales.service.NotificationStreamService$Subscriber");
    Method accessor = subscriberClass.getDeclaredMethod("lastSeenNotificationId");
    accessor.setAccessible(true);
    return ((AtomicLong) accessor.invoke(subscriber)).get();
  }
}
