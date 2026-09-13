package com.academicplanner.planning.client;

import com.academicplanner.entity.StudyPlanItem;

import java.util.List;

/**
 * Output of {@link CpSatPlanningService#generatePlan}.
 * Deliberately shaped like the existing {@code PlanningResult} record so a
 * later cutover in {@code StudyPlanService} is a small, mechanical change.
 */
public record CpSatPlanningResult(
        List<StudyPlanItem> items,
        double totalPlannedHours,
        double totalAvailableHours,
        double unallocatedHours,
        String solverStatus,
        List<String> warnings) {
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
