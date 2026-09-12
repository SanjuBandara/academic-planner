package com.academicplanner.dto.assessment;

import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Assessment.AssessmentType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Request body for creating or updating an assessment.
 *
 * <p>Priority is intentionally absent — assessment priority is system-generated
 * by the planning engine (basePriority by type + deadline urgency bonus).
 * Students should NOT manually choose assessment priority.
 */
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

        AssessmentStatus status
) {}
