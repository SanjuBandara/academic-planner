package com.academicplanner.dto.studyplan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

/**
 * Request body for generating a monthly study plan.
 * Can accept a flat dailyAvailableHours (same every day) or a per-day map.
 */
public record MonthlyPlanRequest(
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        /** Flat daily availability — used when every day has the same hours. */
        @DecimalMin(value = "0.0", message = "Daily hours must be >= 0")
        Double dailyAvailableHours,

        /**
         * Per-day-of-week availability override.
         * If both dailyAvailableHours and this map are provided,
         * this map takes precedence for the days it specifies.
         */
        Map<String, Double> weeklyAvailability
) {}
