package com.academicplanner.planning.model;

/**
 * Value object capturing every individual factor that contributed to a
 * candidate's final priority score.  Stored on {@link PlanningCandidate}
 * so that the decision is fully explainable for debugging.
 *
 * <pre>
 *   priorityScore = typeWeight × creditWeight × urgencyWeight × workloadFactor
 * </pre>
 */
public record PriorityFactors(
        /** Academic type weight — EXAM=5.0, PROJECT=3.0, ASSIGNMENT=2.5, QUIZ=2.0, OTHER=1.0. */
        double typeWeight,

        /** Normalised credit fraction — module credits / total credits across active modules. */
        double creditWeight,

        /** Deadline urgency multiplier (1.0 – 7.0). */
        double urgencyWeight,

        /** Remaining work relative to the maximum remaining across all candidates (0–1). */
        double workloadFactor,

        /** Final composite score (product of the four factors above). */
        double score
) {
    /**
     * Returns a human-readable explanation string suitable for logging.
     */
    public String explain() {
        return String.format(
                "typeWeight=%.2f  creditWeight=%.4f  urgencyWeight=%.2f  workloadFactor=%.4f  → score=%.4f",
                typeWeight, creditWeight, urgencyWeight, workloadFactor, score);
    }
}
