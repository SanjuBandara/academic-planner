package com.academicplanner.dto.studyplan;

import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import jakarta.validation.constraints.DecimalMin;

/**
 * PATCH body for updating a plan item (start/complete/skip, log actual hours, add notes).
 * All fields are optional — only non-null fields are applied.
 */
public record StudyPlanItemUpdate(
        ItemStatus status,

        @DecimalMin(value = "0.0", message = "Actual hours must be >= 0")
        Double actualHours,

        String notes
) {}
