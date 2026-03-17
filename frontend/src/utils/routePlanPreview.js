import { tripStyle } from './routeColors.js';

export function normalizeTripNumber(value) {
  return Number.isFinite(Number(value)) && Number(value) > 0
    ? Math.trunc(Number(value))
    : 1;
}

export function createTripKey(routeKey, tripNumber) {
  return `${routeKey}:${normalizeTripNumber(tripNumber)}`;
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
