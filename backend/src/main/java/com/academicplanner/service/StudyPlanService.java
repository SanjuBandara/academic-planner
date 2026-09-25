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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
        // Part 1: Planning period always starts from today (the current server date)
        // and spans exactly 7 days. The startDate from the request is ignored.
        LocalDateTime currentDateTime = LocalDateTime.now();
        LocalDate startDate = currentDateTime.toLocalDate();
        LocalDate endDate = startDate.plusDays(6);

        log.info("[StudyPlanService] Generating plan for user {} | period {} to {} | currentTime {}",
                user.getId(), startDate, endDate, currentDateTime);

        // Cancel previous active weekly plan if any (prevents duplicates)
        studyPlanRepository.findByUserAndTypeAndStatus(user, PlanType.WEEKLY, PlanStatus.ACTIVE)
                .ifPresent(p -> {
                    // Delete old items first to avoid orphan rows
                    studyPlanItemRepository.deleteAll(
                            studyPlanItemRepository.findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(p.getId()));
                    p.setStatus(PlanStatus.CANCELLED);
                    studyPlanRepository.save(p);
                });

        // Build DailyAvailability map per day
        Map<LocalDate, com.academicplanner.planning.model.DailyAvailability> dailyAvailMap = new java.util.LinkedHashMap<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            String dayName = date.getDayOfWeek().name();

            Double hours = 0.0; // default 0 if no availability declared
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
        CpSatPlanningResult result = cpSatPlanningService.generatePlan(
                studyPlan, startDate, endDate, currentDateTime, assessments, tasks, dailyAvailMap);
        // ===============================

        // Log warnings if any
        if (result.hasWarnings()) {
            log.warn("[StudyPlanService] Planning warnings for user {}: {}", user.getId(), result.warnings());
        }

        List<StudyPlanItem> items = studyPlanItemRepository.saveAll(result.items());

        studyPlan.setTotalPlannedHours(result.totalPlannedHours());
        studyPlan.getItems().clear();
        studyPlan.getItems().addAll(items);
        studyPlanRepository.save(studyPlan);

        // Forward solver status and any scheduling warnings to the response
        return StudyPlanResponse.from(studyPlan, result.solverStatus(), result.warnings());
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
    public TodaysScheduleResponse getTodaysSchedule(User user) {
        LocalDate today = LocalDate.now();
        List<StudyPlanItem> items = studyPlanItemRepository.findTodaysItems(user.getId(), today);
        int totalMinutes = items.stream()
                .mapToInt(item -> {
                    if (item.getStartTime() != null && item.getEndTime() != null) {
                        return (int) java.time.Duration.between(item.getStartTime(), item.getEndTime()).toMinutes();
                    }
                    return (int) (item.getPlannedHours() * 60);
                })
                .sum();
        List<TodaysScheduleResponse.SessionSummary> sessions = items.stream()
                .map(item -> new TodaysScheduleResponse.SessionSummary(
                        item.getId(),
                        item.getTask() != null ? "T-" + item.getTask().getId()
                                : item.getAssessment() != null ? "A-" + item.getAssessment().getId() : null,
                        item.getActivityLabel() != null ? item.getActivityLabel()
                                : item.getTask() != null ? item.getTask().getTitle()
                                        : item.getAssessment() != null ? item.getAssessment().getTitle()
                                                : "Study Session",
                        item.getActivityType(),
                        item.getModule() != null ? item.getModule().getCode() : null,
                        item.getModule() != null ? item.getModule().getName() : null,
                        item.getStartTime() != null ? item.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                : null,
                        item.getEndTime() != null ? item.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                : null,
                        item.getStartTime() != null && item.getEndTime() != null
                                ? (int) java.time.Duration.between(item.getStartTime(), item.getEndTime()).toMinutes()
                                : (int) (item.getPlannedHours() * 60),
                        item.getStatus().name()))
                .toList();
        return new TodaysScheduleResponse(today, totalMinutes, (double) totalMinutes / 60.0, sessions);
    }

    @Transactional(readOnly = true)
    public StudyPlanResponse getActivePlan(User user) {
        StudyPlan plan = studyPlanRepository
                .findByUserAndTypeAndStatus(user, PlanType.WEEKLY, PlanStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active study plan found"));

        // Do NOT replace plan.items because it is a Hibernate-managed
        // orphanRemoval collection.
        //
        // The StudyPlanResponse should be built from the items queried
        // directly from the repository.
        // List<StudyPlanItem> items = studyPlanItemRepository
        // .findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(plan.getId());

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