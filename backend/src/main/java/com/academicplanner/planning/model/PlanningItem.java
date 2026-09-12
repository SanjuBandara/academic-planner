package com.academicplanner.planning.model;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.Task.TaskPriority;

import java.time.LocalDateTime;

/**
 * A single unit of work that the planning engine can schedule.
 *
 * <p>A PlanningItem is either:
 * <ul>
 *   <li>An <b>assessment preparation item</b> — created from a pending {@link Assessment}.
 *       The student does not create these manually; they are generated automatically
 *       for every pending assessment.</li>
 *   <li>A <b>standalone task item</b> — created from a student's {@link Task}.</li>
 * </ul>
 *
 * <h2>Pipeline population order</h2>
 * <ol>
 *   <li>Construction — entity references, deadline, activity label.</li>
 *   <li>{@link com.academicplanner.planning.PriorityCalculator} — weight.</li>
 *   <li>{@link com.academicplanner.planning.TimeAllocator} — allocatedHours.</li>
 *   <li>{@link com.academicplanner.planning.FeasibilityAnalyzer} — feasibilityStatus, warning.</li>
 *   <li>{@link com.academicplanner.planning.SessionScheduler} → StudyPlanItems.</li>
 * </ol>
 */
public final class PlanningItem {

    // ── identity ──────────────────────────────────────────────────────────────

    /** Non-null for assessment-based items. */
    private final Assessment assessment;

    /** Non-null for task-based items. */
    private final Task task;

    /** Effective deadline (assessment.dueDateTime or task.dueDateTime). */
    private final LocalDateTime deadline;

    /**
     * Human-readable label for this planning session.
     * Examples: "DSA Quiz Preparation", "Complete DSA Practice Problems"
     */
    private final String activityLabel;

    /** Type string stored on StudyPlanItem. "ASSESSMENT_PREP" or "TASK". */
    private final String activityType;

    // ── pipeline-filled ───────────────────────────────────────────────────────

    /** Planning weight (effectivePriority × credits). Set by PriorityCalculator. */
    private double weight;

    /**
     * Hours allocated from the student's available study time.
     * Set by TimeAllocator.
     */
    private double allocatedHours;

    /** Feasibility status. Set by FeasibilityAnalyzer. */
    private FeasibilityStatus feasibilityStatus = FeasibilityStatus.FEASIBLE;

    /** Human-readable warning when not FEASIBLE. May be null. */
    private String feasibilityWarning;

    // ── constructor ───────────────────────────────────────────────────────────

    private PlanningItem(Assessment assessment, Task task,
                         LocalDateTime deadline,
                         String activityLabel, String activityType) {
        this.assessment = assessment;
        this.task = task;
        this.deadline = deadline;
        this.activityLabel = activityLabel;
        this.activityType = activityType;
    }

    // ── factory methods ───────────────────────────────────────────────────────

    /**
     * Creates a planning item representing preparation for an assessment.
     * The label will be "{AssessmentTitle} Preparation" or "{AssessmentTitle} Work".
     */
    public static PlanningItem forAssessment(Assessment assessment) {
        String label = buildAssessmentLabel(assessment);
        return new PlanningItem(assessment, null, assessment.getDueDateTime(), label, "ASSESSMENT_PREP");
    }

    /**
     * Creates a planning item for a standalone task.
     */
    public static PlanningItem forTask(Task task) {
        String label = task.getTitle() != null ? task.getTitle() : "Study Task";
        return new PlanningItem(null, task, task.getDueDateTime(), label, "TASK");
    }

    // ── derived helpers ───────────────────────────────────────────────────────

    /** True if this item was created from an assessment. */
    public boolean isAssessmentItem() {
        return assessment != null;
    }

    /** Returns the module's credit value; 1 if no module is linked. */
    public int getModuleCredits() {
        if (assessment != null && assessment.getModule() != null) {
            return Math.max(1, assessment.getModule().getCredits());
        }
        if (task != null && task.getModule() != null) {
            return Math.max(1, task.getModule().getCredits());
        }
        return 1;
    }

    /** Returns the assessment type; null if this is a task item. */
    public AssessmentType getAssessmentType() {
        return assessment != null ? assessment.getType() : null;
    }

    /** Returns the task priority; null if this is an assessment item. */
    public TaskPriority getTaskPriority() {
        return task != null ? task.getPriority() : null;
    }

    /**
     * Maximum hours the planning engine should allocate to this item.
     * For tasks: uses estimatedHours as a cap (or null = no cap).
     * For assessments: no explicit cap (allocation driven purely by weight).
     */
    public Double getAllocationCap() {
        if (task != null && task.getEstimatedHours() != null && task.getEstimatedHours() > 0) {
            // Use remaining hours as cap if available, else estimated hours
            if (task.getRemainingHours() != null && task.getRemainingHours() > 0) {
                return task.getRemainingHours();
            }
            return task.getEstimatedHours();
        }
        return null; // no cap for assessments
    }

    /**
     * Stable unique key for maps and deduplication.
     * Format: "A-{id}" for assessments, "T-{id}" for tasks.
     */
    public String itemKey() {
        if (assessment != null) {
            return "A-" + (assessment.getId() != null ? assessment.getId() : System.identityHashCode(assessment));
        }
        return "T-" + (task != null && task.getId() != null ? task.getId() : System.identityHashCode(task));
    }

    // ── getters ───────────────────────────────────────────────────────────────

    public Assessment getAssessment()       { return assessment; }
    public Task getTask()                   { return task; }
    public LocalDateTime getDeadline()      { return deadline; }
    public String getActivityLabel()        { return activityLabel; }
    public String getActivityType()         { return activityType; }
    public double getWeight()               { return weight; }
    public double getAllocatedHours()        { return allocatedHours; }
    public FeasibilityStatus getFeasibilityStatus() { return feasibilityStatus; }
    public String getFeasibilityWarning()   { return feasibilityWarning; }

    // ── setters (called by pipeline components only) ──────────────────────────

    public void setWeight(double weight)                        { this.weight = weight; }
    public void setAllocatedHours(double allocatedHours)        { this.allocatedHours = allocatedHours; }
    public void setFeasibilityStatus(FeasibilityStatus status)  { this.feasibilityStatus = status; }
    public void setFeasibilityWarning(String warning)           { this.feasibilityWarning = warning; }

    // ── private helpers ───────────────────────────────────────────────────────

    private static String buildAssessmentLabel(Assessment assessment) {
        String title = assessment.getTitle() != null ? assessment.getTitle() : "Assessment";
        if (assessment.getType() == null) return title + " Preparation";
        return switch (assessment.getType()) {
            case EXAM         -> title + " Exam Preparation";
            case PROJECT      -> title + " Project Work";
            case ASSIGNMENT   -> title + " Assignment Work";
            case QUIZ         -> title + " Quiz Preparation";
            case REPORT       -> title + " Report Work";
            case PRESENTATION -> title + " Presentation Preparation";
            case OTHER        -> title + " Preparation";
        };
    }

    // ── inner enum ────────────────────────────────────────────────────────────

    public enum FeasibilityStatus {
        FEASIBLE,
        AT_RISK,
        IMPOSSIBLE_WITH_CURRENT_AVAILABILITY
    }

    @Override
    public String toString() {
        return String.format("PlanningItem[%s | %s | weight=%.2f | allocated=%.2fh | deadline=%s]",
                itemKey(), activityLabel, weight, allocatedHours, deadline);
    }
}
