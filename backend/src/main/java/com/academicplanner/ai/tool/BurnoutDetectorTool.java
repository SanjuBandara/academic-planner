package com.academicplanner.ai.tool;

import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.User;
import com.academicplanner.repository.StudyPlanItemRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 4 tool: Analyses the student's recent completion patterns to detect
 * burnout signals, consistently skipped days, and under-allocation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BurnoutDetectorTool {

    private final StudyPlanItemRepository studyPlanItemRepository;

    @Data
    @Builder
    public static class BurnoutReport {
        private int totalSessions;
        private int completed;
        private int skipped;
        private int planned;
        private double completionRate;        // 0–100
        private double skippedRate;           // 0–100
        private double totalPlannedHours;
        private double completedHours;
        private String mostSkippedDay;        // e.g. "FRIDAY"
        private boolean burnoutDetected;
        private String summary;
        private String recommendation;
    }

    /**
     * Analyses the last 14 days of study data for the given user.
     *
     * @return a detailed BurnoutReport
     */
    @Transactional(readOnly = true)
    public BurnoutReport analyse(User user) {
        LocalDate today = LocalDate.now();
        LocalDate twoWeeksAgo = today.minusDays(14);

        List<StudyPlanItem> items = studyPlanItemRepository.findItemsInDateRange(user.getId(), twoWeeksAgo, today);

        if (items.isEmpty()) {
            return BurnoutReport.builder()
                    .totalSessions(0)
                    .completionRate(0)
                    .skippedRate(0)
                    .burnoutDetected(false)
                    .summary("No study activity found in the last 14 days.")
                    .recommendation("Start by generating a study plan in the Adaptive Planner.")
                    .build();
        }

        int total = items.size();
        long completed = items.stream().filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.COMPLETED).count();
        long skipped = items.stream().filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.SKIPPED).count();
        long planned = items.stream().filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.PLANNED
                || i.getStatus() == StudyPlanItem.ItemStatus.IN_PROGRESS).count();

        double totalHours = items.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        double completedHours = items.stream()
                .filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.COMPLETED)
                .mapToDouble(i -> i.getActualHours() != null ? i.getActualHours() : i.getPlannedHours())
                .sum();

        double completionRate = total > 0 ? (100.0 * completed / total) : 0;
        double skippedRate = total > 0 ? (100.0 * skipped / total) : 0;

        // Find the day of the week with the highest skip count
        Map<DayOfWeek, Long> skipsByDay = items.stream()
                .filter(i -> i.getStatus() == StudyPlanItem.ItemStatus.SKIPPED && i.getDate() != null)
                .collect(Collectors.groupingBy(i -> i.getDate().getDayOfWeek(), Collectors.counting()));

        String mostSkippedDay = skipsByDay.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey().name())
                .orElse(null);

        // Burnout signal: > 40% skip rate over last 14 days
        boolean burnoutDetected = skippedRate > 40;

        String summary = String.format(
                "Over the last 14 days: **%d sessions** scheduled (%.1f hrs planned).\n" +
                "✅ Completed: **%d** (%.1f hrs) | ⏭️ Skipped: **%d** | 📖 Pending: **%d**\n" +
                "**Completion Rate: %.0f%%** | Skip Rate: %.0f%%",
                total, totalHours, completed, completedHours, skipped, planned, completionRate, skippedRate
        );

        String recommendation;
        if (burnoutDetected) {
            recommendation = "⚠️ High skip rate detected (" + String.format("%.0f%%", skippedRate) + "). "
                    + "This may indicate overloading or burnout. Consider reducing your daily study quota in the Adaptive Planner "
                    + "or taking a planned rest day."
                    + (mostSkippedDay != null ? " You tend to skip most on **" + mostSkippedDay + "** — consider keeping that day lighter." : "");
        } else if (completionRate >= 80) {
            recommendation = "🌟 Excellent consistency! You're completing " + String.format("%.0f%%", completionRate)
                    + " of your planned sessions. Keep it up!";
        } else if (completionRate >= 50) {
            recommendation = "👍 Good progress (" + String.format("%.0f%%", completionRate) + " completion rate). "
                    + "Try to push towards completing at least 80% of sessions this week."
                    + (mostSkippedDay != null ? " Most skips happen on **" + mostSkippedDay + "**." : "");
        } else {
            recommendation = "📉 Completion rate is below 50%. Consider checking your availability settings "
                    + "and reducing your planned study hours to something more achievable.";
        }

        return BurnoutReport.builder()
                .totalSessions(total)
                .completed((int) completed)
                .skipped((int) skipped)
                .planned((int) planned)
                .completionRate(Math.round(completionRate * 10.0) / 10.0)
                .skippedRate(Math.round(skippedRate * 10.0) / 10.0)
                .totalPlannedHours(Math.round(totalHours * 10.0) / 10.0)
                .completedHours(Math.round(completedHours * 10.0) / 10.0)
                .mostSkippedDay(mostSkippedDay)
                .burnoutDetected(burnoutDetected)
                .summary(summary)
                .recommendation(recommendation)
                .build();
    }
}
