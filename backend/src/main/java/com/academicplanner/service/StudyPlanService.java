package com.academicplanner.service;

import com.academicplanner.dto.studyplan.*;
import com.academicplanner.entity.*;
import com.academicplanner.entity.StudyPlan.PlanStatus;
import com.academicplanner.entity.StudyPlan.PlanType;
import com.academicplanner.entity.StudyPlanItem.ItemStatus;
import com.academicplanner.exception.ResourceNotFoundException;
import com.academicplanner.planning.result.CpSatPlanningResult;
import com.academicplanner.planning.service.CpSatPlanningService;
import com.academicplanner.repository.AssessmentRepository;
import com.academicplanner.repository.StudyPlanItemRepository;
import com.academicplanner.repository.StudyPlanRepository;
import com.academicplanner.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;
    private final AssessmentRepository assessmentRepository;
    private final TaskRepository taskRepository;
    private final CpSatPlanningService cpSatPlanningService;

    @Transactional
    public StudyPlanResponse generateWeeklyPlan(WeeklyPlanRequest request, User user) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = startDate.plusDays(6);

        // Cancel previous active weekly plan if any
        studyPlanRepository.findByUserAndTypeAndStatus(user, PlanType.WEEKLY, PlanStatus.ACTIVE)
                .ifPresent(p -> p.setStatus(PlanStatus.CANCELLED));

        // Build DailyAvailability map per day
        Map<LocalDate, com.academicplanner.planning.model.DailyAvailability> dailyAvailMap = new java.util.LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            String dayName = date.getDayOfWeek().name();

            Double hours = 4.0; // default fallback
            List<com.academicplanner.planning.model.TimeSlot> timeSlots = new java.util.ArrayList<>();

            if (request.dailyAvailability() != null && request.dailyAvailability().containsKey(dayName)) {
                DailyAvailabilityRequest dayReq = request.dailyAvailability().get(dayName);
                if (dayReq.availableHours() != null) {
                    hours = Math.max(0.0, dayReq.availableHours());
                } else if (request.availability() != null && request.availability().containsKey(dayName)) {
                    hours = Math.max(0.0, request.availability().get(dayName));
                }

                if (dayReq.timeSlots() != null && !dayReq.timeSlots().isEmpty()) {
                    timeSlots = validateAndMapTimeSlots(dayName, dayReq.timeSlots());
                }
            } else if (request.availability() != null && request.availability().containsKey(dayName)) {
                hours = Math.max(0.0, request.availability().get(dayName));
            }

            dailyAvailMap.put(date, new com.academicplanner.planning.model.DailyAvailability(date, hours, timeSlots));
        }

        double totalAvailable = dailyAvailMap.values().stream()
                .mapToDouble(com.academicplanner.planning.model.DailyAvailability::effectiveSchedulableHours).sum();

        StudyPlan studyPlan = StudyPlan.builder()
                .user(user)
                .type(PlanType.WEEKLY)
                .status(PlanStatus.ACTIVE)
                .startDate(startDate)
                .endDate(endDate)
                .totalAvailableHours(totalAvailable)
                .build();

        studyPlan = studyPlanRepository.save(studyPlan);

        List<Assessment> assessments = assessmentRepository.findActivePlanningAssessments(user.getId());
        List<Task> tasks = taskRepository.findActivePlanningTasks(user.getId());

        // ======= CP-SAT CUTOVER =======
        // Replaces: planningEngine.generatePlanWithAvailability(studyPlan, assessments,
        // tasks, dailyAvailMap, LocalDateTime.now());
        CpSatPlanningResult result = cpSatPlanningService.generatePlan(
                studyPlan, startDate, endDate, assessments, tasks, dailyAvailMap);
        // ===============================

        // Log warnings if any
        if (result.hasWarnings()) {
            log.warn("[StudyPlanService] Planning warnings for user {}: {}", user.getId(), result.warnings());
        }

        List<StudyPlanItem> items = studyPlanItemRepository.saveAll(result.items());

        studyPlan.setTotalPlannedHours(result.totalPlannedHours());
        studyPlan.setItems(items);
        studyPlanRepository.save(studyPlan);

        return StudyPlanResponse.from(studyPlan);
    }

    private List<com.academicplanner.planning.model.TimeSlot> validateAndMapTimeSlots(String dayName,
            List<TimeSlotRequest> requests) {
        List<TimeSlotRequest> validRequests = requests.stream()
                .filter(r -> r.startTime() != null && r.endTime() != null)
                .sorted(java.util.Comparator.comparing(TimeSlotRequest::startTime))
                .toList();

        List<com.academicplanner.planning.model.TimeSlot> slots = new java.util.ArrayList<>();
        TimeSlotRequest prev = null;

        for (TimeSlotRequest curr : validRequests) {
            if (!curr.startTime().isBefore(curr.endTime())) {
                throw new IllegalArgumentException("Invalid time slot for " + dayName + ": Start time ("
                        + curr.startTime() + ") must be before end time (" + curr.endTime() + ").");
            }
            if (prev != null && curr.startTime().isBefore(prev.endTime())) {
                throw new IllegalArgumentException("Overlapping time slots for " + dayName + ": [" + prev.startTime()
                        + " - " + prev.endTime() + "] and [" + curr.startTime() + " - " + curr.endTime() + "].");
            }
            slots.add(new com.academicplanner.planning.model.TimeSlot(curr.startTime(), curr.endTime()));
            prev = curr;
        }

        return slots;
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
        List<StudyPlanItem> items = studyPlanItemRepository
                .findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(plan.getId());
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