import { ROUTE_COLORS, routeColor, tripStyle } from './routeColors.js';

describe('route color helpers', () => {
  it('cycles base route colors predictably', () => {
    expect(routeColor(0)).toBe(ROUTE_COLORS[0]);
    expect(routeColor(ROUTE_COLORS.length)).toBe(ROUTE_COLORS[0]);
    expect(routeColor(-1)).toBe(ROUTE_COLORS[ROUTE_COLORS.length - 1]);
  });

  it('assigns more distinct visuals to later trips of the same route', () => {
    const firstTrip = tripStyle(1, 1);
    const secondTrip = tripStyle(1, 2);
    const thirdTrip = tripStyle(1, 3);
    const fifthTrip = tripStyle(1, 5);
    const sixthTrip = tripStyle(1, 6);

    expect(firstTrip.color).toBe(routeColor(1));
    expect(firstTrip.dashArray).toBeUndefined();
    expect(secondTrip.color).not.toBe(firstTrip.color);
    expect(secondTrip.dashArray).toBe('16 10');
    expect(thirdTrip.color).not.toBe(firstTrip.color);
    expect(thirdTrip.dashArray).toBe('6 10');
    expect(fifthTrip.dashArray).toBe(firstTrip.dashArray);
    expect(fifthTrip.color).not.toBe(firstTrip.color);
    expect(sixthTrip.dashArray).toBe(secondTrip.dashArray);
    expect(sixthTrip.color).not.toBe(secondTrip.color);
  });
});
