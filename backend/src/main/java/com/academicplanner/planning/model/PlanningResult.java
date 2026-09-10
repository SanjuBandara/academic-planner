package com.academicplanner.planning.model;

import com.academicplanner.entity.StudyPlanItem;

import java.util.List;
import java.util.Map;

/**
 * Structured output produced by the planning engine for a single generation run.
 *
 * <p>Contains the generated schedule items as well as meta-information that
 * can be used for display or further processing:
 * <ul>
 *   <li>Hour totals — planned vs available vs unallocated.</li>
 *   <li>Per-candidate feasibility warnings so the UI can inform the student.</li>
 * </ul>
 *
 * Example warnings:
 * <pre>
 *   "Database Exam can be completed before the deadline."
 *   "Software Assignment is at risk: only 3h available before deadline but 5h remain."
 *   "Math Quiz cannot be completed with current availability (2h available, 4h remaining)."
 * </pre>
 */
public record PlanningResult(
        /** All generated study-plan items, ordered by date + start-time. */
        List<StudyPlanItem> items,

        /** Total hours assigned across all items. */
        double totalPlannedHours,

        /** Total hours the student declared as available. */
        double totalAvailableHours,

        /** Hours that were not used (available - planned). */
        double unallocatedHours,

        /**
         * Map of candidateKey → human-readable feasibility warning.
         * Key format: "A-{assessmentId}" or "T-{taskId}".
         * Only contains entries for AT_RISK and IMPOSSIBLE candidates.
         */
        Map<String, String> feasibilityWarnings
) {
    /**
     * Convenience: returns true if any candidate is at risk or impossible.
     */
    public boolean hasWarnings() {
        return !feasibilityWarnings.isEmpty();
    }
}
