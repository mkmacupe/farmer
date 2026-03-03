package com.farm.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record AutoAssignApproveRequest(
    @NotEmpty List<@Valid AutoAssignApproveItemRequest> assignments
) {
}
