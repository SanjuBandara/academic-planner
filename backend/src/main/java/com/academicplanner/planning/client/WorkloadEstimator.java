package com.academicplanner.planning.client;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Converts existing {@link Assessment}/{@link Task} entities into
 * (remainingWorkUnits, productivityUnitsPerHour) pairs for the Python
 * planning service.
 *
 * <h2>Why this exists</h2>
 * The spec (Section 19) is explicit that workload units must NOT be
 * assumed equal to hours unless the model says so, and that the
 * conversion should be a documented, configurable, and later-replaceable
 * assumption — not a permanent hard-coded truth.
 *
 * <h2>Current behavior</h2>
 * <ul>
 * <li><b>Task</b> already tracks {@code remainingHours}/{@code estimatedHours}
 * directly. We treat those hours as the work-unit figure 1:1
 * (productivityUnitsPerHour = 1.0) so existing task data keeps its
 * exact meaning — no silent re-scaling.</li>
 * <li><b>Assessment</b> has no explicit hour/workload field in the
 * current entity. Until one exists, we fall back to a configurable
 * default-work-units-per-type table below (mirrors the "Small=1,
 * Medium=2, Large=4" style workload sizing in the spec, scaled up
 * per assessment type). This is a placeholder assumption, flagged
 * for replacement once assessments carry real workload estimates or
 * historical productivity data (see README "Known limitations").</li>
 * </ul>
 */
@Component
public class WorkloadEstimator {

    /**
     * Default remaining work units for an assessment, keyed by type, used
     * ONLY when the assessment has no other workload signal available.
     * Values are in the same "work unit" space as Task hours — configurable,
     * not derived from any measurement yet.
     */
    private static final Map<AssessmentType, Double> DEFAULT_ASSESSMENT_WORK_UNITS = Map.of(
            AssessmentType.EXAM, 8.0,
            AssessmentType.PROJECT, 8.0,
            AssessmentType.ASSIGNMENT, 6.0,
            AssessmentType.QUIZ, 3.0,
            AssessmentType.REPORT, 5.0,
            AssessmentType.PRESENTATION, 4.0,
            AssessmentType.OTHER, 4.0);

    /**
     * units/hour used for task-hour-based figures — kept at 1.0 so hours pass
     * through unchanged.
     */
    public static final double TASK_UNITS_PER_HOUR = 1.0;

    public double remainingWorkUnitsFor(Assessment assessment) {
        // TODO(Phase 2+): replace with a real workload estimate once Assessment
        // exposes one, or with a learned per-student/per-type productivity model.
        AssessmentType type = assessment.getType();
        return DEFAULT_ASSESSMENT_WORK_UNITS.getOrDefault(type, 4.0);
    }

    public Double productivityUnitsPerHourFor(Assessment assessment) {
        // No per-assessment override yet — let the Python service's
        // configured default apply.
        return null;
    }

    public double remainingWorkUnitsFor(Task task) {
        if (task.getRemainingHours() != null && task.getRemainingHours() > 0) {
            return task.getRemainingHours();
        }
        if (task.getEstimatedHours() != null && task.getEstimatedHours() > 0) {
            return task.getEstimatedHours();
        }
        // No estimate at all: fall back to the same default-medium sizing
        // used for assessments so the task still gets scheduled.
        return 2.0;
    }

    public Double productivityUnitsPerHourFor(Task task) {
        return TASK_UNITS_PER_HOUR;
    }
}
