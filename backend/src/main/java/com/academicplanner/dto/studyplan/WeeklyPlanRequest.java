package com.academicplanner.dto.studyplan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * Request body for generating a weekly study plan.
 * availability maps DayOfWeek name (e.g. "MONDAY") to available hours.
 */
public record WeeklyPlanRequest(
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        /** Key: DayOfWeek name (MONDAY…SUNDAY), Value: available hours that day. */
        @NotNull(message = "Availability map is required")
        Map<String, Double> availability
) {}
