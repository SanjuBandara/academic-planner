package com.academicplanner.planning;

import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningCandidate.FeasibilityStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Classifies each {@link PlanningCandidate} by feasibility:
 * can the remaining work realistically be completed before the deadline?
 *
 * <h2>Classification thresholds</h2>
 * <pre>
 *   remaining ≤ available                     → FEASIBLE
 *   remaining ≤ available × AT_RISK_THRESHOLD  → AT_RISK
 *   remaining > available × AT_RISK_THRESHOLD  → IMPOSSIBLE_WITH_CURRENT_AVAILABILITY
 * </pre>
 *
 * <p>When no deadline exists, the candidate is always {@code FEASIBLE}.
 *
 * <p>This class is stateless and deterministic.
 */
@Component
public class FeasibilityAnalyzer {

    /**
     * Upper bound ratio: if remaining ≤ available × this factor, the candidate
     * is AT_RISK (tight, but technically possible with perfect execution).
     * A factor of 1.2 means remaining can be up to 20 % over available.
     */
    public static final double AT_RISK_THRESHOLD = 1.2;

    /**
     * Classifies feasibility and sets the warning message for every candidate
     * in the list.
     *
     * @param candidates candidates already populated with remainingWorkHours
     *                   and availableHoursBeforeDeadline
     */
    public void analyzeAll(List<PlanningCandidate> candidates) {
        for (PlanningCandidate c : candidates) {
            classify(c);
        }
    }

    /**
     * Classifies a single candidate and sets {@code feasibilityStatus} and
     * {@code feasibilityWarning} on it.
     */
    public void classify(PlanningCandidate candidate) {
        double remaining  = candidate.getRemainingWorkHours();
        double available  = candidate.getAvailableHoursBeforeDeadline();
        String label      = label(candidate);

        if (candidate.getEffectiveDeadline() == null) {
            // No deadline — always feasible, no warning needed
            candidate.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            candidate.setFeasibilityWarning(null);
            return;
        }

        if (remaining <= 0) {
            candidate.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            candidate.setFeasibilityWarning(null);
            return;
        }

        if (remaining <= available) {
            candidate.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            candidate.setFeasibilityWarning(null);
        } else if (remaining <= available * AT_RISK_THRESHOLD) {
            candidate.setFeasibilityStatus(FeasibilityStatus.AT_RISK);
            candidate.setFeasibilityWarning(String.format(
                    "%s is AT RISK: %.1fh remain but only %.1fh are available before its deadline.",
                    label, remaining, available));
        } else {
            candidate.setFeasibilityStatus(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
            candidate.setFeasibilityWarning(String.format(
                    "%s CANNOT be completed before the deadline with current availability "
                            + "(%.1fh remain, only %.1fh available).",
                    label, remaining, available));
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String label(PlanningCandidate c) {
        if (c.getTask() != null) {
            return "\"" + c.getTask().getTitle() + "\"";
        }
        if (c.getAssessment() != null) {
            return "\"" + c.getAssessment().getTitle() + "\"";
        }
        return "Candidate[" + c.candidateKey() + "]";
    }
}
