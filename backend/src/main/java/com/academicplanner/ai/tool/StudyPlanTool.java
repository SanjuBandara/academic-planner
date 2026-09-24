package com.academicplanner.ai.tool;

import com.academicplanner.dto.studyplan.StudyPlanItemResponse;
import com.academicplanner.dto.studyplan.StudyPlanResponse;
import com.academicplanner.dto.studyplan.TodaysScheduleResponse;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.User;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.repository.StudyPlanItemRepository;
import com.academicplanner.repository.StudyPlanRepository;
import com.academicplanner.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Controlled tool for retrieving study plan data for the authenticated student.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyPlanTool {

    private final StudyPlanService studyPlanService;
    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;

    @Transactional(readOnly = true)
    public TodaysScheduleResponse getTodayPlan(User user) {
        log.info("[StudyPlanTool] Retrieving today's plan for user: {}", user.getId());
        return studyPlanService.getTodaysSchedule(user);
    }

    @Transactional(readOnly = true)
    public Optional<StudyPlanResponse> getWeekPlan(User user) {
        log.info("[StudyPlanTool] Retrieving active weekly plan for user: {}", user.getId());
        try {
            return Optional.of(studyPlanService.getActivePlan(user));
        } catch (ResourceNotFoundException e) {
            log.debug("[StudyPlanTool] No active plan found for user: {}", user.getId());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<StudyPlanItemResponse> getPlanForDate(LocalDate date, User user) {
        log.info("[StudyPlanTool] Retrieving plan for date {} and user: {}", date, user.getId());
        Optional<StudyPlan> activePlanOpt = studyPlanRepository.findByUserAndTypeAndStatus(
                user, StudyPlan.PlanType.WEEKLY, StudyPlan.PlanStatus.ACTIVE);

        if (activePlanOpt.isEmpty()) {
            return List.of();
        }

        return studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(activePlanOpt.get().getId(), date)
                .stream()
                .map(StudyPlanItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StudyPlanItemResponse> getActivityAllocation(Long activityId, User user) {
        log.info("[StudyPlanTool] Checking activity allocation for itemId: {}, user: {}", activityId, user.getId());
        return studyPlanItemRepository.findByIdAndStudyPlan_User_Id(activityId, user.getId())
                .map(StudyPlanItemResponse::from);
    }
}
