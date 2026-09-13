package com.academicplanner.planning.dto;

import java.util.List;

/**
 * Full response body from POST /api/v1/plan on the Python planning service.
 * {@code status} is one of OPTIMAL, FEASIBLE, INFEASIBLE, UNKNOWN.
 */
public record PlanningResponseDto(
        String status,
        List<SessionDto> sessions,
        List<String> warnings,
        StatisticsDto statistics) {
    public boolean isUsable() {
        return "OPTIMAL".equals(status) || "FEASIBLE".equals(status);
    }
}
