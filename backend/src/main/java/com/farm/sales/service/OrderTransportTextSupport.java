package com.farm.sales.service;

import com.farm.sales.dto.AutoAssignRoutePointResponse;
import com.farm.sales.dto.AutoAssignRouteTripResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class OrderTransportTextSupport {
  private final double vehicleMaxWeightKg;
  private final double vehicleMaxVolumeM3;

  OrderTransportTextSupport(double vehicleMaxWeightKg, double vehicleMaxVolumeM3) {
    this.vehicleMaxWeightKg = vehicleMaxWeightKg;
    this.vehicleMaxVolumeM3 = vehicleMaxVolumeM3;
  }

  List<String> buildRouteInsights(List<AutoAssignRouteTripResponse> trips) {
    if (trips == null || trips.isEmpty()) {
      return List.of("В текущем плане этому водителю не назначено ни одной точки.");
    }

    List<String> insights = new ArrayList<>();
    insights.add(
        "Точки распределялись одновременно между выбранными водителями: на каждом шаге система выбирала тот активный рейс, "
            + "для которого добавление точки давало наименьший прирост пути и не нарушало ограничения "
            + formatOneDecimal(vehicleMaxWeightKg)
            + " кг / "
            + formatOneDecimal(vehicleMaxVolumeM3)
            + " м³."
    );
    insights.add(
        "После распределения порядок точек внутри каждого рейса был упорядочен по ближайшей следующей остановке, "
            + "чтобы убрать лишние переезды между дальними адресами."
    );
    if (trips.size() > 1) {
      insights.add(
          "Для этого водителя открыто "
              + formatCountWithNoun(trips.size(), "рейс", "рейса", "рейсов")
              + ", потому что весь объём не помещался в один выезд."
      );
    } else {
      insights.add("Все точки этого водителя поместились в один рейс без повторной загрузки.");
    }
    return List.copyOf(insights);
  }

  List<String> buildTripInsights(
      int tripNumber,
      List<AutoAssignRoutePointResponse> tripPoints,
      double weightUtilizationPercent,
      double volumeUtilizationPercent
  ) {
    List<String> insights = new ArrayList<>();
    insights.add(
        tripNumber <= 1
            ? "Рейс начинается на складе."
            : "Рейс начинается на складе после возврата и повторной загрузки."
    );
    insights.add(
        tripPoints.size() <= 1
            ? "В этом рейсе одна точка, поэтому дополнительных перестановок внутри рейса не требовалось."
            : "Точки в рейсе идут в порядке ближайшей следующей подходящей остановки."
    );
    insights.add(
        "Заполнение рейса: "
            + formatOneDecimal(weightUtilizationPercent)
            + "% по весу и "
            + formatOneDecimal(volumeUtilizationPercent)
            + "% по объёму."
    );
    insights.add(
        "После последней точки этого рейса машина возвращается на склад, и это плечо включено в суммарный километраж."
    );
    return List.copyOf(insights);
  }

  List<String> buildPlanningHighlights(int driverCount, boolean approximatePlanningDistances) {
    List<String> highlights = new ArrayList<>();
    highlights.add(
        "Заказы распределяются одновременно между "
            + driverCount
            + " водителями: на каждом шаге точка достаётся тому активному рейсу, которому она добавляет минимальный прирост полного пути с учётом возврата на склад."
    );
    highlights.add(
        "На каждый рейс действуют ограничения "
            + formatOneDecimal(vehicleMaxWeightKg)
            + " кг и "
            + formatOneDecimal(vehicleMaxVolumeM3)
            + " м³. Если оставшиеся точки уже не помещаются, для них открывается следующий рейс от склада."
    );
    return List.copyOf(highlights);
  }

  private String formatCountWithNoun(int count, String singular, String paucal, String plural) {
    int normalized = Math.max(0, count);
    int mod10 = normalized % 10;
    int mod100 = normalized % 100;
    if (mod10 == 1 && mod100 != 11) {
      return normalized + " " + singular;
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
      return normalized + " " + paucal;
    }
    return normalized + " " + plural;
  }

  private String formatOneDecimal(double value) {
    BigDecimal normalized = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
    return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
  }
}
