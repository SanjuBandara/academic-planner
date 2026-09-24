package com.academicplanner.ai.tool;

import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.User;
import com.academicplanner.repository.StudyPlanItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Tool that allows the AI assistant to mark study plan items as COMPLETED
 * or SKIPPED on behalf of the authenticated student.
 * <p>
 * Strict ownership check: a student may only modify items that belong to them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkCompletedTool {

    private final StudyPlanItemRepository studyPlanItemRepository;

    /**
     * Marks a specific study plan item as COMPLETED for the authenticated user.
     *
     * @return true if the item was found, owned by the user, and successfully marked.
     */
    @Transactional
    public boolean markItemCompleted(Long itemId, User user) {
        log.info("[MarkCompletedTool] Marking item {} as COMPLETED for user {}", itemId, user.getId());
        Optional<StudyPlanItem> itemOpt = studyPlanItemRepository.findByIdAndStudyPlan_User_Id(itemId, user.getId());
        if (itemOpt.isEmpty()) {
            log.warn("[MarkCompletedTool] Item {} not found or not owned by user {}", itemId, user.getId());
            return false;
        }
        StudyPlanItem item = itemOpt.get();
        if (item.getStatus() == StudyPlanItem.ItemStatus.COMPLETED) {
            log.info("[MarkCompletedTool] Item {} is already COMPLETED", itemId);
            return true; // idempotent
        }
        item.setStatus(StudyPlanItem.ItemStatus.COMPLETED);
        item.setActualHours(item.getPlannedHours());
        studyPlanItemRepository.save(item);
        log.info("[MarkCompletedTool] Successfully marked item {} as COMPLETED", itemId);
        return true;
    }

    /**
     * Attempts to find and mark a study session completed by fuzzy-matching
     * the activity label against today's or this week's sessions for the user.
     *
     * @param titleFragment  a partial label provided by the student (e.g. "DSA")
     * @return the title of the matched and completed session, or null if none matched
     */
    @Transactional
    public String markByTitleFragment(String titleFragment, User user) {
        if (titleFragment == null || titleFragment.isBlank()) return null;
        String fragment = titleFragment.trim().toLowerCase();

        // Look at today's items first, then within the week
        LocalDate today = LocalDate.now();
        List<StudyPlanItem> candidates = studyPlanItemRepository.findTodaysItems(user.getId(), today);

        Optional<StudyPlanItem> match = candidates.stream()
                .filter(i -> i.getStatus() != StudyPlanItem.ItemStatus.COMPLETED)
                .filter(i -> {
                    String label = i.getActivityLabel() != null ? i.getActivityLabel().toLowerCase() : "";
                    String taskTitle = i.getTask() != null && i.getTask().getTitle() != null
                            ? i.getTask().getTitle().toLowerCase() : "";
                    String assessTitle = i.getAssessment() != null && i.getAssessment().getTitle() != null
                            ? i.getAssessment().getTitle().toLowerCase() : "";
                    String moduleCode = i.getModule() != null && i.getModule().getCode() != null
                            ? i.getModule().getCode().toLowerCase() : "";
                    return label.contains(fragment) || taskTitle.contains(fragment)
                            || assessTitle.contains(fragment) || moduleCode.contains(fragment);
                })
                .findFirst();

        if (match.isEmpty()) {
            log.info("[MarkCompletedTool] No matching session found for fragment '{}' on {}", titleFragment, today);
            return null;
        }

        StudyPlanItem item = match.get();
        item.setStatus(StudyPlanItem.ItemStatus.COMPLETED);
        item.setActualHours(item.getPlannedHours());
        studyPlanItemRepository.save(item);

        String displayTitle = item.getActivityLabel() != null ? item.getActivityLabel()
                : (item.getTask() != null ? item.getTask().getTitle()
                : (item.getAssessment() != null ? item.getAssessment().getTitle() : "Study Session"));
        log.info("[MarkCompletedTool] Marked '{}' (id={}) as COMPLETED for user {}", displayTitle, item.getId(), user.getId());
        return displayTitle;
    }
}
