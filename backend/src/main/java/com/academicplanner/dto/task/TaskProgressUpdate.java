package com.academicplanner.dto.task;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Used when the student records actual work on a task.
 * workedHours is subtracted from remainingHours (floored at 0).
 */
public record TaskProgressUpdate(
        @NotNull(message = "Worked hours are required")
        @DecimalMin(value = "0.0", message = "Worked hours must be >= 0")
        Double workedHours
) {}
