package com.farm.sales.dto;

import java.util.List;

public record AutoAssignPreviewRequest(
    List<Long> driverIds
) {
}
