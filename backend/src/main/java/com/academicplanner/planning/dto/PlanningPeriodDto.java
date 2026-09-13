package com.academicplanner.planning.dto;

import java.time.LocalDate;

/**
 * Mirrors the Python planning service's `planningPeriod` field.
 * Field names match the JSON contract exactly (camelCase over the wire).
 */
public record PlanningPeriodDto(
        LocalDate startDate,
        LocalDate endDate
) {}
