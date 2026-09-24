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

/**
 * Phase 4 tool: Marks all of today's pending study sessions as SKIPPED in a single
 * operation, for use when the student is sick, has an emergency, or is taking a rest day.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkipTodayTool {

    private final StudyPlanItemRepository studyPlanItemRepository;

    /**
     * Marks all PLANNED sessions for today as SKIPPED.
     *
     * @return count of sessions that were skipped
     */
    @Transactional
    public int skipAllTodaySessions(User user) {
        LocalDate today = LocalDate.now();
        List<StudyPlanItem> todayItems = studyPlanItemRepository.findTodaysItems(user.getId(), today);

        int skippedCount = 0;
        for (StudyPlanItem item : todayItems) {
            if (item.getStatus() == StudyPlanItem.ItemStatus.PLANNED
                    || item.getStatus() == StudyPlanItem.ItemStatus.IN_PROGRESS) {
                item.setStatus(StudyPlanItem.ItemStatus.SKIPPED);
                studyPlanItemRepository.save(item);
                skippedCount++;
                log.info("[SkipTodayTool] Skipped item id={} for user={}", item.getId(), user.getId());
            }
        }

        log.info("[SkipTodayTool] Skipped {} session(s) for user {} on {}", skippedCount, user.getId(), today);
        return skippedCount;
    }
}
