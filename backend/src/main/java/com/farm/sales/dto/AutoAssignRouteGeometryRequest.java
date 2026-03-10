package com.farm.sales.dto;

import java.util.List;

public record AutoAssignRouteGeometryRequest(
    List<AutoAssignRoutePathPointResponse> points
) {
}
