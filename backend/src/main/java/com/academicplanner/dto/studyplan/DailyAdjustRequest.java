package com.academicplanner.dto.studyplan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request to adjust today's available study hours.
 * The backend recalculates today's remaining schedule and moves
 * any displaced work to future days.
 */
public record DailyAdjustRequest(
        @NotNull(message = "New available hours are required")
        @DecimalMin(value = "0.0", message = "Available hours must be >= 0")
        Double newAvailableHours
) {}
