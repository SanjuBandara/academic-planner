package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the full deterministic planning pipeline:
 *
 * <pre>
 *   build candidates
 *        ↓
 *   WorkloadEstimator      → remainingWorkHours
 *        ↓
 *   DeadlineAnalyzer       → availableHoursBeforeDeadline
 *        ↓
 *   PriorityCalculator     → priorityFactors / score
 *        ↓
 *   FeasibilityAnalyzer    → feasibilityStatus / warning
 *        ↓
 *   WorkloadAllocator      → allocatedHours
 *        ↓
 *   SessionScheduler       → StudyPlanItem list
 *        ↓
 *   PlanningResult
 * </pre>
 *
 * <p>
 * This class holds no planning logic itself — it is purely wiring. Every
 * decision (priority, feasibility, allocation, scheduling) is delegated to
 * the dedicated component responsible for it, which keeps the pipeline
 * explainable and independently testable.
 *
 * <p>
 * Deterministic: given the same assessments, tasks, availability, and
 * {@code now}, this always produces the same plan. No AI/ML/randomness.
 */
@Component
public class PlanningEngine {

    private final WorkloadEstimator workloadEstimator;
    private final DeadlineAnalyzer deadlineAnalyzer;
    private final PriorityCalculator priorityCalculator;
    private final FeasibilityAnalyzer feasibilityAnalyzer;
    private final WorkloadAllocator workloadAllocator;
    private final SessionScheduler sessionScheduler;

    public PlanningEngine(WorkloadEstimator workloadEstimator,
            DeadlineAnalyzer deadlineAnalyzer,
            PriorityCalculator priorityCalculator,
            FeasibilityAnalyzer feasibilityAnalyzer,
            WorkloadAllocator workloadAllocator,
            SessionScheduler sessionScheduler) {
        this.workloadEstimator = workloadEstimator;
        this.deadlineAnalyzer = deadlineAnalyzer;
        this.priorityCalculator = priorityCalculator;
        this.feasibilityAnalyzer = feasibilityAnalyzer;
        this.workloadAllocator = workloadAllocator;
        this.sessionScheduler = sessionScheduler;
    }

    /**
     * Runs the full pipeline and returns a structured {@link PlanningResult}.
     * Uses {@link LocalDateTime#now()} as the wall-clock reference.
     */
    public PlanningResult generatePlan(StudyPlan studyPlan,
            List<Assessment> assessments,
            List<Task> tasks,
            Map<LocalDate, Double> dailyHours) {
        return generatePlan(studyPlan, assessments, tasks, dailyHours, LocalDateTime.now());
    }

    /**
     * Overload accepting an explicit {@code now}, so unit tests can fix the
     * wall-clock reference and get fully reproducible results.
     */
    public PlanningResult generatePlan(StudyPlan studyPlan,
            List<Assessment> assessments,
            List<Task> tasks,
            Map<LocalDate, Double> dailyHours,
            LocalDateTime now) {

        double totalAvailable = dailyHours.values().stream().mapToDouble(Double::doubleValue).sum();

        // 1. Build candidates (task-level where possible, per section 16 of the spec)
        List<PlanningCandidate> candidates = buildCandidates(assessments, tasks);

        // 2. Remaining workload
        workloadEstimator.estimate(candidates, tasks);

        // Nothing left to schedule — short-circuit with an empty, well-formed result
        candidates = candidates.stream()
                .filter(c -> c.getRemainingWorkHours() > 0)
                .toList();

        if (candidates.isEmpty()) {
            return new PlanningResult(List.of(), 0.0, totalAvailable, totalAvailable, Map.of());
        }

        // 3. Capacity available before each candidate's effective deadline
        deadlineAnalyzer.analyzeAll(candidates, dailyHours, now);

        // 4. Priority score (type × credit × urgency × workload)
        priorityCalculator.calculateAll(candidates, now);

        // 5. Feasibility classification (FEASIBLE / AT_RISK /
        // IMPOSSIBLE_WITH_CURRENT_AVAILABILITY)
        feasibilityAnalyzer.analyzeAll(candidates);

        // 6. Deadline-constrained capacity allocation
        workloadAllocator.allocate(candidates, dailyHours);

        // 7. Concrete day/time session scheduling
        List<StudyPlanItem> items = sessionScheduler.schedule(studyPlan, candidates, dailyHours);

        double totalPlanned = items.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        double unallocated = Math.max(0.0, totalAvailable - totalPlanned);

        Map<String, String> warnings = candidates.stream()
                .filter(c -> c.getFeasibilityWarning() != null)
                .collect(Collectors.toMap(
                        PlanningCandidate::candidateKey,
                        PlanningCandidate::getFeasibilityWarning,
                        (a, b) -> a,
                        LinkedHashMap::new));

        return new PlanningResult(items, totalPlanned, totalAvailable, unallocated, warnings);
    }

    // ── Candidate construction ────────────────────────────────────────────

    /**
     * Builds one candidate per schedulable unit of work:
     * <ul>
     * <li>Assessment with active (non-completed/cancelled) linked tasks →
     * one candidate per task, inheriting the assessment's type/module/deadline
     * context but refined by the task's own deadline and remaining work.</li>
     * <li>Assessment with no usable linked tasks → one candidate for the
     * assessment itself, falling back to its estimated hours.</li>
     * <li>Task with no assessment link → one standalone candidate
     * (type weight OTHER applies downstream, via {@code assessment == null}).</li>
     * </ul>
     * Completed/cancelled tasks are excluded entirely — they contribute no
     * remaining work and would only add noise.
     */
    private List<PlanningCandidate> buildCandidates(List<Assessment> assessments, List<Task> tasks) {
        List<PlanningCandidate> candidates = new ArrayList<>();

        Map<Long, List<Task>> tasksByAssessmentId = tasks.stream()
                .filter(t -> t.getAssessment() != null && t.getAssessment().getId() != null)
                .collect(Collectors.groupingBy(t -> t.getAssessment().getId()));

        for (Assessment a : assessments) {
            List<Task> linked = a.getId() != null
                    ? tasksByAssessmentId.getOrDefault(a.getId(), List.of())
                    : tasks.stream().filter(t -> t.getAssessment() == a).toList();

            List<Task> activeLinked = linked.stream()
                    .filter(this::isSchedulable)
                    .toList();

            if (!activeLinked.isEmpty()) {
                for (Task t : activeLinked) {
                    LocalDateTime effective = deadlineAnalyzer.effectiveDeadline(t.getDueDateTime(),
                            a.getDueDateTime());
                    candidates.add(PlanningCandidate.forTask(t, a, effective));
                }
            } else {
                candidates.add(PlanningCandidate.forAssessment(a, a.getDueDateTime()));
            }
        }

        for (Task t : tasks) {
            if (t.getAssessment() != null)
                continue; // already handled above
            if (!isSchedulable(t))
                continue;
            candidates.add(PlanningCandidate.forStandaloneTask(t, t.getDueDateTime()));
        }

        return candidates;
    }

    private boolean isSchedulable(Task t) {
        return t.getStatus() != Task.TaskStatus.COMPLETED && t.getStatus() != Task.TaskStatus.CANCELLED;
    }
}