import { ROUTE_COLORS, routeColor, tripStyle } from './routeColors.js';

function parseColorChannels(color) {
  const normalizedHex = String(color || '').replace('#', '');
  if (/^[0-9a-f]{6}$/i.test(normalizedHex)) {
    return [
      parseInt(normalizedHex.slice(0, 2), 16),
      parseInt(normalizedHex.slice(2, 4), 16),
      parseInt(normalizedHex.slice(4, 6), 16)
    ];
  }

  const rgbMatch = String(color || '').match(/^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/i);
  if (!rgbMatch) {
    throw new Error(`Unsupported color format: ${color}`);
  }

  return rgbMatch.slice(1).map((value) => Number(value));
}

function colorDistance(left, right) {
  const [leftR, leftG, leftB] = parseColorChannels(left);
  const [rightR, rightG, rightB] = parseColorChannels(right);
  return Math.abs(leftR - rightR) + Math.abs(leftG - rightG) + Math.abs(leftB - rightB);
}

describe('route color helpers', () => {
  it('cycles base route colors predictably', () => {
    expect(routeColor(0)).toBe(ROUTE_COLORS[0]);
    expect(routeColor(ROUTE_COLORS.length)).toBe(ROUTE_COLORS[0]);
    expect(routeColor(-1)).toBe(ROUTE_COLORS[ROUTE_COLORS.length - 1]);
  });

  it('assigns more distinct visuals to later trips of the same route', () => {
    ROUTE_COLORS.forEach((_, routeIndex) => {
      const firstTrip = tripStyle(routeIndex, 1);
      const secondTrip = tripStyle(routeIndex, 2);
      const thirdTrip = tripStyle(routeIndex, 3);
      const fourthTrip = tripStyle(routeIndex, 4);
      const fifthTrip = tripStyle(routeIndex, 5);
      const sixthTrip = tripStyle(routeIndex, 6);
      const firstCycleTrips = [firstTrip, secondTrip, thirdTrip, fourthTrip];

      expect(firstTrip.color).toBe(routeColor(routeIndex));
      expect(secondTrip.color).not.toBe(firstTrip.color);
      expect(thirdTrip.color).not.toBe(firstTrip.color);
      expect(thirdTrip.color).not.toBe(secondTrip.color);
      expect(fourthTrip.color).not.toBe(secondTrip.color);
      expect(fifthTrip.color).not.toBe(firstTrip.color);
      expect(sixthTrip.color).not.toBe(secondTrip.color);

      firstCycleTrips.forEach((trip, leftIndex) => {
        firstCycleTrips.slice(leftIndex + 1).forEach((otherTrip) => {
          expect(colorDistance(trip.color, otherTrip.color)).toBeGreaterThanOrEqual(25);
        });
      });
    });
  });
});
