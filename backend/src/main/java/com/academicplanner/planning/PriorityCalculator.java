package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentPriority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Stateless, deterministic calculator for assessment urgency and priority scores.
 *
 * <h2>Urgency classification (days until deadline)</h2>
 * <pre>
 *   &gt; 30 days  → 1.0  (low urgency)
 *  15–30 days  → 1.5  (moderate)
 *   7–14 days  → 2.5  (high)
 *    3–6 days  → 4.0  (very high)
 *    0–2 days  → 6.0  (critical)
 *     overdue  → 7.0  (critical / overdue)
 *   no deadline → 1.0  (default — treat like low urgency)
 * </pre>
 *
 * <h2>Assessment priority multiplier</h2>
 * <pre>
 *   LOW      → 1.0
 *   MEDIUM   → 1.5
 *   HIGH     → 2.5
 *   CRITICAL → 4.0
 * </pre>
 */
@Component
public class PriorityCalculator {

    // ----- Urgency thresholds -----

    /** Days threshold below which urgency escalates to MODERATE. */
    public static final int THRESHOLD_MODERATE = 30;
    /** Days threshold below which urgency escalates to HIGH. */
    public static final int THRESHOLD_HIGH = 15;
    /** Days threshold below which urgency escalates to VERY_HIGH. */
    public static final int THRESHOLD_VERY_HIGH = 7;
    /** Days threshold below which urgency escalates to CRITICAL. */
    public static final int THRESHOLD_CRITICAL = 3;

    // ----- Urgency multipliers -----
    public static final double URGENCY_LOW       = 1.0;
    public static final double URGENCY_MODERATE  = 1.5;
    public static final double URGENCY_HIGH      = 2.5;
    public static final double URGENCY_VERY_HIGH = 4.0;
    public static final double URGENCY_CRITICAL  = 6.0;
    public static final double URGENCY_OVERDUE   = 7.0;

    // ----- Priority multipliers -----
    public static final double PRIORITY_LOW      = 1.0;
    public static final double PRIORITY_MEDIUM   = 1.5;
    public static final double PRIORITY_HIGH     = 2.5;
    public static final double PRIORITY_CRITICAL = 4.0;

    /**
     * Returns the number of whole days between now and the deadline.
     * Negative value means the deadline has passed (overdue).
     */
    public long daysUntilDeadline(LocalDateTime dueDateTime) {
        return ChronoUnit.DAYS.between(LocalDateTime.now(), dueDateTime);
    }

    /**
     * Calculates the deadline urgency multiplier for a given number of days remaining.
     * A higher multiplier means more study time should be allocated now.
     *
     * @param daysRemaining number of days until the deadline (may be negative)
     * @return urgency multiplier in range [1.0, 7.0]
     */
    public double urgencyMultiplier(long daysRemaining) {
        if (daysRemaining < 0) {
            return URGENCY_OVERDUE;
        } else if (daysRemaining < THRESHOLD_CRITICAL) {
            return URGENCY_CRITICAL;
        } else if (daysRemaining < THRESHOLD_VERY_HIGH) {
            return URGENCY_VERY_HIGH;
        } else if (daysRemaining < THRESHOLD_HIGH) {
            return URGENCY_HIGH;
        } else if (daysRemaining < THRESHOLD_MODERATE) {
            return URGENCY_MODERATE;
        } else {
            return URGENCY_LOW;
        }
    }

    /**
     * Calculates the urgency multiplier for an assessment using the current wall-clock time.
     * If the assessment has no deadline, returns the low-urgency multiplier.
     */
    public double urgencyMultiplier(Assessment assessment) {
        if (assessment.getDueDateTime() == null) {
            return URGENCY_LOW;
        }
        return urgencyMultiplier(daysUntilDeadline(assessment.getDueDateTime()));
    }

    /**
     * Returns the priority weight for a given {@link AssessmentPriority} level.
     */
    public double priorityMultiplier(AssessmentPriority priority) {
        if (priority == null) {
            return PRIORITY_MEDIUM;
        }
        return switch (priority) {
            case LOW      -> PRIORITY_LOW;
            case MEDIUM   -> PRIORITY_MEDIUM;
            case HIGH     -> PRIORITY_HIGH;
            case CRITICAL -> PRIORITY_CRITICAL;
        };
    }

    /**
     * Computes a raw (un-normalized) priority score for a single assessment.
     *
     * <pre>
     *   score = creditWeight × urgencyMultiplier × priorityMultiplier × remainingWorkFactor
     * </pre>
     *
     * All four factors are positive real numbers; their product is also positive.
     * Callers are responsible for normalizing across all assessments before
     * translating scores into hour allocations.
     *
     * @param creditWeight        module credits / total credits (0 < w ≤ 1)
     * @param urgencyMultiplier   result of {@link #urgencyMultiplier(Assessment)}
     * @param priorityMultiplier  result of {@link #priorityMultiplier(AssessmentPriority)}
     * @param remainingWorkFactor remaining hours / max remaining hours across all assessments (0 ≤ f ≤ 1)
     * @return raw priority score (higher = more time should be allocated)
     */
    public double rawScore(double creditWeight,
                           double urgencyMultiplier,
                           double priorityMultiplier,
                           double remainingWorkFactor) {
        // Guard against a zero remainingWorkFactor — if no work remains, score is 0.
        if (remainingWorkFactor <= 0.0) {
            return 0.0;
        }
        return creditWeight * urgencyMultiplier * priorityMultiplier * remainingWorkFactor;
    }
}
