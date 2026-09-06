package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Module;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Core deterministic planning engine.
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 *   <li>Collect active assessments (not COMPLETED/CANCELLED) with their modules.</li>
 *   <li>Compute per-assessment raw priority scores using {@link PriorityCalculator}.</li>
 *   <li>Distribute total available hours using {@link WorkloadAllocator}.</li>
 *   <li>For each calendar day in the plan period, schedule time-blocked sessions
 *       from the highest-priority assessment first, respecting daily capacity,
 *       inserting 30-minute breaks between sessions ≥ 2 hours.</li>
 *   <li>Never create overlapping sessions for the same student.</li>
 *   <li>Never schedule work after an assessment's deadline.</li>
 *   <li>Completed / cancelled assessments and tasks are excluded.</li>
 * </ol>
 *
 * <h2>Session timing</h2>
 * Sessions start at {@code DEFAULT_START_TIME} and are placed sequentially.
 * A 30-minute break is inserted after any session lasting ≥ 2 hours.
 * Sessions end when the day's capacity is exhausted.
 */
@Component
@RequiredArgsConstructor
public class PlanningEngine {

    /** Default daily start time for scheduled sessions. */
    public static final LocalTime DEFAULT_START_TIME = LocalTime.of(9, 0);
    /** Minimum session length (hours) before a break is inserted. */
    public static final double BREAK_THRESHOLD_HOURS = 2.0;
    /** Break duration in minutes inserted after long sessions. */
    public static final int BREAK_MINUTES = 30;
    /** Maximum single-session length in hours (sessions are split if allocation is larger). */
    public static final double MAX_SESSION_HOURS = 3.0;

    private final PriorityCalculator priorityCalculator;
    private final WorkloadAllocator workloadAllocator;

    /**
     * Generates a list of {@link StudyPlanItem}s for the given date range.
     *
     * @param studyPlan   the parent plan (id and metadata already saved)
     * @param assessments active, non-cancelled assessments belonging to the student
     * @param tasks       active tasks (status != COMPLETED/CANCELLED, remainingHours > 0)
     * @param dailyHours  map of LocalDate → available hours for that day
     * @return ordered list of plan items ready to persist
     */
    public List<StudyPlanItem> generateItems(StudyPlan studyPlan,
                                             List<Assessment> assessments,
                                             List<Task> tasks,
                                             Map<LocalDate, Double> dailyHours) {

        // --- Step 1: Build assessment → module → remaining hours map ---
        // For each assessment, compute total remaining hours from linked tasks.
        // If no tasks exist for an assessment, fall back to estimatedHours.
        Map<Long, Double> remainingHoursMap = buildRemainingHoursMap(assessments, tasks);

        // --- Step 2: Compute credit weights ---
        int totalCredits = assessments.stream()
                .map(Assessment::getModule)
                .distinct()
                .mapToInt(Module::getCredits)
                .sum();
        if (totalCredits == 0) totalCredits = 1; // avoid divide-by-zero

        // --- Step 3: Compute raw scores ---
        Map<Long, Double> rawScores = new LinkedHashMap<>();
        for (Assessment a : assessments) {
            double remaining = remainingHoursMap.getOrDefault(a.getId(), 0.0);
            if (remaining <= 0) continue; // nothing to schedule

            double creditWeight = (double) a.getModule().getCredits() / totalCredits;
            double urgency = priorityCalculator.urgencyMultiplier(a);
            double priority = priorityCalculator.priorityMultiplier(a.getPriority());
            // Normalize remaining hours factor relative to the max remaining
            double maxRemaining = remainingHoursMap.values().stream()
                    .mapToDouble(Double::doubleValue).max().orElse(1.0);
            double remainingFactor = maxRemaining > 0 ? remaining / maxRemaining : 0.0;

            double score = priorityCalculator.rawScore(creditWeight, urgency, priority, remainingFactor);
            rawScores.put(a.getId(), score);
        }

        if (rawScores.isEmpty()) {
            return List.of();
        }

        // --- Step 4: Distribute hours ---
        double totalAvailable = dailyHours.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<Long, Double> allocation = workloadAllocator.allocate(rawScores, remainingHoursMap, totalAvailable);

        // --- Step 5: Build a sorted priority queue of (assessment, allocatedHours) ---
        // Sort by raw score descending (highest priority scheduled first each day)
        List<AllocationEntry> agenda = new ArrayList<>();
        for (Assessment a : assessments) {
            double allocated = allocation.getOrDefault(a.getId(), 0.0);
            double score = rawScores.getOrDefault(a.getId(), 0.0);
            if (allocated > 0) {
                agenda.add(new AllocationEntry(a, allocated, score));
            }
        }
        agenda.sort(Comparator.comparingDouble(AllocationEntry::score).reversed());

        // --- Step 6: Build assessment → linked tasks map (for item linking) ---
        Map<Long, Task> assessmentTaskMap = new LinkedHashMap<>();
        for (Task t : tasks) {
            if (t.getAssessment() != null) {
                assessmentTaskMap.putIfAbsent(t.getAssessment().getId(), t);
            }
        }

        // --- Step 7: Schedule items day by day ---
        List<StudyPlanItem> items = new ArrayList<>();
        List<LocalDate> sortedDays = new ArrayList<>(dailyHours.keySet());
        Collections.sort(sortedDays);

        // Mutable remaining allocations to track cross-day spending
        Map<Long, Double> remainingAllocation = new LinkedHashMap<>();
        for (AllocationEntry entry : agenda) {
            remainingAllocation.put(entry.assessment().getId(), entry.hours());
        }

        for (LocalDate day : sortedDays) {
            double dayCapacity = dailyHours.getOrDefault(day, 0.0);
            if (dayCapacity <= 0) continue;

            double dayRemaining = dayCapacity;
            LocalTime cursor = DEFAULT_START_TIME;

            for (AllocationEntry entry : agenda) {
                if (dayRemaining <= 0) break;

                Assessment assessment = entry.assessment();
                double stillNeed = remainingAllocation.getOrDefault(assessment.getId(), 0.0);
                if (stillNeed <= 0) continue;

                // Do not schedule after deadline
                if (assessment.getDueDateTime() != null
                        && day.isAfter(assessment.getDueDateTime().toLocalDate())) {
                    continue;
                }

                // Session is the min of: what's needed, day capacity, max session length
                double sessionHours = Math.min(stillNeed, Math.min(dayRemaining, MAX_SESSION_HOURS));
                sessionHours = Math.round(sessionHours * 4.0) / 4.0; // round to 15-min slots
                if (sessionHours < 0.25) continue;

                LocalTime start = cursor;
                LocalTime end = cursor.plusMinutes((long) (sessionHours * 60));

                StudyPlanItem item = StudyPlanItem.builder()
                        .studyPlan(studyPlan)
                        .date(day)
                        .startTime(start)
                        .endTime(end)
                        .module(assessment.getModule())
                        .assessment(assessment)
                        .task(assessmentTaskMap.get(assessment.getId()))
                        .plannedHours(sessionHours)
                        .priorityScore(entry.score())
                        .status(StudyPlanItem.ItemStatus.PLANNED)
                        .build();

                items.add(item);

                // Advance cursor; add break after long sessions
                cursor = end;
                if (sessionHours >= BREAK_THRESHOLD_HOURS) {
                    cursor = cursor.plusMinutes(BREAK_MINUTES);
                } else {
                    cursor = cursor.plusMinutes(15); // short gap between sessions
                }

                dayRemaining -= sessionHours;
                remainingAllocation.merge(assessment.getId(), -sessionHours, Double::sum);
            }
        }

        return items;
    }

    // ---- helpers ----

    /**
     * Builds a map of assessmentId → remaining hours.
     * Uses the sum of task.remainingHours if tasks exist for the assessment,
     * otherwise falls back to assessment.estimatedHours.
     */
    private Map<Long, Double> buildRemainingHoursMap(List<Assessment> assessments,
                                                     List<Task> tasks) {
        // Group tasks by assessmentId
        Map<Long, List<Task>> tasksByAssessment = new LinkedHashMap<>();
        for (Task t : tasks) {
            if (t.getAssessment() != null) {
                tasksByAssessment
                        .computeIfAbsent(t.getAssessment().getId(), k -> new ArrayList<>())
                        .add(t);
            }
        }

        Map<Long, Double> result = new LinkedHashMap<>();
        for (Assessment a : assessments) {
            List<Task> linkedTasks = tasksByAssessment.get(a.getId());
            double remaining;
            if (linkedTasks != null && !linkedTasks.isEmpty()) {
                remaining = linkedTasks.stream()
                        .mapToDouble(t -> t.getRemainingHours() != null ? t.getRemainingHours() : 0.0)
                        .sum();
            } else {
                remaining = a.getEstimatedHours() != null ? a.getEstimatedHours() : 0.0;
            }
            result.put(a.getId(), remaining);
        }
        return result;
    }

    /** Internal value holder used during scheduling. */
    private record AllocationEntry(Assessment assessment, double hours, double score) {}
}
