package com.academicplanner.planning.mapper;

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
 * {@code PlanningEngine} used (assessments, tasks, per-day availability).
 *
 * <p>
 * <b>Phase 2 change:</b> activities are now built with
 * {@code activityType}/{@code credits}/{@code remainingHours}/{@code priority}
 * instead of the Phase 1 {@code type}/{@code moduleCredits}/
 * {@code remainingWorkUnits}/{@code importance} shape.
 * {@link WorkloadEstimator}
 * now returns hours directly (no productivity factor); {@link PriorityMapper}
 * replaces {@code ImportanceMapper} and returns a numeric 1-5 priority.
 */
@Component
public class PlanningRequestMapper {

    private final WorkloadEstimator workloadEstimator;
    private final PriorityMapper priorityMapper;

    public PlanningRequestMapper(WorkloadEstimator workloadEstimator, PriorityMapper priorityMapper) {
        this.workloadEstimator = workloadEstimator;
        this.priorityMapper = priorityMapper;
    }

    public PlanningRequestDto toRequest(LocalDate startDate,
            LocalDate endDate,
            List<Assessment> assessments,
            List<Task> tasks,
            Map<LocalDate, DailyAvailability> dailyAvailability) {
        return new PlanningRequestDto(
                new PlanningPeriodDto(startDate, endDate),
                toAvailability(dailyAvailability),
                toActivities(assessments, tasks));
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
                if (!isSchedulable(a))
                    continue;
                String moduleCode = a.getModule() != null ? a.getModule().getCode() : null;
                Double credits = a.getModule() != null ? (double) a.getModule().getCredits() : null;
                activities.add(new ActivityDto(
                        "A-" + a.getId(),
                        a.getTitle(),
                        a.getType() != null ? a.getType().name() : "OTHER",
                        moduleCode,
                        credits,
                        a.getDueDateTime(),
                        workloadEstimator.remainingHoursFor(a),
                        priorityMapper.priorityFor(a.getType())));
            }
        }

        if (tasks != null) {
            for (Task t : tasks) {
                if (!isSchedulable(t))
                    continue;
                String moduleCode = t.getModule() != null ? t.getModule().getCode() : null;
                Double credits = t.getModule() != null ? (double) t.getModule().getCredits() : null;
                activities.add(new ActivityDto(
                        "T-" + t.getId(),
                        t.getTitle(),
                        "TASK",
                        moduleCode,
                        credits,
                        t.getDueDateTime(),
                        workloadEstimator.remainingHoursFor(t),
                        priorityMapper.priorityFor(t.getPriority())));
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
