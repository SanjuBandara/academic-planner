package com.academicplanner.dto.task;

import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.entity.Task.TaskStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * Request body for creating or updating a task.
 *
 * <p>Priority is user-selected: HIGH / MEDIUM / LOW.
 * It drives the planning weight: HIGH=2.5, MEDIUM=2.0, LOW=1.0.
 * Unlike assessments, task priority is NOT system-generated.
 */
public record TaskRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description,

        Long moduleId,

        @DecimalMin(value = "0.0", message = "Estimated hours must be >= 0")
        Double estimatedHours,

        /**
         * User-selected priority.
         * HIGH = 2.5, MEDIUM = 2.0, LOW = 1.0 (planning weight multiplier).
         * Defaults to MEDIUM if not provided.
         */
        TaskPriority priority,

        TaskStatus status,

        LocalDateTime dueDateTime
) {}
