package com.academicplanner.planning;

import com.academicplanner.entity.Assessment;
import com.academicplanner.entity.Assessment.AssessmentType;
import com.academicplanner.entity.StudyPlan;
import com.academicplanner.entity.StudyPlanItem;
import com.academicplanner.entity.Task;
import com.academicplanner.planning.model.DailyAvailability;
import com.academicplanner.planning.model.PlanningItem;
import com.academicplanner.planning.model.TimeSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionScheduler")
class SessionSchedulerTest {

    private SessionScheduler scheduler;
    private StudyPlan dummyPlan;

    @BeforeEach
    void setUp() {
        scheduler = new SessionScheduler();
        dummyPlan = StudyPlan.builder().id(1L).build();
    }

    @Test
    @DisplayName("Mode A (Hours only): Sessions are created with null startTime and endTime (blank time column) and populate activityLabel")
    void testModeAHoursOnlyNoInventedTimes() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        Task task = Task.builder().id(1L).title("Practice Problems").build();
        PlanningItem item = PlanningItem.forTask(task);
        item.setAllocatedHours(2.0);

        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(item), Map.of(day, 4.0));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getStartTime()).isNull();
        assertThat(items.get(0).getEndTime()).isNull();
        assertThat(items.get(0).getPlannedHours()).isEqualTo(2.0);
        assertThat(items.get(0).getActivityLabel()).isEqualTo("Practice Problems");
        assertThat(items.get(0).getActivityType()).isEqualTo("TASK");
    }

    @Test
    @DisplayName("Mode B (Hours + Time Slots): Schedules inside user-specified time slots with activityLabel")
    void testModeBTimeSlotsScheduling() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        Assessment assessment = Assessment.builder().id(1L).title("DSA Quiz").type(AssessmentType.QUIZ).build();
        PlanningItem item = PlanningItem.forAssessment(assessment);
        item.setAllocatedHours(2.0);

        DailyAvailability avail = new DailyAvailability(
                day,
                4.0,
                List.of(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(12, 0)))
        );

        List<StudyPlanItem> items = scheduler.scheduleWithAvailability(dummyPlan, List.of(item), Map.of(day, avail));

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(items.get(0).getPlannedHours()).isEqualTo(2.0);
        assertThat(items.get(0).getActivityLabel()).isEqualTo("DSA Quiz Quiz Preparation");
        assertThat(items.get(0).getActivityType()).isEqualTo("ASSESSMENT_PREP");
    }

    @Test
    @DisplayName("Mode B (Multiple Slots): Respects multiple slots and pauses between slots")
    void testModeBMultipleTimeSlots() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningItem item1 = PlanningItem.forTask(Task.builder().id(1L).title("T1").build());
        item1.setAllocatedHours(2.0);

        PlanningItem item2 = PlanningItem.forTask(Task.builder().id(2L).title("T2").build());
        item2.setAllocatedHours(2.0);

        DailyAvailability avail = new DailyAvailability(
                day,
                6.0,
                List.of(
                        new TimeSlot(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                        new TimeSlot(LocalTime.of(18, 0), LocalTime.of(20, 0))
                )
        );

        List<StudyPlanItem> items = scheduler.scheduleWithAvailability(dummyPlan, List.of(item1, item2), Map.of(day, avail));

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(items.get(0).getEndTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(items.get(1).getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(items.get(1).getEndTime()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    @DisplayName("Maximum single session is 3 hours with 30 min break inside slot")
    void testMaxSessionAndBreakInSlot() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningItem item = PlanningItem.forTask(Task.builder().id(1L).title("Deep Study").build());
        item.setAllocatedHours(5.0);

        DailyAvailability avail = new DailyAvailability(
                day,
                8.0,
                List.of(new TimeSlot(LocalTime.of(9, 0), LocalTime.of(17, 0)))
        );

        List<StudyPlanItem> items = scheduler.scheduleWithAvailability(dummyPlan, List.of(item), Map.of(day, avail));

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
    @DisplayName("Never schedules after deadline date or exact deadline timestamp")
    void testNeverSchedulesAfterDeadline() {
        LocalDate friday = LocalDate.of(2026, 9, 11);
        LocalDate saturday = LocalDate.of(2026, 9, 12);

        LocalDateTime deadline = LocalDateTime.of(2026, 9, 11, 13, 0);
        Task task = Task.builder().id(1L).title("Friday Test").dueDateTime(deadline).build();
        PlanningItem item = PlanningItem.forTask(task);
        item.setAllocatedHours(4.0);

        DailyAvailability fridayAvail = new DailyAvailability(
                friday,
                6.0,
                List.of(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(16, 0)))
        );
        DailyAvailability saturdayAvail = new DailyAvailability(
                saturday,
                6.0,
                List.of(new TimeSlot(LocalTime.of(8, 0), LocalTime.of(16, 0)))
        );

        Map<LocalDate, DailyAvailability> daily = new LinkedHashMap<>();
        daily.put(friday, fridayAvail);
        daily.put(saturday, saturdayAvail);

        List<StudyPlanItem> items = scheduler.scheduleWithAvailability(dummyPlan, List.of(item), daily);

        // All items must end on or before Friday 13:00, none on Saturday
        for (StudyPlanItem planItem : items) {
            assertThat(planItem.getDate()).isEqualTo(friday);
            assertThat(planItem.getEndTime()).isBeforeOrEqualTo(LocalTime.of(13, 0));
        }
    }

    @Test
    @DisplayName("Total planned hours never exceeds daily capacity")
    void testNeverExceedsDailyCapacity() {
        LocalDate day = LocalDate.of(2026, 9, 7);
        PlanningItem item1 = PlanningItem.forTask(Task.builder().id(1L).title("T1").build());
        item1.setAllocatedHours(4.0);

        PlanningItem item2 = PlanningItem.forTask(Task.builder().id(2L).title("T2").build());
        item2.setAllocatedHours(4.0);

        // Only 3 hours available that day
        List<StudyPlanItem> items = scheduler.schedule(dummyPlan, List.of(item1, item2), Map.of(day, 3.0));

        double sum = items.stream().mapToDouble(StudyPlanItem::getPlannedHours).sum();
        assertThat(sum).isLessThanOrEqualTo(3.0);
    }
}
