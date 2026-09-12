package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskStatus;
import com.academicplanner.planning.model.DailyAvailability;
import com.academicplanner.planning.model.PlanningItem;
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
 *   1. Build PlanningItems (from active assessments and standalone tasks)
 *        ↓
 *   2. PriorityCalculator   → item.weight = (effectivePriority × credits)
 *        ↓
 *   3. TimeAllocator        → item.allocatedHours = totalAvailable × (weight / totalWeight)
 *        ↓
 *   4. FeasibilityAnalyzer → item.feasibilityStatus & warning
 *        ↓
 *   5. SessionScheduler    → concrete StudyPlanItem list
 *        ↓
 *   6. PlanningResult
 * </pre>
 *
 * <p>Deterministic: given the same inputs and reference time {@code now},
 * this always produces the exact same schedule.
 */
@Component
public class PlanningEngine {

    private final PriorityCalculator priorityCalculator;
    private final TimeAllocator timeAllocator;
    private final FeasibilityAnalyzer feasibilityAnalyzer;
    private final SessionScheduler sessionScheduler;

    public PlanningEngine(PriorityCalculator priorityCalculator,
                          TimeAllocator timeAllocator,
                          FeasibilityAnalyzer feasibilityAnalyzer,
                          SessionScheduler sessionScheduler) {
        this.priorityCalculator = priorityCalculator;
        this.timeAllocator = timeAllocator;
        this.feasibilityAnalyzer = feasibilityAnalyzer;
        this.sessionScheduler = sessionScheduler;
    }

    /**
     * Generates a study plan using wall-clock now.
     */
    public PlanningResult generatePlan(StudyPlan studyPlan,
                                       List<Assessment> assessments,
                                       List<Task> tasks,
                                       Map<LocalDate, Double> dailyHours) {
        return generatePlan(studyPlan, assessments, tasks, dailyHours, LocalDateTime.now());
    }

    /**
     * Generates a study plan with an explicit reference time {@code now} (for deterministic tests).
     */
    public PlanningResult generatePlan(StudyPlan studyPlan,
                                       List<Assessment> assessments,
                                       List<Task> tasks,
                                       Map<LocalDate, Double> dailyHours,
                                       LocalDateTime now) {
        Map<LocalDate, DailyAvailability> dailyAvail = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Double> entry : dailyHours.entrySet()) {
            dailyAvail.put(entry.getKey(), DailyAvailability.ofHoursOnly(entry.getKey(), entry.getValue()));
        }
        return generatePlanWithAvailability(studyPlan, assessments, tasks, dailyAvail, now);
    }

    /**
     * Runs the pipeline with rich {@link DailyAvailability} per day.
     */
    public PlanningResult generatePlanWithAvailability(StudyPlan studyPlan,
                                                       List<Assessment> assessments,
                                                       List<Task> tasks,
                                                       Map<LocalDate, DailyAvailability> dailyAvail,
                                                       LocalDateTime now) {
        double totalAvailable = dailyAvail.values().stream()
                .mapToDouble(DailyAvailability::effectiveSchedulableHours)
                .sum();

        // 1. Build planning items
        List<PlanningItem> items = buildPlanningItems(assessments, tasks);

        if (items.isEmpty() || totalAvailable <= 0.0) {
            return new PlanningResult(List.of(), 0.0, totalAvailable, totalAvailable, Map.of());
        }

        // 2. Compute weights: effectivePriority × credits
        priorityCalculator.calculateAll(items, now);

        // 3. Allocate hours proportionally by weight (respecting task caps)
        timeAllocator.allocate(items, totalAvailable);

        // 4. Feasibility analysis
        Map<LocalDate, Double> dailyCapacityMap = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, DailyAvailability> entry : dailyAvail.entrySet()) {
            dailyCapacityMap.put(entry.getKey(), entry.getValue().effectiveSchedulableHours());
        }
        feasibilityAnalyzer.analyzeAll(items, dailyCapacityMap, now);

        // 5. Schedule sessions across days and time slots
        List<StudyPlanItem> scheduledItems = sessionScheduler.scheduleWithAvailability(studyPlan, items, dailyAvail);

        double totalPlanned = scheduledItems.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        double unallocated = Math.max(0.0, totalAvailable - totalPlanned);

        Map<String, String> warnings = items.stream()
                .filter(i -> i.getFeasibilityWarning() != null)
                .collect(Collectors.toMap(
                        PlanningItem::itemKey,
                        PlanningItem::getFeasibilityWarning,
                        (a, b) -> a,
                        LinkedHashMap::new));

        return new PlanningResult(scheduledItems, totalPlanned, totalAvailable, unallocated, warnings);
    }

    /**
     * Builds planning items for active assessments and standalone tasks.
     */
    private List<PlanningItem> buildPlanningItems(List<Assessment> assessments, List<Task> tasks) {
        List<PlanningItem> items = new ArrayList<>();

        if (assessments != null) {
            for (Assessment a : assessments) {
                if (isSchedulable(a)) {
                    items.add(PlanningItem.forAssessment(a));
                }
            }
        }

        if (tasks != null) {
            for (Task t : tasks) {
                if (isSchedulable(t)) {
                    items.add(PlanningItem.forTask(t));
                }
            }
        }

        return items;
    }

    private boolean isSchedulable(Assessment a) {
        return a.getStatus() != AssessmentStatus.COMPLETED && a.getStatus() != AssessmentStatus.CANCELLED;
    }

    private boolean isSchedulable(Task t) {
        return t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED;
    }
}