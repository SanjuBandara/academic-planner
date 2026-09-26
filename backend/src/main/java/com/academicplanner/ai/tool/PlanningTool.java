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
import java.time.LocalTime;
import java.util.ArrayList;
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
            java.util.Map<Long, Integer> adjustments,
            java.util.List<Long> itemsToMarkSkipped,
            Long itemToMoveId,
            LocalDate newDateForMovedItem,
            long timestamp
    ) {
        public PlanProposalHolder(String proposalId, Long userId, PlanModificationRequest request,
                                  PlanModificationResult result, java.util.Map<Long, Integer> adjustments, long timestamp) {
            this(proposalId, userId, request, result, adjustments, java.util.List.of(), null, null, timestamp);
        }
    }

    /**
     * Checks feasibility and generates a proposal for modifying an activity allocation.
     * Supports both Scenario A (sufficient free time) and Scenario B (displacing/reducing other sessions).
     */
    @Transactional(readOnly = true)
    public PlanModificationResult requestPlanModification(PlanModificationRequest request, User user) {
        log.info("[PlanningTool] Requesting plan modification for user: {}, action: {}, activityId: {}, moduleCode: {}, activityName: {}",
                user.getId(), request.getAction(), request.getActivityId(), request.getModuleCode(), request.getActivityName());

        StudyPlanItem item = resolveStudyPlanItem(request, user);
        if (item == null) {
            String target = request.getModuleCode() != null ? request.getModuleCode() :
                    (request.getActivityName() != null ? request.getActivityName() : null);
            String reason = target != null
                    ? String.format("The requested study activity '%s' was not found in your active schedule.", target)
                    : "The requested study activity was not found in your active schedule.";
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason(reason)
                    .build();
        }

        if (item.getStatus() == StudyPlanItem.ItemStatus.COMPLETED) {
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason("Cannot modify an already completed study activity.")
                    .build();
        }

        String activityTitle = item.getActivityLabel() != null ? item.getActivityLabel() :
                (item.getTask() != null ? item.getTask().getTitle() :
                        (item.getAssessment() != null ? item.getAssessment().getTitle() :
                                (item.getModule() != null ? item.getModule().getCode() : "Study Session")));

        int requestedMinutes = request.getAdditionalMinutes() != null ? request.getAdditionalMinutes() : 60;
        String proposalId = UUID.randomUUID().toString();

        // ── Handle DECREASE_ACTIVITY_TIME ───────────────────────────────────
        if (request.getAction() == com.academicplanner.ai.model.AiAction.DECREASE_ACTIVITY_TIME) {
            int currentMinutes = (int) Math.round(item.getPlannedHours() * 60);
            if (currentMinutes <= requestedMinutes) {
                return PlanModificationResult.builder()
                        .status("INFEASIBLE")
                        .reason(String.format("Cannot reduce %s by %d minutes because it is only %d minutes long.",
                                activityTitle, requestedMinutes, currentMinutes))
                        .build();
            }

            int newMinutes = currentMinutes - requestedMinutes;
            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("FEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(-requestedMinutes)
                    .reason(String.format("Propose reducing %s by %d minutes (%.1fh → %.1fh). This will free up study capacity.",
                            activityTitle, requestedMinutes, item.getPlannedHours(), newMinutes / 60.0))
                    .affectedActivities(List.of(activityTitle))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result,
                    java.util.Map.of(item.getId(), -requestedMinutes), System.currentTimeMillis()));
            return result;
        }

        // ── Handle INCREASE_ACTIVITY_TIME ───────────────────────────────────
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
        double requestedHours = requestedMinutes / 60.0;

        // Scenario A: Sufficient unused capacity
        if (remainingMinutes >= requestedMinutes) {
            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("FEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(requestedMinutes)
                    .reason(String.format("Sufficient unused study time (%d min remaining on %s) to add %d minutes to %s (%.1fh → %.1fh). No other activities need to move.",
                            remainingMinutes, itemDate, requestedMinutes, activityTitle, item.getPlannedHours(), item.getPlannedHours() + requestedHours))
                    .affectedActivities(List.of(activityTitle))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result,
                    java.util.Map.of(item.getId(), requestedMinutes), System.currentTimeMillis()));
            return result;
        }

        // Scenario B: Insufficient unused capacity — check if other sessions on the same day can be adjusted
        int shortfall = requestedMinutes - remainingMinutes;
        java.util.Map<Long, Integer> adjustments = new java.util.HashMap<>();
        java.util.List<String> displacedList = new java.util.ArrayList<>();
        int rescuedMinutes = 0;

        for (StudyPlanItem other : dayItems) {
            if (other.getId().equals(item.getId()) || other.getStatus() != StudyPlanItem.ItemStatus.PLANNED) {
                continue;
            }
            int otherMinutes = (int) Math.round(other.getPlannedHours() * 60);
            int availableToTrim = Math.max(0, otherMinutes - 30); // Leave at least 30 min
            if (availableToTrim > 0 && rescuedMinutes < shortfall) {
                int take = Math.min(availableToTrim, shortfall - rescuedMinutes);
                rescuedMinutes += take;
                adjustments.put(other.getId(), -take);
                String otherTitle = other.getActivityLabel() != null ? other.getActivityLabel() :
                        (other.getModule() != null ? other.getModule().getCode() : "Other Session");
                displacedList.add(String.format("%s (-%d min)", otherTitle, take));
            }
        }

        if (remainingMinutes + rescuedMinutes >= requestedMinutes) {
            // Feasible via displacement!
            adjustments.put(item.getId(), requestedMinutes);
            java.util.List<String> affected = new java.util.ArrayList<>();
            affected.add(String.format("%s (+%d min)", activityTitle, requestedMinutes));
            affected.addAll(displacedList);

            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("REQUIRES_CONFIRMATION")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(requestedMinutes)
                    .reason(String.format("There is not enough unused time (%d min remaining) on %s for the full %d minutes. The planner proposes adding %d minutes to %s by reducing: %s. Would you like to apply this change?",
                            remainingMinutes, itemDate, requestedMinutes, requestedMinutes, activityTitle, String.join(", ", displacedList)))
                    .affectedActivities(affected)
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result,
                    adjustments, System.currentTimeMillis()));
            return result;
        } else if (remainingMinutes > 0) {
            // Partially feasible with only the free time
            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("PARTIALLY_FEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(remainingMinutes)
                    .reason(String.format("Only %d minutes of unused study time remains on %s. Would you like to add %d minutes to %s instead?",
                            remainingMinutes, itemDate, remainingMinutes, activityTitle))
                    .affectedActivities(List.of(activityTitle))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(proposalId, user.getId(), request, result,
                    java.util.Map.of(item.getId(), remainingMinutes), System.currentTimeMillis()));
            return result;
        } else {
            // Infeasible
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .requestedMinutes(requestedMinutes)
                    .allocatedMinutes(0)
                    .reason(String.format("No unused study time is available on %s (allocated: %.1f / %.1f hours limit) and other sessions cannot be displaced. Cannot add time.",
                            itemDate, totalDayPlannedHours, dayAvailableHours))
                    .affectedActivities(List.of(activityTitle))
                    .build();
        }
    }

    /**
     * Resolves a StudyPlanItem by ID, or looks it up from the student's active plan by moduleCode or name.
     */
    private StudyPlanItem resolveStudyPlanItem(PlanModificationRequest request, User user) {
        if (request.getActivityId() != null) {
            return studyPlanItemRepository.findByIdAndStudyPlan_User_Id(request.getActivityId(), user.getId())
                    .orElse(null);
        }

        String search = request.getModuleCode() != null ? request.getModuleCode().trim() :
                (request.getActivityName() != null ? request.getActivityName().trim() : null);

        if (search == null || search.isBlank()) {
            return null;
        }

        String lowerSearch = search.toLowerCase();

        // 1. Check today's items first
        List<StudyPlanItem> todays = studyPlanItemRepository.findTodaysItems(user.getId(), LocalDate.now());
        for (StudyPlanItem item : todays) {
            if (matchesItem(item, lowerSearch)) {
                return item;
            }
        }

        // 2. Check active weekly plan items
        Optional<StudyPlan> planOpt = studyPlanRepository.findByUserAndTypeAndStatus(
                user, StudyPlan.PlanType.WEEKLY, StudyPlan.PlanStatus.ACTIVE);
        if (planOpt.isPresent()) {
            List<StudyPlanItem> allItems = studyPlanItemRepository.findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(planOpt.get().getId());
            for (StudyPlanItem item : allItems) {
                if (matchesItem(item, lowerSearch) && item.getStatus() != StudyPlanItem.ItemStatus.COMPLETED) {
                    return item;
                }
            }
        }

        return null;
    }

    private boolean matchesItem(StudyPlanItem item, String lowerSearch) {
        if (item.getModule() != null) {
            if (item.getModule().getCode() != null && item.getModule().getCode().toLowerCase().contains(lowerSearch)) return true;
            if (item.getModule().getName() != null && item.getModule().getName().toLowerCase().contains(lowerSearch)) return true;
        }
        if (item.getActivityLabel() != null && item.getActivityLabel().toLowerCase().contains(lowerSearch)) return true;
        if (item.getTask() != null && item.getTask().getTitle().toLowerCase().contains(lowerSearch)) return true;
        if (item.getAssessment() != null && item.getAssessment().getTitle().toLowerCase().contains(lowerSearch)) return true;
        return false;
    }

    /**
     * Applies a confirmed proposal. Persists all item modifications, skips, moves, and updates the parent plan.
     */
    @Transactional
    public boolean applyProposal(String proposalId, User user) {
        PlanProposalHolder holder = pendingProposals.remove(proposalId);
        if (holder == null || !holder.userId().equals(user.getId())) {
            log.warn("[PlanningTool] Invalid or expired proposal: {}", proposalId);
            return false;
        }

        // 1. Mark skipped items
        if (holder.itemsToMarkSkipped() != null) {
            for (Long skippedId : holder.itemsToMarkSkipped()) {
                studyPlanItemRepository.findByIdAndStudyPlan_User_Id(skippedId, user.getId())
                        .ifPresent(it -> {
                            it.setStatus(StudyPlanItem.ItemStatus.SKIPPED);
                            studyPlanItemRepository.save(it);
                        });
            }
        }

        // 2. Move item date if requested
        if (holder.itemToMoveId() != null && holder.newDateForMovedItem() != null) {
            studyPlanItemRepository.findByIdAndStudyPlan_User_Id(holder.itemToMoveId(), user.getId())
                    .ifPresent(it -> {
                        it.setDate(holder.newDateForMovedItem());
                        studyPlanItemRepository.save(it);
                    });
        }

        // 3. Apply minute adjustments
        java.util.Map<Long, Integer> adjustments = holder.adjustments();
        double netPlanHoursDelta = 0.0;
        StudyPlan parentPlan = null;

        if (adjustments != null && !adjustments.isEmpty()) {
            for (java.util.Map.Entry<Long, Integer> entry : adjustments.entrySet()) {
                Long itemId = entry.getKey();
                int deltaMinutes = entry.getValue();
                double deltaHours = deltaMinutes / 60.0;

                Optional<StudyPlanItem> itemOpt = studyPlanItemRepository.findByIdAndStudyPlan_User_Id(itemId, user.getId());
                if (itemOpt.isPresent()) {
                    StudyPlanItem item = itemOpt.get();
                    double updatedHours = Math.max(0.25, item.getPlannedHours() + deltaHours);
                    item.setPlannedHours(updatedHours);
                    if (item.getEndTime() != null) {
                        item.setEndTime(item.getEndTime().plusMinutes(deltaMinutes));
                    }
                    studyPlanItemRepository.save(item);
                    netPlanHoursDelta += deltaHours;
                    if (parentPlan == null) {
                        parentPlan = item.getStudyPlan();
                    }
                }
            }
        }

        if (parentPlan != null && netPlanHoursDelta != 0.0) {
            parentPlan.setTotalPlannedHours(Math.max(0.0, parentPlan.getTotalPlannedHours() + netPlanHoursDelta));
            studyPlanRepository.save(parentPlan);
        }

        log.info("[PlanningTool] Successfully applied proposal {} for user {}", proposalId, user.getId());
        return true;
    }

    public void cancelProposal(String proposalId, User user) {
        PlanProposalHolder holder = pendingProposals.get(proposalId);
        if (holder != null && holder.userId().equals(user.getId())) {
            pendingProposals.remove(proposalId);
            log.info("[PlanningTool] Cancelled proposal: {}", proposalId);
        }
    }

    // ── Phase 8: Adaptive Replanning Methods ─────────────────────────────────

    /**
     * Identifies a missed study session and proposes rescheduling its hours to an upcoming day with capacity.
     */
    @Transactional(readOnly = true)
    public PlanModificationResult createMissedSessionProposal(String moduleOrTitle, User user) {
        log.info("[PlanningTool] Creating missed session proposal for user {}: search='{}'", user.getId(), moduleOrTitle);

        StudyPlanItem missedItem = null;
        String lower = moduleOrTitle != null ? moduleOrTitle.toLowerCase() : "";

        // 1. Search today's items
        List<StudyPlanItem> todays = studyPlanItemRepository.findTodaysItems(user.getId(), LocalDate.now());
        for (StudyPlanItem it : todays) {
            if (matchesItem(it, lower) && it.getStatus() != StudyPlanItem.ItemStatus.COMPLETED) {
                missedItem = it;
                break;
            }
        }

        // 2. If not found in today, check active plan items for recent uncompleted session
        if (missedItem == null) {
            Optional<StudyPlan> planOpt = studyPlanRepository.findByUserAndTypeAndStatus(
                    user, StudyPlan.PlanType.WEEKLY, StudyPlan.PlanStatus.ACTIVE);
            if (planOpt.isPresent()) {
                List<StudyPlanItem> allItems = studyPlanItemRepository.findAllByStudyPlan_IdOrderByDateAscStartTimeAsc(planOpt.get().getId());
                for (StudyPlanItem it : allItems) {
                    if (matchesItem(it, lower) && it.getStatus() == StudyPlanItem.ItemStatus.PLANNED) {
                        missedItem = it;
                        break;
                    }
                }
            }
        }

        if (missedItem == null) {
            return PlanModificationResult.builder()
                    .status("INFEASIBLE")
                    .reason(String.format("Could not find a scheduled session matching '%s' to reschedule.", moduleOrTitle))
                    .build();
        }

        String title = missedItem.getActivityLabel() != null ? missedItem.getActivityLabel() :
                (missedItem.getModule() != null ? missedItem.getModule().getCode() : "Study Session");
        double missedHours = missedItem.getPlannedHours();
        int missedMinutes = (int) Math.round(missedHours * 60);

        // Find upcoming day with capacity in the active weekly plan
        StudyPlan plan = missedItem.getStudyPlan();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate candidateDate = null;
        double maxFree = -1.0;

        if (plan != null && plan.getEndDate() != null) {
            for (LocalDate d = tomorrow; !d.isAfter(plan.getEndDate()); d = d.plusDays(1)) {
                Optional<StudyAvailability> availOpt = availabilityRepository.findByUserAndDayOfWeek(user, d.getDayOfWeek());
                double dayAvail = availOpt.map(StudyAvailability::getAvailableHours).orElse(4.0);
                double dayPlanned = studyPlanItemRepository.findAllByStudyPlan_IdAndDateOrderByStartTimeAsc(plan.getId(), d)
                        .stream().filter(i -> i.getStatus() != StudyPlanItem.ItemStatus.SKIPPED).mapToDouble(StudyPlanItem::getPlannedHours).sum();
                double free = Math.max(0.0, dayAvail - dayPlanned);

                if (free >= missedHours && free > maxFree) {
                    candidateDate = d;
                    maxFree = free;
                }
            }
        }

        String proposalId = UUID.randomUUID().toString();

        if (candidateDate != null) {
            String reason = String.format("Identified missed session: **%s** (%.1fh on %s). The adaptive planner proposes marking today's session as missed and rescheduling %.1f hours to **%s (%s)** where you have %.1fh unused capacity. Would you like to apply this change?",
                    title, missedHours, missedItem.getDate(), missedHours, candidateDate, candidateDate.getDayOfWeek().name(), maxFree);

            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("REQUIRES_CONFIRMATION")
                    .requestedMinutes(missedMinutes)
                    .allocatedMinutes(missedMinutes)
                    .reason(reason)
                    .affectedActivities(List.of(
                            String.format("Mark %s as missed (today)", title),
                            String.format("Reschedule %s to %s", title, candidateDate)
                    ))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(
                    proposalId, user.getId(), null, result, java.util.Map.of(),
                    List.of(missedItem.getId()), missedItem.getId(), candidateDate, System.currentTimeMillis()));

            return result;
        } else {
            String reason = String.format("Identified missed session: **%s** (%.1fh). Your upcoming days this week are already at capacity. The planner proposes marking this session as skipped for now and recommends regenerating your weekly plan to re-optimize remaining workloads.",
                    title, missedHours);

            PlanModificationResult result = PlanModificationResult.builder()
                    .proposalId(proposalId)
                    .status("REQUIRES_CONFIRMATION")
                    .requestedMinutes(missedMinutes)
                    .allocatedMinutes(0)
                    .reason(reason)
                    .affectedActivities(List.of(String.format("Mark %s as skipped", title)))
                    .build();

            pendingProposals.put(proposalId, new PlanProposalHolder(
                    proposalId, user.getId(), null, result, java.util.Map.of(),
                    List.of(missedItem.getId()), null, null, System.currentTimeMillis()));

            return result;
        }
    }

    /**
     * Proposes adjustments when today's available hours decrease.
     */
    @Transactional(readOnly = true)
    public PlanModificationResult createAvailabilityChangeProposal(double newMaxHoursToday, User user) {
        log.info("[PlanningTool] Creating availability change proposal for user {}: newMaxHours={}", user.getId(), newMaxHoursToday);

        List<StudyPlanItem> todays = studyPlanItemRepository.findTodaysItems(user.getId(), LocalDate.now());
        double totalPlannedToday = todays.stream()
                .filter(i -> i.getStatus() != StudyPlanItem.ItemStatus.SKIPPED)
                .mapToDouble(StudyPlanItem::getPlannedHours)
                .sum();

        if (totalPlannedToday <= newMaxHoursToday) {
            return PlanModificationResult.builder()
                    .status("FEASIBLE")
                    .reason(String.format("Your current schedule for today has %.1f hours of sessions, which already fits within your updated limit of %.1f hours. No sessions need to be moved!",
                            totalPlannedToday, newMaxHoursToday))
                    .build();
        }

        double excessHours = totalPlannedToday - newMaxHoursToday;
        int excessMinutes = (int) Math.round(excessHours * 60);

        List<StudyPlanItem> plannedItems = todays.stream()
                .filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.PLANNED)
                .sorted((a, b) -> (b.getStartTime() != null && a.getStartTime() != null)
                        ? b.getStartTime().compareTo(a.getStartTime()) : 0) // Latest first
                .toList();

        List<String> trimmedNames = new ArrayList<>();
        List<Long> itemsToSkip = new ArrayList<>();
        double accumulated = 0.0;

        for (StudyPlanItem it : plannedItems) {
            if (accumulated < excessHours) {
                itemsToSkip.add(it.getId());
                accumulated += it.getPlannedHours();
                String title = it.getActivityLabel() != null ? it.getActivityLabel() :
                        (it.getModule() != null ? it.getModule().getCode() : "Session");
                trimmedNames.add(String.format("%s (%.1fh)", title, it.getPlannedHours()));
            }
        }

        String proposalId = UUID.randomUUID().toString();
        String reason = String.format("You have %.1f hours planned today, which exceeds your new limit of %.1f hours. The planner proposes trimming %.1f hours from today's latest sessions (%s) to stay within your availability. Would you like to apply this adjustment?",
                totalPlannedToday, newMaxHoursToday, accumulated, String.join(", ", trimmedNames));

        PlanModificationResult result = PlanModificationResult.builder()
                .proposalId(proposalId)
                .status("REQUIRES_CONFIRMATION")
                .requestedMinutes(excessMinutes)
                .allocatedMinutes((int) Math.round(accumulated * 60))
                .reason(reason)
                .affectedActivities(trimmedNames)
                .build();

        pendingProposals.put(proposalId, new PlanProposalHolder(
                proposalId, user.getId(), null, result, java.util.Map.of(),
                itemsToSkip, null, null, System.currentTimeMillis()));

        return result;
    }

    /**
     * Proposes adjusting today's sessions when a cutoff time (e.g. 20:00) is specified.
     */
    @Transactional(readOnly = true)
    public PlanModificationResult createTimeCutoffProposal(LocalTime cutoff, User user) {
        log.info("[PlanningTool] Creating time cutoff proposal for user {}: cutoff={}", user.getId(), cutoff);

        List<StudyPlanItem> todays = studyPlanItemRepository.findTodaysItems(user.getId(), LocalDate.now());
        List<StudyPlanItem> pastCutoff = todays.stream()
                .filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.PLANNED)
                .filter(i -> (i.getStartTime() != null && !i.getStartTime().isBefore(cutoff)) ||
                             (i.getEndTime() != null && i.getEndTime().isAfter(cutoff)))
                .toList();

        if (pastCutoff.isEmpty()) {
            return PlanModificationResult.builder()
                    .status("FEASIBLE")
                    .reason(String.format("None of your scheduled sessions today extend past %s. Your timetable is completely unaffected!",
                            cutoff.toString()))
                    .build();
        }

        List<String> affectedNames = pastCutoff.stream()
                .map(it -> String.format("%s (%s - %s)",
                        it.getActivityLabel() != null ? it.getActivityLabel() : (it.getModule() != null ? it.getModule().getCode() : "Session"),
                        it.getStartTime() != null ? it.getStartTime().toString() : "TBD",
                        it.getEndTime() != null ? it.getEndTime().toString() : "TBD"))
                .toList();

        List<Long> skipIds = pastCutoff.stream().map(StudyPlanItem::getId).toList();
        String proposalId = UUID.randomUUID().toString();

        String reason = String.format("Found %d session(s) scheduled past %s: %s. The planner proposes removing these late sessions from today's timetable so you finish on time. Would you like to apply this change?",
                pastCutoff.size(), cutoff.toString(), String.join(", ", affectedNames));

        PlanModificationResult result = PlanModificationResult.builder()
                .proposalId(proposalId)
                .status("REQUIRES_CONFIRMATION")
                .reason(reason)
                .affectedActivities(affectedNames)
                .build();

        pendingProposals.put(proposalId, new PlanProposalHolder(
                proposalId, user.getId(), null, result, java.util.Map.of(),
                skipIds, null, null, System.currentTimeMillis()));

        return result;
    }
}
