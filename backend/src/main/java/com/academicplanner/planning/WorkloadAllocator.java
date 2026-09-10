package com.academicplanner.planning;

import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningCandidate.FeasibilityStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Translates priority scores and feasibility analysis into concrete hour
 * allocations per candidate.
 *
 * <h2>Algorithm (deadline-constrained reservation)</h2>
 * <ol>
 *   <li>Sort candidates by scheduling urgency:
 *       {@code IMPOSSIBLE} first, then {@code AT_RISK}, then {@code FEASIBLE};
 *       within each group by earliest effective deadline, then by priority score.</li>
 *   <li>For each candidate, compute the <em>capacity budget</em>:
 *       {@code min(remainingWorkHours, availableHoursBeforeDeadline)}.</li>
 *   <li>Deduct reservations from a per-day mutable capacity copy so that
 *       allocating hours for an early deadline genuinely reduces what is
 *       available for later deadlines.</li>
 *   <li>After all deadline-constrained reservations, distribute any
 *       leftover capacity proportionally by priority score (uncapped up to
 *       remaining work).</li>
 * </ol>
 *
 * <p>Invariants guaranteed by this allocator:
 * <ul>
 *   <li>allocated ≤ remainingWorkHours for every candidate.</li>
 *   <li>Σ(allocated) ≤ totalAvailableHours.</li>
 *   <li>If a deadline is IMPOSSIBLE, up to availableHoursBeforeDeadline are
 *       still reserved so partial progress can be made.</li>
 * </ul>
 */
@Component
public class WorkloadAllocator {

    /**
     * Populates {@code allocatedHours} on every candidate.
     *
     * @param candidates candidates with remaining work, available hours, feasibility, and score set
     * @param dailyHours mutable copy of the daily availability (will be consumed internally)
     */
    public void allocate(List<PlanningCandidate> candidates,
                         Map<LocalDate, Double> dailyHours) {

        if (candidates.isEmpty()) return;

        // Working copy of daily capacity — we consume it as we reserve hours
        Map<LocalDate, Double> remainingCapacity = new TreeMap<>(dailyHours);

        // ── Step 1: Sort by deadline urgency for reservation pass ──────────
        List<PlanningCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort(deadlineUrgencyComparator());

        // ── Step 2: Deadline-constrained reservation pass ──────────────────
        for (PlanningCandidate c : sorted) {
            if (c.getRemainingWorkHours() <= 0) {
                c.setAllocatedHours(0);
                continue;
            }

            double needed   = c.getRemainingWorkHours();
            double deadline = c.getAvailableHoursBeforeDeadline();

            // Reserve up to min(needed, deadline-capacity)
            double reserve  = Math.min(needed, deadline);

            // Consume from per-day capacity before the deadline
            double consumed = consumeCapacityBeforeDeadline(reserve, c.getEffectiveDeadline(), remainingCapacity);
            c.setAllocatedHours(consumed);
        }

        // ── Step 3: Redistribute leftover capacity by priority score ───────
        double leftover = remainingCapacity.values().stream().mapToDouble(Double::doubleValue).sum();
        if (leftover > 0.01) {
            redistributeLeftover(candidates, leftover, remainingCapacity);
        }

        // ── Step 4: Round to 15-minute increments and enforce caps ─────────
        for (PlanningCandidate c : candidates) {
            double rounded = Math.round(c.getAllocatedHours() * 4.0) / 4.0;
            double maxPossible = c.getEffectiveDeadline() != null
                    ? Math.min(c.getRemainingWorkHours(), c.getAvailableHoursBeforeDeadline())
                    : c.getRemainingWorkHours();
            rounded = Math.min(rounded, maxPossible);
            c.setAllocatedHours(Math.max(0.0, rounded));
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Consumes up to {@code hours} from remaining daily capacity, only using
     * days that are on or before the effective deadline date.
     *
     * @return actual hours consumed (≤ requested)
     */
    private double consumeCapacityBeforeDeadline(double hours,
                                                  java.time.LocalDateTime deadline,
                                                  Map<LocalDate, Double> remainingCapacity) {
        double consumed = 0.0;
        for (Map.Entry<LocalDate, Double> entry : new TreeMap<>(remainingCapacity).entrySet()) {
            if (consumed >= hours) break;
            LocalDate date  = entry.getKey();

            // Only use days on or before the deadline date
            if (deadline != null && date.isAfter(deadline.toLocalDate())) continue;

            double avail = remainingCapacity.getOrDefault(date, 0.0);
            if (avail <= 0) continue;

            double usableOnDay = avail;
            if (deadline != null && date.isEqual(deadline.toLocalDate())) {
                usableOnDay = Math.min(avail, hoursAvailableOnDeadlineDay(deadline.toLocalTime(), avail));
            }

            double take = Math.min(usableOnDay, hours - consumed);
            if (take <= 0) continue;

            remainingCapacity.put(date, avail - take);
            consumed += take;
        }
        return consumed;
    }

    private double hoursAvailableOnDeadlineDay(java.time.LocalTime deadlineTime, double dayCapacity) {
        if (!deadlineTime.isAfter(SessionScheduler.DEFAULT_START)) {
            return 0.0;
        }
        double hoursUntilDeadline = (double) SessionScheduler.DEFAULT_START.until(deadlineTime, java.time.temporal.ChronoUnit.MINUTES) / 60.0;
        return Math.min(dayCapacity, hoursUntilDeadline);
    }

    /**
     * Distributes {@code leftover} hours among candidates that still need
     * more time, proportionally by priority score.
     */
    private void redistributeLeftover(List<PlanningCandidate> candidates,
                                       double leftover,
                                       Map<LocalDate, Double> remainingCapacity) {
        // Only candidates that still have unmet work and unexhausted deadline capacity are eligible
        List<PlanningCandidate> eligible = candidates.stream()
                .filter(c -> {
                    double maxPossible = c.getEffectiveDeadline() != null
                            ? Math.min(c.getRemainingWorkHours(), c.getAvailableHoursBeforeDeadline())
                            : c.getRemainingWorkHours();
                    return maxPossible > c.getAllocatedHours() + 0.01;
                })
                .toList();

        if (eligible.isEmpty()) return;

        double totalScore = eligible.stream()
                .mapToDouble(PlanningCandidate::getPriorityScore)
                .sum();
        if (totalScore <= 0) return;

        // Proportional distribution (iterative with cap)
        Map<String, Double> extra = new LinkedHashMap<>();
        for (PlanningCandidate c : eligible) {
            extra.put(c.candidateKey(), 0.0);
        }

        double toDistribute = leftover;
        for (int iter = 0; iter < 20 && toDistribute > 0.01; iter++) {
            double scoreSum = eligible.stream()
                    .filter(c -> {
                        double maxPossible = c.getEffectiveDeadline() != null
                                ? Math.min(c.getRemainingWorkHours(), c.getAvailableHoursBeforeDeadline())
                                : c.getRemainingWorkHours();
                        return extra.get(c.candidateKey()) < maxPossible - c.getAllocatedHours();
                    })
                    .mapToDouble(PlanningCandidate::getPriorityScore)
                    .sum();
            if (scoreSum <= 0) break;

            double overflow = 0.0;
            for (PlanningCandidate c : eligible) {
                double alreadyExtra = extra.get(c.candidateKey());
                double maxPossible = c.getEffectiveDeadline() != null
                        ? Math.min(c.getRemainingWorkHours(), c.getAvailableHoursBeforeDeadline())
                        : c.getRemainingWorkHours();
                double stillNeed = maxPossible - c.getAllocatedHours() - alreadyExtra;
                if (stillNeed <= 0) continue;

                double share = (c.getPriorityScore() / scoreSum) * toDistribute;
                double add   = Math.min(share, stillNeed);
                extra.put(c.candidateKey(), alreadyExtra + add);
                overflow += (share - add);
            }
            toDistribute = overflow;
        }

        // Apply extra allocations
        for (PlanningCandidate c : eligible) {
            double add = extra.getOrDefault(c.candidateKey(), 0.0);
            c.setAllocatedHours(c.getAllocatedHours() + add);
        }
    }

    /**
     * Comparator that puts the most time-constrained candidates first:
     * IMPOSSIBLE → AT_RISK → FEASIBLE, then earliest deadline, then highest score.
     */
    private Comparator<PlanningCandidate> deadlineUrgencyComparator() {
        return Comparator
                // Higher feasibility risk first
                .comparingInt((PlanningCandidate c) -> feasibilityOrder(c.getFeasibilityStatus()))
                // Earlier deadline first (null = no deadline = last)
                .thenComparing(c -> c.getEffectiveDeadline() == null
                                ? java.time.LocalDateTime.MAX
                                : c.getEffectiveDeadline())
                // Higher priority score first (reversed)
                .thenComparingDouble(c -> -c.getPriorityScore());
    }

    private int feasibilityOrder(FeasibilityStatus status) {
        return switch (status) {
            case IMPOSSIBLE_WITH_CURRENT_AVAILABILITY -> 0;
            case AT_RISK                              -> 1;
            case FEASIBLE                             -> 2;
        };
    }

    /**
     * Normalizes a raw score map so values sum to 1.0.
     * Kept for backward compatibility; unused internally by the new algorithm.
     */
    public Map<String, Double> normalize(Map<String, Double> rawScores) {
        double total = rawScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return new LinkedHashMap<>(rawScores);
        Map<String, Double> normalized = new LinkedHashMap<>();
        rawScores.forEach((k, v) -> normalized.put(k, v / total));
        return normalized;
    }
}
