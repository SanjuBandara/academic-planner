package com.academicplanner.planning;

import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task.TaskPriority;
import com.academicplanner.planning.model.PlanningItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Calculates the planning weight for each {@link PlanningItem}.
 *
 * <h2>Assessment Weight Formula</h2>
 * <pre>
 *   Base Priority (by type):
 *     EXAM         = 5
 *     PROJECT      = 4
 *     ASSIGNMENT   = 3
 *     QUIZ         = 3
 *     REPORT       = 3
 *     PRESENTATION = 3
 *     OTHER        = 2
 *
 *   Effective Priority = Base Priority + Deadline Urgency Bonus
 *
 *   Assessment Weight  = Effective Priority × Module Credits
 * </pre>
 *
 * <h2>Task Weight Formula</h2>
 * <pre>
 *   User Priority Value:
 *     HIGH   = 2.5
 *     MEDIUM = 2.0
 *     LOW    = 1.0
 *
 *   Task Weight = Priority Value × Module Credits
 *   (Module Credits = 1 if task has no module)
 * </pre>
 *
 * <p>This class is stateless. All inputs are explicit — no side effects.
 */
@Component
public class PriorityCalculator {

    // ── Assessment base priority values ──────────────────────────────────────

    public static final int BASE_EXAM         = 5;
    public static final int BASE_PROJECT      = 4;
    public static final int BASE_ASSIGNMENT   = 3;
    public static final int BASE_QUIZ         = 3;
    public static final int BASE_REPORT       = 3;
    public static final int BASE_PRESENTATION = 3;
    public static final int BASE_OTHER        = 2;

    // ── Task priority numeric values ─────────────────────────────────────────

    public static final double TASK_HIGH   = 2.5;
    public static final double TASK_MEDIUM = 2.0;
    public static final double TASK_LOW    = 1.0;

    private final DeadlineUrgencyCalculator urgencyCalculator;

    public PriorityCalculator(DeadlineUrgencyCalculator urgencyCalculator) {
        this.urgencyCalculator = urgencyCalculator;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Computes and sets the {@code weight} on every planning item in the list.
     *
     * @param items list of planning items (mutated in place)
     * @param now   reference time for deadline urgency calculation
     */
    public void calculateAll(List<PlanningItem> items, LocalDateTime now) {
        for (PlanningItem item : items) {
            double weight = computeWeight(item, now);
            item.setWeight(weight);
        }
    }

    /**
     * Computes the planning weight for a single item.
     *
     * @param item the planning item
     * @param now  reference time
     * @return planning weight (positive)
     */
    public double computeWeight(PlanningItem item, LocalDateTime now) {
        int credits = Math.max(1, item.getModuleCredits()); // fallback to 1 if no module

        if (item.isAssessmentItem()) {
            int basePriority = basePriorityFor(item.getAssessmentType());
            int urgencyBonus = urgencyCalculator.urgencyBonus(item.getDeadline(), now);
            int effectivePriority = basePriority + urgencyBonus;
            return (double) effectivePriority * credits;
        } else {
            // Standalone task
            double priorityValue = taskPriorityValue(item.getTaskPriority());
            return priorityValue * credits;
        }
    }

    // ── Type / priority helpers ───────────────────────────────────────────────

    /**
     * Returns the base planning priority for an assessment type.
     * This is the starting value before deadline urgency is added.
     */
    public int basePriorityFor(AssessmentType type) {
        if (type == null) return BASE_OTHER;
        return switch (type) {
            case EXAM         -> BASE_EXAM;
            case PROJECT      -> BASE_PROJECT;
            case ASSIGNMENT   -> BASE_ASSIGNMENT;
            case QUIZ         -> BASE_QUIZ;
            case REPORT       -> BASE_REPORT;
            case PRESENTATION -> BASE_PRESENTATION;
            case OTHER        -> BASE_OTHER;
        };
    }

    /**
     * Returns the numeric priority value for a task priority level.
     */
    public double taskPriorityValue(TaskPriority priority) {
        if (priority == null) return TASK_MEDIUM;
        return switch (priority) {
            case HIGH   -> TASK_HIGH;
            case MEDIUM -> TASK_MEDIUM;
            case LOW    -> TASK_LOW;
        };
    }
}
