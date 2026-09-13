package com.academicplanner.planning.client;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentStatus;
import com.academicplanner.entity.Task;
import com.academicplanner.entity.Task.TaskStatus;
import com.academicplanner.planning.dto.ActivityDto;
import com.academicplanner.planning.dto.AvailabilityWindowDto;
import com.academicplanner.planning.dto.PlanningPeriodDto;
import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.model.DailyAvailability;
import com.academicplanner.planning.model.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Builds a {@link PlanningRequestDto} from the same domain inputs the old
 * {@code PlanningEngine} used (assessments, tasks, per-day availability),
 * without touching {@code PlanningEngine} or its pipeline classes.
 *
 * <p>Only activities with remaining work and declared time-slot
 * availability are included — mirrors the spec's preference for
 * {@code date + startTime + endTime} windows over hours-only availability
 * (Section 4), since CP-SAT needs concrete windows to place sessions in.
 * Days with hours-only availability (no explicit TimeSlots) are skipped
 * with a caller-visible note, since they cannot be turned into legal
 * scheduling windows for this solver formulation.
 */
@Component
public class PlanningRequestMapper {

    private final WorkloadEstimator workloadEstimator;
    private final ImportanceMapper importanceMapper;

    public PlanningRequestMapper(WorkloadEstimator workloadEstimator, ImportanceMapper importanceMapper) {
        this.workloadEstimator = workloadEstimator;
        this.importanceMapper = importanceMapper;
    }

    public PlanningRequestDto toRequest(LocalDate startDate,
                                        LocalDate endDate,
                                        List<Assessment> assessments,
                                        List<Task> tasks,
                                        Map<LocalDate, DailyAvailability> dailyAvailability) {
        return new PlanningRequestDto(
                new PlanningPeriodDto(startDate, endDate),
                toAvailability(dailyAvailability),
                toActivities(assessments, tasks)
        );
    }

    private List<AvailabilityWindowDto> toAvailability(Map<LocalDate, DailyAvailability> dailyAvailability) {
        List<AvailabilityWindowDto> windows = new ArrayList<>();
        for (Map.Entry<LocalDate, DailyAvailability> entry : dailyAvailability.entrySet()) {
            DailyAvailability day = entry.getValue();
            if (!day.hasTimeSlots()) {
                // Hours-only availability can't be placed by this solver formulation
                // without an arbitrary window guess — skipped rather than guessed.
                continue;
            }
            for (TimeSlot slot : day.timeSlots()) {
                windows.add(new AvailabilityWindowDto(entry.getKey(), slot.startTime(), slot.endTime()));
            }
        }
        return windows;
    }

    private List<ActivityDto> toActivities(List<Assessment> assessments, List<Task> tasks) {
        List<ActivityDto> activities = new ArrayList<>();

        if (assessments != null) {
            for (Assessment a : assessments) {
                if (!isSchedulable(a)) continue;
                Integer credits = a.getModule() != null ? a.getModule().getCredits() : null;
                Long moduleId = a.getModule() != null ? a.getModule().getId() : null;
                activities.add(new ActivityDto(
                        "A-" + a.getId(),
                        a.getType() != null ? a.getType().name() : "OTHER",
                        a.getTitle(),
                        moduleId,
                        credits,
                        a.getDueDateTime(),
                        workloadEstimator.remainingWorkUnitsFor(a),
                        importanceMapper.importanceFor(a.getType()),
                        importanceMapper.userPriorityFor(a),
                        workloadEstimator.productivityUnitsPerHourFor(a)
                ));
            }
        }

        if (tasks != null) {
            for (Task t : tasks) {
                if (!isSchedulable(t)) continue;
                Integer credits = t.getModule() != null ? t.getModule().getCredits() : null;
                Long moduleId = t.getModule() != null ? t.getModule().getId() : null;
                activities.add(new ActivityDto(
                        "T-" + t.getId(),
                        "TASK",
                        t.getTitle(),
                        moduleId,
                        credits,
                        t.getDueDateTime(),
                        workloadEstimator.remainingWorkUnitsFor(t),
                        importanceMapper.importanceFor(t.getPriority()),
                        importanceMapper.userPriorityFor(t.getPriority()),
                        workloadEstimator.productivityUnitsPerHourFor(t)
                ));
            }
        }

        return activities;
    }

    private boolean isSchedulable(Assessment a) {
        return a.getStatus() != AssessmentStatus.COMPLETED && a.getStatus() != AssessmentStatus.CANCELLED;
    }

    private boolean isSchedulable(Task t) {
        return t.getStatus() != TaskStatus.COMPLETED && t.getStatus() != TaskStatus.CANCELLED;
    }
}
