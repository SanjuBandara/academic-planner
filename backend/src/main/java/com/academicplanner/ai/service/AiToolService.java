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

    public Optional<StudyAvailability> getTodayAvailability(User user) {
        return availabilityTool.getTodayAvailability(user);
    }

    public List<StudyAvailability> getWeeklyAvailability(User user) {
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
}
