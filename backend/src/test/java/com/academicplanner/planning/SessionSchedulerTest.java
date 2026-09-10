package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.PlanningCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSchedulerTest {

    private SessionScheduler scheduler;
    private StudyPlan dummyPlan;

    @BeforeEach
    void setUp() {
        scheduler = new SessionScheduler();
        dummyPlan = StudyPlan.builder().id(1L).build();
    }

    @Test
    @DisplayName("Default start time is 09:00")
    void testDefaultStartTime() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), null);
        c.setAllocatedHours(2.0);

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c), Map.of(day, 4.0));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(items.get(0).getPlannedHours()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Maximum single session is 3 hours with 30 min break")
    void testMaxSessionAndBreak() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Deep Study").build(), null);
        c.setAllocatedHours(5.0); // 5 hours allocated on an 8 hour day

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c), Map.of(day, 8.0));

        assertThat(items).hasSize(2);
        // Session 1: 09:00 -> 12:00 (3h)
        assertThat(items.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(items.get(0).getPlannedHours()).isEqualTo(3.0);

        // Break: 30 minutes (since >= 2h) -> Session 2 starts at 12:30
        assertThat(items.get(1).getStartTime()).isEqualTo(LocalTime.of(12, 30));
        assertThat(items.get(1).getEndTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(items.get(1).getPlannedHours()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Sessions under 2 hours have a 15 minute gap")
    void testShortSessionGap() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), null);
        c1.setAllocatedHours(1.5);

        PlanningCandidate c2 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("T2").build(), null);
        c2.setAllocatedHours(1.0);

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c1, c2), Map.of(day, 6.0));

        assertThat(items).hasSize(2);
        // Session 1: 09:00 -> 10:30 (1.5h)
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(10, 30));

        // Gap: 15 minutes (since < 2h) -> Session 2 starts at 10:45
        assertThat(items.get(1).getStartTime()).isEqualTo(LocalTime.of(10, 45));
        assertThat(items.get(1).getEndTime()).isEqualTo(LocalTime.of(11, 45));
    }

    @Test
    @DisplayName("Never schedules after deadline date or exact deadline timestamp")
    void testNeverSchedulesAfterDeadline() {
        LocalDate friday = LocalDate.of(2026, 9, 11);
        LocalDate saturday = LocalDate.of(2026, 9, 12);

        Map<LocalDate, Double> daily = new LinkedHashMap<>();
        daily.put(friday, 6.0);
        daily.put(saturday, 6.0);

        // Deadline is Friday 13:00
        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 13, 0);
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("Friday Test").build(), deadline);
        c.setAllocatedHours(4.0);

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c), daily);

        // All items must end on or before Friday 13:00, none on Saturday
        for (StudyPlanItem item : items) {
            assertThat(item.getDate()).isEqualTo(friday);
            assertThat(item.getEndTime()).isBeforeOrEqualTo(LocalTime.of(13, 0));
        }
    }

    @Test
    @DisplayName("Sessions are rounded to 15-minute increments (0.25h, 0.5h, 0.75h, etc.)")
    void testFifteenMinuteRounding() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningCandidate c = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), null);
        c.setAllocatedHours(1.25);

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c), Map.of(day, 4.0));

        assertThat(items).hasSize(1);
        double hours = items.get(0).getPlannedHours();
        // hours * 4 must be an integer
        assertThat(hours * 4).isEqualTo(Math.round(hours * 4));
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(10, 15));
    }

    @Test
    @DisplayName("Total planned hours never exceeds daily capacity")
    void testNeverExceedsDailyCapacity() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningCandidate c1 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(1L).title("T1").build(), null);
        c1.setAllocatedHours(4.0);

        PlanningCandidate c2 = PlanningCandidate.forStandaloneTask(
                Task.builder().id(2L).title("T2").build(), null);
        c2.setAllocatedHours(4.0);

        // Only 3 hours available that day
        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(c1, c2), Map.of(day, 3.0));

        double sum = items.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(sum).isLessThanOrEqualTo(3.0);
    }
}
