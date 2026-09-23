package com.academicplanner.dto.assessment;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Assessment.AssessmentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Response DTO for an assessment.
 *
 * <p>Includes {@code calculatedBasePriority} so the UI can display
 * "System Priority: 3" (for QUIZ) without allowing the user to change it.
 *
 * <p>Priority mapping (base values):
 * <pre>
 *   EXAM         = 5
 *   PROJECT      = 4
 *   ASSIGNMENT   = 3
 *   QUIZ         = 3
 *   REPORT       = 3
 *   PRESENTATION = 3
 *   OTHER        = 2
 * </pre>
 */
public record AssessmentResponse(
        Long id,
        Long moduleId,
        String moduleCode,
        String moduleName,
        String title,
        AssessmentType type,
        String description,
        LocalDateTime dueDateTime,
        Long daysUntilDeadline,
        Double weight,
        int calculatedBasePriority,
        AssessmentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AssessmentResponse from(Assessment assessment) {
        Long daysUntil = null;
        if (assessment.getDueDateTime() != null) {
            LocalDate today = LocalDate.now();
            LocalDate dueDate = assessment.getDueDateTime().toLocalDate();
            daysUntil = ChronoUnit.DAYS.between(today, dueDate);
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
                basePriorityFor(assessment.getType()),
                assessment.getStatus(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt()
        );
    }

    /** Returns the base planning priority for an assessment type. */
    public static int basePriorityFor(AssessmentType type) {
        if (type == null) return 2;
        return switch (type) {
            case EXAM         -> 5;
            case PROJECT      -> 4;
            case ASSIGNMENT   -> 3;
            case QUIZ         -> 3;
            case REPORT       -> 3;
            case PRESENTATION -> 3;
            case OTHER        -> 2;
        };
    }
}
