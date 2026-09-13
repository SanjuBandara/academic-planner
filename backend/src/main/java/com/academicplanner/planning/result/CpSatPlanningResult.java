package com.academicplanner.planning.result;

import com.academicplanner.entity.StudyPlanItem;

import java.util.List;

/**
 * Output of {@link com.academicplanner.planning.service.CpSatPlanningService#generatePlan}.
 *
 * <p>
 * <b>Phase 2 change:</b> added {@code completedRequiredHours}/
 * {@code unfinishedRequiredHours}, sourced from the Python service's new
 * {@code completedRequiredMinutes}/{@code unfinishedRequiredMinutes}
 * statistics — more meaningful to show a student than the old
 * available/planned/unallocated-only breakdown, since it distinguishes
 * "time we used" from "how much of your actual required work got done."
 */
public record CpSatPlanningResult(
        List<StudyPlanItem> items,
        double totalPlannedHours,
        double totalAvailableHours,
        double unallocatedHours,
        double completedRequiredHours,
        double unfinishedRequiredHours,
        String solverStatus,
        List<String> warnings) {
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
}
