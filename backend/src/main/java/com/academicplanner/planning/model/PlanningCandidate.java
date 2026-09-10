package com.academicplanner.planning.model;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Task;

import java.time.LocalDateTime;

/**
 * A single unit of work that the planning engine can schedule.
 *
 * <p>A candidate is either:
 * <ul>
 *   <li>An <b>assessment</b> — potentially backed by linked tasks.</li>
 *   <li>A <b>standalone task</b> — no assessment link.</li>
 * </ul>
 *
 * <p>Fields are populated progressively as the pipeline advances:
 * <ol>
 *   <li>Construction — entity references and effective deadline.</li>
 *   <li>{@link com.academicplanner.planning.WorkloadEstimator} — remainingWorkHours.</li>
 *   <li>{@link com.academicplanner.planning.DeadlineAnalyzer}   — availableHoursBeforeDeadline.</li>
 *   <li>{@link com.academicplanner.planning.PriorityCalculator} — priorityFactors.</li>
 *   <li>{@link com.academicplanner.planning.FeasibilityAnalyzer}— feasibilityStatus, feasibilityWarning.</li>
 *   <li>{@link com.academicplanner.planning.WorkloadAllocator}  — allocatedHours.</li>
 * </ol>
 */
public final class PlanningCandidate {

    // ── identity ───────────────────────────────────────────────────────────

    /** The assessment this candidate represents; null if standalone task. */
    private final Assessment assessment;

    /**
     * A single representative task for the candidate.
     * For assessment candidates this holds ONE specific task being scheduled.
     * For standalone task candidates this holds the task itself.
     */
    private final Task task;

    // ── computed / pipeline-filled ─────────────────────────────────────────

    /**
     * Effective deadline = min(task.dueDateTime, assessment.dueDateTime), null-safe.
     * Set at construction time.
     */
    private final LocalDateTime effectiveDeadline;

    /** Hours of work remaining. Set by WorkloadEstimator. */
    private double remainingWorkHours;

    /** Available capacity in dailyHours before the effective deadline. */
    private double availableHoursBeforeDeadline;

    /** Factors and final score. Set by PriorityCalculator. */
    private PriorityFactors priorityFactors;

    /** Feasibility classification. Set by FeasibilityAnalyzer. */
    private FeasibilityStatus feasibilityStatus = FeasibilityStatus.FEASIBLE;

    /** Human-readable warning message when not FEASIBLE. May be null. */
    private String feasibilityWarning;

    /** Hours allocated by WorkloadAllocator. */
    private double allocatedHours;

    // ── constructor ────────────────────────────────────────────────────────

    public PlanningCandidate(Assessment assessment, Task task, LocalDateTime effectiveDeadline) {
        this.assessment = assessment;
        this.task = task;
        this.effectiveDeadline = effectiveDeadline;
    }

    // ── convenience factory methods ────────────────────────────────────────

    /** Factory: candidate representing a whole assessment (tasks handled separately). */
    public static PlanningCandidate forAssessment(Assessment assessment, LocalDateTime effectiveDeadline) {
        return new PlanningCandidate(assessment, null, effectiveDeadline);
    }

    /**
     * Factory: candidate representing one specific task linked to an assessment.
     * The task inherits the assessment's type, module, and deadline context.
     */
    public static PlanningCandidate forTask(Task task, Assessment assessment, LocalDateTime effectiveDeadline) {
        return new PlanningCandidate(assessment, task, effectiveDeadline);
    }

    /** Factory: candidate representing a standalone task (no assessment). */
    public static PlanningCandidate forStandaloneTask(Task task, LocalDateTime effectiveDeadline) {
        return new PlanningCandidate(null, task, effectiveDeadline);
    }

    // ── derived helpers ────────────────────────────────────────────────────

    /**
     * Stable, unique key for maps.
     * Uses "A-{id}" for assessment candidates and "T-{id}" for task candidates.
     */
    public String candidateKey() {
        if (task != null) {
            return "T-" + (task.getId() != null ? task.getId() : System.identityHashCode(task));
        }
        if (assessment != null) {
            return "A-" + (assessment.getId() != null ? assessment.getId() : System.identityHashCode(assessment));
        }
        return "C-" + System.identityHashCode(this);
    }

    /** Returns the associated module from either assessment or task, if present. */
    public com.academicplanner.entity.Module getEffectiveModule() {
        if (assessment != null && assessment.getModule() != null) {
            return assessment.getModule();
        }
        if (task != null && task.getModule() != null) {
            return task.getModule();
        }
        return null;
    }

    /** Returns the candidate's title for logging and warnings. */
    public String getTitle() {
        if (task != null && task.getTitle() != null) {
            return task.getTitle();
        }
        if (assessment != null && assessment.getTitle() != null) {
            return assessment.getTitle();
        }
        return "Untitled";
    }

    /** True if this candidate is a standalone task (no associated assessment). */
    public boolean isStandaloneTask() {
        return assessment == null;
    }

    // ── getters ────────────────────────────────────────────────────────────

    public Assessment getAssessment() { return assessment; }

    public Task getTask() { return task; }

    public LocalDateTime getEffectiveDeadline() { return effectiveDeadline; }

    public double getRemainingWorkHours() { return remainingWorkHours; }

    public double getAvailableHoursBeforeDeadline() { return availableHoursBeforeDeadline; }

    public PriorityFactors getPriorityFactors() { return priorityFactors; }

    public double getPriorityScore() {
        return priorityFactors != null ? priorityFactors.score() : 0.0;
    }

    public FeasibilityStatus getFeasibilityStatus() { return feasibilityStatus; }

    public String getFeasibilityWarning() { return feasibilityWarning; }

    public double getAllocatedHours() { return allocatedHours; }

    // ── setters (package-private; only planning sub-components should write) ──

    public void setRemainingWorkHours(double v) { this.remainingWorkHours = v; }

    public void setAvailableHoursBeforeDeadline(double v) { this.availableHoursBeforeDeadline = v; }

    public void setPriorityFactors(PriorityFactors pf) { this.priorityFactors = pf; }

    public void setFeasibilityStatus(FeasibilityStatus s) { this.feasibilityStatus = s; }

    public void setFeasibilityWarning(String w) { this.feasibilityWarning = w; }

    public void setAllocatedHours(double v) { this.allocatedHours = v; }

    // ── debug ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        String label = task != null
                ? "Task[" + task.getId() + ":" + task.getTitle() + "]"
                : "Assessment[" + assessment.getId() + ":" + assessment.getTitle() + "]";
        return String.format("%s remaining=%.2fh deadline=%s %s allocated=%.2fh",
                label, remainingWorkHours, effectiveDeadline, feasibilityStatus, allocatedHours);
    }

    // ── inner enum ─────────────────────────────────────────────────────────

    public enum FeasibilityStatus {
        /** Remaining work can be completed before the deadline with current availability. */
        FEASIBLE,
        /** Remaining work is close to the available capacity — risk of falling short. */
        AT_RISK,
        /** Available capacity before the deadline is insufficient to complete remaining work. */
        IMPOSSIBLE_WITH_CURRENT_AVAILABILITY
    }
}
