package com.academicplanner.planning.mapper;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Converts existing {@link Assessment}/{@link Task} entities into a
 * {@code remainingHours} figure for the Python planning service.
 *
 * <p>
 * <b>Phase 2 change:</b> the Python contract no longer has a
 * productivity-factor/work-unit layer — {@code remainingHours} is used
 * directly (spec Section 7: "Do not invent study hours from task
 * estimates" beyond what's declared). This class is now just a single
 * hours lookup, not a unit-conversion.
 *
 * <h2>Current behavior</h2>
 * <ul>
 * <li><b>Task</b> already tracks {@code remainingHours}/{@code estimatedHours}
 * directly — used as-is.</li>
 * <li><b>Assessment</b> has no explicit hour field in the current entity.
 * Until one exists, a configurable default-hours-per-type table is
 * used as a documented placeholder (unchanged in spirit from Phase 1,
 * just no longer run through a units/hour conversion).</li>
 * </ul>
 */
@Component
public class WorkloadEstimator {

    /**
     * Default remaining hours for an assessment, keyed by type, used ONLY
     * when the assessment has no other workload signal available.
     */
    private static final Map<AssessmentType, Double> DEFAULT_ASSESSMENT_HOURS = Map.of(
            AssessmentType.EXAM, 8.0,
            AssessmentType.PROJECT, 8.0,
            AssessmentType.ASSIGNMENT, 6.0,
            AssessmentType.QUIZ, 3.0,
            AssessmentType.REPORT, 5.0,
            AssessmentType.PRESENTATION, 4.0,
            AssessmentType.OTHER, 4.0);

    public double remainingHoursFor(Assessment assessment) {
        // TODO(Phase 4+): replace with a real workload estimate once
        // Assessment exposes one, or a learned/historical productivity model.
        AssessmentType type = assessment.getType();
        return DEFAULT_ASSESSMENT_HOURS.getOrDefault(type, 4.0);
    }

    public double remainingHoursFor(Task task) {
        if (task.getRemainingHours() != null && task.getRemainingHours() > 0) {
            return task.getRemainingHours();
        }
        if (task.getEstimatedHours() != null && task.getEstimatedHours() > 0) {
            return task.getEstimatedHours();
        }
        return 2.0; // no estimate at all — fall back to a medium default
    }
}
