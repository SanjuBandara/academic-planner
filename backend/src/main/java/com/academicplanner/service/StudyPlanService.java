package com.academicplanner.service;

import com.academicplanner.dto.studyplan.*;
import com.academicplanner.entity.*;
import com.academicplanner.entity.StudyPlan.PlanStatus;
import com.academicplanner.entity.StudyPlan.PlanType;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.planning.PlanningEngine;
import com.academicplanner.repository.AssessmentRepository;
import com.academicplanner.repository.StudyPlanItemRepository;
import com.academicplanner.repository.StudyPlanRepository;
import com.academicplanner.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;
    private final AssessmentRepository assessmentRepository;
    private final TaskRepository taskRepository;
    private final PlanningEngine planningEngine;

    @Transactional
    public StudyPlanResponse generateWeeklyPlan(WeeklyPlanRequest request, User user) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = startDate.plusDays(6);

        // Cancel previous active weekly plan if any
        studyPlanRepository.findByUserAndTypeAndStatus(user, PlanType.WEEKLY, PlanStatus.ACTIVE)
                .ifPresent(p -> p.setStatus(PlanStatus.CANCELLED));

        double totalAvailable = request.availability().values().stream().mapToDouble(Double::doubleValue).sum();

        StudyPlan studyPlan = StudyPlan.builder()
                .user(user)
                .type(PlanType.WEEKLY)
                .status(PlanStatus.ACTIVE)
                .startDate(startDate)
                .endDate(endDate)
                .totalAvailableHours(totalAvailable)
                .build();

        studyPlan = studyPlanRepository.save(studyPlan);

        // Map daily hours from availability input
        Map<LocalDate, Double> dailyHours = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            String dayName = date.getDayOfWeek().name();
            Double hours = request.availability().getOrDefault(dayName, 4.0); // default 4 hours if omitted
            dailyHours.put(date, hours);
        }

        List<Assessment> assessments = assessmentRepository.findActivePlanningAssessments(user.getId());
        List<Task> tasks = taskRepository.findActivePlanningTasks(user.getId());

        List<StudyPlanItem> items = planningEngine.generateItems(studyPlan, assessments, tasks, dailyHours);
        items = studyPlanItemRepository.saveAll(items);

        double totalPlanned = items.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        studyPlan.setTotalPlannedHours(totalPlanned);
        studyPlan.setItems(items);
        studyPlanRepository.save(studyPlan);

        return StudyPlanResponse.from(studyPlan);
    }

    @Transactional(readOnly = true)
    public List<StudyPlanItemResponse> getTodaysItems(User user) {
        return studyPlanItemRepository.findTodaysItems(user.getId(), LocalDate.now())
                .stream()
                .map(StudyPlanItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudyPlanResponse getActivePlan(User user) {
        StudyPlan plan = studyPlanRepository.findByUserAndTypeAndStatus(user, PlanType.WEEKLY, PlanStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active study plan found"));
        List<StudyPlanItem> items = studyPlanItemRepository.findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(plan.getId());
        plan.setItems(items);
        return StudyPlanResponse.from(plan);
    }

    @Transactional
    public StudyPlanItemResponse updateItemStatus(Long itemId, ItemStatus status, User user) {
        StudyPlanItem item = studyPlanItemRepository.findByIdAndStudyPlan_User_Id(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Study plan item not found: " + itemId));

        item.setStatus(status);
        if (status == ItemStatus.COMPLETED) {
            item.setActualHours(item.getPlannedHours());
        }

        return StudyPlanItemResponse.from(studyPlanItemRepository.save(item));
    }
}
