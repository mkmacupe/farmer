package com.farm.sales.service;

import com.farm.sales.audit.AuditTrailPublisher;
import com.farm.sales.dto.RealtimeNotificationResponse;
import com.farm.sales.model.Order;
import com.farm.sales.model.OrderItem;
import com.farm.sales.model.OrderStatus;
import com.farm.sales.model.StockMovementType;
import com.farm.sales.model.User;
import io.micrometer.core.instrument.Metrics;
import java.time.Instant;
import java.util.Set;

final class OrderLifecyclePublisher {
  private final AuditTrailPublisher auditTrailPublisher;
  private final StockMovementService stockMovementService;
  private final OrderTimelineService orderTimelineService;
  private final NotificationStreamService notificationStreamService;

  OrderLifecyclePublisher(AuditTrailPublisher auditTrailPublisher,
                          StockMovementService stockMovementService,
                          OrderTimelineService orderTimelineService,
                          NotificationStreamService notificationStreamService) {
    this.auditTrailPublisher = auditTrailPublisher;
    this.stockMovementService = stockMovementService;
    this.orderTimelineService = orderTimelineService;
    this.notificationStreamService = notificationStreamService;
  }

  void publishCreated(Order saved) {
    orderTimelineService.recordCreation(saved);
    for (OrderItem item : saved.getItems()) {
      stockMovementService.record(
          item.getProduct(),
          saved,
          StockMovementType.OUTBOUND,
          -item.getQuantity(),
          "ORDER_CREATED"
      );
    }

    auditTrailPublisher.publish(
        "ORDER_CREATED",
        "ORDER",
        String.valueOf(saved.getId()),
        "items=" + saved.getItems().size() + ",total=" + saved.getTotalAmount()
    );
    Metrics.counter("farm.sales.orders.created").increment();
    notificationStreamService.publishToRoles(
        Set.of("MANAGER"),
        new RealtimeNotificationResponse(
            "ORDER_CREATED",
            "Новая заявка на доставку",
            "Заказ №" + saved.getId() + " создан пользователем " + saved.getCustomer().getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
  }

  void publishApproved(Order saved, User manager, OrderStatus previousStatus) {
    orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());
    auditTrailPublisher.publish(
        "ORDER_APPROVED",
        "ORDER",
        String.valueOf(saved.getId()),
        "managerId=" + manager.getId()
    );
    Metrics.counter("farm.sales.orders.approved").increment();
    notificationStreamService.publishToRoles(
        Set.of("LOGISTICIAN"),
        new RealtimeNotificationResponse(
            "ORDER_APPROVED",
            "Заказ одобрен",
            "Заказ №" + saved.getId() + " одобрен менеджером",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
        new RealtimeNotificationResponse(
            "ORDER_APPROVED",
            "Заказ одобрен",
            "Заказ №" + saved.getId() + " одобрен менеджером",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
  }

  void publishAssigned(Order saved,
                       User logistician,
                       User driver,
                       User previousDriver,
                       OrderStatus previousStatus) {
    if (previousStatus != saved.getStatus()) {
      orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());
    }

    String auditDetails = "driverId=" + driver.getId() + ",logisticianId=" + logistician.getId();
    if (previousDriver != null && !previousDriver.getId().equals(driver.getId())) {
      auditDetails += ",previousDriverId=" + previousDriver.getId();
    }

    auditTrailPublisher.publish(
        "ORDER_DRIVER_ASSIGNED",
        "ORDER",
        String.valueOf(saved.getId()),
        auditDetails
    );
    Metrics.counter("farm.sales.orders.assigned").increment();
    notificationStreamService.publishToRoles(
        Set.of("MANAGER"),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DRIVER"),
        Set.of(driver.getId()),
        new RealtimeNotificationResponse(
            "ORDER_DRIVER_ASSIGNED",
            "Водитель назначен",
            "Заказ №" + saved.getId() + " назначен водителю " + driver.getFullName(),
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
  }

  void publishDelivered(Order saved, User driver, OrderStatus previousStatus) {
    orderTimelineService.recordStatusChange(saved, previousStatus, saved.getStatus());
    auditTrailPublisher.publish(
        "ORDER_DELIVERED",
        "ORDER",
        String.valueOf(saved.getId()),
        "driverId=" + driver.getId()
    );
    Metrics.counter("farm.sales.orders.delivered").increment();
    notificationStreamService.publishToRoles(
        Set.of("MANAGER", "LOGISTICIAN"),
        new RealtimeNotificationResponse(
            "ORDER_DELIVERED",
            "Заказ доставлен",
            "Заказ №" + saved.getId() + " отмечен как доставленный",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
    notificationStreamService.publishToRolesAndUsers(
        Set.of("DIRECTOR"),
        Set.of(saved.getCustomer().getId()),
        new RealtimeNotificationResponse(
            "ORDER_DELIVERED",
            "Заказ доставлен",
            "Заказ №" + saved.getId() + " отмечен как доставленный",
            saved.getId(),
            saved.getStatus().name(),
            Instant.now()
        )
    );
  }
}
