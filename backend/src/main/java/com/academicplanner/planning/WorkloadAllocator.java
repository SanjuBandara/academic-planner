package com.academicplanner.planning;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates raw priority scores into concrete hour allocations.
 *
 * <p>The allocation is proportional to normalized scores so that
 * all available hours are distributed fully (no wasted capacity),
 * while each assessment receives hours proportional to its urgency,
 * credit weight, and remaining workload.
 *
 * <p>Allocation is also capped at each assessment's remaining work —
 * we never allocate more hours than the student actually needs.
 */
@Component
public class WorkloadAllocator {

    /**
     * Distributes {@code totalAvailableHours} among assessments according to their raw scores,
     * capping each assessment's allocation at its remaining hours.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Normalize raw scores to proportions (sum = 1.0).</li>
     *   <li>For each assessment, compute proportional hours.</li>
     *   <li>Cap at {@code remainingHours} for that assessment.</li>
     *   <li>Redistribute any leftover hours from capped assessments proportionally among uncapped ones.</li>
     *   <li>Repeat redistribution until stable (converges quickly in practice).</li>
     * </ol>
     *
     * @param scores           map of assessmentId → raw priority score
     * @param remainingHours   map of assessmentId → remaining study hours needed
     * @param totalAvailable   total hours to distribute
     * @return map of assessmentId → allocated hours
     */
    public Map<Long, Double> allocate(Map<Long, Double> scores,
                                      Map<Long, Double> remainingHours,
                                      double totalAvailable) {

        Map<Long, Double> allocation = new LinkedHashMap<>();

        // Initialize all allocations to 0
        for (Long id : scores.keySet()) {
            allocation.put(id, 0.0);
        }

        if (scores.isEmpty() || totalAvailable <= 0) {
            return allocation;
        }

        double hoursToDistribute = totalAvailable;
        Map<Long, Double> workingScores = new LinkedHashMap<>(scores);

        // Iterative capped allocation (max 20 iterations for safety)
        for (int iteration = 0; iteration < 20; iteration++) {
            double scoreSum = workingScores.values().stream().mapToDouble(Double::doubleValue).sum();
            if (scoreSum <= 0 || hoursToDistribute <= 0) break;

            double leftover = 0.0;
            Map<Long, Double> iterAllocation = new LinkedHashMap<>();

            for (Map.Entry<Long, Double> entry : workingScores.entrySet()) {
                Long id = entry.getKey();
                double proportion = entry.getValue() / scoreSum;
                double proposed = proportion * hoursToDistribute;
                double cap = remainingHours.getOrDefault(id, Double.MAX_VALUE);

                if (proposed > cap) {
                    iterAllocation.put(id, cap);
                    leftover += (proposed - cap);
                    workingScores.put(id, 0.0); // exclude from future redistributions
                } else {
                    iterAllocation.put(id, proposed);
                }
            }

            // Add iteration results to cumulative allocation
            for (Map.Entry<Long, Double> entry : iterAllocation.entrySet()) {
                allocation.merge(entry.getKey(), entry.getValue(), Double::sum);
            }

            hoursToDistribute = leftover;

            // If no scores remain eligible for redistribution, stop
            if (workingScores.values().stream().allMatch(s -> s <= 0)) break;
        }

        // Round to 2 decimal places for cleaner display
        allocation.replaceAll((id, hours) -> Math.round(hours * 100.0) / 100.0);
        return allocation;
    }

    /**
     * Normalizes a raw score map so values sum to 1.0.
     * Returns an empty map if all scores are 0.
     */
    public Map<Long, Double> normalize(Map<Long, Double> rawScores) {
        double total = rawScores.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            return new LinkedHashMap<>(rawScores);
        }
        Map<Long, Double> normalized = new LinkedHashMap<>();
        rawScores.forEach((id, score) -> normalized.put(id, score / total));
        return normalized;
    }
}
