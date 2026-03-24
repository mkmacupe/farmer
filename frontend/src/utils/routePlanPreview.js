import { tripStyle } from './routeColors.js';

export function normalizeTripNumber(value) {
  return Number.isFinite(Number(value)) && Number(value) > 0
    ? Math.trunc(Number(value))
    : 1;
}

export function createTripKey(routeKey, tripNumber) {
  return `${routeKey}:${normalizeTripNumber(tripNumber)}`;
}

function countLabel(count, one, few, many) {
  const normalized = Number.isFinite(Number(count)) ? Math.max(0, Math.trunc(Number(count))) : 0;
  const mod10 = normalized % 10;
  const mod100 = normalized % 100;
  if (mod10 === 1 && mod100 !== 11) {
    return `${normalized} ${one}`;
  }
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
    return `${normalized} ${few}`;
  }
  return `${normalized} ${many}`;
}

function orderCountLabel(count) {
  return countLabel(count, 'заказ', 'заказа', 'заказов');
}

function routePointKey(point) {
  return [
    point?.orderId ?? 'order',
    Number(point?.tripNumber) || 1,
    Number(point?.stopSequence) || 0
  ].join(':');
}

function sameRouteStop(left, right) {
  return Boolean(left && right)
    && normalizeTripNumber(left?.tripNumber) === normalizeTripNumber(right?.tripNumber)
    && Number(left?.latitude) === Number(right?.latitude)
    && Number(left?.longitude) === Number(right?.longitude);
}

function formatStopOrderSummary(orderIds = []) {
  if (!Array.isArray(orderIds) || !orderIds.length) {
    return '';
  }
  return orderIds.map((orderId) => `#${orderId}`).join(', ');
}

function normalizeStopOrder(point) {
  return {
    orderId: point?.orderId ?? null,
    items: Array.isArray(point?.items)
      ? point.items.map((item) => ({
          productId: item?.productId ?? null,
          productName: item?.productName || 'Без названия',
          quantity: item?.quantity ?? 0,
          price: item?.price ?? null,
          lineTotal: item?.lineTotal ?? null
        }))
      : []
  };
}

export function enhanceRoutePreview(route) {
  if (!route || !Array.isArray(route.points)) {
    return route;
  }

  const sortedTrips = Array.isArray(route.trips)
    ? [...route.trips].sort(
        (left, right) => normalizeTripNumber(left?.tripNumber) - normalizeTripNumber(right?.tripNumber)
      )
    : [];
  const tripMetaByNumber = new Map(
    sortedTrips.map((trip) => [normalizeTripNumber(trip?.tripNumber), trip])
  );
  const orderedPoints = [...route.points].sort((left, right) => {
    const tripDiff = normalizeTripNumber(left?.tripNumber) - normalizeTripNumber(right?.tripNumber);
    if (tripDiff !== 0) {
      return tripDiff;
    }
    return (Number(left?.stopSequence) || 0) - (Number(right?.stopSequence) || 0);
  });

  const tripPointCounters = new Map();
  const displayPointNumbers = new Map();
  const displayStops = [];
  let currentDisplayStop = null;

  orderedPoints.forEach((point) => {
    const tripNumber = normalizeTripNumber(point?.tripNumber);
    if (!sameRouteStop(currentDisplayStop, point)) {
      const nextDisplayNumber = (tripPointCounters.get(tripNumber) || 0) + 1;
      tripPointCounters.set(tripNumber, nextDisplayNumber);
      currentDisplayStop = {
        stopKey: `${tripNumber}:${nextDisplayNumber}:${point.latitude}:${point.longitude}`,
        tripNumber,
        stopSequence: point.stopSequence,
        displayStopSequence: nextDisplayNumber,
        deliveryAddress: point.deliveryAddress,
        latitude: point.latitude,
        longitude: point.longitude,
        distanceFromPreviousKm: point.distanceFromPreviousKm,
        selectionReason: point.selectionReason,
        orderIds: [],
        orderCount: 0,
        cumulativeDistanceKm: 0,
        returnToDepotDistanceKm: 0,
        isLastStopInTrip: false,
        tripStopCount: 0,
        tripEstimatedRouteDistanceKm: 0,
        tripAssignedOrders: 0,
        tripTotalWeightKg: 0,
        tripTotalVolumeM3: 0,
        tripWeightUtilizationPercent: 0,
        tripVolumeUtilizationPercent: 0,
        returnsToDepot: false,
        orders: []
      };
      displayStops.push(currentDisplayStop);
    }

    displayPointNumbers.set(routePointKey(point), currentDisplayStop.displayStopSequence);
    currentDisplayStop.orderIds.push(point.orderId);
    currentDisplayStop.orderCount += 1;
    currentDisplayStop.orders.push(normalizeStopOrder(point));
  });

  const stopsByTrip = new Map();
  displayStops.forEach((stop) => {
    const tripNumber = normalizeTripNumber(stop?.tripNumber);
    const tripStops = stopsByTrip.get(tripNumber) || [];
    tripStops.push(stop);
    stopsByTrip.set(tripNumber, tripStops);
  });

  stopsByTrip.forEach((tripStops, tripNumber) => {
    const tripMeta = tripMetaByNumber.get(tripNumber);
    const tripDistanceKm = Number(tripMeta?.estimatedRouteDistanceKm) || 0;
    const tripAssignedOrders = Number(tripMeta?.assignedOrders) || 0;
    const tripTotalWeightKg = Number(tripMeta?.totalWeightKg) || 0;
    const tripTotalVolumeM3 = Number(tripMeta?.totalVolumeM3) || 0;
    const tripWeightUtilizationPercent = Number(tripMeta?.weightUtilizationPercent) || 0;
    const tripVolumeUtilizationPercent = Number(tripMeta?.volumeUtilizationPercent) || 0;
    const returnsToDepot = Boolean(tripMeta?.returnsToDepot);

    let cumulativeDistanceKm = 0;
    const totalLegDistanceKm = tripStops.reduce(
      (sum, stop) => sum + (Number(stop?.distanceFromPreviousKm) || 0),
      0
    );
    const returnToDepotDistanceKm = returnsToDepot
      ? Math.max(0, tripDistanceKm - totalLegDistanceKm)
      : 0;

    tripStops.forEach((stop, stopIndex) => {
      cumulativeDistanceKm += Number(stop?.distanceFromPreviousKm) || 0;
      Object.assign(stop, {
        cumulativeDistanceKm,
        returnToDepotDistanceKm: stopIndex === tripStops.length - 1 ? returnToDepotDistanceKm : 0,
        isLastStopInTrip: stopIndex === tripStops.length - 1,
        tripStopCount: tripStops.length,
        tripEstimatedRouteDistanceKm: tripDistanceKm,
        tripAssignedOrders: tripAssignedOrders || route.assignedOrders || stop.orderCount,
        tripTotalWeightKg,
        tripTotalVolumeM3,
        tripWeightUtilizationPercent,
        tripVolumeUtilizationPercent,
        returnsToDepot
      });
    });
  });

  const normalizedDisplayStops = displayStops.map((stop) => {
    const orderSummary = formatStopOrderSummary(stop.orderIds);
    const orderHint = stop.orderCount > 1
      ? ` На этом адресе объединены ${orderCountLabel(stop.orderCount)}.`
      : '';
    return {
      ...stop,
      orderSummary,
      selectionReason: `${stop.selectionReason || 'Точка включена в маршрут.'}${orderHint}`.trim()
    };
  });

  return {
    ...route,
    trips: sortedTrips.length ? sortedTrips : route.trips,
    displayStops: normalizedDisplayStops,
    points: orderedPoints.map((point) => ({
      ...point,
      displayStopSequence: displayPointNumbers.get(routePointKey(point)) ?? point.stopSequence
    }))
  };
}

export function buildRoutePlanPreview(plan) {
  if (!plan || !Array.isArray(plan.routes)) {
    return plan;
  }

  return {
    ...plan,
    routes: plan.routes.map((route, index) => ({
      ...enhanceRoutePreview(route),
      colorIndex: index
    }))
  };
}

export function extractRoutePlanAssignments(plan) {
  if (!plan || !Array.isArray(plan.routes)) {
    return [];
  }

  return plan.routes.flatMap((route) => {
    if (!route || !Array.isArray(route.points)) {
      return [];
    }
    return route.points.map((point) => ({
      orderId: point.orderId,
      driverId: route.driverId,
      tripNumber: point.tripNumber || 1,
      stopSequence: point.stopSequence,
      estimatedDistanceKm: point.distanceFromPreviousKm
    }));
  });
}

export function collectTripNumbers(route) {
  const tripNumbers = new Set();

  if (Array.isArray(route?.trips) && route.trips.length) {
    route.trips.forEach((trip) => {
      tripNumbers.add(normalizeTripNumber(trip?.tripNumber));
    });
  } else {
    (route?.displayStops || route?.points || []).forEach((point) => {
      tripNumbers.add(normalizeTripNumber(point?.tripNumber));
    });
  }

  return tripNumbers.size
    ? [...tripNumbers].sort((left, right) => left - right)
    : [1];
}

export function filterRouteByTrip(route, visibleTripNumber = 'all') {
  if (!route || visibleTripNumber === 'all') {
    return route;
  }

  const normalizedTripNumber = normalizeTripNumber(visibleTripNumber);
  const matchesTrip = (item) => normalizeTripNumber(item?.tripNumber) === normalizedTripNumber;
  const filteredTrips = Array.isArray(route?.trips)
    ? route.trips.filter(matchesTrip)
    : route?.trips;
  const filteredStops = Array.isArray(route?.displayStops)
    ? route.displayStops.filter(matchesTrip)
    : route?.displayStops;
  const filteredPoints = Array.isArray(route?.points)
    ? route.points.filter(matchesTrip)
    : route?.points;

  return {
    ...route,
    path: [],
    trips: filteredTrips,
    displayStops: filteredStops,
    points: filteredPoints
  };
}

export function selectVisiblePlan(plan, visibleDriverId = 'all', visibleTripNumber = 'all') {
  if (!plan || !Array.isArray(plan.routes)) {
    return plan;
  }

  const filteredRoutes = plan.routes
    .filter((route) => visibleDriverId === 'all' || String(route?.driverId) === String(visibleDriverId))
    .map((route) =>
      visibleDriverId === 'all'
        ? route
        : filterRouteByTrip(route, visibleTripNumber)
    )
    .filter((route) => {
      const tripsCount = Array.isArray(route?.trips) ? route.trips.length : 0;
      const stopsCount = Array.isArray(route?.displayStops) ? route.displayStops.length : 0;
      const pointsCount = Array.isArray(route?.points) ? route.points.length : 0;
      return tripsCount > 0 || stopsCount > 0 || pointsCount > 0;
    });

  return {
    ...plan,
    routes: filteredRoutes
  };
}

export function buildTripLegendEntries(plan, visibleDriverId = 'all', visibleTripNumber = 'all') {
  const visiblePlan = selectVisiblePlan(plan, visibleDriverId, visibleTripNumber);
  if (!visiblePlan || !Array.isArray(visiblePlan.routes)) {
    return [];
  }

  const includeDriverName = visibleDriverId === 'all';

  return visiblePlan.routes.flatMap((route, routeIndex) => {
    const colorIndex = Number.isInteger(route?.colorIndex) ? route.colorIndex : routeIndex;
    const routeKey = route?.driverId || routeIndex;

    return collectTripNumbers(route).map((tripNumber) => {
      const visual = tripStyle(colorIndex, tripNumber);
      return {
        key: createTripKey(routeKey, tripNumber),
        driverId: route?.driverId,
        tripNumber,
        label: includeDriverName
          ? `${route?.driverName || `Водитель ${routeIndex + 1}`} · Рейс ${tripNumber}`
          : `Рейс ${tripNumber}`,
        ...visual
      };
    });
  });
}
