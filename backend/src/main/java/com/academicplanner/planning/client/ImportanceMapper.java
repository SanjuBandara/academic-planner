package com.academicplanner.planning.client;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task.TaskPriority;
import org.springframework.stereotype.Component;

/**
 * Maps existing entity-level type/priority enums to the HIGH/MEDIUM/LOW
 * importance vocabulary used by the Python planning service's Activity
 * model (Section 5 — "Activity importance" as one of several priority
 * factors, distinct from the deadline-urgency and user-priority factors).
 */
@Component
public class ImportanceMapper {

    public String importanceFor(AssessmentType type) {
        if (type == null)
            return "MEDIUM";
        return switch (type) {
            case EXAM, PROJECT -> "HIGH";
            case ASSIGNMENT, QUIZ, REPORT, PRESENTATION -> "MEDIUM";
            case OTHER -> "LOW";
        };
    }

    /**
     * Assessments don't currently expose a student-set override; reserved for
     * later.
     */
    public String userPriorityFor(Assessment assessment) {
        return null;
    }

    public String importanceFor(TaskPriority priority) {
        if (priority == null)
            return "MEDIUM";
        return switch (priority) {
            case HIGH -> "HIGH";
            case MEDIUM -> "MEDIUM";
            case LOW -> "LOW";
        };
    }

    /**
     * For tasks, the student's own priority IS the importance signal — also pass it
     * as the explicit override.
     */
    public String userPriorityFor(TaskPriority priority) {
        return importanceFor(priority);
    }
}
