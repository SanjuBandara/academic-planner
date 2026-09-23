package com.academicplanner.planning.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Full request body for POST /api/v1/plan on the Python planning service.
 * Matches the JSON contract in the Phase 1 spec (Section 11) exactly.
 */
public record PlanningRequestDto(
                PlanningPeriodDto planningPeriod,
                LocalDateTime currentDateTime,
                List<AvailabilityWindowDto> availability,
                List<ActivityDto> activities) {
}
