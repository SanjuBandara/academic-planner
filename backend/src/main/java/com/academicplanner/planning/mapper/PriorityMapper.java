package com.academicplanner.planning.mapper;

import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task.TaskPriority;
import org.springframework.stereotype.Component;

/**
 * Maps existing entity-level type/priority enums to the Phase 2 Python
 * contract's numeric 1-5 priority scale (replaces the Phase 1
 * {@code ImportanceMapper}, which produced HIGH/MEDIUM/LOW strings — the
 * Python service no longer has an "importance" field, only {@code priority}).
 *
 * <p>
 * The assessment-type numbers below are the same sensible 1-5 ordering
 * the old {@code PriorityCalculator.BASE_*} constants used (EXAM highest,
 * OTHER lowest) — reusing that ordering is fine; what the spec forbids is
 * reusing the old proportional-allocation *algorithm*
 * (basePriority × credits), which this class does not do.
 */
@Component
public class PriorityMapper {

    public int priorityFor(AssessmentType type) {
        if (type == null)
            return 2;
        return switch (type) {
            case EXAM -> 5;
            case PROJECT -> 4;
            case ASSIGNMENT, QUIZ, REPORT, PRESENTATION -> 3;
            case OTHER -> 2;
        };
    }

    public int priorityFor(TaskPriority priority) {
        if (priority == null)
            return 3;
        return switch (priority) {
            case HIGH -> 5;
            case MEDIUM -> 3;
            case LOW -> 1;
        };
    }
}
