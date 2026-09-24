package com.academicplanner.ai.tool;

import com.academicplanner.ai.dto.DailyPlanRequest;
import com.academicplanner.ai.dto.PlanModificationRequest;
import com.academicplanner.ai.dto.PlanModificationResult;
import com.academicplanner.dto.studyplan.StudyPlanItemResponse;
import com.academicplanner.dto.studyplan.StudyPlanResponse;
import com.academicplanner.entity.StudyAvailability;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.User;
import com.academicplanner.repository.StudyAvailabilityRepository;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controlled planning tool connecting the AI assistant to the underlying planning rules
 * and Python OR-Tools planning service.
 *
 * <p>Validates constraints, checks available hours, and ensures that student data isolation is strictly enforced.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanningTool {

    private final StudyPlanService studyPlanService;
    private final StudyPlanRepository studyPlanRepository;
    private final StudyPlanItemRepository studyPlanItemRepository;
    private final StudyAvailabilityRepository availabilityRepository;

    // Cache of pending proposals awaiting user confirmation
    private final ConcurrentHashMap<String, PlanProposalHolder> pendingProposals = new ConcurrentHashMap<>();

    public record PlanProposalHolder(
            String proposalId,
            Long userId,
            PlanModificationRequest request,
            PlanModificationResult result,
            long timestamp
    ) {}

    /**
     * Checks feasibility and generates a proposal for modifying an activity allocation.
     */
    @Transactional(readOnly = true)
    public PlanModificationResult requestPlanModification(PlanModificationRequest request, User user) {
        log.info("[PlanningTool] Requesting plan modification for user: {}, action: {}, activityId: {}",
                user.getId(), request.getAction(), request.getActivityId());

        if (request.getActivityId() == null) {
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason("Activity ID is required to modify plan allocation.")
                    .build();
        }

        Optional<StudyPlanItem> itemOpt = studyPlanItemRepository.findByIdAndStudyPlan_User_Id(
                request.getActivityId(), user.getId());

        if (itemOpt.isEmpty()) {
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason("The requested study activity was not found in your active schedule.")
                    .build();
        }

        StudyPlanItem item = itemOpt.get();
        if (item.getStatus() == StudyPlanItem.ItemStatus.COMPLETED) {
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason("Cannot modify an already completed study activity.")
                    .build();
        }

        int requestedMinutes = request.getAdditionalMinutes() != null ? request.getAdditionalMinutes() : 60;
        double requestedHours = requestedMinutes / 60.0;

        // Check availability on item's date
        LocalDate itemDate = item.getDate();
        Optional<StudyAvailability> availOpt = availabilityRepository.findByUserAndDayOfWeek(user, itemDate.getDayOfWeek());
        double dayAvailableHours = availOpt.map(StudyAvailability::getAvailableHours).orElse(4.0);

        List<StudyPlanItem> dayItems = studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(
                item.getStudyPlan().getId(), itemDate);

        double totalDayPlannedHours = dayItems.stream()
                .filter(i -> i.getStatus() != StudyPlanItem.ItemStatus.SKIPPED)
                .mapToDouble(StudyPlanItem::getPlannedHours)
                .sum();


        double remainingDayHours = Math.max(0.0, dayAvailableHours - totalDayPlannedHours);
        int remainingMinutes = (int) Math.round(remainingDayHours * 60);

        String proposalId = UUID.randomUUID().toString();
        String activityTitle = item.getActivityLabel() != null ? item.getActivityLabel() :
                (item.getTask() != null ? item.getTask().getTitle() :
                        (item.getAssessment() != null ? item.getAssessment().getTitle() : "Study Session"));

        if (remainingMinutes >= requestedMinutes) {
            // Fully feasible without displacing other sessions
            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("FEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(requestedMinutes)
                    .reason(String.format("There is sufficient unused availability (%d minutes remaining on %s) to add %d minutes to %s.",
                            remainingMinutes, itemDate, requestedMinutes, activityTitle))
                    .affectedActivities(List.of(activityTitle))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result, System.currentTimeMillis()));
            return result;
        } else if (remainingMinutes > 0) {
            // Partially feasible
            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("PARTIALLY_FEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(remainingMinutes)
                    .reason(String.format("Only %d minutes of unused study time remains on %s. Would you like to add %d minutes instead?",
                            remainingMinutes, itemDate, remainingMinutes))
                    .affectedActivities(List.of(activityTitle))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result, System.currentTimeMillis()));
            return result;
        } else {
            // Infeasible without schedule conflict
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(0)
                    .reason(String.format("No unused study time is available on %s (allocated: %.1f / %.1f hours limit). Cannot add time without displacing existing sessions.",
                            itemDate, totalDayPlannedHours, dayAvailableHours))
                    .affectedActivities(List.of(activityTitle))
                    .build();
        }
    }

    /**
     * Applies a confirmed proposal.
     */
    @Transactional
    public boolean applyProposal(String proposalId, User user) {
        PlanProposalHolder holder = pendingProposals.remove(proposalId);
        if (holder == null || !holder.userId().equals(user.getId())) {
            log.warn("[PlanningTool] Invalid or expired proposal: {}", proposalId);
            return false;
        }

        PlanModificationRequest request = holder.request();
        Optional<StudyPlanItem> itemOpt = studyPlanItemRepository.findByIdAndStudyPlan_User_Id(
                request.getActivityId(), user.getId());

        if (itemOpt.isEmpty()) {
            return false;
        }

        StudyPlanItem item = itemOpt.get();
        int minutesToAdd = holder.result().getAllocatedMinutes() != null ? holder.result().getAllocatedMinutes() : 0;
        double additionalHours = minutesToAdd / 60.0;

        item.setPlannedHours(item.getPlannedHours() + additionalHours);
        if (item.getEndTime() != null) {
            item.setEndTime(item.getEndTime().plusMinutes(minutesToAdd));
        }
        studyPlanItemRepository.save(item);

        // Update parent plan total planned hours
        StudyPlan plan = item.getStudyPlan();
        if (plan != null) {
            plan.setTotalPlannedHours(plan.getTotalPlannedHours() + additionalHours);
            studyPlanRepository.save(plan);
        }

        log.info("[PlanningTool] Successfully applied proposal {} for item {}", proposalId, item.getId());
        return true;
    }

    public void cancelProposal(String proposalId, User user) {
        PlanProposalHolder holder = pendingProposals.get(proposalId);
        if (holder != null && holder.userId().equals(user.getId())) {
            pendingProposals.remove(proposalId);
            log.info("[PlanningTool] Cancelled proposal: {}", proposalId);
        }
    }
}
