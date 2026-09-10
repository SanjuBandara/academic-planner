package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Determines how many hours of work remain for each {@link PlanningCandidate}.
 *
 * <h2>Strategy</h2>
 * <ol>
 *   <li>For an assessment that has linked tasks: sum {@code task.remainingHours}
 *       across all non-completed, non-cancelled tasks.</li>
 *   <li>For an assessment with no linked tasks: fall back to
 *       {@code assessment.estimatedHours}.</li>
 *   <li>For a standalone task: use {@code task.remainingHours}, falling back
 *       to {@code task.estimatedHours} if remaining is null.</li>
 * </ol>
 *
 * <p>Remaining hours are never negative — they are floored at {@code 0.0}.
 *
 * <p>This class is stateless and deterministic.
 */
@Component
public class WorkloadEstimator {

    /**
     * Populates {@code remainingWorkHours} on every candidate in the list.
     *
     * @param candidates list of planning candidates (mutated in-place)
     * @param allTasks   all active planning tasks for the user (pre-loaded)
     */
    public void estimate(List<PlanningCandidate> candidates, List<Task> allTasks) {
        // Build a map: assessmentId → list of linked tasks (if id is present)
        Map<Long, List<Task>> tasksByAssessmentId = allTasks.stream()
                .filter(t -> t.getAssessment() != null && t.getAssessment().getId() != null)
                .collect(Collectors.groupingBy(t -> t.getAssessment().getId()));

        for (PlanningCandidate candidate : candidates) {
            double remaining;

            if (candidate.isStandaloneTask()) {
                // Standalone task — use remainingHours or fall back to estimatedHours
                remaining = resolveTaskRemaining(candidate.getTask());
            } else if (candidate.getTask() != null) {
                // Task-level candidate linked to an assessment — use that task's remaining hours
                remaining = resolveTaskRemaining(candidate.getTask());
            } else {
                // Assessment-level candidate — derive from linked tasks or fall back
                Assessment a = candidate.getAssessment();
                List<Task> linked = a.getId() != null ? tasksByAssessmentId.get(a.getId()) : null;
                if (linked == null) {
                    linked = allTasks.stream().filter(t -> t.getAssessment() == a).toList();
                }
                if (linked != null && !linked.isEmpty()) {
                    remaining = linked.stream()
                            .mapToDouble(this::resolveTaskRemaining)
                            .sum();
                } else {
                    remaining = a.getEstimatedHours() != null ? a.getEstimatedHours() : 0.0;
                }
            }

            candidate.setRemainingWorkHours(Math.max(0.0, remaining));
        }
    }

    // ── public helper (used directly in unit tests and pipeline) ───────────────

    public double resolveTaskRemaining(Task task) {
        if (task == null) {
            return 0.0;
        }
        if (task.getStatus() == Task.TaskStatus.COMPLETED || task.getStatus() == Task.TaskStatus.CANCELLED) {
            return 0.0;
        }
        if (task.getRemainingHours() != null) {
            return Math.max(0.0, task.getRemainingHours());
        }
        if (task.getEstimatedHours() != null) {
            return Math.max(0.0, task.getEstimatedHours());
        }
        return 0.0;
    }
}
