import {
  buildRoutePlanPreview,
  buildTripLegendEntries,
  collectTripNumbers,
  filterRouteByTrip,
  normalizeTripNumber,
  selectVisiblePlan
} from './routePlanPreview.js';

describe('route plan preview helpers', () => {
  const plan = {
    depotLatitude: 53.9,
    depotLongitude: 30.33,
    routes: [
      {
        driverId: 1,
        driverName: 'Водитель 1',
        colorIndex: 0,
        path: [[53.9, 30.33], [53.91, 30.34]],
        trips: [
          { tripNumber: 1, assignedOrders: 2 },
          { tripNumber: 2, assignedOrders: 3 }
        ],
        displayStops: [
          { tripNumber: 1, deliveryAddress: 'A' },
          { tripNumber: 2, deliveryAddress: 'B' }
        ],
        points: [
          { tripNumber: 1, orderId: 101 },
          { tripNumber: 2, orderId: 102 }
        ]
      },
      {
        driverId: 2,
        driverName: 'Водитель 2',
        colorIndex: 1,
        trips: [{ tripNumber: 1, assignedOrders: 1 }],
        displayStops: [{ tripNumber: 1, deliveryAddress: 'C' }],
        points: [{ tripNumber: 1, orderId: 103 }]
      }
    ]
  };

  it('normalizes trip numbers and collects them predictably', () => {
    expect(normalizeTripNumber('2')).toBe(2);
    expect(normalizeTripNumber(0)).toBe(1);
    expect(collectTripNumbers(plan.routes[0])).toEqual([1, 2]);
  });

  it('filters route and visible plan by selected trip', () => {
    const filteredRoute = filterRouteByTrip(plan.routes[0], 2);
    expect(filteredRoute.trips).toHaveLength(1);
    expect(filteredRoute.displayStops).toHaveLength(1);
    expect(filteredRoute.points).toHaveLength(1);
    expect(filteredRoute.path).toEqual([]);
    expect(filteredRoute.trips[0].tripNumber).toBe(2);

    const visiblePlan = selectVisiblePlan(plan, 1, 2);
    expect(visiblePlan.routes).toHaveLength(1);
    expect(visiblePlan.routes[0].driverId).toBe(1);
    expect(visiblePlan.routes[0].displayStops[0].tripNumber).toBe(2);
  });

  it('builds legend entries for visible scope', () => {
    expect(buildTripLegendEntries(plan, 'all', 'all')).toHaveLength(3);
    expect(buildTripLegendEntries(plan, 1, 'all').map((entry) => entry.label)).toEqual([
      'Рейс 1',
      'Рейс 2'
    ]);
    expect(buildTripLegendEntries(plan, 1, 2).map((entry) => entry.tripNumber)).toEqual([2]);
  });

  it('preserves order contents inside aggregated display stops', () => {
    const preview = buildRoutePlanPreview({
      routes: [
        {
          driverId: 1,
          driverName: 'Водитель 1',
          points: [
            {
              orderId: 201,
              tripNumber: 1,
              stopSequence: 1,
              deliveryAddress: 'Могилёв, адрес 1',
              latitude: 53.9,
              longitude: 30.33,
              distanceFromPreviousKm: 1.2,
              selectionReason: 'Ближайшая точка',
              items: [{ productId: 1, productName: 'Молоко', quantity: 3 }]
            },
            {
              orderId: 202,
              tripNumber: 1,
              stopSequence: 2,
              deliveryAddress: 'Могилёв, адрес 1',
              latitude: 53.9,
              longitude: 30.33,
              distanceFromPreviousKm: 0,
              selectionReason: 'Тот же адрес',
              items: [{ productId: 2, productName: 'Сыр', quantity: 1 }]
            }
          ],
          trips: [{ tripNumber: 1, assignedOrders: 2 }]
        }
      ]
    });

    expect(preview.routes[0].displayStops).toHaveLength(1);
    expect(preview.routes[0].displayStops[0].orders).toEqual([
      {
        orderId: 201,
        items: [{ productId: 1, productName: 'Молоко', quantity: 3, price: null, lineTotal: null }]
      },
      {
        orderId: 202,
        items: [{ productId: 2, productName: 'Сыр', quantity: 1, price: null, lineTotal: null }]
      }
    ]);
  });
});
