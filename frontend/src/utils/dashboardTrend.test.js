import { buildTrendBadge, buildTrendChartModel } from "./dashboardTrend.js";

describe("dashboard trend helpers", () => {
  it("builds a readable chart model for sparse weekly data", () => {
    const trendSeries = [
      { key: "2026-03-11", label: "11.03", orders: 0 },
      { key: "2026-03-12", label: "12.03", orders: 0 },
      { key: "2026-03-13", label: "13.03", orders: 0 },
      { key: "2026-03-14", label: "14.03", orders: 0 },
      { key: "2026-03-15", label: "15.03", orders: 0 },
      { key: "2026-03-16", label: "16.03", orders: 0 },
      { key: "2026-03-17", label: "17.03", orders: 36 },
    ];

    const chart = buildTrendChartModel(trendSeries, 5.1);

    expect(chart.yMax).toBe(40);
    expect(chart.yTicks.map((tick) => tick.value)).toEqual([40, 30, 20, 10, 0]);
    expect(chart.ticks).toHaveLength(7);
    expect(chart.latestPoint?.key).toBe("2026-03-17");
    expect(chart.peakPoint?.key).toBe("2026-03-17");
    expect(chart.points.at(-1)?.showLabel).toBe(true);
    expect(chart.averageLineY).not.toBeNull();
  });

  it("returns an informative badge for positive, flat, and empty states", () => {
    expect(
      buildTrendBadge({
        latestOrders: 36,
        previousOrders: 0,
        momentum: 36,
        activeDays: 1,
        seriesLength: 7,
      }),
    ).toMatchObject({
      color: "success",
      direction: "up",
      label: "+36 к вчера",
    });

    expect(
      buildTrendBadge({
        latestOrders: 0,
        previousOrders: 0,
        momentum: 0,
        activeDays: 0,
        seriesLength: 7,
      }),
    ).toMatchObject({
      color: "default",
      direction: "flat",
      label: "Без заявок",
    });

    expect(
      buildTrendBadge({
        latestOrders: 5,
        previousOrders: 5,
        momentum: 0,
        activeDays: 3,
        seriesLength: 7,
      }),
    ).toMatchObject({
      color: "default",
      direction: "flat",
      label: "На уровне вчера",
    });
  });
});
