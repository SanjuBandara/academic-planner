package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Converts a list of {@link PlanningCandidate}s with allocated hours into
 * concrete, time-blocked {@link StudyPlanItem}s.
 *
 * <h2>Scheduling rules</h2>
 * <ul>
 *   <li>Sessions start at {@link #DEFAULT_START} (09:00).</li>
 *   <li>Maximum single session = {@link #MAX_SESSION_HOURS} (3 h).</li>
 *   <li>After a session ≥ {@link #BREAK_THRESHOLD_HOURS} (2 h): 30-minute break.</li>
 *   <li>After a session {@literal <} 2 h: 15-minute gap.</li>
 *   <li>All session lengths are rounded to 15-minute increments.</li>
 *   <li>A session is never created if its duration would be &lt; 15 minutes.</li>
 *   <li>A session must not start on or after the effective deadline.</li>
 *   <li>A session must not end after the effective deadline.</li>
 * </ul>
 *
 * <h2>Scheduling order</h2>
 * Each day iterates candidates in <em>scheduling priority</em> order:
 * feasibility risk DESC → effective deadline ASC → priority score DESC.
 * This ensures deadline-constrained items get the day's first slots.
 */
@Component
public class SessionScheduler {

    /** Default session start time for each day. */
    public static final LocalTime DEFAULT_START         = LocalTime.of(9, 0);
    /** Maximum hours in a single study session. */
    public static final double    MAX_SESSION_HOURS     = 3.0;
    /** Minimum session duration to bother scheduling (15 min). */
    public static final double    MIN_SESSION_HOURS     = 0.25;
    /** Session duration threshold that triggers a long break. */
    public static final double    BREAK_THRESHOLD_HOURS = 2.0;
    /** Break duration (minutes) after a long session. */
    public static final int       BREAK_MINUTES         = 30;
    /** Gap duration (minutes) between shorter sessions. */
    public static final int       GAP_MINUTES           = 15;

    /**
     * Generates study-plan items for all candidates across all days.
     *
     * @param studyPlan  parent plan entity (already saved)
     * @param candidates candidates with {@code allocatedHours} set
     * @param dailyHours daily availability map
     * @return ordered list of items, ready to persist
     */
    public List<StudyPlanItem> schedule(StudyPlan studyPlan,
                                        List<PlanningCandidate> candidates,
                                        Map<LocalDate, Double> dailyHours) {

        List<StudyPlanItem> items = new ArrayList<>();

        // Sort days chronologically
        List<LocalDate> days = dailyHours.keySet().stream().sorted().toList();

        // Sort candidates by scheduling priority (immutable list → new list)
        List<PlanningCandidate> agenda = new ArrayList<>(candidates);
        agenda.sort(schedulingComparator());

        // Mutable remaining allocations per candidate
        Map<String, Double> remainingAllocation = new LinkedHashMap<>();
        for (PlanningCandidate c : agenda) {
            remainingAllocation.put(c.candidateKey(), c.getAllocatedHours());
        }

        for (LocalDate day : days) {
            double dayCapacity = dailyHours.getOrDefault(day, 0.0);
            if (dayCapacity <= 0) continue;

            double    dayRemaining = dayCapacity;
            LocalTime cursor       = DEFAULT_START;

            boolean scheduledAny = true;
            while (dayRemaining >= MIN_SESSION_HOURS && scheduledAny) {
                scheduledAny = false;

                for (PlanningCandidate candidate : agenda) {
                    if (dayRemaining < MIN_SESSION_HOURS) break;

                    String key       = candidate.candidateKey();
                    double stillNeed = remainingAllocation.getOrDefault(key, 0.0);
                    if (stillNeed < MIN_SESSION_HOURS) continue;

                    // ── Deadline boundary checks ───────────────────────────────
                    LocalDateTime deadline = candidate.getEffectiveDeadline();
                    if (deadline != null) {
                        // Do not start a session on a day after the deadline
                        if (day.isAfter(deadline.toLocalDate())) continue;

                        // On the deadline day, cursor must be before deadline time
                        if (day.isEqual(deadline.toLocalDate())) {
                            if (!cursor.isBefore(deadline.toLocalTime())) continue;
                        }
                    }

                    // ── Compute maximum session hours ──────────────────────────
                    double maxForSession = Math.min(stillNeed, Math.min(dayRemaining, MAX_SESSION_HOURS));

                    // Trim to deadline time boundary on the deadline day
                    if (deadline != null && day.isEqual(deadline.toLocalDate())) {
                        double minutesUntilDeadline = (double) cursor.until(deadline.toLocalTime(), ChronoUnit.MINUTES);
                        double hoursUntilDeadline   = minutesUntilDeadline / 60.0;
                        maxForSession = Math.min(maxForSession, hoursUntilDeadline);
                    }

                    // Round to 15-minute slot
                    double sessionHours = Math.round(maxForSession * 4.0) / 4.0;
                    if (sessionHours > maxForSession + 0.001) {
                        sessionHours = Math.floor(maxForSession * 4.0) / 4.0;
                    }
                    if (sessionHours < MIN_SESSION_HOURS) continue;

                    LocalTime start = cursor;
                    LocalTime end   = cursor.plusMinutes(Math.round(sessionHours * 60));

                    // Final safety check: end must not exceed deadline time
                    if (deadline != null && day.isEqual(deadline.toLocalDate())) {
                        if (end.isAfter(deadline.toLocalTime())) continue;
                    }

                    // ── Build item ─────────────────────────────────────────────
                    Assessment assessment = candidate.getAssessment();
                    Task       task       = candidate.getTask();
                    Module     module     = resolveModule(assessment, task);

                    StudyPlanItem item = StudyPlanItem.builder()
                            .studyPlan(studyPlan)
                            .date(day)
                            .startTime(start)
                            .endTime(end)
                            .module(module)
                            .assessment(assessment)
                            .task(task)
                            .plannedHours(sessionHours)
                            .priorityScore(candidate.getPriorityScore())
                            .status(ItemStatus.PLANNED)
                            .build();

                    items.add(item);
                    scheduledAny = true;

                    // ── Advance cursor and deduct ──────────────────────────────
                    cursor = end;
                    if (sessionHours >= BREAK_THRESHOLD_HOURS) {
                        cursor = cursor.plusMinutes(BREAK_MINUTES);
                    } else {
                        cursor = cursor.plusMinutes(GAP_MINUTES);
                    }

                    dayRemaining -= sessionHours;
                    remainingAllocation.merge(key, -sessionHours, Double::sum);
                }
            }
        }

        return items;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Module resolveModule(Assessment assessment, Task task) {
        if (assessment != null) return assessment.getModule();
        if (task != null) return task.getModule();
        return null;
    }

    /**
     * Scheduling-priority comparator:
     * IMPOSSIBLE / AT_RISK first → earliest deadline → highest score.
     */
    private Comparator<PlanningCandidate> schedulingComparator() {
        return Comparator
                .comparingInt((PlanningCandidate c) -> feasibilityOrder(c.getFeasibilityStatus()))
                .thenComparing(c -> c.getEffectiveDeadline() == null
                        ? LocalDateTime.MAX
                        : c.getEffectiveDeadline())
                .thenComparingDouble(c -> -c.getPriorityScore());
    }

    private int feasibilityOrder(PlanningCandidate.FeasibilityStatus status) {
        return switch (status) {
            case IMPOSSIBLE_WITH_CURRENT_AVAILABILITY -> 0;
            case AT_RISK                              -> 1;
            case FEASIBLE                             -> 2;
        };
    }
}
