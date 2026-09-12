package com.academicplanner.planning;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Calculates the deadline urgency bonus added to an assessment's base priority.
 *
 * <h2>Urgency bonus values</h2>
 * <pre>
 *   More than 3 days remaining → +0
 *   Exactly 3 days remaining   → +1
 *   Exactly 2 days remaining   → +2
 *   Exactly 1 day remaining    → +3
 *   Due today or overdue       → +4  (highest urgency)
 * </pre>
 *
 * <p>Days remaining is computed as floor(hoursUntilDeadline / 24).
 * Overdue deadlines always return +4.
 *
 * <p>This class is stateless and deterministic. It has no dependencies so
 * it can be changed independently without touching priority or allocation logic.
 */
@Component
public class DeadlineUrgencyCalculator {

    /**
     * Returns the urgency bonus for the given deadline relative to {@code now}.
     *
     * @param deadline deadline of the assessment; null means no deadline → 0 bonus
     * @param now      reference time (injected by tests for reproducibility)
     * @return urgency bonus: 0, 1, 2, 3, or 4
     */
    public int urgencyBonus(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) return 0;

        long hoursUntil = ChronoUnit.HOURS.between(now, deadline);

        if (hoursUntil < 0) {
            // Overdue
            return 4;
        }

        long daysRemaining = hoursUntil / 24;

        if (daysRemaining > 3) return 0; // more than 3 days → no urgency
        if (daysRemaining == 3) return 1; // 3 days
        if (daysRemaining == 2) return 2; // 2 days
        if (daysRemaining == 1) return 3; // 1 day
        // 0 days = due today
        return 4;
    }

    /**
     * Overload that reads the deadline from a {@link LocalDateTime} urgency check,
     * using days remaining directly (used by unit tests).
     *
     * @param daysRemaining number of days remaining; negative = overdue
     * @return urgency bonus
     */
    public int urgencyBonusFromDays(long daysRemaining) {
        if (daysRemaining < 0)  return 4;
        if (daysRemaining > 3) return 0;
        if (daysRemaining == 3) return 1;
        if (daysRemaining == 2) return 2;
        if (daysRemaining == 1) return 3;
        return 4; // 0 = due today
    }

    /**
     * Sums the student's available hours strictly on or before the deadline date.
     *
     * <p>Used by {@link FeasibilityAnalyzer} to determine if allocated work
     * can physically be completed before the deadline.
     *
     * @param deadline    the item's effective deadline; null means all hours count
     * @param dailyHours  date → schedulable hours per day in the planning window
     * @param now         reference time
     * @return total schedulable hours available before the deadline
     */
    public double availableHoursBeforeDeadline(LocalDateTime deadline,
                                                Map<LocalDate, Double> dailyHours,
                                                LocalDateTime now) {
        if (deadline == null) {
            return dailyHours.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        LocalDate deadlineDate = deadline.toLocalDate();
        LocalDate today = now.toLocalDate();

        double total = 0.0;
        for (Map.Entry<LocalDate, Double> entry : dailyHours.entrySet()) {
            LocalDate day = entry.getKey();
            if (day.isBefore(today)) continue;
            if (day.isAfter(deadlineDate)) continue;
            double dayHours = entry.getValue() != null ? entry.getValue() : 0.0;
            total += Math.max(0.0, dayHours);
        }
        return total;
    }
}
