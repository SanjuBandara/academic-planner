package com.academicplanner.planning;

import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import com.academicplanner.planning.model.PlanningCandidate.FeasibilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeasibilityAnalyzerTest {

    private FeasibilityAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new FeasibilityAnalyzer();
    }

    @Test
    @DisplayName("Remaining hours <= available capacity -> FEASIBLE")
    void testFeasibleStatus() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Assignment").build(),
                LocalDateTime.of(2026, 9, 15, 17, 0));
        c.setRemainingWorkHours(6.0);
        c.setAvailableHoursBeforeDeadline(10.0);

        analyzer.classify(c);

        assertThat(c.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(c.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("Remaining hours slightly above available (<= 1.2x) -> AT_RISK")
    void testAtRiskStatus() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("Math Quiz").build(),
                LocalDateTime.of(2026, 9, 15, 17, 0));
        c.setRemainingWorkHours(11.0);
        c.setAvailableHoursBeforeDeadline(10.0); // 11 <= 10 * 1.2 (12.0)

        analyzer.classify(c);

        assertThat(c.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.AT_RISK);
        assertThat(c.getFeasibilityWarning()).isNotNull();
        assertThat(c.getFeasibilityWarning()).contains("AT RISK");
    }

    @Test
    @DisplayName("Remaining hours significantly exceed available (> 1.2x) -> IMPOSSIBLE")
    void testImpossibleStatus() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(3L).title("Large Project").build(),
                LocalDateTime.of(2026, 9, 15, 17, 0));
        c.setRemainingWorkHours(15.0);
        c.setAvailableHoursBeforeDeadline(6.0); // 15 > 6 * 1.2 (7.2)

        analyzer.classify(c);

        assertThat(c.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
        assertThat(c.getFeasibilityWarning()).isNotNull();
        assertThat(c.getFeasibilityWarning()).contains("CANNOT be completed");
    }

    @Test
    @DisplayName("No deadline is always FEASIBLE")
    void testNoDeadlineAlwaysFeasible() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(4L).title("Long-term reading").build(),
                null);
        c.setRemainingWorkHours(25.0);
        c.setAvailableHoursBeforeDeadline(5.0);

        analyzer.classify(c);

        assertThat(c.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(c.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("Zero remaining work is always FEASIBLE")
    void testZeroRemainingWorkFeasible() {
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(5L).title("Finished task").build(),
                LocalDateTime.of(2026, 9, 10, 12, 0));
        c.setRemainingWorkHours(0.0);
        c.setAvailableHoursBeforeDeadline(0.0);

        analyzer.classify(c);

        assertThat(c.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(c.getFeasibilityWarning()).isNull();
    }

    @Test
    @DisplayName("analyzeAll classifies all candidates in batch")
    void testAnalyzeAll() {
        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), LocalDateTime.now().plusDays(2));
        c1.setRemainingWorkHours(2.0);
        c1.setAvailableHoursBeforeDeadline(5.0);

        PlanningCandidate c2 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("T2").build(), LocalDateTime.now().plusDays(2));
        c2.setRemainingWorkHours(10.0);
        c2.setAvailableHoursBeforeDeadline(3.0);

        List<PlanningCandidate> list = List.of(c1, c2);
        analyzer.analyzeAll(list);

        assertThat(c1.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(c2.getFeasibilityStatus()).isEqualTo(FeasibilityStatus.IMPOSSIBLE_WITH_CURRENT_AVAILABILITY);
    }
}
