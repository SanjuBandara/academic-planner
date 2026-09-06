package com.academicplanner.dto.task;

import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.entity.Task.TaskStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record TaskRequest(
        @NotBlank(message = "Title is required")
        String title,

        String description,

        Long moduleId,

        Long assessmentId,

        @DecimalMin(value = "0.0", message = "Estimated hours must be >= 0")
        Double estimatedHours,

        TaskPriority priority,

        TaskStatus status,

        LocalDateTime dueDateTime
) {}
