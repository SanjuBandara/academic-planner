package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.DailyAvailability;
import com.academicplanner.planning.model.PlanningItem;
import com.academicplanner.planning.model.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Converts a list of {@link PlanningItem}s with allocated hours into
 * concrete {@link StudyPlanItem}s.
 *
 * <p>Supports two availability modes:
 * <ul>
 *   <li><b>Mode A (Hours Only):</b> No exact time slots provided. Sessions are scheduled
 *       with day-level duration; {@code startTime} and {@code endTime} remain {@code null}
 *       so the table display has clean, blank Time cells without invented times.</li>
 *   <li><b>Mode B (Hours + Time Slots):</b> Explicit time slots provided. Sessions are scheduled
 *       strictly within the declared windows, populating exact {@code startTime} and {@code endTime}.
 *       Breaks/gaps never extend past slot boundaries.</li>
 * </ul>
 */
@Component
public class SessionScheduler {

    /** Default session start time when needed. */
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
     * Scheduling method using daily hours map (Mode A).
     */
    public List<StudyPlanItem> schedule(StudyPlan studyPlan,
                                        List<PlanningItem> items,
                                        Map<LocalDate, Double> dailyHours) {
        Map<LocalDate, DailyAvailability> dailyAvail = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, Double> entry : dailyHours.entrySet()) {
            dailyAvail.put(entry.getKey(), DailyAvailability.ofHoursOnly(entry.getKey(), entry.getValue()));
        }
        return scheduleWithAvailability(studyPlan, items, dailyAvail);
    }

    /**
     * Primary scheduling method accepting full {@link DailyAvailability} per day.
     */
    public List<StudyPlanItem> scheduleWithAvailability(StudyPlan studyPlan,
                                                        List<PlanningItem> planningItems,
                                                        Map<LocalDate, DailyAvailability> dailyAvailabilityMap) {
        List<StudyPlanItem> scheduledItems = new ArrayList<>();

        // Sort days chronologically
        List<LocalDate> days = dailyAvailabilityMap.keySet().stream().sorted().toList();

        // Sort items by scheduling priority (urgent / at-risk first, then deadline, then weight)
        List<PlanningItem> agenda = new ArrayList<>(planningItems);
        agenda.sort(schedulingComparator());

        // Mutable remaining allocations per item
        Map<String, Double> remainingAllocation = new LinkedHashMap<>();
        for (PlanningItem item : agenda) {
            remainingAllocation.put(item.itemKey(), item.getAllocatedHours());
        }

        for (LocalDate day : days) {
            DailyAvailability dayAvail = dailyAvailabilityMap.get(day);
            if (dayAvail == null) continue;

            double dayCapacity = dayAvail.effectiveSchedulableHours();
            if (dayCapacity <= 0) continue;

            if (dayAvail.hasTimeSlots()) {
                // Mode B: Hours + Time Slots
                scheduleDayWithTimeSlots(studyPlan, day, dayAvail, agenda, remainingAllocation, scheduledItems);
            } else {
                // Mode A: Hours Only (No time slots -> startTime and endTime remain null)
                scheduleDayHoursOnly(studyPlan, day, dayCapacity, agenda, remainingAllocation, scheduledItems);
            }
        }

        return scheduledItems;
    }

    /**
     * Mode A: Day-level duration allocation without exact time windows.
     * Table columns startTime and endTime remain null (blank in UI, no fake times).
     */
    private void scheduleDayHoursOnly(StudyPlan studyPlan,
                                      LocalDate day,
                                      double dayCapacity,
                                      List<PlanningItem> agenda,
                                      Map<String, Double> remainingAllocation,
                                      List<StudyPlanItem> items) {
        double dayRemaining = dayCapacity;
        boolean scheduledAny = true;

        while (dayRemaining >= MIN_SESSION_HOURS && scheduledAny) {
            scheduledAny = false;

            for (PlanningItem item : agenda) {
                if (dayRemaining < MIN_SESSION_HOURS) break;

                String key = item.itemKey();
                double stillNeed = remainingAllocation.getOrDefault(key, 0.0);
                if (stillNeed < MIN_SESSION_HOURS) continue;

                // Deadline check: do not schedule on a day after deadline
                LocalDateTime deadline = item.getDeadline();
                if (deadline != null && day.isAfter(deadline.toLocalDate())) {
                    continue;
                }

                double maxForSession = Math.min(stillNeed, Math.min(dayRemaining, MAX_SESSION_HOURS));
                double sessionHours = Math.round(maxForSession * 4.0) / 4.0;
                if (sessionHours > maxForSession + 0.001) {
                    sessionHours = Math.floor(maxForSession * 4.0) / 4.0;
                }
                if (sessionHours < MIN_SESSION_HOURS) continue;

                Assessment assessment = item.getAssessment();
                Task task = item.getTask();
                Module module = resolveModule(assessment, task);

                StudyPlanItem planItem = StudyPlanItem.builder()
                        .studyPlan(studyPlan)
                        .date(day)
                        .startTime(null)
                        .endTime(null)
                        .module(module)
                        .assessment(assessment)
                        .task(task)
                        .activityLabel(item.getActivityLabel())
                        .activityType(item.getActivityType())
                        .plannedHours(sessionHours)
                        .priorityScore(item.getWeight())
                        .status(ItemStatus.PLANNED)
                        .build();

                items.add(planItem);
                scheduledAny = true;

                dayRemaining -= sessionHours;
                remainingAllocation.merge(key, -sessionHours, Double::sum);
            }
        }
    }

    /**
     * Mode B: Schedules sessions strictly within declared time slots.
     * Populates exact startTime and endTime. Breaks and gaps stay within slots.
     */
    private void scheduleDayWithTimeSlots(StudyPlan studyPlan,
                                          LocalDate day,
                                          DailyAvailability dayAvail,
                                          List<PlanningItem> agenda,
                                          Map<String, Double> remainingAllocation,
                                          List<StudyPlanItem> items) {
        double dayRemaining = dayAvail.effectiveSchedulableHours();
        List<TimeSlot> sortedSlots = new ArrayList<>(dayAvail.timeSlots());
        sortedSlots.sort(Comparator.comparing(TimeSlot::startTime));

        for (TimeSlot slot : sortedSlots) {
            if (dayRemaining < MIN_SESSION_HOURS) break;

            LocalTime cursor = slot.startTime();
            LocalTime slotEnd = slot.endTime();

            boolean scheduledInSlot = true;
            while (dayRemaining >= MIN_SESSION_HOURS && scheduledInSlot && cursor.isBefore(slotEnd)) {
                scheduledInSlot = false;

                long minutesLeftInSlot = Duration.between(cursor, slotEnd).toMinutes();
                double slotHoursLeft = minutesLeftInSlot / 60.0;
                if (slotHoursLeft < MIN_SESSION_HOURS) break;

                for (PlanningItem item : agenda) {
                    if (dayRemaining < MIN_SESSION_HOURS) break;

                    String key = item.itemKey();
                    double stillNeed = remainingAllocation.getOrDefault(key, 0.0);
                    if (stillNeed < MIN_SESSION_HOURS) continue;

                    LocalDateTime deadline = item.getDeadline();
                    if (deadline != null) {
                        if (day.isAfter(deadline.toLocalDate())) continue;
                        if (day.isEqual(deadline.toLocalDate())) {
                            if (!cursor.isBefore(deadline.toLocalTime())) continue;
                        }
                    }

                    double maxForSession = Math.min(stillNeed, Math.min(dayRemaining, Math.min(slotHoursLeft, MAX_SESSION_HOURS)));

                    if (deadline != null && day.isEqual(deadline.toLocalDate())) {
                        double minutesUntilDeadline = (double) cursor.until(deadline.toLocalTime(), ChronoUnit.MINUTES);
                        double hoursUntilDeadline = minutesUntilDeadline / 60.0;
                        maxForSession = Math.min(maxForSession, hoursUntilDeadline);
                    }

                    double sessionHours = Math.round(maxForSession * 4.0) / 4.0;
                    if (sessionHours > maxForSession + 0.001) {
                        sessionHours = Math.floor(maxForSession * 4.0) / 4.0;
                    }
                    if (sessionHours < MIN_SESSION_HOURS) continue;

                    LocalTime start = cursor;
                    LocalTime end = cursor.plusMinutes(Math.round(sessionHours * 60));

                    if (end.isAfter(slotEnd)) continue;
                    if (deadline != null && day.isEqual(deadline.toLocalDate())) {
                        if (end.isAfter(deadline.toLocalTime())) continue;
                    }

                    Assessment assessment = item.getAssessment();
                    Task task = item.getTask();
                    Module module = resolveModule(assessment, task);

                    StudyPlanItem planItem = StudyPlanItem.builder()
                            .studyPlan(studyPlan)
                            .date(day)
                            .startTime(start)
                            .endTime(end)
                            .module(module)
                            .assessment(assessment)
                            .task(task)
                            .activityLabel(item.getActivityLabel())
                            .activityType(item.getActivityType())
                            .plannedHours(sessionHours)
                            .priorityScore(item.getWeight())
                            .status(ItemStatus.PLANNED)
                            .build();

                    items.add(planItem);
                    scheduledInSlot = true;

                    // Advance cursor with break or gap
                    cursor = end;
                    int breakOrGap = (sessionHours >= BREAK_THRESHOLD_HOURS) ? BREAK_MINUTES : GAP_MINUTES;
                    cursor = cursor.plusMinutes(breakOrGap);

                    dayRemaining -= sessionHours;
                    remainingAllocation.merge(key, -sessionHours, Double::sum);
                    break;
                }
            }
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private Module resolveModule(Assessment assessment, Task task) {
        if (assessment != null) return assessment.getModule();
        if (task != null) return task.getModule();
        return null;
    }

    /**
     * Scheduling-priority comparator:
     * IMPOSSIBLE / AT_RISK first → earliest deadline → highest weight.
     */
    private Comparator<PlanningItem> schedulingComparator() {
        return Comparator
                .comparingInt((PlanningItem c) -> feasibilityOrder(c.getFeasibilityStatus()))
                .thenComparing(c -> c.getDeadline() == null
                        ? LocalDateTime.MAX
                        : c.getDeadline())
                .thenComparingDouble(c -> -c.getWeight());
    }

    private int feasibilityOrder(PlanningItem.FeasibilityStatus status) {
        return switch (status) {
            case IMPOSSIBLE_WITH_CURRENT_AVAILABILITY -> 0;
            case AT_RISK                              -> 1;
            case FEASIBLE                             -> 2;
        };
    }
}
