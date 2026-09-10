package com.academicplanner.planning;

import com.academicplanner.planning.model.PlanningCandidate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Stateless, deterministic component responsible for everything
 * deadline-related:
 * <ul>
 * <li>Computing the <b>effective deadline</b> of a task/assessment pair
 * (earliest non-null of the two).</li>
 * <li>Computing <b>available capacity before a deadline</b> from the daily
 * availability map — the number of hours a student genuinely has left
 * to work on an item before it is due.</li>
 * <li>Computing the <b>urgency multiplier</b> used by
 * {@link PriorityCalculator}.</li>
 * </ul>
 *
 * <h2>Urgency thresholds</h2>
 * 
 * <pre>
 *   &gt; 30 days      = 1.0
 *   15–30 days     = 1.5
 *   7–14 days      = 2.5
 *   3–6 days       = 4.0
 *   0–2 days       = 6.0
 *   Overdue        = 7.0
 *   No deadline    = 1.0
 * </pre>
 */
@Component
public class DeadlineAnalyzer {

    public static final double URGENCY_FAR = 1.0; // > 30 days
    public static final double URGENCY_MEDIUM_FAR = 1.5; // 15–30 days
    public static final double URGENCY_MEDIUM = 2.5; // 7–14 days
    public static final double URGENCY_CLOSE = 4.0; // 3–6 days
    public static final double URGENCY_VERY_CLOSE = 6.0; // 0–2 days
    public static final double URGENCY_OVERDUE = 7.0; // overdue
    public static final double URGENCY_NONE = 1.0; // no deadline

    /**
     * effectiveDeadline = min(taskDeadline, assessmentDeadline), null-safe.
     * If only one is present, that one is used. If neither, returns null
     * (treated as "no deadline" everywhere downstream).
     */
    public LocalDateTime effectiveDeadline(LocalDateTime taskDeadline, LocalDateTime assessmentDeadline) {
        if (taskDeadline != null && assessmentDeadline != null) {
            return taskDeadline.isBefore(assessmentDeadline) ? taskDeadline : assessmentDeadline;
        }
        return taskDeadline != null ? taskDeadline : assessmentDeadline;
    }

    /** Static entity-based convenience overload for effectiveDeadline. */
    public static LocalDateTime effectiveDeadline(com.academicplanner.entity.Assessment assessment, com.academicplanner.entity.Task task) {
        LocalDateTime assessmentDl = assessment != null ? assessment.getDueDateTime() : null;
        LocalDateTime taskDl = task != null ? task.getDueDateTime() : null;
        if (taskDl != null && assessmentDl != null) {
            return taskDl.isBefore(assessmentDl) ? taskDl : assessmentDl;
        }
        return taskDl != null ? taskDl : assessmentDl;
    }

    // ── Urgency ──────────────────────────────────────────────────────────────

    /** Convenience overload reading the deadline straight off the candidate. */
    public double urgencyMultiplier(PlanningCandidate candidate, LocalDateTime now) {
        return urgencyMultiplier(candidate.getEffectiveDeadline(), now);
    }

    /**
     * Returns the urgency multiplier for a given deadline relative to {@code now}.
     * Deterministic — no calendar/business-day logic, plain elapsed time.
     */
    public double urgencyMultiplier(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null) {
            return URGENCY_NONE;
        }

        long hoursUntil = ChronoUnit.HOURS.between(now, deadline);
        if (hoursUntil < 0) {
            return URGENCY_OVERDUE;
        }

        double daysUntil = hoursUntil / 24.0;
        if (daysUntil > 30)
            return URGENCY_FAR;
        if (daysUntil >= 15)
            return URGENCY_MEDIUM_FAR;
        if (daysUntil >= 7)
            return URGENCY_MEDIUM;
        if (daysUntil >= 3)
            return URGENCY_CLOSE;
        return URGENCY_VERY_CLOSE; // 0–2 days
    }

    /** Overload accepting days remaining directly (used by PriorityCalculator and tests). */
    public double urgencyMultiplier(long daysRemaining) {
        if (daysRemaining < 0) return URGENCY_OVERDUE;
        if (daysRemaining < 3) return URGENCY_VERY_CLOSE;
        if (daysRemaining < 7) return URGENCY_CLOSE;
        if (daysRemaining < 15) return URGENCY_MEDIUM;
        if (daysRemaining < 30) return URGENCY_MEDIUM_FAR;
        return URGENCY_FAR;
    }

    /** Alias for availableHoursBeforeDeadline. */
    public double computeAvailableBeforeDeadline(LocalDateTime deadline,
            Map<LocalDate, Double> dailyHours,
            LocalDateTime now) {
        return availableHoursBeforeDeadline(deadline, dailyHours, now);
    }

    // ── Available capacity before deadline ──────────────────────────────────

    /**
     * Populates {@code availableHoursBeforeDeadline} on every candidate by
     * summing the daily availability map up to (and, on the deadline day,
     * bounded by) the effective deadline.
     *
     * @param candidates candidates to analyze (mutated in place)
     * @param dailyHours the planning window's daily availability
     * @param now        current time — used to exclude already-elapsed time on
     *                   today
     */
    public void analyzeAll(java.util.List<PlanningCandidate> candidates,
            Map<LocalDate, Double> dailyHours,
            LocalDateTime now) {
        for (PlanningCandidate c : candidates) {
            double available = availableHoursBeforeDeadline(c.getEffectiveDeadline(), dailyHours, now);
            c.setAvailableHoursBeforeDeadline(available);
        }
    }

    /**
     * Sums usable daily capacity strictly on/before the effective deadline.
     *
     * <p>
     * Rules:
     * <ul>
     * <li>No deadline → all capacity in the planning window counts (nothing to
     * protect against).</li>
     * <li>Days before "today" are never usable (can't schedule in the past).</li>
     * <li>Days after the deadline date are excluded entirely.</li>
     * <li>On the deadline day itself, only the portion of the day's capacity
     * that falls before the deadline's clock time is usable — the same
     * {@code DEFAULT_START}-based reasoning {@link SessionScheduler} and
     * {@link WorkloadAllocator} use, so the three stay consistent.</li>
     * </ul>
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
            if (day.isBefore(today))
                continue;
            if (day.isAfter(deadlineDate))
                continue;

            double dayHours = entry.getValue() != null ? entry.getValue() : 0.0;
            if (dayHours <= 0)
                continue;

            if (day.isEqual(deadlineDate)) {
                dayHours = Math.min(dayHours, hoursUsableOnDeadlineDay(deadline.toLocalTime(), dayHours));
            }

            total += dayHours;
        }
        return total;
    }

    private double hoursUsableOnDeadlineDay(LocalTime deadlineTime, double dayCapacity) {
        if (!deadlineTime.isAfter(SessionScheduler.DEFAULT_START)) {
            return 0.0;
        }
        double hoursUntilDeadline = (double) SessionScheduler.DEFAULT_START.until(deadlineTime, ChronoUnit.MINUTES)
                / 60.0;
        return Math.min(dayCapacity, hoursUntilDeadline);
    }
}