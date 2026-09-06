package com.academicplanner.dto.assessment;

import com.academicplanner.entity.Assessment.AssessmentPriority;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Assessment.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AssessmentRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotNull(message = "Assessment type is required")
        AssessmentType type,

        String description,

        LocalDateTime dueDateTime,

        @DecimalMin(value = "0.0", message = "Weight must be between 0 and 100")
        @DecimalMax(value = "100.0", message = "Weight must be between 0 and 100")
        Double weight,

        AssessmentPriority priority,

        AssessmentStatus status
) {}
