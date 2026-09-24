package com.academicplanner.ai.service;

import com.academicplanner.ai.dto.PlanModificationRequest;
import com.academicplanner.ai.dto.PlanModificationResult;
import com.academicplanner.ai.tool.*;
import com.academicplanner.dto.assessment.AssessmentResponse;
import com.academicplanner.dto.studyplan.StudyPlanItemResponse;
import com.academicplanner.dto.studyplan.StudyPlanResponse;
import com.academicplanner.dto.studyplan.TodaysScheduleResponse;
import com.academicplanner.dto.task.TaskResponse;
import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service acting as the single gatekeeper and dispatcher for all AI-callable tools.
 * Ensures tools can only execute within the security boundary of the authenticated student.
 */
@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class AiToolService {

    private final StudyPlanTool studyPlanTool;
    private final AssessmentTool assessmentTool;
    private final TaskTool taskTool;
    private final AvailabilityTool availabilityTool;
    private final PlanningTool planningTool;
    private final MarkCompletedTool markCompletedTool;

    // ── Phase 4 ──────────────────────────────────────────────────────────────
    private final QuickAddTool quickAddTool;
    private final SkipTodayTool skipTodayTool;
    private final BurnoutDetectorTool burnoutDetectorTool;

    public TodaysScheduleResponse getTodayPlan(User user) {
        return studyPlanTool.getTodayPlan(user);
    }

    public Optional<StudyPlanResponse> getWeekPlan(User user) {
        return studyPlanTool.getWeekPlan(user);
    }

    public List<StudyPlanItemResponse> getPlanForDate(LocalDate date, User user) {
        return studyPlanTool.getPlanForDate(date, user);
    }

    public List<AssessmentResponse> getUpcomingAssessments(User user) {
        return assessmentTool.getUpcomingAssessments(user);
    }

    public List<TaskResponse> getTasks(User user) {
        return taskTool.getTasks(user);
    }

    public List<TaskResponse> getPendingTasks(User user) {
        return taskTool.getTasks(user).stream()
                .filter(t -> !"COMPLETED".equals(t.status() != null ? t.status().name() : ""))
                .toList();
    }

    public Optional<StudyAvailability> getTodayAvailability(User user) {
        return availabilityTool.getTodayAvailability(user);
    }

    public List<StudyAvailability> getWeeklyAvailability(User user) {
        return availabilityTool.getWeeklyAvailability(user);
    }

    public List<StudyAvailability> getAvailability(User user) {
        return availabilityTool.getWeeklyAvailability(user);
    }

    public PlanModificationResult requestPlanModification(PlanModificationRequest request, User user) {
        return planningTool.requestPlanModification(request, user);
    }

    public boolean applyProposal(String proposalId, User user) {
        return planningTool.applyProposal(proposalId, user);
    }

    public void cancelProposal(String proposalId, User user) {
        planningTool.cancelProposal(proposalId, user);
    }

    // ── Phase 2: Mark Completed ──────────────────────────────────────────────

    public boolean markItemCompleted(Long itemId, User user) {
        return markCompletedTool.markItemCompleted(itemId, user);
    }

    public String markByTitleFragment(String titleFragment, User user) {
        return markCompletedTool.markByTitleFragment(titleFragment, user);
    }

    // ── Phase 4: Quick-Add Task ───────────────────────────────────────────────

    public com.academicplanner.dto.task.TaskResponse quickAddTask(String message, User user) {
        return quickAddTool.quickAddTask(message, user);
    }

    // ── Phase 4: Skip Today ───────────────────────────────────────────────────

    public int skipAllTodaySessions(User user) {
        return skipTodayTool.skipAllTodaySessions(user);
    }

    // ── Phase 4: Burnout Detection ────────────────────────────────────────────

    public BurnoutDetectorTool.BurnoutReport analyseBurnout(User user) {
        return burnoutDetectorTool.analyse(user);
    }
}

