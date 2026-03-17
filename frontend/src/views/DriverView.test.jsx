import { buildDisplayRouteStops, buildOrderedRoute, buildRouteStops } from './DriverView.jsx';

function createOrder({
  id,
  status,
  latitude,
  longitude,
  address,
  routeTripNumber = 1,
  routeStopSequence,
  deliveredAt = null,
  assignedAt = '2026-03-17T08:00:00Z',
  createdAt = '2026-03-17T07:00:00Z'
}) {
  return {
    id,
    status,
    deliveryLatitude: latitude,
    deliveryLongitude: longitude,
    deliveryAddressText: address,
    routeTripNumber,
    routeStopSequence,
    deliveredAt,
    assignedAt,
    createdAt
  };
}

function buildDisplayedStops(orders) {
  return buildDisplayRouteStops(buildRouteStops(buildOrderedRoute(orders)));
}

describe('DriverView route stop sequencing', () => {
  it('keeps original stop numbers when delivered stops move below active ones', () => {
    const orders = [
      createOrder({
        id: 385,
        status: 'DELIVERED',
        latitude: 53.909,
        longitude: 30.333,
        address: 'Могилёв, ул. Миронова 9',
        routeStopSequence: 1,
        deliveredAt: '2026-03-17T10:00:00Z'
      }),
      createOrder({
        id: 384,
        status: 'ASSIGNED',
        latitude: 53.914,
        longitude: 30.341,
        address: 'Могилёв, ул. Первомайская 18',
        routeStopSequence: 2,
        assignedAt: '2026-03-17T08:15:00Z',
        createdAt: '2026-03-17T07:15:00Z'
      })
    ];

    const displayedStops = buildDisplayedStops(orders);

    expect(displayedStops.map((stop) => stop.displayStopNumber)).toEqual([2, 1]);
    expect(displayedStops[0].primaryOrder.orderId).toBe(384);
    expect(displayedStops[1].primaryOrder.orderId).toBe(385);
  });

  it('reuses one stable stop number for multiple orders at the same address', () => {
    const orders = [
      createOrder({
        id: 385,
        status: 'ASSIGNED',
        latitude: 53.909,
        longitude: 30.333,
        address: 'Могилёв, ул. Миронова 9',
        routeStopSequence: 1
      }),
      createOrder({
        id: 384,
        status: 'ASSIGNED',
        latitude: 53.909,
        longitude: 30.333,
        address: 'Могилёв, ул. Миронова 9',
        routeStopSequence: 1,
        assignedAt: '2026-03-17T08:20:00Z',
        createdAt: '2026-03-17T07:20:00Z'
      })
    ];

    const displayedStops = buildDisplayedStops(orders);

    expect(displayedStops).toHaveLength(1);
    expect(displayedStops[0].displayStopNumber).toBe(1);
    expect(displayedStops[0].orders.map((order) => order.orderId)).toEqual([384, 385]);
  });
});
