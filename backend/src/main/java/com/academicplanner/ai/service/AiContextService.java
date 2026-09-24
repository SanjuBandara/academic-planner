package com.academicplanner.ai.service;

import com.academicplanner.ai.model.AiContext;
import com.academicplanner.dto.assessment.AssessmentResponse;
import com.academicplanner.dto.studyplan.StudyPlanItemResponse;
import com.academicplanner.dto.studyplan.StudyPlanResponse;
import com.academicplanner.dto.studyplan.TodaysScheduleResponse;
import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for gathering focused, relevant contextual data for the authenticated student.
 * Never sends irrelevant or foreign student records.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiContextService {

    private final AiToolService aiToolService;

    public AiContext buildContext(User user) {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        AiContext.AiContextBuilder builder = AiContext.builder()
                .currentDate(today)
                .currentTime(now)
                .studentEmail(user.getEmail());

        // 1. Today's Plan
        try {
            TodaysScheduleResponse todaySchedule = aiToolService.getTodayPlan(user);
            if (todaySchedule != null && todaySchedule.sessions() != null) {
                List<AiContext.SessionSummary> todaySessions = todaySchedule.sessions().stream()
                        .map(s -> AiContext.SessionSummary.builder()
                                .id(s.id())
                                .date(today.toString())
                                .startTime(s.startTime())
                                .endTime(s.endTime())
                                .moduleCode(s.moduleCode())
                                .moduleName(s.moduleName())
                                .activityLabel(s.title())
                                .activityType(s.activityType())
                                .plannedHours(s.durationMinutes() / 60.0)
                                .status(s.status())
                                .build())
                        .toList();
                builder.todayPlan(todaySessions);
            }
        } catch (Exception e) {
            log.warn("[AiContextService] Could not fetch today's plan: {}", e.getMessage());
        }

        // 2. Active Weekly Plan
        try {
            Optional<StudyPlanResponse> weekPlanOpt = aiToolService.getWeekPlan(user);
            if (weekPlanOpt.isPresent()) {
                StudyPlanResponse weekPlan = weekPlanOpt.get();
                builder.activePlanId(weekPlan.id());
                builder.planStatus(weekPlan.status() != null ? weekPlan.status().name() : "ACTIVE");

                if (weekPlan.items() != null) {
                    List<AiContext.SessionSummary> weekSessions = weekPlan.items().stream()
                            .map(i -> AiContext.SessionSummary.builder()
                                    .id(i.id())
                                    .date(i.date() != null ? i.date().toString() : null)
                                    .startTime(i.startTime() != null ? i.startTime().toString() : null)
                                    .endTime(i.endTime() != null ? i.endTime().toString() : null)
                                    .moduleCode(i.moduleCode())
                                    .moduleName(i.moduleName())
                                    .activityLabel(i.activityLabel() != null ? i.activityLabel() :
                                            (i.taskTitle() != null ? i.taskTitle() : i.assessmentTitle()))
                                    .activityType(i.activityType())
                                    .plannedHours(i.plannedHours())
                                    .status(i.status() != null ? i.status().name() : "PLANNED")
                                    .build())
                            .toList();
                    builder.weekPlan(weekSessions);
                }
            }
        } catch (Exception e) {
            log.warn("[AiContextService] Could not fetch weekly plan: {}", e.getMessage());
        }

        // 3. Upcoming Assessments
        try {
            List<AssessmentResponse> assessments = aiToolService.getUpcomingAssessments(user);
            if (assessments != null) {
                List<AiContext.AssessmentSummary> assessmentSummaries = assessments.stream()
                        .map(a -> AiContext.AssessmentSummary.builder()
                                .id(a.id())
                                .title(a.title())
                                .moduleCode(a.moduleCode())
                                .moduleName(a.moduleName())
                                .dueDateTime(a.dueDateTime() != null ? a.dueDateTime().toString() : null)
                                .type(a.type() != null ? a.type().name() : null)
                                .weight(a.weight())
                                .status(a.status() != null ? a.status().name() : null)
                                .build())
                        .toList();
                builder.upcomingAssessments(assessmentSummaries);
            }
        } catch (Exception e) {
            log.warn("[AiContextService] Could not fetch upcoming assessments: {}", e.getMessage());
        }

        // 4. Pending Tasks
        try {
            List<TaskResponse> tasks = aiToolService.getTasks(user);
            if (tasks != null) {
                List<AiContext.TaskSummary> taskSummaries = tasks.stream()
                        .filter(t -> !"COMPLETED".equalsIgnoreCase(String.valueOf(t.status())))
                        .map(t -> AiContext.TaskSummary.builder()
                                .id(t.id())
                                .title(t.title())
                                .moduleCode(t.moduleCode())
                                .dueDateTime(t.dueDateTime() != null ? t.dueDateTime().toString() : null)
                                .estimatedHours(t.estimatedHours())
                                .remainingHours(t.remainingHours())
                                .priority(t.priority() != null ? t.priority().name() : null)
                                .status(t.status() != null ? t.status().name() : null)
                                .build())
                        .toList();
                builder.tasks(taskSummaries);
            }
        } catch (Exception e) {
            log.warn("[AiContextService] Could not fetch tasks: {}", e.getMessage());
        }

        // 5. Weekly Availability
        try {
            List<StudyAvailability> availabilities = aiToolService.getWeeklyAvailability(user);
            if (availabilities != null) {
                List<AiContext.AvailabilitySummary> availSummaries = availabilities.stream()
                        .map(a -> AiContext.AvailabilitySummary.builder()
                                .dayOfWeek(a.getDayOfWeek().name())
                                .availableHours(a.getAvailableHours())
                                .build())
                        .toList();
                builder.availability(availSummaries);
            }
        } catch (Exception e) {
            log.warn("[AiContextService] Could not fetch availability: {}", e.getMessage());
        }

        return builder.build();
    }
}
