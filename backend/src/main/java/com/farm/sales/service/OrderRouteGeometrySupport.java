package com.farm.sales.service;

import com.farm.sales.dto.AutoAssignRoutePathPointResponse;
import com.farm.sales.dto.AutoAssignRouteTripResponse;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

final class OrderRouteGeometrySupport {
  private final RoadRoutingService roadRoutingService;
  private final Logger log;
  private final AutoAssignRoutePathPointResponse depotPoint;

  OrderRouteGeometrySupport(RoadRoutingService roadRoutingService, Logger log, double depotLatitude, double depotLongitude) {
    this.roadRoutingService = roadRoutingService;
    this.log = log;
    this.depotPoint = new AutoAssignRoutePathPointResponse(depotLatitude, depotLongitude);
  }

  AutoAssignRoutePathPointResponse depotPoint() {
    return depotPoint;
  }

  List<AutoAssignRoutePathPointResponse> buildPreviewRoutePathFromCoordinates(
      List<AutoAssignRoutePathPointResponse> points,
      boolean returnsToDepot
  ) {
    if (points == null || points.isEmpty()) {
      return List.of();
    }

    List<RoadRoutingService.RouteCoordinate> waypoints = new ArrayList<>(points.size() + (returnsToDepot ? 2 : 1));
    waypoints.add(toRouteCoordinate(depotPoint));
    points.forEach(point -> waypoints.add(toRouteCoordinate(point)));
    if (returnsToDepot) {
      waypoints.add(toRouteCoordinate(depotPoint));
    }

    try {
      return roadRoutingService.drivingRouteGeometry(waypoints).stream()
          .map(point -> new AutoAssignRoutePathPointResponse(point.latitude(), point.longitude()))
          .toList();
    } catch (RuntimeException exception) {
      log.warn("Road route geometry unavailable for preview, keeping stop markers only: {}", exception.getMessage());
      return List.of();
    }
  }

  List<AutoAssignRoutePathPointResponse> buildPreviewTripPath(
      List<AutoAssignRoutePathPointResponse> points,
      AutoAssignRoutePathPointResponse startPoint,
      boolean returnsToDepot
  ) {
    if (points == null || points.isEmpty()) {
      return List.of();
    }

    AutoAssignRoutePathPointResponse tripStart = startPoint == null ? depotPoint : startPoint;
    List<AutoAssignRoutePathPointResponse> path = new ArrayList<>(points.size() + (returnsToDepot ? 2 : 1));
    path.add(tripStart);
    for (AutoAssignRoutePathPointResponse point : points) {
      if (point == null || path.getLast().equals(point)) {
        continue;
      }
      path.add(point);
    }
    if (returnsToDepot && !path.getLast().equals(depotPoint)) {
      path.add(depotPoint);
    }
    return List.copyOf(path);
  }

  List<AutoAssignRoutePathPointResponse> flattenPreviewRoutePaths(List<AutoAssignRouteTripResponse> trips) {
    if (trips == null || trips.isEmpty()) {
      return List.of();
    }

    List<AutoAssignRoutePathPointResponse> flattened = new ArrayList<>();
    for (AutoAssignRouteTripResponse trip : trips) {
      if (trip == null || trip.path() == null || trip.path().isEmpty()) {
        continue;
      }
      if (flattened.isEmpty()) {
        flattened.addAll(trip.path());
        continue;
      }

      List<AutoAssignRoutePathPointResponse> segment = trip.path();
      if (flattened.getLast().equals(segment.getFirst())) {
        flattened.addAll(segment.subList(1, segment.size()));
      } else {
        flattened.addAll(segment);
      }
    }
    return List.copyOf(flattened);
  }

  private RoadRoutingService.RouteCoordinate toRouteCoordinate(AutoAssignRoutePathPointResponse point) {
    return new RoadRoutingService.RouteCoordinate(point.latitude(), point.longitude());
  }
}
