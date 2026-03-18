export const ROUTE_COLORS = ['#4f7399', '#b07a63', '#5a8a78', '#9b6b78', '#8f7458', '#6f739c'];

const TRIP_VISUAL_VARIANTS = [
  {
    opacity: 0.96,
    weight: 6,
    transform: (baseColor) => baseColor
  },
  {
    opacity: 0.98,
    weight: 6,
    transform: (baseColor) => mixColor(baseColor, '#24313f', 0.22)
  },
  {
    opacity: 0.98,
    weight: 6,
    transform: (baseColor) => mixColor(baseColor, '#f2eadf', 0.22)
  },
  {
    opacity: 0.98,
    weight: 6,
    transform: (baseColor) => mixColor(baseColor, '#57483f', 0.56)
  }
];

function normalizeHexColor(hexColor) {
  const normalized = String(hexColor || '').replace('#', '');
  return /^[0-9a-f]{6}$/i.test(normalized) ? normalized : null;
}

function parseRgbColor(color) {
  const match = String(color || '').match(/^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/i);
  if (!match) {
    return null;
  }

  const channels = match.slice(1).map((value) => Number(value));
  return channels.every((channel) => Number.isInteger(channel) && channel >= 0 && channel <= 255)
    ? channels
    : null;
}

function parseColorChannels(color) {
  const hexColor = normalizeHexColor(color);
  if (hexColor) {
    return [
      parseInt(hexColor.slice(0, 2), 16),
      parseInt(hexColor.slice(2, 4), 16),
      parseInt(hexColor.slice(4, 6), 16)
    ];
  }

  return parseRgbColor(color);
}

function mixChannel(channel, target, ratio) {
  return Math.round(channel + (target - channel) * ratio);
}

function mixColor(sourceColor, targetColor, ratio) {
  const source = parseColorChannels(sourceColor);
  const target = parseColorChannels(targetColor);
  if (!source || !target) {
    return sourceColor;
  }

  const safeRatio = Math.max(0, Math.min(1, Number(ratio) || 0));
  return `rgb(${mixChannel(source[0], target[0], safeRatio)}, ${mixChannel(source[1], target[1], safeRatio)}, ${mixChannel(source[2], target[2], safeRatio)})`;
}

export function routeColor(routeIndex) {
  return ROUTE_COLORS[((routeIndex % ROUTE_COLORS.length) + ROUTE_COLORS.length) % ROUTE_COLORS.length];
}

export function tripStyle(routeIndex, tripNumber = 1) {
  const normalizedTripNumber = Number.isFinite(Number(tripNumber)) && Number(tripNumber) > 0
    ? Math.trunc(Number(tripNumber))
    : 1;
  const variantIndex = (normalizedTripNumber - 1) % TRIP_VISUAL_VARIANTS.length;
  const variant = TRIP_VISUAL_VARIANTS[variantIndex];
  const baseColor = routeColor(routeIndex);
  const cycleIndex = Math.floor((normalizedTripNumber - 1) / TRIP_VISUAL_VARIANTS.length);
  let color = variant.transform(baseColor);

  if (cycleIndex > 0) {
    const cycleTarget = cycleIndex % 2 === 1 ? '#ffffff' : '#0f172a';
    const cycleRatio = Math.min(0.06 * cycleIndex, 0.18);
    color = mixColor(color, cycleTarget, cycleRatio);
  }

  return {
    baseColor,
    color,
    opacity: variant.opacity,
    tripNumber: normalizedTripNumber,
    weight: variant.weight
  };
}
