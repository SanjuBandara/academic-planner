package com.academicplanner.planning;

import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningItem;
import com.academicplanner.planning.model.PlanningItem.FeasibilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FeasibilityAnalyzer")
class FeasibilityAnalyzerTest {

    private FeasibilityAnalyzer analyzer;
    private LocalDateTime now;
    private Map<LocalDate, Double> dailyHours;

    @BeforeEach
    void setUp() {
        DeadlineUrgencyCalculator urgencyCalculator = new DeadlineUrgencyCalculator();
        analyzer = new FeasibilityAnalyzer(urgencyCalculator);
        now = LocalDateTime.of(2026, 9, 10, 8, 0);

        dailyHours = new LinkedHashMap<>();
        dailyHours.put(LocalDate.of(2026, 9, 10), 4.0);
        dailyHours.put(LocalDate.of(2026, 9, 11), 6.0); // 10h total across 2 days
    }

    @Test
    @DisplayName("Work needed <= available capacity -> FEASIBLE")
    void testFeasibleStatus() {
        Task task = Task.builder().id(1L).title("Assignment").remainingHours(6.0).build();
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 23, 59);

        PlanningItem item = PlanningItem.forTask(task);
        // Needed: 6.0h, Available: 10.0h
        analyzer.classify(item, dailyHours, now);

        assertThat(item.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(item.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("Work needed slightly above available (<= 1.2x) -> AT_RISK")
    void testAtRiskStatus() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 23, 59);
        Task task = Task.builder().id(2L).title("Math Quiz").remainingHours(11.0).dueDateTime(deadline).build();

        PlanningItem item = PlanningItem.forTask(task);
        // Needed: 11.0h, Available: 10.0h (11.0 <= 10.0 * 1.2 = 12.0)
        analyzer.classify(item, dailyHours, now);

        assertThat(item.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.AT_RISK);
        assertThat(item.getFeasibilityWarning()).isNotNull();
        assertThat(item.getFeasibilityWarning()).contains("AT RISK");
    }

    @Test
    @DisplayName("Work needed significantly exceeds available (> 1.2x) -> IMPOSSIBLE")
    void testImpossibleStatus() {
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 23, 59);
        Task task = Task.builder().id(3L).title("Large Project").remainingHours(15.0).dueDateTime(deadline).build();

        PlanningItem item = PlanningItem.forTask(task);
        // Needed: 15.0h, Available: 10.0h (15.0 > 12.0)
        analyzer.classify(item, dailyHours, now);

        assertThat(item.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
        assertThat(item.getFeasibilityWarning()).isNotNull();
        assertThat(item.getFeasibilityWarning()).contains("CANNOT be completed");
    }

    @Test
    @DisplayName("No deadline is always FEASIBLE")
    void testNoDeadlineAlwaysFeasible() {
        Task task = Task.builder().id(4L).title("Long-term reading").remainingHours(25.0).build();
        PlanningItem item = PlanningItem.forTask(task);

        analyzer.classify(item, dailyHours, now);

        assertThat(item.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(item.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("Zero work needed is always FEASIBLE")
    void testZeroWorkNeededFeasible() {
        Task task = Task.builder().id(5L).title("Finished task").remainingHours(0.0).build();
        PlanningItem item = PlanningItem.forTask(task);

        analyzer.classify(item, dailyHours, now);

        assertThat(item.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(item.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("analyzeAll classifies all items in batch")
    void testAnalyzeAll() {
        Task t1 = Task.builder().id(1L).title("T1").remainingHours(2.0).dueDateTime(LocalDateTime.of(2026, 9, 11, 23, 59)).build();
        Task t2 = Task.builder().id(2L).title("T2").remainingHours(20.0).dueDateTime(LocalDateTime.of(2026, 9, 11, 23, 59)).build();

        PlanningItem item1 = PlanningItem.forTask(t1);
        PlanningItem item2 = PlanningItem.forTask(t2);

        analyzer.analyzeAll(List.of(item1, item2), dailyHours, now);

        assertThat(item1.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(item2.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
    }
}
