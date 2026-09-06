package com.academicplanner.dto.assessment;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentPriority;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Assessment.AssessmentType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public record AssessmentResponse(
        Long id,
        Long moduleId,
        String moduleCode,
        String moduleName,
        String title,
        AssessmentType type,
        String description,
        LocalDateTime dueDateTime,
        Long daysUntilDeadline,   // null if no deadline; negative if overdue
        Double weight,
        AssessmentPriority priority,
        AssessmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AssessmentResponse from(Assessment assessment) {
        Long daysUntil = null;
        if (assessment.getDueDateTime() != null) {
            daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), assessment.getDueDateTime());
        }
        return new AssessmentResponse(
                assessment.getId(),
                assessment.getModule().getId(),
                assessment.getModule().getCode(),
                assessment.getModule().getName(),
                assessment.getTitle(),
                assessment.getType(),
                assessment.getDescription(),
                assessment.getDueDateTime(),
                daysUntil,
                assessment.getWeight(),
                assessment.getPriority(),
                assessment.getStatus(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt()
        );
    }
}
