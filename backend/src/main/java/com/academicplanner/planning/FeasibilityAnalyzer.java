package com.academicplanner.planning;

import com.academicplanner.planning.model.PlanningItem;
import com.academicplanner.planning.model.PlanningItem.FeasibilityStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Classifies each {@link PlanningItem} by feasibility:
 * can the allocated/required work realistically be completed before the deadline?
 *
 * <h2>Classification thresholds</h2>
 * <pre>
 *   workNeeded ≤ available                     → FEASIBLE
 *   workNeeded ≤ available × AT_RISK_THRESHOLD  → AT_RISK
 *   workNeeded > available × AT_RISK_THRESHOLD  → IMPOSSIBLE_WITH_CURRENT_AVAILABILITY
 * </pre>
 *
 * <p>When no deadline exists, the item is always {@code FEASIBLE}.
 */
@Component
public class FeasibilityAnalyzer {

    /**
     * Upper bound ratio: if needed ≤ available × this factor, the item
     * is AT_RISK (tight, but possible with optimal schedule).
     */
    public static final double AT_RISK_THRESHOLD = 1.2;

    private final DeadlineUrgencyCalculator urgencyCalculator;

    public FeasibilityAnalyzer(DeadlineUrgencyCalculator urgencyCalculator) {
        this.urgencyCalculator = urgencyCalculator;
    }

    /**
     * Analyzes feasibility for all planning items and sets status & warning in-place.
     */
    public void analyzeAll(List<PlanningItem> items, Map<LocalDate, Double> dailyHours, LocalDateTime now) {
        for (PlanningItem item : items) {
            classify(item, dailyHours, now);
        }
    }

    /**
     * Classifies a single item.
     */
    public void classify(PlanningItem item, Map<LocalDate, Double> dailyHours, LocalDateTime now) {
        if (item.getDeadline() == null) {
            item.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            item.setFeasibilityWarning(null);
            return;
        }

        double available = urgencyCalculator.availableHoursBeforeDeadline(item.getDeadline(), dailyHours, now);
        double needed = getWorkNeeded(item);

        if (needed <= 0) {
            item.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            item.setFeasibilityWarning(null);
            return;
        }

        String label = item.getActivityLabel() != null ? "\"" + item.getActivityLabel() + "\"" : "Activity";

        if (needed <= available) {
            item.setFeasibilityStatus(FeasibilityStatus.FEASIBLE);
            item.setFeasibilityWarning(null);
        } else if (needed <= available * AT_RISK_THRESHOLD) {
            item.setFeasibilityStatus(FeasibilityStatus.AT_RISK);
            item.setFeasibilityWarning(String.format(
                    "%s is AT RISK: %.1fh needed/allocated but only %.1fh are available before its deadline.",
                    label, needed, available));
        } else {
            item.setFeasibilityStatus(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
            item.setFeasibilityWarning(String.format(
                    "%s CANNOT be completed before the deadline with current availability (%.1fh needed/allocated, only %.1fh available).",
                    label, needed, available));
        }
    }

    private double getWorkNeeded(PlanningItem item) {
        if (item.getTask() != null) {
            if (item.getTask().getRemainingHours() != null && item.getTask().getRemainingHours() > 0) {
                return item.getTask().getRemainingHours();
            }
            if (item.getTask().getEstimatedHours() != null && item.getTask().getEstimatedHours() > 0) {
                return item.getTask().getEstimatedHours();
            }
        }
        return item.getAllocatedHours();
    }
}
