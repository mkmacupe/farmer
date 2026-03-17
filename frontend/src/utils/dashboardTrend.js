const DEFAULT_DIMENSIONS = Object.freeze({
  width: 680,
  height: 260,
  left: 54,
  right: 24,
  top: 24,
  bottom: 42,
});

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function niceIntegerStep(maxValue) {
  const safeMax = Math.max(1, maxValue);
  const roughStep = safeMax / 4;
  const magnitude = 10 ** Math.floor(Math.log10(Math.max(roughStep, 1)));
  const normalized = roughStep / magnitude;

  if (normalized <= 1) return magnitude;
  if (normalized <= 2) return 2 * magnitude;
  if (normalized <= 5) return 5 * magnitude;
  return 10 * magnitude;
}

function pickTickIndexes(points, peakPoint) {
  if (!points.length) {
    return [];
  }
  if (points.length <= 7) {
    return points.map((_, index) => index);
  }

  const indexes = new Set([0, points.length - 1, Math.floor((points.length - 1) / 2)]);
  indexes.add(Math.floor((points.length - 1) / 3));
  indexes.add(Math.floor(((points.length - 1) * 2) / 3));

  if (peakPoint) {
    const peakIndex = points.findIndex((point) => point.key === peakPoint.key);
    if (peakIndex >= 0) {
      indexes.add(peakIndex);
    }
  }

  return [...indexes]
    .filter((index) => index >= 0 && index < points.length)
    .sort((a, b) => a - b);
}

export function buildTrendBadge(meta) {
  const latestOrders = Number(meta?.latestOrders || 0);
  const previousOrders = Number(meta?.previousOrders || 0);
  const momentum = Number(meta?.momentum || 0);
  const activeDays = Number(meta?.activeDays || 0);
  const seriesLength = Number(meta?.seriesLength || 0);

  if (!seriesLength || activeDays === 0) {
    return {
      color: "default",
      direction: "flat",
      label: "Без заявок",
      variant: "outlined",
    };
  }

  if (seriesLength < 2) {
    return {
      color: "info",
      direction: "flat",
      label: "Старт периода",
      variant: "outlined",
    };
  }

  if (momentum > 0) {
    return {
      color: "success",
      direction: "up",
      label: `+${momentum} к вчера`,
      variant: "filled",
    };
  }

  if (momentum < 0) {
    return {
      color: "warning",
      direction: "down",
      label: `${momentum} к вчера`,
      variant: "filled",
    };
  }

  if (latestOrders === 0 && previousOrders === 0) {
    return {
      color: "default",
      direction: "flat",
      label: "На уровне нуля",
      variant: "outlined",
    };
  }

  return {
    color: "default",
    direction: "flat",
    label: "На уровне вчера",
    variant: "outlined",
  };
}

export function buildTrendChartModel(trendSeries = [], averageOrders = 0) {
  const { width, height, left, right, top, bottom } = DEFAULT_DIMENSIONS;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const baseY = top + plotHeight;
  const normalizedAverage = Number.isFinite(averageOrders) ? Math.max(0, averageOrders) : 0;
  const maxOrders = Math.max(0, ...trendSeries.map((point) => Number(point?.orders || 0)));
  const axisMaxSource = Math.max(maxOrders, Math.ceil(normalizedAverage));
  const step = niceIntegerStep(axisMaxSource || 1);
  const yMax = Math.max(step * 4, Math.ceil((axisMaxSource || 1) / step) * step);

  const rawPoints = trendSeries.map((point, index) => {
    const orders = Number(point?.orders || 0);
    const x =
      left +
      (trendSeries.length <= 1
        ? plotWidth / 2
        : (index / (trendSeries.length - 1)) * plotWidth);
    const y = baseY - (orders / yMax) * plotHeight;
    return {
      ...point,
      orders,
      x,
      y,
    };
  });

  const linePath = rawPoints
    .map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`)
    .join(" ");
  const areaPath =
    rawPoints.length > 1
      ? `${linePath} L ${rawPoints[rawPoints.length - 1].x} ${baseY} L ${rawPoints[0].x} ${baseY} Z`
      : "";

  const latestPoint = rawPoints.at(-1) || null;
  const peakPoint =
    rawPoints.reduce(
      (best, point) => (point.orders > (best?.orders ?? -1) ? point : best),
      null,
    ) || null;
  const nonZeroPoints = rawPoints.filter((point) => point.orders > 0);
  const labelKeys = new Set();

  if (nonZeroPoints.length > 0 && nonZeroPoints.length <= 3) {
    nonZeroPoints.forEach((point) => labelKeys.add(point.key));
  }
  if (latestPoint?.orders > 0) {
    labelKeys.add(latestPoint.key);
  }
  if (peakPoint?.orders > 0) {
    labelKeys.add(peakPoint.key);
  }

  const points = rawPoints.map((point) => {
    const valueLabel = String(point.orders);
    const labelWidth = Math.max(28, valueLabel.length * 8 + 14);
    const labelX = clamp(point.x - labelWidth / 2, left, width - right - labelWidth);
    const labelY = Math.max(top + 6, point.y - 28);
    return {
      ...point,
      isLatest: point.key === latestPoint?.key,
      isPeak: point.key === peakPoint?.key && (peakPoint?.orders || 0) > 0,
      showLabel: labelKeys.has(point.key),
      valueLabel,
      labelWidth,
      labelX,
      labelY,
    };
  });

  const ticks = pickTickIndexes(points, peakPoint).map((index) => points[index]);
  const yTicks = Array.from({ length: 5 }, (_, index) => {
    const value = yMax - step * index;
    return {
      key: `y-${value}`,
      value,
      y: baseY - (value / yMax) * plotHeight,
    };
  });

  return {
    width,
    height,
    left,
    right,
    top,
    bottom,
    baseY,
    yMax,
    points,
    linePath,
    areaPath,
    ticks,
    yTicks,
    latestPoint,
    peakPoint,
    averageLineY:
      normalizedAverage > 0
        ? baseY - (Math.min(normalizedAverage, yMax) / yMax) * plotHeight
        : null,
  };
}
