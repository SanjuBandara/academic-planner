package com.academicplanner.planning;

import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PriorityFactors;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stateless, deterministic calculator for planning priority scores.
 *
 * <h2>Formula</h2>
 * <pre>
 *   priorityScore = typeWeight × creditWeight × urgencyWeight × workloadFactor
 * </pre>
 *
 * <h2>Type weights (academic importance)</h2>
 * <pre>
 *   EXAM         = 5.0
 *   PROJECT      = 3.0
 *   ASSIGNMENT   = 2.5
 *   REPORT       = 2.5
 *   QUIZ         = 2.0
 *   PRESENTATION = 2.0
 *   OTHER        = 1.0
 * </pre>
 *
 * <h2>Credit weight</h2>
 * Normalised: {@code moduleCredits / totalCreditsAcrossAllCandidateModules}.
 * Candidates with no module (standalone tasks) receive a weight of
 * {@code 1 / (distinctModuleCount + 1)} to avoid completely suppressing them.
 *
 * <h2>Urgency weight</h2>
 * Derived from {@link DeadlineAnalyzer#urgencyMultiplier(long)}.
 *
 * <h2>Workload factor</h2>
 * {@code remainingHours / maxRemainingHours} across all candidates.
 * A candidate with 0 remaining hours gets a score of 0 (nothing to schedule).
 */
@Component
public class PriorityCalculator {

    // ── Type weights ───────────────────────────────────────────────────────
    public static final double TYPE_EXAM         = 5.0;
    public static final double TYPE_PROJECT      = 3.0;
    public static final double TYPE_ASSIGNMENT   = 2.5;
    public static final double TYPE_REPORT       = 2.5;
    public static final double TYPE_QUIZ         = 2.0;
    public static final double TYPE_PRESENTATION = 2.0;
    public static final double TYPE_OTHER        = 1.0;

    private final DeadlineAnalyzer deadlineAnalyzer;

    public PriorityCalculator(DeadlineAnalyzer deadlineAnalyzer) {
        this.deadlineAnalyzer = deadlineAnalyzer;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Returns the academic type weight for an assessment type.
     * Standalone tasks (no type) receive {@link #TYPE_OTHER}.
     */
    public double typeWeight(AssessmentType type) {
        if (type == null) return TYPE_OTHER;
        return switch (type) {
            case EXAM         -> TYPE_EXAM;
            case PROJECT      -> TYPE_PROJECT;
            case ASSIGNMENT   -> TYPE_ASSIGNMENT;
            case REPORT       -> TYPE_REPORT;
            case QUIZ         -> TYPE_QUIZ;
            case PRESENTATION -> TYPE_PRESENTATION;
            case OTHER        -> TYPE_OTHER;
        };
    }

    /**
     * Computes and sets {@link PriorityFactors} on every candidate.
     *
     * <p>Must be called after {@link WorkloadEstimator} and
     * {@link DeadlineAnalyzer} have populated their fields.
     *
     * @param candidates candidates to score
     * @param now        current time reference (for urgency calculation)
     */
    public void calculateAll(List<PlanningCandidate> candidates,
                             java.time.LocalDateTime now) {
        if (candidates.isEmpty()) return;

        // Compute total credits across distinct modules (for credit normalisation)
        List<com.academicplanner.entity.Module> distinctModules = candidates.stream()
                .map(PlanningCandidate::getEffectiveModule)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        int totalCredits = distinctModules.stream()
                .mapToInt(com.academicplanner.entity.Module::getCredits)
                .sum();
        if (totalCredits <= 0) totalCredits = 1; // guard against no-module or zero-credits scenarios

        // Count distinct modules for standalone-task fallback credit weight
        long distinctModuleCount = distinctModules.size();
        double standaloneCreditWeight = 1.0 / (distinctModuleCount + 1);

        // Max remaining hours (for workload factor normalisation)
        double maxRemaining = candidates.stream()
                .mapToDouble(PlanningCandidate::getRemainingWorkHours)
                .max().orElse(1.0);
        if (maxRemaining <= 0) maxRemaining = 1.0;

        for (PlanningCandidate c : candidates) {
            double remaining = c.getRemainingWorkHours();
            if (remaining <= 0) {
                // Nothing to schedule — score is 0
                c.setPriorityFactors(new PriorityFactors(0, 0, 0, 0, 0));
                continue;
            }

            // 1. Type weight
            AssessmentType type = c.getAssessment() != null ? c.getAssessment().getType() : null;
            double tw = typeWeight(type);

            // 2. Credit weight
            double cw;
            com.academicplanner.entity.Module effectiveMod = c.getEffectiveModule();
            if (effectiveMod != null && effectiveMod.getCredits() > 0) {
                cw = (double) effectiveMod.getCredits() / totalCredits;
            } else {
                cw = standaloneCreditWeight;
            }

            // 3. Urgency weight
            double uw = deadlineAnalyzer.urgencyMultiplier(c, now);

            // 4. Workload factor
            double wf = remaining / maxRemaining;

            // 5. Final score
            double score = tw * cw * uw * wf;

            c.setPriorityFactors(new PriorityFactors(tw, cw, uw, wf, score));
        }
    }
}
